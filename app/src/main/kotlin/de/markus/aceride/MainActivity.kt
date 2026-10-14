package de.markus.aceride

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.view.View
import android.widget.*
import de.markus.aceride.core.CameraAdapter.Camera
import de.markus.aceride.core.Controller
import de.markus.aceride.core.CameraModels
import de.markus.aceride.core.CameraAdapter.Recording

/** Fixed ride controls; scrolling is confined to the first-time setup dialog. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var title: TextView
    private lateinit var connection: TextView
    private lateinit var battery: TextView
    private lateinit var duration: TextView
    private lateinit var storage: TextView
    private lateinit var message: TextView
    private var cameraLabel: TextView? = null
    private lateinit var list: LinearLayout
    private lateinit var root: LinearLayout
    private var setupDialog: AlertDialog? = null
    private var consentDialog: AlertDialog? = null
    private var cameraDialog: AlertDialog? = null
    private var languageDialog: AlertDialog? = null
    private var scan: ScanCallback? = null
    private val found = mutableSetOf<String>()
    private var pendingAction: Controller.Action? = null
    private var pendingScan = false
    private var pendingChoose = false
    private var startingSession = false
    private var active = false
    private var localError: String? = null
    private val scanStop = Runnable { stopScan(); cameraLabel?.text = UiText.get(R.string.scan_finished) }
    private val redraw = object : Runnable {
        override fun run() {
            if (!active) return
            val state = SessionRuntime.controller?.state()
            val camera = state?.camera() ?: CameraStore(this@MainActivity).load()
            findViewById<TextView>(R.id.screen_camera).apply {
                text = camera?.let { "${CameraModels.label(it.model())} ▾" } ?: UiText.get(R.string.control_camera_select)
                contentDescription = camera?.let { UiText.get(R.string.camera_active, CameraModels.label(it.model())) } ?: UiText.get(R.string.message_001)
            }
            findViewById<View>(R.id.screen_settings).contentDescription = UiText.get(R.string.control_settings)
            findViewById<View>(R.id.screen_back).contentDescription = UiText.get(R.string.control_back)
            title.text = StateText.title(state)
            findViewById<View>(R.id.screen_status).setBackgroundColor(
                if (state?.recording() == de.markus.aceride.core.CameraAdapter.Recording.RECORDING)
                    Color.rgb(177, 14, 35) else Color.rgb(22, 26, 32))
            connection.text = StateText.connection(state)
            battery.text = StateText.battery(state)
            duration.text = StateText.duration(state)
            storage.text = StateText.storage(state)
            message.text = localError?.let { UiText.message(it) } ?: StateText.message(state)
            cameraLabel?.text = CameraStore(this@MainActivity).load()?.bleName() ?: UiText.get(R.string.camera_none)
            ControlViews.update(this@MainActivity, state)
            handler.postDelayed(this, 500)
        }
    }
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(UiLanguage.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 11, 15)
        window.navigationBarColor = Color.rgb(9, 11, 15)
        setContentView(R.layout.activity_control)
        title = findViewById(R.id.screen_state)
        connection = findViewById(R.id.screen_connection)
        battery = findViewById(R.id.screen_battery)
        duration = findViewById(R.id.screen_time)
        storage = findViewById(R.id.screen_storage)
        message = findViewById(R.id.screen_message)
        findViewById<View>(R.id.screen_back).setOnClickListener { finish() }
        findViewById<View>(R.id.screen_settings).setOnClickListener { showSetup() }
        findViewById<View>(R.id.screen_camera).setOnClickListener { showCameraChoice() }
        findViewById<View>(R.id.screen_status).setOnClickListener { showStatus() }
        message.setOnClickListener { showStatus() }
        for (item in ControlViews.items) findViewById<View>(item.view).setOnClickListener {
            localError = null
            withPermissions(de.markus.aceride.core.RideControls.action(item.slot, SessionRuntime.controller?.state()))
        }
        pendingAction = intentAction(intent)
        pendingChoose = intent.getBooleanExtra("chooseCamera", false)
        intent.removeExtra("chooseCamera")
        if (!SdkConsent.accepted(this)) {
            pendingAction = null // Acceptance alone must never begin a recording.
            showSdkConsent()
        } else if (basePermissions()) startSession() else withPermissions(pendingAction)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.getBooleanExtra("chooseCamera", false)) {
            intent.removeExtra("chooseCamera"); pendingChoose = true; withPermissions(null); return
        }
        intentAction(intent)?.let { localError = null; withPermissions(it) }
    }
    override fun onStart() { super.onStart(); active = true; handler.post(redraw) }
    override fun onStop() { active = false; handler.removeCallbacks(redraw); stopScan(); super.onStop() }
    override fun onDestroy() {
        consentDialog?.dismiss()
        languageDialog?.dismiss()
        cameraDialog?.dismiss(); setupDialog?.dismiss(); handler.removeCallbacksAndMessages(null); super.onDestroy()
    }
    private fun showSdkConsent() {
        if (consentDialog?.isShowing == true) return
        consentDialog = AlertDialog.Builder(this).setTitle(UiText.get(R.string.consent_title))
            .setMessage(UiText.get(R.string.consent_body))
            .setPositiveButton(UiText.get(R.string.consent_accept)) { _, _ ->
                if (SdkConsent.accept(this)) withPermissions(null)
                else showError(UiText.get(R.string.consent_failed))
            }
            .setNegativeButton(UiText.get(R.string.consent_exit)) { _, _ -> finish() }
            .setCancelable(false).create().also {
                it.setOnDismissListener { consentDialog = null }; it.show()
            }
    }
    private fun showStatus() {
        val state = SessionRuntime.controller?.state()
        val offHint = if (state?.power() == Controller.Power.OFF_REQUESTED)
            UiText.get(R.string.off_hint) else ""
        AlertDialog.Builder(this).setTitle(StateText.title(state))
            .setMessage(UiText.get(R.string.status_body,
                state?.camera()?.let { CameraModels.label(it.model()) } ?: UiText.get(R.string.camera_none),
                StateText.detail(state),
                (localError ?: state?.message())?.let { UiText.message(it) } ?: UiText.get(R.string.control_select_first), offHint))
            .setPositiveButton(UiText.get(R.string.close), null).show()
    }
    private fun showCameraChoice() {
        if (cameraDialog?.isShowing == true) return
        if (!basePermissions()) { withPermissions(null); return }
        val store = CameraStore(this)
        val cameras = store.all()
        val selected = store.load()?.address()
        val labels = cameras.map { camera ->
            "${if (camera.address() == selected) "● " else ""}${CameraModels.label(camera.model())}\n${camera.bleName()}"
        } + UiText.get(R.string.camera_add)
        cameraDialog = AlertDialog.Builder(this).setTitle(UiText.get(R.string.message_001))
            .setItems(labels.toTypedArray()) { _, index ->
                if (index == cameras.size) {
                    showSetup(); pendingScan = true; withPermissions(null)
                } else switchCamera(cameras[index])
            }.setNegativeButton(UiText.get(R.string.cancel), null).create().also {
                it.setOnDismissListener { cameraDialog = null }; it.show()
            }
    }
    private fun switchCamera(camera: Camera) {
        val ctl = SessionRuntime.controller
        if (ctl?.state()?.let { (it.busy() && !it.background()) || it.recording() == Recording.RECORDING || it.recording() == Recording.TRANSITION } == true) {
            showError(UiText.get(R.string.camera_switch_wait)); return
        }
        val activate = {
            runCatching {
                pendingAction = null; pendingScan = false
                stopScan(); SessionRuntime.select(this, camera); localError = null
                setupDialog?.dismiss()
                // Only connect/status. A saved pause never resumes on camera selection.
                withPermissions(Controller.Action.CONNECT)
            }.onFailure { showError(it.message ?: UiText.get(R.string.camera_switch_failed)) }
            Unit
        }
        if (ctl?.selectedCamera()?.address() != camera.address() &&
            ctl?.state()?.let { it.recording() == Recording.UNKNOWN && it.lastConfirmed() == Recording.RECORDING } == true) {
            AlertDialog.Builder(this).setTitle(UiText.get(R.string.camera_previous_unclear))
                .setMessage(UiText.get(R.string.camera_previous_warning))
                .setNegativeButton(UiText.get(R.string.cancel), null).setPositiveButton(UiText.get(R.string.camera_switch_anyway)) { _, _ -> activate() }.show()
        } else activate()
    }
    private fun showSetup() {
        if (setupDialog?.isShowing == true) return
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10, 4, 10, 4) }
        val scroll = ScrollView(this).apply { addView(root) }
        addButton(UiText.get(R.string.language_menu)) { setupDialog?.dismiss(); showLanguageChoice() }
        cameraLabel = addText(CameraStore(this).load()?.bleName() ?: UiText.get(R.string.camera_none), 16f)
        addButton(UiText.get(R.string.camera_saved_choose)) { showCameraChoice() }
        addButton(UiText.get(R.string.camera_scan)) { pendingScan = true; withPermissions(null) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(list)
        addText(UiText.get(R.string.setup_wake_hint), 14f)
        addButton(UiText.get(R.string.wake_id_check)) {
            if (CameraStore(this).load()?.model() == CameraModels.GO) {
                AlertDialog.Builder(this).setTitle(UiText.get(R.string.wake_go_title))
                    .setMessage(UiText.get(R.string.wake_go_body))
                    .setPositiveButton(UiText.get(R.string.close), null).show()
                return@addButton
            }
            val present = CameraStore(this).wakeIdentifier() != null
            AlertDialog.Builder(this).setTitle(UiText.get(R.string.control_wake))
                .setMessage(if (present) UiText.get(R.string.wake_id_present)
                    else UiText.get(R.string.wake_id_missing))
                .setNeutralButton(UiText.get(R.string.wake_id_enter)) { _, _ -> enterWakeIdentifier() }
                .setPositiveButton(UiText.get(R.string.close), null).show()
        }
        addButton(UiText.get(R.string.connection_disconnect)) { runCatching { SessionRuntime.controller?.disconnect() }
            .onFailure { showError(it.message ?: UiText.get(R.string.operation_running)) } }
        addButton(UiText.get(R.string.camera_forget)) {
            AlertDialog.Builder(this).setMessage(UiText.get(R.string.camera_forget_body))
                .setNegativeButton(UiText.get(R.string.cancel), null).setPositiveButton(UiText.get(R.string.forget)) { _, _ ->
                    if (SessionRuntime.controller?.state()?.let { it.busy() || it.recording() == Recording.RECORDING } == true) { showError(UiText.get(R.string.wait_stop)); return@setPositiveButton }
                    stopService(Intent(this, CameraSessionService::class.java))
                    SessionRuntime.close(); CameraStore(this).forget(); localError = null
                    startSession()
                }.show()
        }
        addButton(UiText.get(R.string.session_end)) {
            stopScan(); stopService(Intent(this, CameraSessionService::class.java)); SessionRuntime.close()
            setupDialog?.dismiss()
        }
        addButton(UiText.get(R.string.capabilities_show)) { showCapabilities() }
        addText(UiText.get(R.string.version_notice, BuildConfig.VERSION_NAME), 13f)
        setupDialog = AlertDialog.Builder(this).setTitle(UiText.get(R.string.setup_title)).setView(scroll)
            .setPositiveButton(UiText.get(R.string.back_controls), null).create().also {
                it.setOnDismissListener { stopScan(); cameraLabel = null; setupDialog = null }; it.show()
            }
    }
    private fun showLanguageChoice() {
        if (languageDialog?.isShowing == true) return
        val languages = arrayOf(de.markus.aceride.core.Language.ENGLISH, de.markus.aceride.core.Language.GERMAN)
        languageDialog = AlertDialog.Builder(this).setTitle(UiText.get(R.string.language_title))
            .setSingleChoiceItems(arrayOf("English", "Deutsch"), languages.indexOf(UiLanguage.current())) { dialog, index ->
                // Update resources/notification only. No recreation, reconnect or action-bearing intent replay.
                if (UiLanguage.select(this, languages[index])) {
                    dialog.dismiss()
                    handler.removeCallbacks(redraw)
                    if (active) handler.post(redraw)
                    showSetup()
                } else showError(UiText.get(R.string.language_save_failed))
            }.setNegativeButton(UiText.get(R.string.cancel), null).create().also {
                it.setOnDismissListener { languageDialog = null }; it.show()
            }
    }
    private fun enterWakeIdentifier() {
        val store = CameraStore(this)
        val camera = store.load() ?: run { showError(UiText.get(R.string.choose_first)); return }
        if (!CameraModels.needsAdvertising(camera.model())) { showError(UiText.get(R.string.wake_go_no_id)); return }
        val input = EditText(this).apply {
            isSingleLine = true; hint = UiText.get(R.string.wake_id_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        AlertDialog.Builder(this).setTitle(UiText.get(R.string.wake_id_title))
            .setMessage(UiText.get(R.string.wake_id_body))
            .setView(input).setNegativeButton(UiText.get(R.string.cancel), null).setPositiveButton(UiText.get(R.string.save)) { _, _ ->
                runCatching {
                    val suffix = input.text.toString().trim()
                    check(suffix.matches(Regex("[A-Za-z0-9]{6}"))) { UiText.get(R.string.wake_id_six) }
                    store.rememberWakeIdentifier(camera.address(), suffix)
                    Toast.makeText(this, UiText.get(R.string.wake_id_saved), Toast.LENGTH_SHORT).show()
                }.onFailure { showError(it.message ?: UiText.get(R.string.wake_id_invalid)) }
            }.show()
    }
    private fun addText(text: String, size: Float): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.BLACK); setPadding(2, 6, 2, 6); root.addView(this)
    }
    private fun addButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text; textSize = 16f; minimumHeight = (48 * resources.displayMetrics.density).toInt()
        root.addView(this, LinearLayout.LayoutParams(-1, -2)); setOnClickListener { action() }
    }
    private fun basePermissions() = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    private fun withPermissions(action: Controller.Action?) {
        if (!SdkConsent.accepted(this)) { pendingAction = null; showSdkConsent(); return }
        if (startingSession && action != null) { showError(UiText.get(R.string.session_starting)); return }
        pendingAction = action
        val required = mutableListOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        // START/RESUME may use automatic wake-up after a failed connection.
        if (CameraStore(this).load()?.model() != CameraModels.GO && action in listOf(Controller.Action.WAKE, Controller.Action.START, Controller.Action.RESUME))
            required += Manifest.permission.BLUETOOTH_ADVERTISE
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 1); return }
        startSession()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 1) return
        if (!basePermissions()) { pendingAction = null; pendingScan = false; showError(UiText.get(R.string.permission_missing_control)); return }
        // Advertising denial still allows controlling a powered-on camera.
        if (pendingAction == Controller.Action.WAKE && CameraStore(this).load()?.model() != CameraModels.GO && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            pendingAction = null; showError(UiText.get(R.string.permission_advertising_missing))
        }
        startSession()
    }
    private fun startSession() {
        if (!SdkConsent.accepted(this)) { pendingAction = null; showSdkConsent(); return }
        if (!basePermissions()) return
        try {
            SessionRuntime.create(this)
            if (SessionRuntime.serviceRunning) { dispatchPending(); return }
            if (startingSession) return
            startingSession = true
            startForegroundService(Intent(this, CameraSessionService::class.java))
            handler.postDelayed({
                startingSession = false
                if (!SessionRuntime.serviceRunning) { showError(UiText.get(R.string.session_cannot_start)); return@postDelayed }
                dispatchPending()
            }, 250)
        } catch (ex: Exception) { startingSession = false; showError(ex.message ?: UiText.get(R.string.session_failed)) }
    }
    private fun dispatchPending() {
        val action = pendingAction; pendingAction = null
        action?.let { SessionRuntime.action(it) }
        if (pendingScan) { pendingScan = false; scanCameras() }
        if (pendingChoose) { pendingChoose = false; showCameraChoice() }
    }
    private fun scanCameras() {
        stopScan(); found.clear(); list.removeAllViews()
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { showError(UiText.get(R.string.bluetooth_enable)); return }
        val scanner = adapter.bluetoothLeScanner ?: run { showError(UiText.get(R.string.scan_unavailable)); return }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handler.post {
                    if (scan !== this) return@post
                    if (!basePermissions()) {
                        stopScan(); showError(UiText.get(R.string.permission_revoked)); return@post
                    }
                    // Permissions can be revoked after a scan was started.
                    val name = try {
                        result.scanRecord?.deviceName ?: result.device.name ?: return@post
                    } catch (ex: SecurityException) {
                        stopScan(); showError(UiText.get(R.string.permission_missing)); return@post
                    }
                    // Names are only a discovery hint. SDK model verification is mandatory after connect.
                    if (CameraModels.discover(name) == null) return@post
                    if (!found.add(result.device.address)) return@post
                    val row = Button(this@MainActivity).apply {
                        text = "$name\n${result.device.address}"
                        minimumHeight = (56 * resources.displayMetrics.density).toInt()
                        setOnClickListener { selectCamera(result.device.address, name) }
                    }
                    list.addView(row)
                }
            }
            override fun onScanFailed(errorCode: Int) { handler.post { stopScan(); showError(UiText.get(R.string.scan_failed, errorCode)) } }
        }
        scan = callback
        try { scanner.startScan(callback); handler.postDelayed(scanStop, 10000) }
        catch (ex: SecurityException) { scan = null; showError(UiText.get(R.string.permission_missing)) }
    }
    private fun selectCamera(address: String, name: String) {
        val model = CameraModels.discover(name) ?: return
        val hint = if (model == CameraModels.GO) UiText.get(R.string.pair_go_hint) else UiText.get(R.string.pair_ace_hint)
        AlertDialog.Builder(this).setMessage(UiText.get(R.string.pair_confirm, name, CameraModels.label(model), hint))
            .setNegativeButton(UiText.get(R.string.cancel), null).setPositiveButton(UiText.get(R.string.select)) { _, _ ->
                switchCamera(Camera(address, name, model))
            }.show()
    }
    private fun stopScan() {
        handler.removeCallbacks(scanStop)
        val callback = scan; scan = null
        if (callback != null) {
            try {
                getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
            } catch (ex: SecurityException) {
                // A revoked permission also ends our local scan/listener lifetime.
            }
        }
    }
    private fun showCapabilities() {
        var advertising = UiText.get(R.string.capabilities_unknown_bt)
        var advertiser = UiText.get(R.string.capabilities_unknown_advert)
        if (basePermissions()) {
            try {
                val adapter = getSystemService(BluetoothManager::class.java)?.adapter
                advertising = adapter?.isMultipleAdvertisementSupported?.toString() ?: UiText.get(R.string.capabilities_unavailable)
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)
                    advertiser = (adapter?.bluetoothLeAdvertiser != null).toString()
            } catch (ex: SecurityException) {
                advertising = UiText.get(R.string.capabilities_revoked)
                advertiser = UiText.get(R.string.capabilities_revoked)
            }
        }
        val info = UiText.get(R.string.capabilities_body, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
            Build.SUPPORTED_ABIS.joinToString(), packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE).toString(), advertising, advertiser)
        AlertDialog.Builder(this).setTitle(UiText.get(R.string.capabilities_title)).setMessage(info).setPositiveButton(UiText.get(R.string.close), null).show()
    }
    private fun intentAction(intent: Intent?): Controller.Action? {
        val expected = intent?.getStringExtra("cameraAddress")
        if (expected != null && expected != CameraStore(this).load()?.address()) {
            localError = UiText.get(R.string.camera_changed_retry); return null
        }
        return runCatching { Controller.Action.valueOf(intent?.getStringExtra("cameraAction") ?: "") }.getOrNull()
    }
    private fun showError(text: String) {
        localError = text
        val display = UiText.message(text)
        Toast.makeText(this, display, Toast.LENGTH_LONG).show(); message.text = display
    }
}

