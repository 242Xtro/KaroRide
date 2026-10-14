package de.markus.aceride

import android.content.Context
import de.markus.aceride.core.CameraAdapter.Camera
import de.markus.aceride.core.CameraModels
import de.markus.aceride.core.CameraProfiles

class CameraStore(context: Context) {
    private val prefs = context.getSharedPreferences("camera", Context.MODE_PRIVATE)
    // Shared lock across Activity, SDK and service instances. One atomic preference value.
    private fun profiles(): CameraProfiles {
        prefs.getString("profilesV1", null)?.let { return CameraProfiles.decode(it) }
        val old = prefs.getString("address", null)?.let { address ->
            prefs.getString("bleName", null)?.let { Camera(address, it, CameraModels.ACE) }
        }
        return CameraProfiles.migrate(old, prefs.getBoolean("pause", false),
            prefs.getString("wakeAddress", null), prefs.getString("wakeId", null))
    }
    private fun write(value: CameraProfiles) {
        check(prefs.edit().putString("profilesV1", value.encode()).commit()) { "Kameraprofile konnten nicht gespeichert werden" }
    }
    fun load(): Camera? = synchronized(lock) { profiles().selected()?.camera() }
    fun all(): List<Camera> = synchronized(lock) { profiles().all().map { it.camera() } }
    fun save(camera: Camera) = synchronized(lock) { write(profiles().select(camera)) }
    fun rememberWakeIdentifier(address: String, serial: String) = synchronized(lock) {
        check(profiles().selected()?.camera()?.address() == address) { "Kameraauswahl wurde geändert" }
        write(profiles().wake(address, serial))
    }
    fun wakeIdentifier(): String? = synchronized(lock) { profiles().selected()?.wakeId() }
    fun pause(): Boolean = synchronized(lock) { profiles().selected()?.pause() ?: false }
    fun pause(address: String): Boolean = synchronized(lock) { profiles().get(address)?.pause() ?: false }
    fun pause(address: String, value: Boolean) = synchronized(lock) { write(profiles().pause(address, value)) }
    fun forget() = synchronized(lock) { write(profiles().forgetSelected()) }
    companion object { private val lock = Any() }
}

