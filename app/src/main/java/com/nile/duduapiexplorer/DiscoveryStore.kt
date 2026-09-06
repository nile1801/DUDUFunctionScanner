package com.nile.duduapiexplorer

object DiscoveryStore {
    private val lock = Any()
    private val lines = mutableListOf<String>()

    fun clear() = synchronized(lock) { lines.clear() }

    fun add(text: String) = synchronized(lock) {
        lines += text
        if (lines.size > 12000) lines.subList(0, lines.size - 12000).clear()
    }

    fun addSection(title: String) {
        add("")
        add("========== $title ==========")
    }

    fun snapshot(): String = synchronized(lock) { lines.joinToString("\n") }
}
