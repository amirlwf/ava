package com.harper.ava.core.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.harper.ava.data.preferences.AppLanguage
import java.util.Locale

/**
 * Applies the in-app language without depending on the system language
 * and without the AppCompat dependency.
 *
 * How it works:
 * - [wrapContextWithLocale] returns a configuration context whose locale
 *   is the chosen language; MainActivity calls it from
 *   `attachBaseContext`, so every resource (including strings) resolves
 *   in the right language and RTL/LTR flips with it.
 * - On Android 13+ the choice is also pushed to the platform
 *   [android.app.LocaleManager] ([updateSystemAppLocale]), so the system
 *   per-app language screen shows the same value.
 * - The language used by `attachBaseContext` is read from a tiny
 *   synchronous SharedPreferences mirror (see UserPreferences) because
 *   DataStore is async and `attachBaseContext` cannot suspend.
 */
object LocaleHelper {

    /** The single language mapping all three paths share — pinned by LocaleHelperTest. */
    fun localeFor(language: AppLanguage): Locale? = when (language) {
        AppLanguage.FA -> Locale("fa")
        AppLanguage.EN -> Locale.ENGLISH
        AppLanguage.SYSTEM -> null
    }

    fun wrapContextWithLocale(base: Context, language: AppLanguage): Context {
        val locale = localeFor(language) ?: return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Mirrors the choice into the platform per-app locale store (API 33+). No-op below that. */
    fun updateSystemAppLocale(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val locale = localeFor(language)
        val manager = context.getSystemService(android.app.LocaleManager::class.java) ?: return
        manager.applicationLocales = if (locale == null) {
            // An empty LocaleList means "follow the system language".
            LocaleList()
        } else {
            LocaleList(locale)
        }
    }

    /**
     * 0.5.6: on Android 13+ the platform recreates the Activity itself
     * when [updateSystemAppLocale] sets the per-app locale (MainActivity
     * declares no `configChanges`), so an explicit `recreate()` restarts
     * the Activity a SECOND time — the 0.5.5 trace showed two onCreate
     * pairs 59 ms apart after one language change. Below API 33 the
     * platform call is a no-op and the manual recreate is the only one
     * that applies the new language.
     */
    fun recreateForLanguageChange(activity: android.app.Activity?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            activity?.recreate()
        }
    }
}
