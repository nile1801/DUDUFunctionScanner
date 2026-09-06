package com.nile.dudufunctionscanner

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var vendorExportButton: Button
    private lateinit var vendorExportStatus: TextView
    private var refreshPending = false

    private val storeListener: () -> Unit = {
        scheduleRefresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        ScanStore.addListener(storeListener)
        ScanStore.add("APP: DUDU Function Scanner 0.2.0 opened")
        refreshStatus()
        refreshLog()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        ScanStore.removeListener(storeListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        root.addView(TextView(this).apply {
            text = "DUDU Function Scanner"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(this).apply {
            text = "Click/Key -> UI node -> logcat/service/Binder candidates -> FYT MAIN/BT/CANBUS correlation"
            textSize = 13f
            setPadding(0, dp(3), 0, dp(8))
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(statusText)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("CAI ACCESSIBILITY") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, weightParams())
        row1.addView(button("QUET API / SERVICE") {
            FytMonitor.start(applicationContext)
            handler.postDelayed({ SystemIntrospector.scanAsync(applicationContext) }, 700L)
        }, weightParams())
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("XOA LOG") { ScanStore.clear() }, weightParams())
        row2.addView(button("XUAT TXT") { exportLog() }, weightParams())
        row2.addView(button("COPY LENH ADB") { copyAdbCommand() }, weightParams())
        root.addView(row2)

        vendorExportButton = button("XUAT DUDU / FYT VENDOR APK") {
            exportVendorApks()
        }
        root.addView(vendorExportButton)

        vendorExportStatus = TextView(this).apply {
            text = "Export APK: chua chay. File se nam trong Downloads/DUDUFunctionScanner/vendor-apks/"
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, dp(2), 0, dp(6))
        }
        root.addView(vendorExportStatus)

        val prefs = getSharedPreferences("scanner", Context.MODE_PRIVATE)
        root.addView(CheckBox(this).apply {
            text = "Root logcat mode (chi bat neu DUDU da root)"
            isChecked = prefs.getBoolean("root_logcat", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("root_logcat", checked).apply()
                LogcatReader.stop()
                LogcatReader.start(applicationContext)
                refreshStatus()
            }
        })

        root.addView(TextView(this).apply {
            text = "De thay them system log: adb shell pm grant com.nile.dudufunctionscanner android.permission.READ_LOGS\n" +
                "Luu y: FUNCTION chi duoc ghi la chinh xac khi co evidence. Accessibility khong the tu nhin thay method Kotlin/Java cua app khac."
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, dp(4), 0, dp(8))
        })

        logScroll = ScrollView(this)
        logText = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(dp(4), dp(6), dp(4), dp(16))
        }
        logScroll.addView(
            logText,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun scheduleRefresh() {
        if (refreshPending) return
        refreshPending = true
        handler.postDelayed({
            refreshPending = false
            refreshStatus()
            refreshLog()
        }, 180L)
    }

    private fun refreshLog() {
        val lines = ScanStore.snapshot(900)
        logText.text = if (lines.isEmpty()) "Dang cho event..." else lines.joinToString("\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun refreshStatus() {
        val accessibility = isAccessibilityEnabled()
        val readLogs = checkSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED
        val rootMode = getSharedPreferences("scanner", Context.MODE_PRIVATE).getBoolean("root_logcat", false)
        statusText.text = buildString {
            append("Accessibility: ${if (accessibility) "ON" else "OFF"}")
            append("   |   READ_LOGS: ${if (readLogs) "YES" else "NO"}")
            append("   |   Root logcat: ${if (rootMode) "ON" else "OFF"}")
            if (!accessibility) append("\n-> Can bat Accessibility de scanner nhin thay nut cua app DUDU khac.")
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, ScannerAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun copyAdbCommand() {
        val command = "adb shell pm grant com.nile.dudufunctionscanner android.permission.READ_LOGS"
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("DUDU scanner ADB", command))
        Toast.makeText(this, "Da copy lenh ADB", Toast.LENGTH_SHORT).show()
    }

    private fun exportVendorApks() {
        if (!vendorExportButton.isEnabled) return
        vendorExportButton.isEnabled = false
        vendorExportButton.text = "DANG XUAT VENDOR APK..."
        vendorExportStatus.text = "Dang tim package com.syu.* / com.dudu.* va copy APK..."
        ScanStore.add("VENDOR_EXPORT: START")

        VendorApkExporter.exportAsync(applicationContext) { result ->
            vendorExportButton.isEnabled = true
            vendorExportButton.text = "XUAT DUDU / FYT VENDOR APK"

            result.onSuccess { summary ->
                val text = summary.displayText()
                vendorExportStatus.text = text
                ScanStore.add("VENDOR_EXPORT: OK found=${summary.foundPackages} copied=${summary.copiedApks} failed=${summary.failedApks}")
                ScanStore.add("VENDOR_EXPORT: report=${summary.reportName}")
                Toast.makeText(this, "Xuat vendor APK xong", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                val text = "Export vendor APK loi: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                vendorExportStatus.text = text
                ScanStore.add("VENDOR_EXPORT: FAILED $text")
                Toast.makeText(this, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportLog() {
        val content = ScanStore.exportText()
        if (content.isBlank()) {
            Toast.makeText(this, "Chua co log de xuat", Toast.LENGTH_SHORT).show()
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "dudu-function-scan-$stamp.txt"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DUDUFunctionScanner")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert returned null")
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                    ?: error("Cannot open output stream")
                Toast.makeText(this, "Da luu Downloads/DUDUFunctionScanner/$fileName", Toast.LENGTH_LONG).show()
            } else {
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DUDUFunctionScanner").apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).bufferedWriter().use { it.write(content) }
                Toast.makeText(this, "Da luu ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Xuat log loi: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
