package com.tonbil.termostat

import android.app.Application
import android.util.Log
import com.tonbil.termostat.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File

class TonbilTermApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global crash handler — write stack trace to file for debugging
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sb = StringBuilder()
                sb.appendLine("=== CRASH on ${thread.name} ===")
                sb.appendLine("Time: ${System.currentTimeMillis()}")
                sb.appendLine()

                var current: Throwable? = throwable
                var depth = 0
                while (current != null && depth < 10) {
                    if (depth > 0) sb.appendLine("\n--- Caused by (depth $depth) ---")
                    sb.appendLine("${current.javaClass.name}: ${current.message}")
                    current.stackTrace.take(15).forEach { frame ->
                        sb.appendLine("  at $frame")
                    }
                    if (current.stackTrace.size > 15) {
                        sb.appendLine("  ... ${current.stackTrace.size - 15} more")
                    }
                    current = current.cause
                    depth++
                }

                val crashLog = sb.toString()
                Log.e("TonbilTermCrash", crashLog)
                val file = File(filesDir, "crash_log.txt")
                file.writeText(crashLog)
            } catch (_: Exception) { /* best effort */ }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        startKoin {
            androidContext(this@TonbilTermApp)
            modules(appModule)
        }
    }
}
