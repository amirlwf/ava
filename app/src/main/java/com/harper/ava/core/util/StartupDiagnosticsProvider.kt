package com.harper.ava.core.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Round-3 launch diagnostics (0.5.3).
 *
 * ContentProviders are created BEFORE Application.onCreate, and the
 * merged manifest's InitializationProvider (androidx.startup) runs its
 * initializers there too. In 0.5.0–0.5.2 the crash handler and the
 * startup tracer only started inside Application.onCreate — so a crash
 * in the provider phase left no trace at all and the safe-mode flag was
 * never created. This provider runs FIRST (initOrder 100, above the
 * startup provider's default 0) and installs the crash handler plus
 * starts the startup trace before anything else in the process.
 *
 * It does no work beyond diagnostics: no Room, no player, no DataStore.
 * All I/O is wrapped in runCatching inside StartupTracer/CrashReporter,
 * so this provider itself can never crash the app.
 */
class StartupDiagnosticsProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        CrashReporter.install(ctx)
        StartupTracer.mark(ctx, "provider.onCreate")
        val crashedLastStart = StartupTracer.beginStartup(ctx)
        StartupTracer.mark(ctx, "provider.tracer.begin crashedLastStart=$crashedLastStart")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = throw UnsupportedOperationException("diagnostics only")

    override fun getType(uri: Uri): String? =
        throw UnsupportedOperationException("diagnostics only")

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("diagnostics only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("diagnostics only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("diagnostics only")
}
