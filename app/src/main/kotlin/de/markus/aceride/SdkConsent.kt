package de.markus.aceride

import android.content.Context

/** Acceptance is local and separate from Bluetooth permissions and camera profiles. */
object SdkConsent {
    private const val VERSION = 1
    fun accepted(context: Context): Boolean =
        context.getSharedPreferences("sdk-consent", Context.MODE_PRIVATE).getInt("version", 0) == VERSION

    fun accept(context: Context): Boolean =
        context.getSharedPreferences("sdk-consent", Context.MODE_PRIVATE).edit().putInt("version", VERSION).commit()
}
