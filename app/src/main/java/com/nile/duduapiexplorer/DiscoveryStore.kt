package com.nile.duduapiexplorer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

object DiscoveryStore {
    private val lock = Any()
    private val text = StringBuilder()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun clear() {
        synchronized(lock) { text.setLength(0) }
        notifyChanged()
    }

    fun section(title: String) {
        addRaw("\n========== $title ==========")
    }

    fun add(message: String) {
        addRaw("[${clock.format(Date())}] $message")
    }

    fun addRaw(message: String) {
        synchronized(lock) {
            if (text.isNotEmpty()) text.append('\n')
            text.append(message)
            if (text.length > 2_000_000) {
                text.delete(0, 500_000)
                text.insert(0, "[older output trimmed]\n")
            }
        }
        notifyChanged()
    }

    fun snapshot(): String = synchronized(lock) { text.toString() }

    private fun notifyChanged() {
        listeners.forEach { runCatching { it() } }
    }
}
