package com.nile.duduapiexplorer

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var packageInput: EditText
    private lateinit var moduleInput: EditText
    private lateinit var getStartInput: EditText
    private lateinit var getEndInput: EditText
    private var refreshPending = false

    private val storeListener: () -> Unit = {
        if (!refreshPending) {
            refreshPending = true
            handler.postDelayed({
                refreshPending = false
                refreshLog()
            }, 120L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        DiscoveryStore.addListener(storeListener)
        DiscoveryStore.add("DUDU API Explorer 1.0.0 opened")
        DiscoveryStore.add("Security profile: NO permissions, NO Accessibility, NO READ_LOGS, NO QUERY_ALL_PACKAGES, NO root, NO CANBUS cmd")
        refreshLog()
        PackageScanner.scanAsync(applicationContext)
    }

    override fun onDestroy() {
        DiscoveryStore.removeListener(storeListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        root.addView(TextView(this).apply {
            text = "DUDU API Explorer"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Read-only discovery • không gửi CANBUS cmd • không Accessibility/logcat/root"
            textSize = 13f
            setPadding(0, dp(3), 0, dp(8))
        })

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("QUÉT PACKAGE") { PackageScanner.scanAsync(applicationContext) }, weight())
        row1.addView(button("PROBE FYT BINDER") { ToolkitProbe.probeModules(applicationContext) }, weight())
        root.addView(row1)

        root.addView(TextView(this).apply {
            text = "DEX/API scanner — nhập package đang cài:"
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        })
        val dexRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        packageInput = EditText(this).apply {
            setText("com.syu.canbus")
            isSingleLine = true
            textSize = 13f
        }
        dexRow.addView(packageInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        dexRow.addView(button("SCAN DEX/API") {
            DexApiScanner.scanAsync(applicationContext, packageInput.text.toString())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(dexRow)

        root.addView(TextView(this).apply {
            text = "FYT GET probe — chỉ đọc. Module mặc định 7 = CANBUS; tối đa 256 code/lần:"
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        })
        val getRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        moduleInput = numberInput("7")
        getStartInput = numberInput("0")
        getEndInput = numberInput("128")
        getRow.addView(moduleInput, weight())
        getRow.addView(getStartInput, weight())
        getRow.addView(getEndInput, weight())
        getRow.addView(button("SCAN GET") {
            val module = moduleInput.text.toString().toIntOrNull() ?: 7
            val start = getStartInput.text.toString().toIntOrNull() ?: 0
            val end = getEndInput.text.toString().toIntOrNull() ?: start
            ToolkitProbe.scanGetCodes(applicationContext, module, start, end)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f))
        root.addView(getRow)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(button("XUẤT REPORT TXT") {
            ReportExporter.exportAsync(applicationContext) { result ->
                result.onSuccess {
                    DiscoveryStore.add("Report saved: $it")
                    Toast.makeText(this, "Đã lưu $it", Toast.LENGTH_LONG).show()
                }.onFailure {
                    DiscoveryStore.add("Report export failed: ${it.javaClass.simpleName}: ${it.message}")
                    Toast.makeText(this, "Xuất report lỗi: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }, weight())
        row3.addView(button("XÓA OUTPUT") { DiscoveryStore.clear() }, weight())
        root.addView(row3)

        root.addView(TextView(this).apply {
            text = "Gợi ý: chạy QUÉT PACKAGE → PROBE FYT BINDER → SCAN DEX/API lần lượt cho com.syu.canbus, com.syu.ms, com.dudu.autoui. GET probe chỉ dùng khi cần xem API đọc của một module."
            textSize = 12f
            setPadding(0, dp(5), 0, dp(5))
        })

        logScroll = ScrollView(this)
        logText = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11.5f
            setTextIsSelectable(true)
            setPadding(dp(4), dp(6), dp(4), dp(20))
        }
        logScroll.addView(logText, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun numberInput(value: String) = EditText(this).apply {
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER
        isSingleLine = true
        textSize = 13f
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun refreshLog() {
        if (!::logText.isInitialized) return
        logText.text = DiscoveryStore.snapshot().ifBlank { "Chưa có output" }
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
