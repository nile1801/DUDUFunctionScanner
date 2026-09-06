package com.nile.dudufunctionscanner

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VendorApkExporter {
    private const val RELATIVE_DIR = "${Environment.DIRECTORY_DOWNLOADS}/DUDUFunctionScanner/vendor-apks"

    private val priorityPackages = listOf(
        "com.syu.canbus",
        "com.syu.ms",
        "com.syu.air",
        "com.syu.carui",
        "com.dudu.autoui",
        "com.syu.protocolupdate",
        "com.syu.mcukey",
        "com.syu.cs"
    )

    data class Summary(
        val foundPackages: Int,
        val copiedApks: Int,
        val failedApks: Int,
        val missingPriorityPackages: List<String>,
        val reportName: String
    ) {
        fun displayText(): String = buildString {
            append("Tim thay $foundPackages package vendor, copy $copiedApks APK")
            if (failedApks > 0) append(", loi $failedApks APK")
            if (missingPriorityPackages.isNotEmpty()) {
                append("\nThieu: ${missingPriorityPackages.joinToString()}")
            }
            append("\nDa luu: $RELATIVE_DIR/$reportName")
        }
    }

    fun exportAsync(context: Context, callback: (Result<Summary>) -> Unit) {
        val app = context.applicationContext
        Thread({
            val result = runCatching { exportNow(app) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
        }, "DUDU-Vendor-Apk-Exporter").start()
    }

    private fun exportNow(context: Context): Summary {
        val packageManager = context.packageManager
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val report = StringBuilder()

        report.appendLine("DUDU Function Scanner - vendor APK export")
        report.appendLine("timestamp=$stamp")
        report.appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        report.appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        report.appendLine("expected_g50_vehicle_id=917769")
        report.appendLine("expected_public_handler=265")
        report.appendLine("expected_public_air_profile=AIR_0265_RZC_ShangQiT60")
        report.appendLine()

        val installedVendorPackages = packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { it.startsWith("com.syu.") || it.startsWith("com.dudu.") }
            .distinct()
            .sorted()

        report.appendLine("=== VENDOR PACKAGE INVENTORY ===")
        installedVendorPackages.forEach { report.appendLine(it) }
        report.appendLine()

        val packagesToExport = (priorityPackages + installedVendorPackages).distinct()
        val missingPriority = mutableListOf<String>()
        var foundPackages = 0
        var copiedApks = 0
        var failedApks = 0

        packagesToExport.forEach { packageName ->
            report.appendLine("=== $packageName ===")
            val info = try {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            } catch (t: Throwable) {
                if (packageName in priorityPackages) missingPriority += packageName
                report.appendLine("status=NOT_FOUND")
                report.appendLine("error=${formatError(t)}")
                report.appendLine()
                return@forEach
            }

            foundPackages++
            appendMetadata(report, info)

            val appInfo = info.applicationInfo
            val sources = buildList {
                appInfo?.sourceDir?.let { add("base" to it) }
                appInfo?.splitSourceDirs?.forEachIndexed { index, path ->
                    add("split${index + 1}" to path)
                }
            }

            if (sources.isEmpty()) {
                failedApks++
                report.appendLine("copy_status=NO_SOURCE_PATH")
                report.appendLine()
                return@forEach
            }

            sources.forEachIndexed { index, (kind, sourcePath) ->
                val source = File(sourcePath)
                val suffix = if (sources.size == 1) "" else "-$kind"
                val outputName = "${safeName(packageName)}$suffix-$stamp.apk"

                report.appendLine("source[$index]=$sourcePath")
                report.appendLine("source_exists[$index]=${source.exists()}")
                report.appendLine("source_readable[$index]=${source.canRead()}")
                report.appendLine("source_size[$index]=${runCatching { source.length() }.getOrDefault(-1L)}")

                try {
                    val sha256 = copyApk(context, source, outputName)
                    copiedApks++
                    report.appendLine("output[$index]=$RELATIVE_DIR/$outputName")
                    report.appendLine("sha256[$index]=$sha256")
                    report.appendLine("copy_status[$index]=OK")
                } catch (t: Throwable) {
                    failedApks++
                    report.appendLine("copy_status[$index]=FAILED")
                    report.appendLine("copy_error[$index]=${formatError(t)}")
                }
            }
            report.appendLine()
        }

        val reportName = "vendor-apk-report-$stamp.txt"
        writeMediaStoreFile(
            context = context,
            fileName = reportName,
            mimeType = "text/plain",
            writer = { output -> output.write(report.toString().toByteArray(Charsets.UTF_8)) }
        )

        return Summary(
            foundPackages = foundPackages,
            copiedApks = copiedApks,
            failedApks = failedApks,
            missingPriorityPackages = missingPriority,
            reportName = reportName
        )
    }

    private fun appendMetadata(report: StringBuilder, info: PackageInfo) {
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val appInfo = info.applicationInfo
        report.appendLine("status=FOUND")
        report.appendLine("versionName=${info.versionName.orEmpty()}")
        report.appendLine("versionCode=$versionCode")
        report.appendLine("sourceDir=${appInfo?.sourceDir.orEmpty()}")
        report.appendLine("publicSourceDir=${appInfo?.publicSourceDir.orEmpty()}")
        report.appendLine("nativeLibraryDir=${appInfo?.nativeLibraryDir.orEmpty()}")
        report.appendLine("splitSourceDirs=${appInfo?.splitSourceDirs?.joinToString(" | ").orEmpty()}")
    }

    private fun copyApk(context: Context, source: File, outputName: String): String {
        require(source.exists()) { "Source APK khong ton tai: ${source.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-256")

        writeMediaStoreFile(
            context = context,
            fileName = outputName,
            mimeType = "application/vnd.android.package-archive"
        ) { output ->
            FileInputStream(source).use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun writeMediaStoreFile(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "DUDUFunctionScanner/vendor-apks")
            if (!dir.exists() && !dir.mkdirs()) error("Khong tao duoc ${dir.absolutePath}")
            java.io.FileOutputStream(File(dir, fileName)).use(writer)
            return
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert returned null: $fileName")

        try {
            context.contentResolver.openOutputStream(uri, "w")?.use(writer)
                ?: error("Khong mo duoc output stream: $fileName")
            val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, ready, null, null)
        } catch (t: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun formatError(t: Throwable): String {
        val message = t.message?.replace('\n', ' ')?.replace('\r', ' ').orEmpty()
        return "${t.javaClass.name}: $message"
    }
}
