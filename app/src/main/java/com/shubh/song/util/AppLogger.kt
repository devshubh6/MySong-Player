package com.shubh.song.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * App diagnostic logging. Captures the app's logcat stream (including the
 * app's own `Log.*` output, warnings, errors and crash traces) and persists it
 * to [LOG_FILE] inside the app's private storage. The user can open this from
 * Settings to inspect what happened, or export it to the Downloads folder /
 * share it as a file so they can send it for debugging.
 *
 * A background thread tail-follows logcat using `logcat -T <last timestamp>`
 * so it only accumulates new lines and stays lightweight. Every file access is
 * guarded (existence checks + try/catch) so a missing file can never crash the
 * collector.
 */
object AppLogger {

    const val LOG_FILE = "app_log.txt"
    private const val BUFFER_SIZE = 20_000

    private val running = AtomicBoolean(false)
    private var filesDir: File? = null

    /** Installs the log collector; safe to call once from [MySongApp.onCreate]. */
    fun init(context: Context) {
        if (running.get()) return
        filesDir = context.filesDir
        running.set(true)
        thread(name = "AppLogger", isDaemon = true) {
            tailLogcat()
        }
    }

    /** Tail-follows logcat for this app and appends new lines to the log file. */
    private fun tailLogcat() {
        val dir = filesDir ?: return
        if (!dir.isDirectory) {
            try {
                dir.mkdirs()
            } catch (_: Throwable) {
            }
        }

        // The marker file must exist before we ever read it, or Java throws
        // FileNotFoundException: .last_logcat ENOENT. Create it eagerly.
        val markerFile = File(dir, ".last_logcat")
        try {
            if (!markerFile.exists()) {
                markerFile.parentFile?.mkdirs()
                markerFile.createNewFile()
                markerFile.writeText("=== APP LOG STARTED ===\n")
            }
        } catch (_: Throwable) {
        }

        var lastTime: String = try {
            if (markerFile.exists()) markerFile.readText().trim() else ""
        } catch (_: Throwable) {
            ""
        }

        while (running.get()) {
            try {
                val cmd = if (lastTime.isBlank()) {
                    arrayOf("logcat", "-d", "-t", "2000", "--pid=${android.os.Process.myPid()}")
                } else {
                    arrayOf("logcat", "-d", "-T", lastTime, "--pid=${android.os.Process.myPid()}")
                }
                val process = Runtime.getRuntime().exec(cmd)
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                if (output.isNotBlank()) {
                    appendLog(output)
                    // Remember the newest timestamp so the next pass only reads
                    // lines emitted after the ones we just consumed.
                    val updated = rememberLastTimestamp(output)
                    if (updated != null) {
                        lastTime = updated
                        try {
                            markerFile.writeText(updated)
                        } catch (_: Throwable) {
                        }
                    }
                }
            } catch (_: Throwable) {
                // logcat unavailable / no permission — just keep trying silently.
            }
            try {
                Thread.sleep(1500)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    /**
     * Extracts the timestamp of the last log line (logcat lines start with
     * "MM-DD HH:MM:SS.mmm") so `-T` can resume from the right place.
     * Returns null when the output has no usable timestamp.
     */
    private fun rememberLastTimestamp(output: String): String? {
        return try {
            val trimmed = output.trimEnd('\n', '\r')
            if (trimmed.isBlank()) return null
            val lastLine = trimmed.substringAfterLast('\n').trim()
            if (lastLine.isBlank()) return null
            // logcat head format: "MM-DD HH:MM:SS.mmm PID TID Level Tag : msg"
            val tokens = lastLine.split(' ')
            if (tokens.size < 2) return null
            val time = tokens[1].trim()
            if (time.isBlank()) return null
            "${tokens[0]} $time"
        } catch (_: Throwable) {
            null
        }
    }

    private fun appendLog(text: String) {
        try {
            val dir = filesDir ?: return
            if (!dir.isDirectory) dir.mkdirs()
            val file = File(dir, LOG_FILE)
            if (file.exists() && file.length() + text.length > BUFFER_SIZE * 10) {
                file.writeText(text)
            } else {
                file.appendText(text)
            }
        } catch (_: Throwable) {
        }
    }

    /** Writes a diagnostic snapshot (device info + fresh logcat) to the log file. */
    fun logSnapshot(context: Context, heading: String) {
        try {
            val sb = StringBuilder()
            sb.appendLine()
            sb.appendLine("==== $heading at ${now()} ====")
            sb.appendLine("App: Mysong ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
            sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLog(sb.toString())
        } catch (_: Throwable) {
        }
    }

    /** Reads the collected log, newest-last, as a string. */
    fun readLog(context: Context): String {
        val file = File(context.filesDir, LOG_FILE)
        return if (file.exists()) {
            try {
                file.readText()
            } catch (_: Throwable) {
                "Log file could not be read."
            }
        } else {
            "No logs captured yet."
        }
    }

    /**
     * Filter levels for the log viewer.
     */
    object Level {
        const val ALL = 0
        const val WARNING = 1
        const val ERROR = 2
    }

    /** A single log line with its severity, used for coloured rendering. */
    data class LogLine(val text: String, val level: Int)

    /**
     * Reads the combined log (any saved crash + live logcat capture) and
     * returns structured [LogLine]s so the UI can colour + filter them.
     * Crash traces are always included so an app that dies on launch still
     * shows what went wrong.
     */
    fun readLogLines(context: Context): List<LogLine> {
        val out = ArrayList<LogLine>()

        val crashFile = File(context.filesDir, CrashLogger.CRASH_FILE)
        if (crashFile.exists()) {
            try {
                val crash = crashFile.readText().trim()
                if (crash.isNotEmpty()) {
                    out.add(LogLine("==== LAST CRASH ====", Level.ERROR))
                    crash.split("\n").forEach { out.add(LogLine(it, Level.ERROR)) }
                    out.add(LogLine("====================", Level.ERROR))
                    out.add(LogLine("", Level.ALL))
                }
            } catch (_: Throwable) {
            }
        }

        val logFile = File(context.filesDir, LOG_FILE)
        if (logFile.exists()) {
            try {
                val lines = logFile.readText().split("\n")
                for (line in lines) {
                    out.add(LogLine(line, classify(line)))
                }
            } catch (_: Throwable) {
            }
        }

        return out
    }

    /** Classifies a logcat line by its priority letter. */
    private fun classify(line: String): Int {
        if (crashPattern().containsMatchIn(line)) return Level.ERROR
        if (priorityPattern('E').containsMatchIn(line)) return Level.ERROR
        if (priorityPattern('W').containsMatchIn(line)) return Level.WARNING
        return Level.ALL
    }

    /** Matches a logcat priority marker like " E " between tokens. */
    private fun priorityPattern(priority: Char): Regex =
        Regex("""\s${priority}\s""")

    /** Matches AndroidException crashes / FATAL lines. */
    private fun crashPattern(): Regex =
        Regex("""FATAL EXCEPTION|AndroidRuntime|Process: com.shubh.song""")

    /** Whether a line passes the given filter level. */
    private fun matchesLevel(line: LogLine, level: Int): Boolean =
        when (level) {
            Level.ALL -> true
            Level.WARNING -> line.level == Level.WARNING
            Level.ERROR -> line.level == Level.ERROR
            else -> true
        }

    fun logFile(context: Context): File = File(context.filesDir, LOG_FILE)

    /** Returns the filtered log text (for copy / share / display). */
    fun filteredText(context: Context, level: Int = Level.ALL): String =
        try {
            readLogLines(context).filter { matchesLevel(it, level) }
                .joinToString("\n") { it.text }
        } catch (_: Throwable) {
            ""
        }

    /** Number of bytes captured so far (for the settings screen). */
    fun logSize(context: Context): Long {
        val file = File(context.filesDir, LOG_FILE)
        return if (file.exists()) {
            try {
                file.length()
            } catch (_: Throwable) {
                0L
            }
        } else {
            0L
        }
    }

    /**
     * Exports the log to the public Downloads folder (scoped-storage safe via
     * MediaStore on API 29+, plain write on older versions) and returns a
     * shareable [Uri]. [level] filters the exported content.
     */
    fun export(context: Context, level: Int = Level.ALL): Uri? {
        return try {
            val content = readLogLines(context).filter { matchesLevel(it, level) }.joinToString("\n") { it.text }
            val name = "MysongLog_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/Mysong"
                    )
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    return uri
                }
                null
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                file.writeText(content)
                Uri.fromFile(file)
            }
        } catch (t: Throwable) {
            // Export failure should never crash — caller falls back to share.
            null
        }
    }

    /** Shares the collected log through the system chooser. */
    fun share(context: Context, level: Int = Level.ALL) {
        try {
            val content = readLogLines(context).filter { matchesLevel(it, level) }.joinToString("\n") { it.text }
            val uri = export(context, level)
                ?: createTempShareUri(context, content)
            if (uri == null) return
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Mysong App Log")
                putExtra(Intent.EXTRA_TEXT, "Attached Mysong diagnostic log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "Share App Log")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
        }
    }

    private fun createTempShareUri(context: Context, content: String): Uri? {
        return try {
            val file = File(context.cacheDir, "share_applog.txt")
            file.writeText(content)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (t: Throwable) {
            null
        }
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}