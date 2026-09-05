package com.nile.dudufunctionscanner

import android.graphics.Rect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

object ScanStore {
    data class ActionContext(
        val id: Long,
        val epochMs: Long,
        val kind: String,
        val packageName: String,
        val className: String?,
        val viewId: String?,
        val text: String?,
        val description: String?,
        val bounds: Rect?,
        val semanticName: String
    )

    private const val MAX_LINES = 2500
    private val lines = ArrayDeque<String>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val actionCounter = AtomicLong(0)
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    var lastAction: ActionContext? = null
        private set

    @Synchronized
    fun add(message: String) {
        val stamped = "${format.format(Date())}  $message"
        lines.addLast(stamped)
        while (lines.size > MAX_LINES) lines.removeFirst()
        listeners.forEach { runCatching { it.invoke() } }
    }

    fun startAction(
        kind: String,
        packageName: String,
        className: String?,
        viewId: String?,
        text: String?,
        description: String?,
        bounds: Rect?,
        semanticName: String
    ): ActionContext {
        val action = ActionContext(
            id = actionCounter.incrementAndGet(),
            epochMs = System.currentTimeMillis(),
            kind = kind,
            packageName = packageName,
            className = className,
            viewId = viewId,
            text = text,
            description = description,
            bounds = bounds,
            semanticName = semanticName
        )
        lastAction = action
        add("=== ACTION #${action.id} ===")
        add("INPUT: ${action.kind} | nut: ${action.semanticName}")
        add("UI: pkg=${action.packageName} class=${action.className ?: "?"} id=${action.viewId ?: "?"}")
        if (!action.text.isNullOrBlank()) add("TEXT: ${action.text}")
        if (!action.description.isNullOrBlank()) add("DESC: ${action.description}")
        action.bounds?.let { add("BOUNDS: [${it.left},${it.top}]-[${it.right},${it.bottom}]") }
        return action
    }

    fun recordFyt(module: String, index: Int, ints: IntArray?, floats: FloatArray?, strings: Array<String?>?) {
        val payload = buildString {
            append("$module:$index")
            if (ints != null) append(" ints=${ints.contentToString()}")
            if (floats != null && floats.isNotEmpty()) append(" floats=${floats.contentToString()}")
            if (strings != null && strings.isNotEmpty()) append(" strings=${strings.contentToString()}")
        }
        val action = lastAction
        val age = if (action == null) Long.MAX_VALUE else System.currentTimeMillis() - action.epochMs
        if (action != null && age in 0..1800) {
            add("CORRELATED #${action.id}: $payload (+${age}ms)")
        } else {
            add("FYT: $payload")
        }
    }

    @Synchronized
    fun clear() {
        lines.clear()
        lastAction = null
        listeners.forEach { runCatching { it.invoke() } }
    }

    @Synchronized
    fun snapshot(max: Int = 1200): List<String> {
        if (lines.size <= max) return lines.toList()
        return lines.drop(lines.size - max)
    }

    @Synchronized
    fun exportText(): String = lines.joinToString("\n")

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }
}
