package com.idlix.tv.startup

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny crash/error breadcrumb log in internal storage.
 *
 * - File: `<filesDir>/startup_error.log`. Internal storage needs no permissions.
 * - Each entry is capped to [ENTRY_CAP_CHARS] chars (stacktrace head).
 * - Ring: file never exceeds [MAX_BYTES]; overflow drops the oldest head.
 * - PII scrub before write: emails and key/token-shaped values are redacted.
 */
object ErrorLog {

    const val FILE_NAME = "startup_error.log"

    /** Ring-file ceiling: 64 KB. */
    const val MAX_BYTES = 64 * 1024

    /** Per-entry cap: 2 KB head (timestamp + tag + message + stacktrace head). */
    const val ENTRY_CAP_CHARS = 2048

    private val lock = Any()

    private val emailRegex =
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    private val secretRegex =
        Regex("(?i)(api[_-]?key|auth[_-]?token|access[_-]?token|refresh[_-]?token|bearer|secret|password|passwd|subdl[_-]?key)(\\s*[:=]\\s*)(['\"]?)([^\\s'\";,]+)")

    private fun timestamp(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        return fmt.format(Date())
    }

    /** Scrub PII-shaped values; safe to call from any thread. */
    fun scrub(raw: String): String {
        var out = emailRegex.replace(raw, "[email-redacted]")
        out = secretRegex.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + "[REDACTED]" }
        return out
    }

    fun append(context: Context, throwable: Throwable, tag: String = "startup") {
        val head = buildString {
            append(throwable.javaClass.name)
            val msg = throwable.message
            if (!msg.isNullOrBlank()) {
                append(": ")
                append(msg.take(300))
            }
            val trace = throwable.stackTrace
            if (trace.isNotEmpty()) {
                append("\n")
                append(trace.take(12).joinToString("\n") { "    at $it" })
            }
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 2) {
                append("\nCaused by: ${cause.javaClass.name}")
                val cmsg = cause.message
                if (!cmsg.isNullOrBlank()) append(": ${cmsg.take(200)}")
                depth++
                cause = cause.cause
            }
        }
        appendMessage(context, tag, head)
    }

    fun appendMessage(context: Context, tag: String, message: String) {
        val entry = scrub("[${timestamp()}] [$tag] $message").take(ENTRY_CAP_CHARS)
        synchronized(lock) {
            try {
                val file = logFile(context)
                file.parentFile?.mkdirs()
                var existing = if (file.exists()) file.readText() else ""
                var combined = if (existing.isEmpty()) entry else "$existing\n---\n$entry"
                if (combined.toByteArray().size > MAX_BYTES) {
                    // Drop the oldest head; keep the tail under half the ceiling so the
                    // next appends have room. Cut on a newline boundary when possible.
                    val bytes = combined.toByteArray()
                    var start = bytes.size - MAX_BYTES / 2
                    if (start < 0) start = 0
                    var cut = start
                    while (cut < bytes.size && bytes[cut] != '\n'.code.toByte()) cut++
                    combined = String(bytes, cut.coerceAtMost(bytes.size - 1), bytes.size - cut.coerceAtMost(bytes.size - 1))
                }
                file.writeText(combined)
            } catch (_: Throwable) {
                // Logging must never crash the app.
            }
        }
    }

    /** Newest-first head of the log, capped for settings/diagnostics display. */
    fun readHead(context: Context, maxChars: Int = 8000): String {
        return try {
            val file = logFile(context)
            if (!file.exists()) return ""
            val text = file.readText()
            if (text.length <= maxChars) text else text.takeLast(maxChars)
        } catch (_: Throwable) {
            ""
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            try {
                val file = logFile(context)
                if (file.exists()) file.delete()
            } catch (_: Throwable) {
                // Best effort.
            }
        }
    }

    private fun logFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)
}
