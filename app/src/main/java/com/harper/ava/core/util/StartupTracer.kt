package com.harper.ava.core.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Round-2 launch diagnostics (0.5.2).
 *
 * 0.5.1 still crashes on launch for the user, but its META-INF/services
 * fix is verified in the APK and the startup path reads clean — so the
 * remaining cause is invisible to static analysis. This tracer records
 * every startup step to a file as it happens, and on a crash the whole
 * trace plus the stack trace is copied to a PUBLIC file the user can
 * find in the Files app (Downloads/Ava/ava-crash-log.txt) and send us —
 * the previous CrashReporter only wrote to private storage, which is
 * useless when the app never opens.
 *
 * Safe mode: a flag file is created at the start of Application.onCreate
 * and deleted once the first real screen has composed. If the flag is
 * still there on the next launch, the previous run died during startup
 * and the app opens a minimal screen (plain Views, no Compose, no Room,
 * no ExoPlayer, no DataStore) that shows the trace and can share it.
 *
 * Everything here is synchronous file I/O wrapped in runCatching — the
 * tracer itself must never crash the app. Nothing is ever sent anywhere
 * automatically; the user shares the log only if they choose to.
 */
object StartupTracer {
    private const val TRACE_NAME = "startup-trace.txt"
    private const val FLAG_NAME = "startup-in-progress.flag"
    const val PUBLIC_FILE_NAME = "ava-crash-log.txt"
    const val PUBLIC_FOLDER = "Ava"
    /**
     * 0.5.4: the startup trace is exported to its OWN public file. In
     * 0.5.2/0.5.3 the safe-mode screen exported the trace-only report to
     * ava-crash-log.txt — which DELETED the crash report (with the stack
     * trace) that the crash handler had just written there. That is why
     * two crash reports arrived with no stack trace at all.
     */
    const val TRACE_FILE_NAME = "ava-startup-trace.txt"

    @Volatile
    var startupComplete: Boolean = false
        private set

    @Volatile
    private var startElapsed: Long = -1L

    /**
     * 0.5.3: beginStartup is now called twice per process — once from
     * the diagnostics provider (before Application.onCreate) and once
     * from Application.onCreate. Only the first call writes the header
     * and creates the flag; later calls return the cached result so the
     * "previous run crashed" information survives.
     */
    @Volatile
    private var begun: Boolean = false

    @Volatile
    private var begunCrashed: Boolean = false

    /**
     * Deletes persisted app data that may have been corrupted by an
     * earlier crashed run: the Room database, the DataStore file, all
     * SharedPreferences, and the cache. The startup trace and saved
     * crash logs are KEPT so the cause can still be read afterwards.
     * Safe to call only when nothing else is using the data (safe mode).
     * Returns how many files/directories were removed.
     */
    fun resetAppData(context: Context): Int {
        val ctx = safeContext(context)
        var deleted = 0
        fun deleteFile(f: File) {
            runCatching { if (f.exists() && f.delete()) deleted++ }
        }
        fun deleteDir(dir: File) {
            runCatching {
                dir.listFiles()?.forEach { child ->
                    if (child.isDirectory) deleteDir(child) else deleteFile(child)
                }
                if (dir.exists() && dir.delete()) deleted++
            }
        }
        // Room database (+ -wal / -shm sidecars).
        runCatching { if (ctx.deleteDatabase("ava.db")) deleted++ }
        // DataStore preferences file.
        deleteDir(File(ctx.filesDir, "datastore"))
        // SharedPreferences (incl. the synchronous language mirror).
        ctx.filesDir.parentFile?.let { deleteDir(File(it, "shared_prefs")) }
        // Cache.
        deleteDir(ctx.cacheDir)
        // Everything else in filesDir except the diagnostics themselves.
        runCatching {
            ctx.filesDir.listFiles()?.forEach { child ->
                if (child.name == TRACE_NAME || child.name == FLAG_NAME ||
                    child.name == CrashReporter.DIR_NAME
                ) {
                    return@forEach
                }
                if (child.isDirectory) deleteDir(child) else deleteFile(child)
            }
        }
        mark(ctx, "app.data.reset deleted=$deleted")
        return deleted
    }

    private fun safeContext(context: Context): Context =
        runCatching { context.applicationContext }.getOrNull() ?: context

    private fun traceFile(context: Context): File =
        File(safeContext(context).filesDir, TRACE_NAME)

    private fun flagFile(context: Context): File =
        File(safeContext(context).filesDir, FLAG_NAME)

    /**
     * Call FIRST in Application.onCreate (after the crash handler is
     * installed). Returns true when the previous run crashed during
     * startup. The trace of that crashed run is kept at the top of the
     * new trace, so the safe-mode screen can still show it.
     */
    fun beginStartup(context: Context): Boolean {
        if (begun) return begunCrashed
        val ctx = safeContext(context)
        startElapsed = SystemClock.elapsedRealtime()
        val crashed = runCatching { flagFile(ctx).exists() }.getOrDefault(false)
        runCatching {
            val previousTrace = if (crashed) {
                runCatching { traceFile(ctx).readText() }.getOrNull()
            } else {
                null
            }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val header = "Ava startup trace\n" +
                "time: $stamp\n" +
                "app: com.harper.ava ${appVersion(ctx)}\n" +
                "device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "previous start crashed: $crashed\n\n"
            val previous = previousTrace?.let { "\n--- previous run trace ---\n$it\n--- end of previous run ---\n\n" } ?: ""
            traceFile(ctx).writeText(header + previous)
            flagFile(ctx).createNewFile()
        }
        begun = true
        begunCrashed = crashed
        mark(ctx, "startup.begin crashedLastStart=$crashed")
        return crashed
    }

    /** One trace line, appended synchronously so it survives a crash right after. */
    fun mark(context: Context, step: String) {
        runCatching {
            val elapsed = if (startElapsed < 0L) {
                -1L
            } else {
                SystemClock.elapsedRealtime() - startElapsed
            }
            traceFile(context).appendText("[+${elapsed}ms] $step\n")
        }
    }

    /**
     * 0.5.4: append a full stack trace to the trace file. The trace is
     * what the user shares from safe mode, so a crash caught here
     * survives even if the public crash log is lost — the report that
     * reaches us carries the exception with it.
     */
    fun markThrowable(context: Context, tag: String, throwable: Throwable) {
        runCatching {
            val elapsed = if (startElapsed < 0L) {
                -1L
            } else {
                SystemClock.elapsedRealtime() - startElapsed
            }
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            traceFile(context).appendText(
                "[+${elapsed}ms] $tag ${throwable::class.qualifiedName}: ${throwable.message}\n$sw\n",
            )
        }
    }

    /** Call once the first real screen has composed: startup succeeded. */
    fun markStartupComplete(context: Context) {
        runCatching {
            startupComplete = true
            mark(context, "startup.complete")
            flagFile(context).delete()
        }
    }

    fun readTrace(context: Context): String? =
        runCatching { traceFile(context).readText() }.getOrNull()

    /** Full report text: header + startup trace + optional stack trace. */
    fun buildReport(context: Context, thread: Thread?, throwable: Throwable?): String {
        val sb = StringBuilder()
        sb.append("Ava diagnostic report\n")
        sb.append("time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
        sb.append("app: com.harper.ava ").append(appVersion(context)).append('\n')
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" / Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        if (thread != null) sb.append("thread: ").append(thread.name).append('\n')
        sb.append('\n')
        sb.append("--- startup trace ---\n")
        sb.append(readTrace(context) ?: "(no startup trace recorded)\n")
        if (throwable != null) {
            sb.append("\n--- crash ---\n")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString())
        } else {
            // 0.5.4: a trace-only report (the one safe mode shares) must
            // still carry the last caught exception, if one was recorded.
            // Without this, a report shared from safe mode never shows
            // the stack trace even when CrashReporter captured it.
            sb.append("\n--- last recorded crash ---\n")
            val last = runCatching { CrashReporter.readLatest(context) }.getOrNull()
            if (last != null) {
                sb.append(last)
            } else {
                sb.append(
                    "(no Java crash was recorded — the process died without " +
                        "an uncaught Java exception, e.g. a native crash or a system kill)\n",
                )
            }
        }
        return sb.toString()
    }

    /**
     * Writes [reportText] to Downloads/Ava/[fileName] via MediaStore
     * (API 29+; no permission needed for the app's own files) so the user
     * can find it in the Files app. Returns the user-visible path, or null.
     *
     * 0.5.4: [fileName] is a parameter because the crash log and the
     * startup trace must be DIFFERENT files. The crash log
     * (ava-crash-log.txt) carries the stack trace; the trace-only report
     * must never overwrite it (see [exportTrace]).
     */
    fun exportPublic(context: Context, reportText: String, fileName: String = PUBLIC_FILE_NAME): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = safeContext(context).contentResolver
                val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                // Replace any previous file with the same name (own files).
                resolver.query(
                    uri,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        resolver.delete(ContentUris.withAppendedId(uri, id), null, null)
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/$PUBLIC_FOLDER")
                }
                val outUri = resolver.insert(uri, values) ?: return@runCatching null
                resolver.openOutputStream(outUri)?.use { out ->
                    out.write(reportText.toByteArray(Charsets.UTF_8))
                } ?: return@runCatching null
                "Downloads/$PUBLIC_FOLDER/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    PUBLIC_FOLDER,
                )
                dir.mkdirs()
                File(dir, fileName).writeText(reportText)
                "Downloads/$PUBLIC_FOLDER/$fileName"
            }
        }.getOrNull()
    }

    /** Trace-only export for the safe-mode screen: a separate file that never touches the crash log. */
    fun exportTrace(context: Context, reportText: String): String? =
        exportPublic(context, reportText, TRACE_FILE_NAME)

    /** Called from the crash handler: trace + stack trace -> public file. */
    fun exportCrash(context: Context, thread: Thread, throwable: Throwable): String? {
        val report = buildReport(context, thread, throwable)
        return exportPublic(context, report)
    }

    private fun appVersion(context: Context): String = runCatching {
        val info = safeContext(context).packageManager.getPackageInfo(
            safeContext(context).packageName, 0,
        )
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName} ($code)"
    }.getOrNull() ?: "unknown"
}
