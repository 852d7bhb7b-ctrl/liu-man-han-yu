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
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityNodeInfo
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.lang.ref.WeakReference

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
    private val overlayViews = mutableListOf<View>()
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

        // Android 14+ 需要指定前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }

        createToggleButton()
        deactivateOverlay()
        return START_STICKY
    }

    private fun createToggleButton() {
        val btn = TextView(this).apply {
            text = "外"
            textSize = 20f
            setTextColor(0xB3FFFFFF.toInt())
            gravity = Gravity.CENTER
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x406DB3FF.toInt())
            })
            setOnClickListener {
                if (isActive) deactivateOverlay() else activateOverlay()
            }
        }

        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT
            width = dpToPx(44); height = dpToPx(44)
            gravity = Gravity.BOTTOM or Gravity.END
            x = dpToPx(16); y = dpToPx(80)
        }
        windowManager.addView(btn, params)
        toggleButton = btn
    }

    private fun activateOverlay() {
        // 检测当前 APP 语言
        currentSourceLang = HanyuAccessibilityService.instance?.let { svc ->
            val root = svc.rootInActiveWindow ?: return@let "en"
            val sb = StringBuilder()
            collectAllText(root, sb)
            root.recycle()
            TranslationEngine.detectAppLanguage(sb.toString())
        } ?: "en"

        isActive = true
        updateToggleAppearance(true)
        HanyuAccessibilityService.instance?.scanAndTranslate()
    }

    private fun deactivateOverlay() {
        isActive = false
        updateToggleAppearance(false)
        clearOverlays()
    }

    private fun updateToggleAppearance(active: Boolean) {
        val btn = toggleButton as? TextView ?: return
        if (active) {
            btn.text = "中"
            btn.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x66FF5C28.toInt())
            })
            btn.setTextColor(0xFFFFFFFF.toInt())
        } else {
            btn.text = "外"
            btn.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x406DB3FF.toInt())
            })
            btn.setTextColor(0xB3FFFFFF.toInt())
        }
    }

    private fun applyOverlays(nodes: List<Pair<Rect, String>>) {
        if (!isActive) return
        clearOverlays()

        // 需要 API 兜底的文本
        val needsApi = mutableListOf<Pair<Rect, String>>()
        // 需要反向翻译的文本（中文→外语）
        val needsReverse = mutableListOf<Pair<Rect, String>>()

        for ((rect, originalText) in nodes) {
            val translated = TranslationEngine.translateToChinese(originalText, currentSourceLang)
            if (translated == null) {
                if (originalText.length <= 80) needsApi.add(rect to originalText)
                continue
            }

            val overlay = TextView(this).apply {
                text = translated
                setTextColor(0xE6000000.toInt())
                textSize = 12f
                setBackgroundColor(0x08FF5C28.toInt())
                setPadding(dpToPx(2), dpToPx(1), dpToPx(2), dpToPx(1))
                maxLines = 3
            }

            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START
                x = rect.left; y = rect.top
            }
            windowManager.addView(overlay, params)
            overlayViews.add(overlay)
        }

        // API 异步兜底：本地没命中的文本通过联网翻译
        if (needsApi.isNotEmpty()) {
            for ((rect, text) in needsApi) {
                ApiTranslator.translate(text, currentSourceLang, object : ApiTranslator.Callback {
                    override fun onResult(translated: String?) {
                        if (translated != null && isActive) {
                            addOverlayView(rect, translated)
                        }
                    }
                })
            }
        }

        // 反向翻译：中文输入框内容 → 外语
        if (needsReverse.isNotEmpty()) {
            for ((_, text) in needsReverse) {
                ApiTranslator.translate(text, currentSourceLang, object : ApiTranslator.Callback {
                    override fun onResult(translated: String?) {
                        if (translated != null) {
                            // 结果可供输入拦截使用
                        }
                    }
                })
            }
        }
    }

    /** 添加单个覆盖层 */
    private fun addOverlayView(rect: Rect, text: String) {
        val overlay = TextView(this).apply {
            this.text = text
            setTextColor(0xE6000000.toInt())
            textSize = 12f
            setBackgroundColor(0x08FF5C28.toInt())
            setPadding(dpToPx(2), dpToPx(1), dpToPx(2), dpToPx(1))
            maxLines = 3
        }
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = rect.left; y = rect.top
        }
        windowManager.addView(overlay, params)
        overlayViews.add(overlay)
    }

    private fun clearOverlays() {
        overlayViews.forEach { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayViews.clear()
    }

    private fun collectAllText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectAllText(it, sb); it.recycle() }
        }
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "汉化服务", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "流氓汉语后台运行中"; setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("流氓汉语")
            .setContentText("汉化服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        clearOverlays()
        try { windowManager.removeView(toggleButton) } catch (_: Exception) {}
        isActive = false; instanceRef = null
        super.onDestroy()
    }
}
