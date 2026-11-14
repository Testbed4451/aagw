package com.meilluer.glanceables.debug

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel(val letter: String) {
    VERBOSE("V"),
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E")
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun toPlainText(): String {
        val err = throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""
        return "${formattedTime()} [${level.letter}/$tag] $message$err"
    }
}

/**
 * In-memory logger with live subscription support and ring-buffer storage.
 * Captures logs from across the application and feeds the Debug Menu live log viewer.
 */
object AppLogger {

    private const val MAX_LOGS = 1000
    private val idGenerator = AtomicLong(1)
    private val logList = mutableListOf<LogEntry>()
    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun addLog(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            id = idGenerator.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )

        logList.add(entry)
        if (logList.size > MAX_LOGS) {
            logList.removeAt(0)
        }

        // Notify subscribers on main thread
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener(entry)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    fun v(tag: String, message: String) {
        Log.v(tag, message)
        addLog(LogLevel.VERBOSE, tag, message)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addLog(LogLevel.DEBUG, tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addLog(LogLevel.INFO, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
        addLog(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        addLog(LogLevel.ERROR, tag, message, throwable)
    }

    @Synchronized
    fun getAllLogs(): List<LogEntry> {
        return ArrayList(logList)
    }

    @Synchronized
    fun clear() {
        logList.clear()
        i("AppLogger", "Logs cleared by user")
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun formatAllLogs(logs: List<LogEntry> = getAllLogs()): String {
        val sb = StringBuilder()
        sb.append("=== Glanceables App Logs (${logs.size} entries) ===\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sb.append("Exported: ").append(sdf.format(Date())).append("\n\n")
        for (log in logs) {
            sb.append(log.toPlainText()).append("\n")
        }
        return sb.toString()
    }

    /**
     * Reads recent logcat output for this process.
     */
    fun readProcessLogcat(maxLines: Int = 200): List<String> {
        val lines = mutableListOf<String>()
        try {
            val pid = android.os.Process.myPid()
            val process = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=$pid")
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                lines.add(line)
                if (lines.size > maxLines) {
                    lines.removeAt(0)
                }
                line = reader.readLine()
            }
            reader.close()
            process.waitFor()
        } catch (e: Exception) {
            lines.add("Failed to read logcat: ${e.message}")
        }
        return lines
    }
}
