package com.nile.duduapiexplorer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {
    fun export(context: Context): Result<String> = runCatching {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "dudu-api-explorer-$stamp.txt"
        val content = buildString {
            appendLine("DUDU API Explorer 1.0.0")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            appendLine("mode=read-only; no READ_LOGS; no Accessibility; no root; no CANBUS cmd")
            appendLine()
            append(DiscoveryStore.snapshot())
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/DUDUApiExplorer")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                ?: error("Cannot open MediaStore output")
        } else {
            val file = java.io.File(context.getExternalFilesDir(null), name)
            file.bufferedWriter().use { it.write(content) }
        }
        "Download/DUDUApiExplorer/$name"
    }
}
