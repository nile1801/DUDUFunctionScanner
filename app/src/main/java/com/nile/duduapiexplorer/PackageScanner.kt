package com.nile.duduapiexplorer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object PackageScanner {
    fun scanAsync(context: Context) {
        val app = context.applicationContext
        Thread({ scan(app) }, "DUDU-Package-Scanner").start()
    }

    private fun scan(context: Context) {
        val pm = context.packageManager
        DiscoveryStore.section("PACKAGE + COMPONENT DISCOVERY")
        DiscoveryStore.add("Device=${Build.MANUFACTURER} ${Build.MODEL}; Android=${Build.VERSION.RELEASE}; SDK=${Build.VERSION.SDK_INT}")
        DiscoveryStore.add("App package=${context.packageName}; declared Android permissions=NONE")

        var found = 0
        KnownTargets.packages.forEach { packageName ->
            val info = getPackageInfo(pm, packageName)
            if (info == null) {
                DiscoveryStore.add("MISS $packageName")
                return@forEach
            }
            found++
            dumpPackage(info)
        }
        DiscoveryStore.add("Package summary: $found/${KnownTargets.packages.size} known targets visible")

        DiscoveryStore.section("KNOWN INTENT RESOLUTION")
        KnownTargets.actions.forEach { action -> resolveAction(pm, action) }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(pm: PackageManager, packageName: String): PackageInfo? {
        val flags = PackageManager.GET_ACTIVITIES.toLong() or
            PackageManager.GET_SERVICES.toLong() or
            PackageManager.GET_RECEIVERS.toLong() or
            PackageManager.GET_PROVIDERS.toLong() or
            PackageManager.GET_PERMISSIONS.toLong() or
            PackageManager.GET_META_DATA.toLong() or
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
            } else {
                pm.getPackageInfo(packageName, flags.toInt())
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun dumpPackage(info: PackageInfo) {
        val app = info.applicationInfo
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        DiscoveryStore.addRaw("\n--- ${info.packageName} ---")
        DiscoveryStore.addRaw("version=${info.versionName.orEmpty()} ($versionCode)")
        if (app != null) {
            val kind = if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) "SYSTEM" else "USER"
            DiscoveryStore.addRaw("app=$kind uid=${app.uid} process=${app.processName.orEmpty()}")
            DiscoveryStore.addRaw("sourceDir=${app.sourceDir.orEmpty()}")
            DiscoveryStore.addRaw("nativeLibraryDir=${app.nativeLibraryDir.orEmpty()}")
        }
        signingSha256(info)?.let { DiscoveryStore.addRaw("signerSHA256=$it") }
        info.requestedPermissions?.takeIf { it.isNotEmpty() }?.let {
            DiscoveryStore.addRaw("requestedPermissions=${it.joinToString()}")
        }

        info.activities?.forEach {
            DiscoveryStore.addRaw("ACTIVITY ${it.name} exported=${it.exported} enabled=${it.enabled} permission=${it.permission.orEmpty()} process=${it.processName.orEmpty()}")
        }
        info.services?.forEach {
            DiscoveryStore.addRaw("SERVICE ${it.name} exported=${it.exported} enabled=${it.enabled} permission=${it.permission.orEmpty()} process=${it.processName.orEmpty()}")
        }
        info.receivers?.forEach {
            DiscoveryStore.addRaw("RECEIVER ${it.name} exported=${it.exported} enabled=${it.enabled} permission=${it.permission.orEmpty()} process=${it.processName.orEmpty()}")
        }
        info.providers?.forEach {
            DiscoveryStore.addRaw("PROVIDER ${it.name} exported=${it.exported} enabled=${it.enabled} authority=${it.authority.orEmpty()} readPermission=${it.readPermission.orEmpty()} writePermission=${it.writePermission.orEmpty()}")
        }
    }

    private fun signingSha256(info: PackageInfo): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val signers = info.signingInfo?.apkContentsSigners ?: return null
        if (signers.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun resolveAction(pm: PackageManager, action: String) {
        val intent = Intent(action)
        val services = runCatching { pm.queryIntentServices(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        val receivers = runCatching { pm.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        val activities = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        DiscoveryStore.addRaw("ACTION $action")
        if (services.isEmpty() && receivers.isEmpty() && activities.isEmpty()) {
            DiscoveryStore.addRaw("  no visible resolver")
        }
        services.forEach { DiscoveryStore.addRaw("  SERVICE ${it.serviceInfo?.packageName}/${it.serviceInfo?.name} exported=${it.serviceInfo?.exported}") }
        receivers.forEach { DiscoveryStore.addRaw("  RECEIVER ${it.activityInfo?.packageName}/${it.activityInfo?.name} exported=${it.activityInfo?.exported}") }
        activities.forEach { DiscoveryStore.addRaw("  ACTIVITY ${it.activityInfo?.packageName}/${it.activityInfo?.name} exported=${it.activityInfo?.exported}") }
    }
}
