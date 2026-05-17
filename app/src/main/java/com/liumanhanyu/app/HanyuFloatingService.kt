package com.liumanhanyu.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import java.lang.ref.WeakReference
import java.util.LinkedHashMap

class HanyuFloatingService : Service() {

    companion object {
        var isActive = false
            private set
        var currentSourceLang = "en"
            private set
        private var instanceRef: WeakReference<HanyuFloatingService>? = null
        fun updateTranslations(nodes: List<Pair<Rect, String>>) {
            instanceRef?.get()?.applyOverlays(nodes)
        }
    }

    private lateinit var windowManager: WindowManager
    private val activeOverlays = LinkedHashMap<String, View>()  // key="x,y,w,h"
    private lateinit var toggleButton: View
    private val CHANNEL_ID = "hanyu_overlay"

    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1001, notification)
        createToggleButton()
        deactivateOverlay()
        return START_STICKY
    }

    private fun createToggleButton() {
        val btn = TextView(this).apply {
            text = "外"; textSize = 20f; setTextColor(0xB3FFFFFF.toInt()); gravity = Gravity.CENTER
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0x406DB3FF.toInt())
            })
            setOnClickListener { if (isActive) deactivateOverlay() else activateOverlay() }
        }
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT; width = dp(44); height = dp(44)
            gravity = Gravity.BOTTOM or Gravity.END; x = dp(16); y = dp(80)
        }
        windowManager.addView(btn, params); toggleButton = btn
    }

    private fun activateOverlay() {
        currentSourceLang = HanyuAccessibilityService.instance?.let { svc ->
            val root = svc.rootInActiveWindow ?: return@let "en"
            val sb = StringBuilder(); collectText(root, sb); root.recycle()
            TranslationEngine.detectAppLanguage(sb.toString())
        } ?: "en"
        isActive = true; updateToggle(true)
        HanyuAccessibilityService.instance?.scanAndTranslate()
    }

    private fun deactivateOverlay() {
        isActive = false; updateToggle(false); clearAllOverlays()
    }

    private fun updateToggle(active: Boolean) {
        val btn = toggleButton as? TextView ?: return
        btn.text = if (active) "中" else "外"
        btn.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(if (active) 0x66FF5C28.toInt() else 0x406DB3FF.toInt())
        })
        btn.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xB3FFFFFF.toInt())
    }

    // ===== 增量覆盖层更新（不再全清重画） =====
    private fun applyOverlays(nodes: List<Pair<Rect, String>>) {
        if (!isActive) return
        val newKeys = HashSet<String>()

        for ((rect, originalText) in nodes) {
            val translated = TranslationEngine.translateToChinese(originalText, currentSourceLang)
                ?: continue
            val key = "${rect.left},${rect.top},${rect.width()},${rect.height()}"

            // 检查是否已有同位置覆盖层
            if (activeOverlays.containsKey(key)) {
                // 更新文本
                val v = activeOverlays[key] as? TextView
                if (v != null && v.text != translated) v.text = translated
                newKeys.add(key)
                continue
            }

            // 新建覆盖层
            val overlay = TextView(this).apply {
                text = translated; setTextColor(0xE6000000.toInt()); textSize = 12f
                setBackgroundColor(0x08FF5C28.toInt())
                setPadding(dp(2), dp(1), dp(2), dp(1)); maxLines = 3
            }
            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START; x = rect.left; y = rect.top
            }
            windowManager.addView(overlay, params)
            activeOverlays[key] = overlay
            newKeys.add(key)
        }

        // 清除不再需要的覆盖层
        val toRemove = activeOverlays.keys.filter { it !in newKeys }
        for (key in toRemove) {
            try { windowManager.removeView(activeOverlays[key]) } catch (_: Exception) {}
            activeOverlays.remove(key)
        }
    }

    private fun clearAllOverlays() {
        for ((_, view) in activeOverlays) {
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
        activeOverlays.clear()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb); it.recycle() }
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "汉化服务", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "流氓汉语后台运行中"; setShowBadge(false)
                })
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("流氓汉语").setContentText("汉化服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        clearAllOverlays()
        try { windowManager.removeView(toggleButton) } catch (_: Exception) {}
        isActive = false; instanceRef = null
        super.onDestroy()
    }
}
