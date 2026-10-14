package de.markus.aceride

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import de.markus.aceride.core.Language
import java.util.concurrent.CopyOnWriteArrayList

/** Private preference and isolated resource contexts; changing language sends no camera action. */
object UiLanguage {
    private data class Snapshot(val language: Language, val resources: Resources)
    @Volatile private var snapshot: Snapshot? = null
    private val observers = CopyOnWriteArrayList<Runnable>()
    private fun stored(context: Context) = Language.fromCode(
        context.getSharedPreferences("ui-settings", Context.MODE_PRIVATE).getString("language", null))
    private fun configured(context: Context, language: Language): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(language.locale()))
        return context.createConfigurationContext(config)
    }
    fun wrap(context: Context) = configured(context, stored(context))
    fun initialize(context: Context) {
        val language = stored(context)
        snapshot = Snapshot(language, configured(context.applicationContext, language).resources)
    }
    fun current(): Language = snapshot?.language ?: Language.ENGLISH
    fun text(id: Int, vararg args: Any): String {
        val resources = checkNotNull(snapshot) { "KaroRide language resources are not initialised" }.resources
        return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
    }
    fun select(context: Context, language: Language): Boolean {
        if (!context.getSharedPreferences("ui-settings", Context.MODE_PRIVATE)
                .edit().putString("language", language.code()).commit()) return false
        initialize(context)
        observers.forEach { it.run() }
        return true
    }
    fun observe(observer: Runnable) { observers.addIfAbsent(observer) }
    fun removeObserver(observer: Runnable) { observers.remove(observer) }
}
