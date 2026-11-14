package com.harper.ava

import com.harper.ava.core.util.LocaleHelper
import com.harper.ava.data.preferences.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the language mapping ([LocaleHelper.localeFor]).
 * This one function is the single mapping every language path shares —
 * `attachBaseContext` ([LocaleHelper.wrapContextWithLocale]), the
 * Android 13+ per-app locale mirror
 * ([LocaleHelper.updateSystemAppLocale]) and the recreate decision all
 * read it — so a swapped value here would flip the whole app's
 * language. The Context/Activity wrappers themselves cannot run on
 * the JVM; the mapping can, so it is pinned directly.
 */
class LocaleHelperTest {

    @Test
    fun `fa maps to the fa locale`() {
        val locale = LocaleHelper.localeFor(AppLanguage.FA)
        assertEquals("fa", locale?.language)
        assertEquals("fa", locale?.toLanguageTag())
    }

    @Test
    fun `en maps to english`() {
        assertEquals(Locale.ENGLISH, LocaleHelper.localeFor(AppLanguage.EN))
    }

    @Test
    fun `system maps to null, follow the system language`() {
        assertNull(LocaleHelper.localeFor(AppLanguage.SYSTEM))
    }

    @Test
    fun `fa and en map to different locales`() {
        assertNotEquals(
            LocaleHelper.localeFor(AppLanguage.FA),
            LocaleHelper.localeFor(AppLanguage.EN),
        )
    }
}
