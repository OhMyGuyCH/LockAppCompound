package com.company.kioskscan

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Kiosk WebView Activity
 * - โหลด URL เดียวตายตัว จำกัดการนำทางให้อยู่แค่ host ที่กำหนด
 * - ปุ่ม Back ไม่ปิดแอป (ใช้ย้อนกลับหน้าในเว็บเท่านั้น)
 * - ถ้าแอปนี้ถูกตั้งเป็น Device Owner: เข้า Lock Task Mode แบบไม่มี popup ยืนยันใดๆ
 *   (ถ้ายังไม่ได้ตั้งเป็น Device Owner จะ fallback เป็น Screen Pinning ปกติซึ่งจะมี popup ระบบโผล่)
 * - จุดลับ (มุมขวาบน แตะ 5 ครั้งใน 3 วิ) เปิด dialog ใส่รหัสผ่าน
 *   เพื่อ "ออกจากแอป" หรือ "เปลี่ยนรหัสผ่าน"
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences

    private var tapCount = 0
    private var firstTapTime = 0L
    private val TAP_WINDOW_MS = 3000L
    private val TAPS_NEEDED = 5

    private lateinit var allowedHost: String
    private lateinit var targetUrl: String

    companion object {
        private const val PREFS_NAME = "kiosk_prefs"
        private const val KEY_PASSWORD = "exit_password"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_PASSWORD)) {
            prefs.edit().putString(KEY_PASSWORD, getString(R.string.default_password)).apply()
        }

        allowedHost = getString(R.string.allowed_host)
        targetUrl = getString(R.string.target_url)

        webView = findViewById(R.id.webView)
        setupWebView()

        val exitTrigger = findViewById<View>(R.id.exitTrigger)
        exitTrigger.setOnClickListener { onSecretTap() }

        // ถ้าเป็น Device Owner แล้ว ให้ whitelist ตัวเองเป็น lock task package
        // ทำครั้งเดียวตอนสตาร์ทแอปก็พอ (ทำซ้ำได้ ไม่มีผลเสีย)
        registerAsLockTaskPackageIfDeviceOwner()
    }

    private fun registerAsLockTaskPackageIfDeviceOwner() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        try {
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            }
        } catch (e: Exception) {
            // ยังไม่ได้เป็น Device Owner หรืออุปกรณ์ไม่รองรับ - ไม่เป็นไร จะ fallback เป็น screen pinning
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val host = Uri.parse(url).host
                return if (host != null && host.equals(allowedHost, ignoreCase = true)) {
                    false
                } else {
                    Toast.makeText(this@MainActivity, "ไม่อนุญาตให้เปิดลิงก์นี้", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }
        }

        webView.loadUrl(targetUrl)
    }

    override fun onResume() {
        super.onResume()
        startKioskLock()
    }

    /**
     * เข้า Lock Task Mode
     * - ถ้าเป็น Device Owner + whitelist ตัวเองแล้ว: เข้าโหมดล็อคทันที ไม่มี popup
     * - ถ้ายังไม่ใช่ Device Owner: ระบบจะถือเป็น Screen Pinning ปกติ (มี popup "App is pinned")
     */
    private fun startKioskLock() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startLockTask()
            }
        } catch (e: Exception) {
            // บาง OEM ปิดใช้งานฟีเจอร์นี้ไว้
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        }
        // ไม่เรียก super เพื่อไม่ให้ปิดแอป
    }

    private fun onSecretTap() {
        val now = System.currentTimeMillis()
        if (now - firstTapTime > TAP_WINDOW_MS) {
            firstTapTime = now
            tapCount = 1
        } else {
            tapCount++
        }

        if (tapCount >= TAPS_NEEDED) {
            tapCount = 0
            showAdminMenu()
        }
    }

    private fun showAdminMenu() {
        promptPassword(title = "ใส่รหัสผ่านเพื่อเข้าเมนูผู้ดูแล") { correct ->
            if (correct) {
                AlertDialog.Builder(this)
                    .setTitle("เมนูผู้ดูแล")
                    .setItems(arrayOf("ออกจากแอป", "เปลี่ยนรหัสผ่าน", "ยกเลิก")) { _, which ->
                        when (which) {
                            0 -> exitApp()
                            1 -> showChangePasswordDialog()
                            else -> { /* ยกเลิก */ }
                        }
                    }
                    .show()
            } else {
                Toast.makeText(this, "รหัสผ่านไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChangePasswordDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "รหัสผ่านใหม่"

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, padding, padding, padding)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("ตั้งรหัสผ่านใหม่")
            .setView(container)
            .setPositiveButton("บันทึก") { _, _ ->
                val newPass = input.text.toString()
                if (newPass.isNotBlank()) {
                    prefs.edit().putString(KEY_PASSWORD, newPass).apply()
                    Toast.makeText(this, "เปลี่ยนรหัสผ่านเรียบร้อย", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "รหัสผ่านห้ามว่าง", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun exitApp() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            // ไม่ได้อยู่ใน lock task ก็ไม่เป็นไร
        }
        finishAndRemoveTask()
    }

    private fun promptPassword(title: String, onResult: (Boolean) -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, padding, padding, padding)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("ตกลง") { _, _ ->
                val entered = input.text.toString()
                val stored = prefs.getString(KEY_PASSWORD, getString(R.string.default_password))
                onResult(entered == stored)
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }
}
