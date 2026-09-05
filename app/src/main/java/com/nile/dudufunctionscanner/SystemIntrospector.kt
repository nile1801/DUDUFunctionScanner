package com.nile.dudufunctionscanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.util.Locale

object SystemIntrospector {
    fun scanAsync(context: Context) {
        Thread({ scan(context.applicationContext) }, "DUDU-System-Scan").apply {
            isDaemon = true
            start()
        }
    }

    @Suppress("DEPRECATION")
    private fun scan(context: Context) {
        val pm = context.packageManager
        ScanStore.add("=== DUDU/FYT API SCAN START ===")

        try {
            val intent = Intent("com.syu.ms.toolkit").setPackage("com.syu.ms")
            val matches = pm.queryIntentServices(intent, PackageManager.MATCH_ALL)
            if (matches.isEmpty()) {
                ScanStore.add("TOOLKIT: no exported query result for com.syu.ms.toolkit")
            } else {
                matches.forEach {
                    val si = it.serviceInfo
                    ScanStore.add("TOOLKIT SERVICE: ${si.packageName}/${si.name} exported=${si.exported} perm=${si.permission ?: "none"}")
                }
            }
        } catch (t: Throwable) {
            ScanStore.add("TOOLKIT QUERY ERROR: ${t.javaClass.simpleName}: ${t.message}")
        }

        try {
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            val packages = pm.getInstalledPackages(flags)
                .filter {
                    val p = it.packageName.lowercase(Locale.US)
                    p.contains("syu") || p.contains("dudu") || p.contains("fyt") ||
                        p.contains("canbus") || p.contains("vehicle") || p.contains("air")
                }
                .sortedBy { it.packageName }

            ScanStore.add("PACKAGES: found ${packages.size} DUDU/FYT/SYU candidates")
            packages.forEach { pi ->
                ScanStore.add("PKG ${pi.packageName} version=${pi.versionName ?: "?"}")
                pi.activities?.filter { it.exported }?.take(12)?.forEach {
                    ScanStore.add("  ACTIVITY ${it.name}")
                }
                pi.services?.filter { it.exported }?.take(20)?.forEach {
                    ScanStore.add("  SERVICE ${it.name} perm=${it.permission ?: "none"}")
                }
                pi.receivers?.filter { it.exported }?.take(20)?.forEach {
                    ScanStore.add("  RECEIVER ${it.name} perm=${it.permission ?: "none"}")
                }
                pi.providers?.filter { it.exported }?.take(20)?.forEach {
                    ScanStore.add("  PROVIDER ${it.authority ?: it.name} readPerm=${it.readPermission ?: "none"} writePerm=${it.writePermission ?: "none"}")
                }
            }
        } catch (t: Throwable) {
            ScanStore.add("PACKAGE SCAN ERROR: ${t.javaClass.simpleName}: ${t.message}")
        }

        try {
            val uri = Uri.parse("content://com.syu.ms.provider/bt")
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                ScanStore.add("PROVIDER bt: columns=${cursor.columnNames.contentToString()} rows=${cursor.count}")
                if (cursor.moveToFirst()) {
                    val values = cursor.columnNames.mapIndexed { i, name ->
                        val value = runCatching { cursor.getString(i) }.getOrNull()
                        "$name=$value"
                    }
                    ScanStore.add("PROVIDER bt firstRow: ${values.joinToString()}")
                }
            } ?: ScanStore.add("PROVIDER bt: query returned null")
        } catch (t: Throwable) {
            ScanStore.add("PROVIDER bt: unavailable (${t.javaClass.simpleName}: ${t.message})")
        }

        try {
            val modules = FytMonitor.probeModules()
            if (modules.isEmpty()) {
                ScanStore.add("FYT MODULE PROBE: toolkit not connected or no modules visible")
            } else {
                ScanStore.add("FYT MODULE PROBE:")
                modules.forEach { ScanStore.add("  $it") }
            }
        } catch (t: Throwable) {
            ScanStore.add("FYT MODULE PROBE ERROR: ${t.javaClass.simpleName}: ${t.message}")
        }

        ScanStore.add("=== DUDU/FYT API SCAN END ===")
    }
}
