package com.liumanhanyu.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import java.lang.ref.WeakReference
import java.util.LinkedHashMap

class HanyuFloatingService : Service() {

    companion object {
        var isActive = false; private set
        var isServiceRunning = false; private set
        var currentSourceLang = "en"; private set
        private var instanceRef: WeakReference<HanyuFloatingService>? = null

        fun updateTranslations(nodes: List<AccessibilityNodeInfo>, texts: List<Pair<Rect, String>>) {
            val svc = instanceRef?.get() ?: return
            svc.handler.post { svc.applyOverlays(nodes, texts) }
        }
        fun showDiag(msg: String) { instanceRef?.get()?.showDiagBanner(msg) }
        fun showToggle() { instanceRef?.get()?.ensureToggleVisible() }
    }

    private val PREFS = "hanyu_floating"
    private val KEY_WAS_ACTIVE = "was_active"

    private lateinit var windowManager: WindowManager
    private val activeOverlays = LinkedHashMap<String, View>()
    private lateinit var toggleButton: View
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    private var diagBanner: View? = null
    private var activateRetry = 0
    private var lastDiagMsg = ""
    private var lastOverlayCount = 0
    private val CHANNEL_ID = "hanyu_overlay"
    private val inflightApi = HashSet<String>()

    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        ApiTranslator.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1001, n)
        if (!isServiceRunning) {
            isServiceRunning = true
            createToggleButton()
            // 恢复上次激活状态
            val wasActive = getSharedPreferences(PREFS, 0).getBoolean(KEY_WAS_ACTIVE, false)
            if (wasActive) {
                handler.postDelayed({ activateOverlay() }, 500)
            }
        }
        return START_STICKY
    }

    private fun saveActiveState(active: Boolean) {
        getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_WAS_ACTIVE, active).apply()
    }

    private fun createToggleButton() {
        if (::toggleButton.isInitialized) try { windowManager.removeView(toggleButton) } catch (_: Exception) {}
        val btn = TextView(this).apply {
            text = "外"; textSize = 20f; setTextColor(0xB3FFFFFF.toInt()); gravity = Gravity.CENTER
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0x406DB3FF.toInt()) })
        }
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT; width = dp(44); height = dp(44)
            gravity = Gravity.TOP or Gravity.END; x = dp(16); y = dp(100)
        }
        btn.setOnTouchListener(ToggleTouchListener(params))
        btn.setOnClickListener { if (isActive) deactivateOverlay() else activateOverlay() }
        windowManager.addView(btn, params); toggleButton = btn
    }

    private inner class ToggleTouchListener(private val p: WindowManager.LayoutParams) : View.OnTouchListener {
        private var ix = 0; private var iy = 0; private var itx = 0f; private var ity = 0f; private var d = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = p.x; iy = p.y; itx = e.rawX; ity = e.rawY; d = false; return true }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - itx) > 10 || Math.abs(e.rawY - ity) > 10) d = true
                    if (d) { p.x = ix - (e.rawX - itx).toInt(); p.y = iy + (e.rawY - ity).toInt()
                        try { windowManager.updateViewLayout(v, p) } catch (_: Exception) {} }; return true }
                MotionEvent.ACTION_UP -> { if (!d) v.performClick(); return true }
            }; return false
        }
    }

    private fun startPeriodicScan() {
        stopPeriodicScan()
        scanRunnable = object : Runnable { override fun run() {
            if (isActive) { HanyuAccessibilityService.instance?.scanAndTranslate(); handler.postDelayed(this, 1500) } } }
        handler.postDelayed(scanRunnable!!, 1500)
    }
    private fun stopPeriodicScan() { scanRunnable?.let { handler.removeCallbacks(it) }; scanRunnable = null }

    private fun ensureToggleVisible() {
        if (::toggleButton.isInitialized) toggleButton.visibility = View.VISIBLE
    }

    // ===== 诊断横幅 =====
    fun showDiagBanner(msg: String) {
        handler.post {
            try { diagBanner?.let { windowManager.removeView(it) } } catch (_: Exception) {}
            val b = TextView(this).apply {
                text = msg; setTextColor(Color.BLACK)
                setBackgroundColor(0xDDFFFF00.toInt()); textSize = 10f
                setPadding(dp(8), dp(4), dp(8), dp(4)); maxLines = 5
            }
            windowManager.addView(b, WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT; width = WindowManager.LayoutParams.WRAP_CONTENT; height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(80) })
            diagBanner = b
            handler.postDelayed({ try { diagBanner?.let { windowManager.removeView(it) } } catch (_: Exception) {}; diagBanner = null }, 4000)
        }
    }

    // ===== 激活/停用 =====
    private fun isAccessibilityEnabledInSystem(): Boolean {
        val serviceName = "$packageName/$packageName.HanyuAccessibilityService"
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains(serviceName) || enabled.contains("$packageName/.HanyuAccessibilityService")
    }

    private fun activateOverlay() {
        try {
            val accSvc = HanyuAccessibilityService.instance
            if (accSvc == null) {
                // 检查系统设置中是否已开启
                if (isAccessibilityEnabledInSystem()) {
                    // 开启了但没连上 → 服务被系统杀了，需要重新开关
                    showDiagBanner("无障碍已在设置中开启但未连接\n请到系统设置中：关闭再重开「流氓汉语」无障碍")
                    isActive = true; updateToggle(true); saveActiveState(true)
                    activateRetry = 0
                    startPeriodicScan()
                    return
                }
                activateRetry++
                showDiagBanner("无障碍未开启，正在重试($activateRetry/10)...")
                isActive = true; updateToggle(true); saveActiveState(true)
                if (activateRetry < 10) handler.postDelayed({ tryReactivate() }, 1500)
                else { activateRetry = 0; showDiagBanner("10次重试失败，请到系统设置开启无障碍") }
                startPeriodicScan(); return
            }
            activateRetry = 0
            showDiagBanner("无障碍已连接 开始翻译...")
            currentSourceLang = accSvc.let { svc ->
                val root = svc.rootInActiveWindow
                if (root != null) { val sb = StringBuilder(); collectText(root, sb); root.recycle(); TranslationEngine.detectAppLanguage(sb.toString()) } else "en" }
            isActive = true; updateToggle(true); saveActiveState(true)
            accSvc.scanAndTranslate()
            handler.postDelayed({ accSvc.scanAndTranslate() }, 300)
            handler.postDelayed({ accSvc.scanAndTranslate() }, 800)
            startPeriodicScan()
        } catch (e: Exception) {
            android.util.Log.e("HanyuSvc", "activateOverlay", e)
            isActive = true; updateToggle(true); saveActiveState(true)
            showDiagBanner("激活出错: ${e.message?.take(50)}")
        }
    }

    private fun tryReactivate() {
        val accSvc = HanyuAccessibilityService.instance
        if (accSvc != null) {
            activateRetry = 0; showDiagBanner("无障碍重连成功")
            currentSourceLang = accSvc.let { svc ->
                val root = svc.rootInActiveWindow
                if (root != null) { val sb = StringBuilder(); collectText(root, sb); root.recycle(); TranslationEngine.detectAppLanguage(sb.toString()) } else "en" }
            accSvc.scanAndTranslate(); startPeriodicScan()
        } else if (isActive && activateRetry < 10) { activateRetry++; handler.postDelayed({ tryReactivate() }, 1500) }
    }

    private fun deactivateOverlay() {
        isActive = false; updateToggle(false); clearAllOverlays(); stopPeriodicScan(); saveActiveState(false)
        try { diagBanner?.let { windowManager.removeView(it) } } catch (_: Exception) {}; diagBanner = null
    }

    private fun updateToggle(active: Boolean) {
        val btn = toggleButton as? TextView ?: return
        btn.text = if (active) "中" else "外"
        btn.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (active) 0xCCFF5C28.toInt() else 0x406DB3FF.toInt()) })
        btn.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xB3FFFFFF.toInt())
    }

    // ===== 覆盖层 =====
    private val GRID = 10

    private fun snapKey(rect: Rect): String {
        val left = (rect.left / GRID) * GRID
        val top = (rect.top / GRID) * GRID
        val w = ((rect.width() + GRID - 1) / GRID) * GRID
        return "$left,$top,$w"
    }

    private fun shouldUpdatePos(key: String, rect: Rect): Boolean {
        val v = activeOverlays[key] ?: return true
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return true
        return Math.abs(lp.x - rect.left) > 8 || Math.abs(lp.y - (rect.bottom + dp(2))) > 8
    }

    // 根据原文高度推算字号
    private fun estFontSize(rect: Rect): Float {
        // 文本高度约等于字号，取 0.7 倍防止过大
        return (rect.height() * 0.7f).coerceIn(9f, 20f) / resources.displayMetrics.scaledDensity
    }

    private fun applyOverlays(nodes: List<AccessibilityNodeInfo>, texts: List<Pair<Rect, String>>) {
        if (!isActive || texts.isEmpty()) return
        val newKeys = HashSet<String>(); val apiQueue = mutableListOf<Triple<Rect, String, String>>()
        val nodeList = nodes.toList(); var localHit = 0; var apiQ = 0

        for (i in texts.indices) {
            val (rect, originalText) = texts[i]
            val len = originalText.length
            val key = snapKey(rect)

            // 先尝试本地词库
            val translated = TranslationEngine.translateToChinese(originalText)
            if (translated != null) {
                localHit++
                if (activeOverlays.containsKey(key)) {
                    (activeOverlays[key] as? TextView)?.let { if (it.text != translated) it.text = translated }
                    if (shouldUpdatePos(key, rect)) updateOverlayPosition(key, rect)
                } else addOverlayView(rect, translated, key)
                newKeys.add(key)
            } else if (originalText.any { it.isLetter() } && originalText !in inflightApi) {
                inflightApi.add(originalText)
                if (len > 300) {
                    // 长文本分段翻译
                    val chunks = originalText.chunked(300)
                    for ((ci, chunk) in chunks.withIndex()) {
                        val cRect = if (ci == 0) rect else Rect(rect.left, rect.top + ci * 30, rect.right, rect.bottom + ci * 30)
                        val cKey = snapKey(cRect)
                        apiQueue.add(Triple(cRect, chunk, cKey))
                    }
                    apiQ += chunks.size
                } else {
                    apiQueue.add(Triple(rect, originalText, key)); apiQ++
                }
            }
        }
        nodeList.forEach { it.recycle() }

        // 移除不再出现的覆盖层
        activeOverlays.keys.filter { it !in newKeys }.forEach {
            try { windowManager.removeView(activeOverlays[it]) } catch (_: Exception) {}
            activeOverlays.remove(it)
        }

        // API 翻译
        for ((rect, text, key) in apiQueue.take(15)) {
            ApiTranslator.translate(text, currentSourceLang, "zh", object : ApiTranslator.Callback {
                override fun onResult(t: String?) {
                    inflightApi.remove(text)
                    if (t != null && isActive) handler.post {
                        if (isActive && !activeOverlays.containsKey(key)) addOverlayView(rect, t, key)
                    }
                }
            })
        }

        // 静默日志
        if (activeOverlays.size != lastOverlayCount) {
            lastOverlayCount = activeOverlays.size
            val msg = "翻译${localHit}+${apiQ}处 | 覆盖${activeOverlays.size}"
            if (msg != lastDiagMsg) { lastDiagMsg = msg; showDiagBanner(msg) }
        }
        android.util.Log.i("Hanyu", "文本${texts.size} 词库${localHit} API${apiQ} 覆盖${activeOverlays.size}")
    }

    private fun addOverlayView(rect: Rect, text: String, key: String) {
        val fontSize = estFontSize(rect)
        val ov = TextView(this).apply {
            this.text = text
            setTextColor(Color.BLACK)
            textSize = fontSize
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(0xF0FFFFFF.toInt())
            setPadding(dp(3), dp(1), dp(3), dp(1))
            maxLines = 8
        }
        // 放在原文下方
        val targetY = rect.bottom + dp(2)
        val p = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = (rect.width() + dp(4)).coerceAtLeast(dp(40))
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = rect.left; y = targetY
        }
        windowManager.addView(ov, p)
        activeOverlays[key] = ov
    }

    private fun updateOverlayPosition(key: String, nr: Rect) {
        val v = activeOverlays[key] ?: return
        try {
            windowManager.updateViewLayout(v, WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                width = (nr.width() + dp(4)).coerceAtLeast(dp(40))
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START
                x = nr.left; y = nr.bottom + dp(2)
            })
        } catch (_: Exception) {}
    }

    private fun clearAllOverlays() {
        activeOverlays.values.forEach { try { windowManager.removeView(it) } catch (_: Exception) {} }
        activeOverlays.clear(); inflightApi.clear(); lastOverlayCount = 0; lastDiagMsg = ""
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectText(it, sb); it.recycle() }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "汉化服务", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "流氓汉语后台运行中"; setShowBadge(false) })
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("流氓汉语")
            .setContentText("汉化服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPeriodicScan(); clearAllOverlays()
        try { diagBanner?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { windowManager.removeView(toggleButton) } catch (_: Exception) {}
        isActive = false; isServiceRunning = false; instanceRef = null
        super.onDestroy()
    }
}
