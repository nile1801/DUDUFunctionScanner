package com.nile.duduapiexplorer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {
    fun exportAsync(context: Context, callback: (Result<String>) -> Unit) {
        val app = context.applicationContext
        Thread({
            val result = runCatching { export(app) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
        }, "DUDU-Report-Exporter").start()
    }

    private fun export(context: Context): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "dudu-api-explorer-$stamp.txt"
        val content = buildString {
            appendLine("DUDU API Explorer 1.0.0")
            appendLine("package=${context.packageName}")
            appendLine("permissions=NONE")
            appendLine("mode=read-only discovery")
            appendLine()
            append(DiscoveryStore.snapshot())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = Environment.DIRECTORY_DOWNLOADS + "/DUDUApiExplorer"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(content) }
                    ?: error("Cannot open MediaStore output")
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
            } catch (t: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw t
            }
            return "$relative/$name"
        }

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DUDUApiExplorer").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(content)
        return file.absolutePath
    }
}
