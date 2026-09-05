package com.nile.dudufunctionscanner

import android.content.Context
import android.content.pm.PackageManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object LogcatReader {
    private const val MAX_RAW = 1200
    private val raw = ArrayDeque<String>()
    private val running = AtomicBoolean(false)
    @Volatile private var process: Process? = null

    fun start(context: Context) {
        if (!running.compareAndSet(false, true)) return
        Thread({ runReader(context.applicationContext) }, "DUDU-Logcat").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        runCatching { process?.destroy() }
        process = null
    }

    private fun runReader(context: Context) {
        val prefs = context.getSharedPreferences("scanner", Context.MODE_PRIVATE)
        val rootMode = prefs.getBoolean("root_logcat", false)
        val hasReadLogs = context.checkSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED
        ScanStore.add("LOGCAT: READ_LOGS=${if (hasReadLogs) "granted" else "not granted"}, rootMode=$rootMode")
        if (!hasReadLogs && !rootMode) {
            ScanStore.add("LOGCAT: limited. ADB: pm grant com.nile.dudufunctionscanner android.permission.READ_LOGS")
        }

        try {
            val command = if (rootMode) {
                arrayOf("su", "-c", "logcat -v time -b main -b system -b events -b crash")
            } else {
                arrayOf("logcat", "-v", "time", "-b", "main", "-b", "system", "-b", "events", "-b", "crash")
            }
            val p = Runtime.getRuntime().exec(command)
            process = p
            BufferedReader(InputStreamReader(p.inputStream)).use { reader ->
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    if (line.contains("com.nile.dudufunctionscanner")) continue
                    synchronized(raw) {
                        raw.addLast(line)
                        while (raw.size > MAX_RAW) raw.removeFirst()
                    }
                }
            }
        } catch (t: Throwable) {
            ScanStore.add("LOGCAT ERROR: ${t.javaClass.simpleName}: ${t.message}")
        } finally {
            process = null
            running.set(false)
        }
    }

    fun recentCandidates(action: ScanStore.ActionContext, limit: Int = 8): List<String> {
        val pkgToken = action.packageName.lowercase(Locale.US)
        val idToken = action.viewId?.substringAfterLast('/')?.lowercase(Locale.US)
        val semantic = action.semanticName.lowercase(Locale.US)
        val important = listOf(
            "cmd(", "binder", "service", "toolkit", "canbus", "climate", "aircon", "air_condition",
            "temperature", "temp", "fan", "mcu", "syu", "vehicle", "camera", "360"
        )
        val copy = synchronized(raw) { raw.toList() }
        return copy.asReversed().asSequence().filter { line ->
            val l = line.lowercase(Locale.US)
            val pkgMatch = pkgToken.isNotBlank() && l.contains(pkgToken)
            val idMatch = !idToken.isNullOrBlank() && l.contains(idToken)
            val semanticMatch = semantic.length >= 4 && l.contains(semantic)
            val importantMatch = important.any { l.contains(it) }
            (pkgMatch || idMatch || semanticMatch) && importantMatch
        }.take(limit).toList().reversed()
    }
}
