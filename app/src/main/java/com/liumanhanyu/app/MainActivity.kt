package com.liumanhanyu.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 主界面 — 极简，只有必要操作：
 * 1. 开启无障碍服务
 * 2. 授予悬浮窗权限
 * 3. 启动/停止汉化
 */
class MainActivity : AppCompatActivity() {

    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnToggle: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnToggle = findViewById(R.id.btnToggle)
        statusText = findViewById(R.id.statusText)

        btnAccessibility.setOnClickListener {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).also {
                startActivity(it)
            }
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).also { startActivity(it) }
            } else {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggle.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (HanyuFloatingService.isActive) {
                // 停止
                stopService(Intent(this, HanyuFloatingService::class.java))
                updateUI()
            } else {
                // 启动
                val intent = Intent(this, HanyuFloatingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                updateUI()
                // 启动完就最小化，让用户切到目标 APP
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val accEnabled = isAccessibilityEnabled()
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
        val isActive = HanyuFloatingService.isActive

        btnAccessibility.text = if (accEnabled) "无障碍：✅ 已开启" else "无障碍：❌ 点击开启"
        btnOverlay.text = if (overlayGranted) "悬浮窗：✅ 已授权" else "悬浮窗：❌ 点击授权"
        btnToggle.text = if (isActive) "关闭汉化" else "启动汉化"

        statusText.text = when {
            !accEnabled -> "请先开启无障碍服务"
            !overlayGranted -> "请先授予悬浮窗权限"
            isActive -> "汉化运行中，切到目标 APP 即可"
            else -> "已就绪，点击「启动汉化」开始"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val serviceName = "$packageName/.HanyuAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName)
    }
}
