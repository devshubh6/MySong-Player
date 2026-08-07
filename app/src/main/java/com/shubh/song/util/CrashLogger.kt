package com.shubh.song.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes every uncaught process crash to [CRASH_FILE] so the stack trace
 * can be recovered without adb/logcat access (AndroidIDE environments).
 * Keeps only the most recent crash to avoid unbounded file growth.
 */
object CrashLogger {

    const val CRASH_FILE = "crash.log"

    private val defaultHandler: Thread.UncaughtExceptionHandler =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install(context: Context) {
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logCrash(appContext, thread, throwable)
            } catch (_: Exception) {
            }
            defaultHandler.uncaughtException(thread, throwable)
        }
    }

    fun logCrash(context: Context, thread: Thread?, throwable: Throwable) {
        try {
            val trace = StringWriter().apply {
                PrintWriter(this).use { writer ->
                    writer.println("==== Crash at ${now()} on thread ${thread?.name} ====")
                    writer.println(throwable.toString())
                    throwable.stackTrace.forEach { writer.println("\tat $it") }
                    throwable.cause?.let { cause ->
                        writer.println("Caused by:")
                        writer.println(cause.toString())
                        cause.stackTrace.forEach { writer.println("\tat $it") }
                    }
                }
            }.toString()
            writeTo(context.filesDir, CRASH_FILE, trace)
            writeTo(context.getExternalFilesDir(null), CRASH_FILE, trace)
            writeTo(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MysongCrash.log",
                trace
            )
        } catch (e: Exception) {
            Log.e("CrashLogger", "Failed to write crash log", e)
        }
    }

    private fun writeTo(dir: File?, fileName: String, trace: String) {
        if (dir == null) return
        try {
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeText(trace)
        } catch (_: Exception) {
        }
    }

    fun crashLogFile(context: Context): File {
        val filesDir: File = context.filesDir
        return File(filesDir, CRASH_FILE)
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
