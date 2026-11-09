package com.harper.ava.core.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash diagnostics (added in 0.5.1).
 *
 * There is no logcat access from outside the phone, so when the app dies
 * unexpectedly the stack trace would otherwise be lost. This handler
 * writes the trace to the app's internal files dir (never leaves the
 * device on its own) and then hands the crash to the previous handler,
 * so the visible behaviour is unchanged — the app still closes, it just
 * leaves a report behind. The About screen lists the reports and can
 * share the latest one as plain text.
 *
 * Install FIRST in Application.onCreate, before anything else is built,
 * so even a crash while constructing the DI container is captured.
 *
 * Since 0.5.2 the handler also exports the report (with the startup
 * trace from [StartupTracer]) to the public Downloads folder, because
 * the private log is unreachable when the app never opens.
 */
object CrashReporter {
    /** Directory (inside filesDir) holding the saved crash reports. */
    internal const val DIR_NAME = "crash-logs"
    private const val MAX_FILES = 5

    /** 0.5.3: the diagnostics provider installs the handler before
     * Application.onCreate; installing twice would chain the handler
     * into itself and double-log every crash. */
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeLog(appContext, thread, throwable) }
            // 0.5.4: also write the stack trace into the STARTUP TRACE
            // file itself. The trace is what safe mode shows and what
            // the user shares — so the exception survives even if the
            // public crash log is lost or overwritten. This covers every
            // uncaught exception, including composition crashes (which
            // cannot be wrapped in try/catch — the Compose compiler
            // forbids it around composable invocations).
            runCatching { StartupTracer.markThrowable(appContext, "crash", throwable) }
            // 0.5.2: also leave a copy where the user can find it without
            // opening the app (Downloads/Ava/ava-crash-log.txt).
            runCatching { StartupTracer.exportCrash(appContext, thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /** All saved crash reports, newest first. */
    fun listLogs(context: Context): List<File> {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    /** The newest report as text, or null when there is none. */
    fun readLatest(context: Context): String? {
        val latest = listLogs(context).firstOrNull() ?: return null
        return runCatching { latest.readText() }.getOrNull()
    }

    fun clearLogs(context: Context) {
        listLogs(context).forEach { runCatching { it.delete() } }
    }

    private fun writeLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName} ($code)"
        }.getOrNull() ?: "unknown"
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val file = File(dir, "crash-$stamp.txt")
        file.writeText(
            "Ava crash report\n" +
                "time: $stamp\n" +
                "app: com.harper.ava $version\n" +
                "device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "thread: ${thread.name}\n\n" +
                trace,
        )
        // Keep only the newest reports.
        listLogs(context).drop(MAX_FILES).forEach { runCatching { it.delete() } }
    }
}
