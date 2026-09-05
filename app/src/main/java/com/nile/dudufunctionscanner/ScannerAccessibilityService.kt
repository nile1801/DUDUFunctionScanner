package com.nile.dudufunctionscanner

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ScannerAccessibilityService : AccessibilityService() {
    private var lastWindowPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        ScanStore.add("SCANNER: Accessibility connected")
        LogcatReader.start(applicationContext)
        FytMonitor.start(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg == packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg.isNotBlank() && pkg != lastWindowPackage) {
                lastWindowPackage = pkg
                ScanStore.add("WINDOW: pkg=$pkg class=${event.className ?: "?"}")
            }
            return
        }

        val kind = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONG_CLICK"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "SELECT"
            else -> return
        }

        val source = event.source
        try {
            val viewId = runCatching { source?.viewIdResourceName }.getOrNull()
            val sourceText = runCatching { source?.text?.toString() }.getOrNull()
            val eventText = event.text?.joinToString(" ")?.takeIf { it.isNotBlank() }
            val text = sourceText?.takeIf { it.isNotBlank() } ?: eventText
            val description = runCatching { source?.contentDescription?.toString() }.getOrNull()
            val className = source?.className?.toString() ?: event.className?.toString()
            val bounds = source?.let {
                Rect().also { rect -> runCatching { it.getBoundsInScreen(rect) } }
            }
            val semantic = FunctionCorrelator.semanticName(viewId, text, description)
            val action = ScanStore.startAction(
                kind = kind,
                packageName = pkg.ifBlank { "?" },
                className = className,
                viewId = viewId,
                text = text,
                description = description,
                bounds = bounds,
                semanticName = semantic
            )
            FunctionCorrelator.correlate(applicationContext, action)
        } finally {
            runCatching { source?.recycle() }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val keyName = KeyEvent.keyCodeToString(event.keyCode)
        val pkg = rootInActiveWindow?.packageName?.toString()
            ?: lastWindowPackage
            ?: "?"
        val semantic = FunctionCorrelator.semanticName(null, null, null, keyName)
        val action = ScanStore.startAction(
            kind = "KEY_DOWN",
            packageName = pkg,
            className = null,
            viewId = null,
            text = "keyCode=${event.keyCode}",
            description = null,
            bounds = null,
            semanticName = semantic
        )
        FunctionCorrelator.correlate(applicationContext, action)
        return false
    }

    override fun onInterrupt() {
        ScanStore.add("SCANNER: Accessibility interrupted")
    }

    override fun onDestroy() {
        ScanStore.add("SCANNER: Accessibility destroyed")
        FytMonitor.stop()
        LogcatReader.stop()
        super.onDestroy()
    }
}
