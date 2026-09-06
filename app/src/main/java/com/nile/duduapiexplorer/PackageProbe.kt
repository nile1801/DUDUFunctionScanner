package com.nile.duduapiexplorer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dalvik.system.DexFile
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Modifier

object PackageProbe {
    fun scan(context: Context, deep: Boolean) {
        val pm = context.packageManager
        DiscoveryStore.addSection(if (deep) "PACKAGE + API SCAN" else "PACKAGE SCAN")
        KnownTargets.packages.forEach { pkg ->
            val info = getPackageInfo(pm, pkg) ?: return@forEach
            dumpPackage(info, pm)
            if (deep) scanCode(context, info)
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(pm: PackageManager, pkg: String): PackageInfo? {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
            PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else pm.getPackageInfo(pkg, flags)
        } catch (_: Throwable) {
            DiscoveryStore.add("NOT_INSTALLED $pkg")
            null
        }
    }

    private fun dumpPackage(info: PackageInfo, pm: PackageManager) {
        val app = info.applicationInfo ?: return
        val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault("")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        DiscoveryStore.add("")
        DiscoveryStore.add("PACKAGE ${info.packageName}")
        DiscoveryStore.add("  label=$label version=${info.versionName}($versionCode)")
        DiscoveryStore.add("  sourceDir=${app.sourceDir}")
        DiscoveryStore.add("  uid=${app.uid} flags=0x${app.flags.toString(16)}")
        dumpComponents("ACTIVITY", info.activities?.map { Triple(it.name, it.exported, it.permission) }.orEmpty())
        dumpComponents("SERVICE", info.services?.map { Triple(it.name, it.exported, it.permission) }.orEmpty())
        dumpComponents("RECEIVER", info.receivers?.map { Triple(it.name, it.exported, it.permission) }.orEmpty())
        dumpComponents("PROVIDER", info.providers?.map { Triple(it.name, it.exported, it.readPermission ?: it.writePermission) }.orEmpty())
        info.requestedPermissions?.forEach { DiscoveryStore.add("  REQUESTED_PERMISSION $it") }
    }

    private fun dumpComponents(type: String, values: List<Triple<String, Boolean, String?>>) {
        values.sortedBy { it.first }.forEach { (name, exported, permission) ->
            DiscoveryStore.add("  $type name=$name exported=$exported permission=${permission.orEmpty()}")
        }
    }

    private fun scanCode(context: Context, info: PackageInfo) {
        val app = info.applicationInfo ?: return
        val source = app.sourceDir ?: return
        if (!File(source).canRead()) {
            DiscoveryStore.add("  CODE_SCAN source_not_readable")
            return
        }

        val candidates = mutableListOf<String>()
        try {
            DexFile(source).use { dex ->
                val e = dex.entries()
                while (e.hasMoreElements() && candidates.size < 220) {
                    val name = e.nextElement()
                    val lower = name.lowercase()
                    if ((name.startsWith("com.syu.") || name.startsWith("com.dudu.")) &&
                        KnownTargets.classKeywords.any { lower.contains(it) }) {
                        candidates += name
                    }
                }
            }
        } catch (t: Throwable) {
            DiscoveryStore.add("  CODE_SCAN dex_error=${t.javaClass.simpleName}:${t.message}")
            return
        }

        DiscoveryStore.add("  CODE_SCAN candidate_classes=${candidates.size}")
        val loader = PathClassLoader(source, context.classLoader)
        var methodBudget = 700
        candidates.sorted().forEach { className ->
            if (methodBudget <= 0) return@forEach
            try {
                val cls = Class.forName(className, false, loader)
                val methods = cls.declaredMethods
                    .filter { Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers) }
                    .take(18)
                val fields = cls.declaredFields
                    .filter { Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers) }
                    .take(12)
                if (methods.isNotEmpty() || fields.isNotEmpty()) {
                    DiscoveryStore.add("  CLASS $className")
                    fields.forEach { f -> DiscoveryStore.add("    FIELD ${Modifier.toString(f.modifiers)} ${f.type.simpleName} ${f.name}") }
                    methods.forEach { m ->
                        val args = m.parameterTypes.joinToString(",") { it.simpleName }
                        DiscoveryStore.add("    METHOD ${Modifier.toString(m.modifiers)} ${m.returnType.simpleName} ${m.name}($args)")
                        methodBudget--
                    }
                }
            } catch (t: Throwable) {
                DiscoveryStore.add("  CLASS_FAIL $className ${t.javaClass.simpleName}")
            }
        }
    }
}
