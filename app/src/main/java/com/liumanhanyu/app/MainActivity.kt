package com.liumanhanyu.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
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
    private lateinit var btnUpdate: Button
    private lateinit var statusText: TextView
    private lateinit var etGeminiKey: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnToggle = findViewById(R.id.btnToggle)
        btnUpdate = findViewById(R.id.btnUpdate)
        statusText = findViewById(R.id.statusText)
        etGeminiKey = findViewById(R.id.etGeminiKey)

        // 初始化 ApiTranslator（全局上下文）
        ApiTranslator.init(applicationContext)

        // 加载已保存的 Gemini Key
        val savedKey = getSharedPreferences("hanyu_settings", 0).getString("gemini_key", "") ?: ""
        etGeminiKey.setText(savedKey)

        // 输入后自动保存
        etGeminiKey.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                getSharedPreferences("hanyu_settings", 0).edit()
                    .putString("gemini_key", s?.toString()?.trim() ?: "")
                    .apply()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

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

        btnUpdate.setOnClickListener {
            UpdateManager.checkUpdate(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        HanyuFloatingService.showToggle()
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
        val serviceName = "$packageName/$packageName.HanyuAccessibilityService"
        // 也兼容 ".ClassName" 格式
        val serviceNameShort = "$packageName/.HanyuAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName) || enabledServices.contains(serviceNameShort)
    }
}
