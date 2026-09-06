package com.nile.duduapiexplorer

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        DiscoveryStore.add("DUDU API Explorer 1.0.0 started")
        refresh()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        root.addView(TextView(this).apply {
            text = "DUDU API Explorer"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Read-only scanner: package/component -> vendor classes/methods -> FYT Binder modules. Không Accessibility, không READ_LOGS, không root, không gửi command."
            textSize = 13f
            setPadding(0, dp(4), 0, dp(8))
        })
        status = TextView(this).apply { text = "Sẵn sàng"; setPadding(0, 0, 0, dp(8)) }
        root.addView(status)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("QUÉT PACKAGE") { runPackageScan(false) }, weight())
        row1.addView(button("QUÉT API / METHOD") { runPackageScan(true) }, weight())
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("PROBE FYT TOOLKIT") { runToolkitProbe() }, weight())
        row2.addView(button("XUẤT REPORT") { exportReport() }, weight())
        row2.addView(button("XÓA") { DiscoveryStore.clear(); refresh() }, weight())
        root.addView(row2)

        scroll = ScrollView(this)
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(dp(4), dp(8), dp(4), dp(16))
        }
        scroll.addView(output, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun runPackageScan(deep: Boolean) {
        status.text = if (deep) "Đang quét class/method vendor..." else "Đang quét package/component..."
        Thread {
            try { PackageProbe.scan(applicationContext, deep) }
            catch (t: Throwable) { DiscoveryStore.add("SCAN_FATAL ${t.javaClass.name}: ${t.message}") }
            mainHandler.post { status.text = "Quét xong"; refresh() }
        }.start()
    }

    private fun runToolkitProbe() {
        status.text = "Đang bind FYT toolkit (read-only)..."
        FytToolkitProbe.probe(applicationContext) {
            mainHandler.post { status.text = "Toolkit probe xong"; refresh() }
        }
    }

    private fun exportReport() {
        val result = ReportExporter.export(applicationContext)
        result.onSuccess { Toast.makeText(this, "Đã lưu $it", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "Lỗi export: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun refresh() {
        output.text = DiscoveryStore.snapshot().ifBlank { "Chưa có kết quả." }
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun button(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
