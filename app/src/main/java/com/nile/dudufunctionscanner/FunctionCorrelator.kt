package com.nile.dudufunctionscanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object FunctionCorrelator {
    private val handler = Handler(Looper.getMainLooper())
    private val componentCache = ConcurrentHashMap<String, List<String>>()

    fun semanticName(viewId: String?, text: String?, description: String?, keyName: String? = null): String {
        if (!keyName.isNullOrBlank()) return keyName
        val raw = listOfNotNull(description, text, viewId?.substringAfterLast('/'))
            .firstOrNull { it.isNotBlank() }
            ?: "unknown-control"
        val l = raw.lowercase(Locale.US)
        val meaning = when {
            l.contains("temp") && (l.contains("down") || l.contains("minus") || l.contains("dec")) -> "Climate temperature down"
            l.contains("temp") && (l.contains("up") || l.contains("plus") || l.contains("inc")) -> "Climate temperature up"
            l.contains("fan") && (l.contains("down") || l.contains("minus")) -> "Climate fan down"
            l.contains("fan") && (l.contains("up") || l.contains("plus")) -> "Climate fan up"
            l.contains("recirc") -> "Climate recirculation"
            l.contains("defrost") -> "Climate defrost"
            l.contains("climate") || l.contains("aircon") || l.contains("air_condition") -> "Climate control"
            l.contains("camera") || l.contains("360") -> "Camera / 360"
            l.contains("volume") || l.contains("vol_") -> "Volume"
            l.contains("home") -> "Home"
            l.contains("back") -> "Back"
            else -> raw
        }
        return if (meaning == raw) raw else "$meaning [$raw]"
    }

    fun correlate(context: Context, action: ScanStore.ActionContext) {
        handler.postDelayed({
            val evidence = LogcatReader.recentCandidates(action)
            if (evidence.isNotEmpty()) {
                ScanStore.add("FUNCTION EVIDENCE #${action.id} (logcat candidates):")
                evidence.forEach { ScanStore.add("  $it") }
            } else {
                ScanStore.add("FUNCTION #${action.id}: CHUA XAC DINH chinh xac; Android khong expose method call cua process khac")
            }

            val components = componentCache.getOrPut(action.packageName) {
                exportedComponents(context, action.packageName)
            }
            if (components.isNotEmpty()) {
                ScanStore.add("IPC/SERVICE CANDIDATES #${action.id} (static, not proof of invocation):")
                components.take(8).forEach { ScanStore.add("  $it") }
            }
            ScanStore.add("=== END ACTION #${action.id} ===")
        }, 450L)
    }

    @Suppress("DEPRECATION")
    private fun exportedComponents(context: Context, packageName: String): List<String> {
        return try {
            val flags = PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            val info = context.packageManager.getPackageInfo(packageName, flags)
            val out = ArrayList<String>()
            info.services?.filter { it.exported }?.forEach { out += "SERVICE ${it.name}${permissionSuffix(it.permission)}" }
            info.receivers?.filter { it.exported }?.forEach { out += "RECEIVER ${it.name}${permissionSuffix(it.permission)}" }
            info.providers?.filter { it.exported }?.forEach { out += "PROVIDER ${it.authority ?: it.name}${permissionSuffix(it.readPermission ?: it.writePermission)}" }
            out.sortedWith(compareBy<String> {
                val l = it.lowercase(Locale.US)
                when {
                    l.contains("climate") || l.contains("air") || l.contains("can") || l.contains("vehicle") -> 0
                    l.contains("syu") || l.contains("dudu") || l.contains("fyt") -> 1
                    else -> 2
                }
            }.thenBy { it })
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun permissionSuffix(permission: String?): String =
        if (permission.isNullOrBlank()) "" else " perm=$permission"
}
