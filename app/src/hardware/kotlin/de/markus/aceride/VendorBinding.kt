package de.markus.aceride

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.arashivision.sdk.camera.api.CameraDevice
import com.arashivision.sdk.camera.api.CameraCapture
import com.arashivision.sdk.camera.core.callback.BleScanCallback
import com.arashivision.inskmp.insble.data.BleDeviceCore
import com.arashivision.inskmp.insble.BleManagerCore
import com.arashivision.inskmp.insble.callback.BleStopWakeupCallbackCore
import com.arashivision.sdk.camera.api.param.listener.*
import com.arashivision.sdk.camera.config.acepro.AceProCameraConfig
import com.arashivision.sdk.camera.core.model.CameraType
import com.arashivision.sdk.camera.core.model.ConnectType
import com.arashivision.sdk.camera.core.model.FunctionMode
import com.arashivision.sdk.camera.core.model.option.BatteryData
import com.arashivision.sdk.camera.core.model.option.StorageData
import com.arashivision.sdk.camera.core.model.authorization.*
import com.arashivision.sdk.camera.core.model.capture.CameraCaptureStatus
import de.markus.aceride.core.CameraAdapter.*
import de.markus.aceride.core.Insta360V2Adapter.V2Api
import de.markus.aceride.core.SdkStatusMapper
import de.markus.aceride.core.Diagnostics.Event
import de.markus.aceride.core.QuietRelease
import de.markus.aceride.core.CameraModels
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** BLE-only binding against the supplied, pinned Camera SDK 2.1.5. No settings writes. */
class VendorBinding(context: Context) : V2Api {
    private val app = context.applicationContext
    private val store = CameraStore(app)
    private val log = de.markus.aceride.core.Diagnostics.NONE
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val ioLock = Mutex()
    private val generation = AtomicLong()
    private val closed = AtomicBoolean()
    private val trafficSuspended = AtomicBoolean(true)
    @Volatile private var device: CameraDevice? = null
    @Volatile private var address: String? = null
    @Volatile private var subscription: Subscription? = null
    private var wakeAt = -1L
    private data class BatterySample(val percent: Int?, val at: Long, val diagnostic: String)
    private data class StorageSample(val value: Storage, val diagnostic: String)
    private val batterySample = AtomicReference(BatterySample(null, -1, "Akku: noch keine Kameradaten"))
    private val storageSample = AtomicReference(StorageSample(Storage.unknown(), "SD: noch keine Kameradaten"))
    @Volatile private var telemetryJob: Job? = null
    private var telemetryResumeAt = 0L
    private var telemetryOwner: CameraDevice? = null

    private fun ensureDevice(): CameraDevice {
        check(!closed.get()) { "Kamerasitzung beendet" }
        return device ?: CameraDevice.get(ConnectType.BLE).also { device = it }
    }
    private fun connectedDevice(): CameraDevice = device?.takeIf { it.isConnected() }
        ?: error("Keine Kameraverbindung. Kamera einschalten und Verbinden wählen.")

    private fun <T> future(timeout: Long = 5000, block: suspend () -> T?): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        if (closed.get()) return CompletableFuture.failedFuture(IllegalStateException("Kamerasitzung beendet"))
        val lease = generation.get()
        val job = scope.launch {
            try {
                check(lease == generation.get() && !closed.get()) { "Verbindung wurde inzwischen beendet" }
                val value = withTimeout(timeout) { block() }
                check(lease == generation.get() && !closed.get()) { "Verbindung wurde inzwischen beendet" }
                result.complete(value)
            } catch (ex: TimeoutCancellationException) {
                result.completeExceptionally(IllegalStateException("Kamera antwortet nicht rechtzeitig. Zustand unbekannt."))
            } catch (ex: LinkageError) {
                result.completeExceptionally(IllegalStateException("Native Kamera-Bibliothek konnte nicht geladen werden. Android-Version und ABI prüfen."))
            } catch (ex: Exception) { result.completeExceptionally(ex) }
        }
        result.whenComplete { _, _ -> if (result.isCancelled) job.cancel() }
        job.invokeOnCompletion { cause ->
            if (!result.isDone) result.completeExceptionally(cause ?: IllegalStateException("Kamerasitzung beendet"))
        }
        return result
    }

    override fun connectBle(address: String): CompletableFuture<Void> = future(27000) {
        log.record(Event.SDK_CONNECT_BEGIN)
        trafficSuspended.set(false)
        check(app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            "Bluetooth-Verbindungsberechtigung fehlt"
        }
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("Bluetooth ist nicht verfügbar")
        check(adapter.isEnabled) { "Bluetooth am Karoo einschalten" }
        val camera = ensureDevice()
        this.address = address
        subscription?.attach(camera)
        if (wakeAt >= 0) delay((wakeAt + 1500 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        if (store.load()?.model() == CameraModels.GO) {
            // The GO connection handshake uses the advertised model name to send
            // the SDK authorization UUID and enable its GO-specific heartbeat.
            // Resolve the exact saved address afresh rather than relying on an OS name cache.
            val target = CompletableFuture<BleDeviceCore>()
            camera.scan(6000, object : BleScanCallback {
                override fun onStarted() {}
                override fun onScanning(bleDevice: BleDeviceCore) {
                    if (bleDevice.mac.equals(address, ignoreCase = true) && CameraModels.discover(bleDevice.name) == CameraModels.GO)
                        target.complete(bleDevice)
                }
                override fun onFinished(bleDeviceList: List<BleDeviceCore>) {
                    target.completeExceptionally(IllegalStateException("GO 3S nicht erreichbar. Kamera einschalten und erneut verbinden."))
                }
                override fun onError(throwable: Throwable) { target.completeExceptionally(throwable) }
            })
            val found = try { withTimeout(7000) { await(target) } } finally { camera.stopScan() }
            camera.connectBle(found).getOrThrow()
        } else camera.connectBle(adapter.getRemoteDevice(address)).getOrThrow()
        check(camera.isConnected()) { "Kamera ist noch nicht verbunden" }
        log.record(Event.SDK_CONNECT_DONE)
        null
    }

    override fun checkAuthorization(): CompletableFuture<Boolean> = future(20000) {
        val camera = connectedDevice()
        // Ace Pro 1 does not need the extra GO-series authorization API (SDK config).
        // Successful BLE connect and actual model query still precede all commands.
        val config = camera.system.getSupportConfig().getOrThrow()
        if (!config.needAuthorization()) true else {
            val reply = CompletableFuture<Boolean>()
            val listener = object : AuthorizationListener {
                override fun onAuthorizationResult(operationType: AuthorizationOperationType, result: AuthorizationResult) {
                    if (operationType != AuthorizationOperationType.BLE_CONNECT || camera !== device) return
                    when (result) {
                        AuthorizationResult.SUCCESS -> reply.complete(true)
                        AuthorizationResult.REJECT -> reply.complete(false)
                        AuthorizationResult.TIMEOUT -> reply.completeExceptionally(IllegalStateException("Autorisierung abgelaufen. Am Action Pod oder mit dem GO-3S-Auslöser bestätigen und erneut verbinden."))
                        AuthorizationResult.SYSTEM_BUSY -> reply.completeExceptionally(IllegalStateException("Kamera ist beschäftigt. Erneut verbinden."))
                    }
                }
            }
            camera.registerAuthorizationListener(listener)
            try {
                when (camera.checkAuthorization().getOrThrow()) {
                    AuthorizationStatus.AUTHORIZED -> true
                    AuthorizationStatus.SYSTEM_BUSY -> error("Kamera ist beschäftigt. Erneut verbinden.")
                    AuthorizationStatus.UNAUTHORIZED -> {
                        // Exported SDK core operation, pinned by the existing Java bridge.
                        // Exactly one request; another approval operation cannot authorize BLE.
                        SdkCaptureAccess.core(camera).requestAuthorization(AuthorizationOperationType.BLE_CONNECT).getOrThrow()
                        await(reply) && camera.checkAuthorization().getOrThrow() == AuthorizationStatus.AUTHORIZED
                    }
                }
            } finally { camera.unregisterAuthorizationListener(listener) }
        }
    }

    override fun fetchExactModel(): CompletableFuture<String> = future(8000) {
        val camera = connectedDevice()
        val selectedAddress = address ?: error("Kameraauswahl fehlt")
        when (camera.system.fetchCameraType().getOrThrow()) {
            CameraType.ACE_PRO -> {
                // A missing wake identifier must not disable control of an already-on camera.
                val serial = withTimeoutOrNull(4000) { camera.system.fetchSerialNumber().getOrNull() }
                check(camera === device && selectedAddress == address) { "Kameraauswahl wurde geändert" }
                if (serial != null) runCatching { store.rememberWakeIdentifier(selectedAddress, serial) }
                "ACE_PRO_1"
            }
            CameraType.ACE_PRO_2 -> "ACE_PRO_2"
            CameraType.GO_3S -> CameraModels.GO
            else -> "OTHER"
        }
    }
    override fun loadCaptureJson(): CompletableFuture<Void> = future {
        val camera = connectedDevice()
        camera.capture.loadJson().getOrThrow()
        ensureTelemetry(camera)
        null
    }

    private suspend fun readStatus(camera: CameraDevice): Status = ioLock.withLock {
        check(!trafficSuspended.get() && camera === device && camera.isConnected()) { "Kameraverbindung ruht oder wurde verloren" }
        val queryStarted = SystemClock.elapsedRealtime()
        // isWorking() is cached. This sends a request with SDK response-ID matching.
        val raw = SdkCaptureAccess.core(camera).fetchCaptureState().getOrThrow()
        check(!trafficSuspended.get() && camera === device && camera.isConnected()) { "Kameraverbindung ruht oder wurde verloren" }
        val currentMode = camera.capture.functionMode.fetchValue().getOrThrow()
        val mode = if (currentMode.isCaptureSupported) SdkStatusMapper.mode(currentMode.name) else Mode.OTHER
        val recording = SdkStatusMapper.recording(raw.status.name, raw.subStatus.name, mode)
        // Optional information never delays the actual capture/mode query.
        ensureTelemetry(camera)
        val battery = batterySample.get()
        val storage = storageSample.get()
        check(!trafficSuspended.get() && camera === device && camera.isConnected()) { "Kameraverbindung ruht oder wurde verloren" }
        val seconds: Long? = when (recording) {
            Recording.IDLE -> 0L
            Recording.RECORDING -> raw.captureTime.takeIf { it >= 0 }?.toLong()
            else -> null
        }
        Status(recording, mode, battery.percent, seconds, queryStarted, storage.value, battery.at,
            "${battery.diagnostic}\n${storage.diagnostic}\nKamera-Aufnahmezähler: ${raw.captureTime}s")
    }
    override fun readFreshCaptureAndMode(): CompletableFuture<Status> = future(5500) {
        readStatus(connectedDevice())
    }
    override fun startCapture(): CompletableFuture<Void> = future(3500) {
        pauseTelemetry()
        val camera = connectedDevice()
        val mode = camera.capture.functionMode.fetchValue().getOrThrow()
        check(camera === device && camera.isConnected()) { "Kameraverbindung verloren" }
        check(mode.isCaptureSupported && SdkStatusMapper.mode(mode.name) == Mode.VIDEO) {
            "Dieser Kameramodus ist nicht für Video steuerbar. An der Kamera einen unterstützten Videomodus wählen."
        }
        camera.capture.startCapture()
        null // A returned command is deliberately not a recording confirmation.
    }
    override fun stopCapture(): CompletableFuture<Void> = future(3500) {
        pauseTelemetry()
        connectedDevice().capture.stopCapture()
        null
    }
    override fun shutdown(): CompletableFuture<Void> {
        trafficSuspended.set(true)
        telemetryJob?.cancel(); telemetryJob = null
        subscription?.close(); subscription = null
        return future(3500) {
            val camera = connectedDevice()
            check(camera.system.getSupportConfig().getOrThrow().supportShutdown()) { "SDK unterstützt Ausschalten für diese Verbindung nicht" }
            log.record(Event.SDK_SHUTDOWN_SEND)
            camera.system.shutdown()
            log.record(Event.SDK_SHUTDOWN_SENT)
            // The native BLE write is queued. Allow a short bounded send window,
            // then the adapter releases the whole SDK device on every outcome.
            delay(250)
            null // One-way request, no camera-off confirmation in this API.
        }
    }
    private fun rememberBattery(camera: CameraDevice, data: BatteryData) {
        if (camera !== device || !camera.isConnected()) return
        val value = if (data.type == BatteryData.Type.NONE) null else SdkStatusMapper.battery(data.level, data.scale)
        batterySample.set(BatterySample(value, SystemClock.elapsedRealtime(),
            "Akku SDK: level=${data.level}, scale=${data.scale}, Typ=${data.type.name}"))
    }
    private fun rememberStorage(camera: CameraDevice, values: List<StorageData>) {
        if (camera !== device || !camera.isConnected()) return
        val sd = values.firstOrNull { it.fileLocation == StorageData.FileLocation.CAMERA || it.fileLocation == StorageData.FileLocation.SD }
        val value = sd?.let { SdkStatusMapper.storage(it.state.name, it.fileLocation.name, it.free, it.total, SystemClock.elapsedRealtime()) }
            ?: Storage.unknown()
        storageSample.set(StorageSample(value, sd?.let { "SD SDK: ${it.state.name}, frei=${it.free}, gesamt=${it.total} Byte" }
            ?: "SD: SDK liefert keinen Kameraspeicher"))
    }
    private suspend fun <T> optionalRead(block: suspend () -> Result<T>): Pair<T?, String> {
        return try {
            val result = withTimeoutOrNull(4000) { block() }
            if (result == null) null to "Zeitüberschreitung" else
                result.getOrNull() to (result.exceptionOrNull()?.javaClass?.simpleName ?: "OK")
        } catch (ex: CancellationException) { throw ex }
        catch (ex: Exception) { null to ex.javaClass.simpleName }
        catch (ex: LinkageError) { null to "Native SDK-Abfrage nicht verfügbar" }
    }
    private fun pauseTelemetry() {
        telemetryResumeAt = SystemClock.elapsedRealtime() + 4000
        telemetryJob?.cancel(); telemetryJob = null
    }
    private fun ensureTelemetry(camera: CameraDevice) {
        if (trafficSuspended.get()) return
        if (telemetryJob?.isActive == true && telemetryOwner === camera) return
        telemetryJob?.cancel(); telemetryOwner = camera
        val lease = generation.get()
        // SDK option/capture queries match separate response IDs. Only one optional
        // request is in flight; capture commands remain serialized by Controller.
        telemetryJob = scope.launch {
            delay((telemetryResumeAt-SystemClock.elapsedRealtime()).coerceAtLeast(250))
            while (!closed.get() && !trafficSuspended.get() && generation.get() == lease && camera === device && camera.isConnected()) {
                val (battery, bError) = optionalRead { camera.system.fetchBatteryData() }
                if (generation.get() != lease || camera !== device) break
                if (battery != null) rememberBattery(camera, battery) else
                    batterySample.updateAndGet { it.copy(diagnostic = "Akku-Abfrage: $bError") }
                val (storage, sError) = optionalRead { camera.system.fetchStorageDataList() }
                if (generation.get() != lease || camera !== device) break
                if (storage != null) rememberStorage(camera, storage) else
                    storageSample.updateAndGet { it.copy(diagnostic = "SD-Abfrage: $sError") }
                delay(8000)
            }
        }
    }
    override fun bleSupportsOriginalAcePro() = AceProCameraConfig(ConnectType.BLE).supportBleBroadcastWakeUp()
    override fun advertiserAvailable(): Boolean {
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) return false
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
            adapter?.isEnabled == true && adapter.isMultipleAdvertisementSupported && adapter.bluetoothLeAdvertiser != null
        } catch (ex: SecurityException) { false }
    }
    override fun bleWakeUp(exactModel: String, bleName: String): CompletableFuture<Void> = future(12000) {
        check(exactModel == "ACE_PRO_1" && bleSupportsOriginalAcePro()) { "Aufwecken nur für die ursprüngliche Ace Pro" }
        check(advertiserAvailable()) { "Bluetooth-Advertising fehlt. Berechtigung und Gerätefähigkeiten prüfen." }
        val identifier = store.wakeIdentifier()
            ?: error("Aufweckkennung fehlt. Kamera zuerst eingeschaltet auswählen und verbinden.")
        check(identifier.matches(Regex("[A-Z0-9]{6}"))) { "Aufweckkennung ungültig. Eingeschaltet erneut verbinden." }
        val reply = CompletableFuture<Void>()
        log.record(Event.WAKE_START)
        ensureDevice().bleWakeUp(CameraType.ACE_PRO, identifier, object : BleWakeUpListener {
            override fun onWakeUpSuccess() { log.record(Event.WAKE_ADVERTISE_STARTED); reply.complete(null) }
            override fun onWakeUpError(errCode: Int) {
                reply.completeExceptionally(IllegalStateException("Aufwecksignal fehlgeschlagen ($errCode). Bluetooth Wakeup an der Kamera prüfen; sonst manuell einschalten."))
            }
        })
        try {
            await(reply)
            wakeAt = SystemClock.elapsedRealtime()
            delay(1500) // A bounded broadcast window; never leave wake advertising on.
        } finally {
            stopWakeAdvertising()
        }
        null // Only signal completion; Controller connects and queries the real state next.
    }

    private fun stopWakeAdvertising() {
        // GO needs no advertising permission and never starts this broadcast.
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) return
        log.record(Event.WAKE_STOP_REQUEST)
        BleManagerCore.stopWakeupAdvertise(object : BleStopWakeupCallbackCore {
            override fun stopWakeupSuccess() { log.record(Event.WAKE_STOPPED) }
            override fun stopWakeupError(errorCode: Int) { log.record(Event.WAKE_STOP_ERROR) }
        })
    }

    private inner class Subscription(val status: Consumer<Status>, val drop: Consumer<String>) : AutoCloseable {
        private val active = AtomicBoolean(true)
        private var owner: CameraDevice? = null
        private var capture: CameraCapture? = null
        private var refresh: Job? = null
        private fun requestQuery() {
            if (!active.get() || trafficSuspended.get() || refresh?.isActive == true) return
            val camera = owner ?: return
            refresh = scope.launch {
                delay(150)
                if (!active.get() || trafficSuspended.get()) return@launch
                try {
                    val value = withTimeout(5500) { readStatus(camera) }
                    if (active.get() && camera === device) status.accept(value)
                } catch (ex: Exception) { /* Controller polling handles missing replies and staleness. */ }
            }
        }
        private val captureListener = object : CaptureStatusListener {
            override fun onCaptureStarting(functionMode: FunctionMode) { requestQuery() }
            override fun onCaptureWorking(functionMode: FunctionMode) { requestQuery() }
            override fun onCaptureStopping(functionMode: FunctionMode) { requestQuery() }
            override fun onCaptureFinish(functionMode: FunctionMode, filePaths: List<String>) { requestQuery() }
            override fun onCaptureError(functionMode: FunctionMode, throwable: Throwable) { requestQuery() }
            // SDK time callbacks are locally generated timers, not fresh camera proof.
            override fun onCaptureTimeChanged(functionMode: FunctionMode, captureTime: Long) {}
            override fun onCaptureCountChanged(functionMode: FunctionMode, captureCount: Int) {}
            override fun onCaptureSubStatusChanged(functionMode: FunctionMode, subStatus: CameraCaptureStatus.SubStatus) { requestQuery() }
        }
        private val disconnectListener = object : DisconnectListener {
            override fun onDisconnect(throwable: Throwable?) {
                log.record(Event.SDK_DISCONNECT_CALLBACK)
                if (active.get()) drop.accept("Verbindung verloren – Aufnahme an der Kamera prüfen")
            }
        }
        private val batteryListener = object : BatteryListener {
            override fun onBatteryLevelChange(batteryData: BatteryData) {
                owner?.let { rememberBattery(it, batteryData) }
            }
            override fun onLowBatteryWarning() { requestQuery() }
        }
        private val storageListener = object : StorageStateListener {
            override fun onStorageStateListChanged(storageDataList: List<StorageData>) {
                if (active.get()) owner?.let { rememberStorage(it, storageDataList) }
            }
        }
        fun attach(camera: CameraDevice) {
            if (!active.get()) return
            owner = camera; capture = camera.capture
            camera.registerDisconnectListener(disconnectListener)
            camera.system.registerBatteryListener(batteryListener)
            camera.system.registerStorageStatusListener(storageListener)
            capture?.registerCaptureStatusListener(captureListener)
        }
        override fun close() {
            if (!active.getAndSet(false)) return
            refresh?.cancel()
            val camera = owner; val cap = capture
            main.post {
                camera?.unregisterDisconnectListener(disconnectListener)
                camera?.system?.unregisterBatteryListener(batteryListener)
                camera?.system?.unregisterStorageStatusListener(storageListener)
                cap?.unregisterCaptureStatusListener(captureListener)
            }
            owner = null; capture = null
        }
    }
    override fun register(status: Consumer<Status>, disconnect: Consumer<String>): AutoCloseable {
        subscription?.close()
        return Subscription(status, disconnect).also { subscription = it }
    }
    override fun disconnectBle() {
        trafficSuspended.set(true)
        generation.incrementAndGet()
        subscription?.close(); subscription = null
        val old = device; device = null; address = null
        telemetryJob?.cancel(); telemetryJob = null; telemetryOwner = null
        batterySample.set(BatterySample(null, -1, "Akku: nicht verbunden"))
        storageSample.set(StorageSample(Storage.unknown(), "SD: nicht verbunden"))
        if (old != null) main.post {
            QuietRelease.release(object : QuietRelease.Port {
                override fun stopAdvertising() = stopWakeAdvertising()
                // CameraDevice.release -> UnifiedTransport.disconnect sends a BLE
                // command. Destroy this SDK's GATT first, so it cannot wake standby.
                override fun closeGatt() { BleManagerCore.destroy() }
                override fun releaseDevice() { old.release() }
            }, log)
        }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        disconnectBle(); scope.cancel()
    }
    private suspend fun <T> await(future: CompletableFuture<T>): T = suspendCancellableCoroutine { continuation ->
        future.whenComplete { value, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(value) else continuation.resumeWithException(error)
            }
        }
        continuation.invokeOnCancellation { future.cancel(true) }
    }
}
