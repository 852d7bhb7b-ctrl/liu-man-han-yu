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
        fun showToggle() { instanceRef?.get()?.showToggleButton() }
    }

    private lateinit var windowManager: WindowManager
    private val activeOverlays = LinkedHashMap<String, View>()
    private lateinit var toggleButton: View
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null
    private var hideToggleRunnable: Runnable? = null
    private var diagBanner: View? = null
    private var activateRetry = 0
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
        if (!isServiceRunning) { isServiceRunning = true; createToggleButton() }
        return START_STICKY
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
            if (isActive) { HanyuAccessibilityService.instance?.scanAndTranslate(); handler.postDelayed(this, 800) } } }
        handler.postDelayed(scanRunnable!!, 800)
    }
    private fun stopPeriodicScan() { scanRunnable?.let { handler.removeCallbacks(it) }; scanRunnable = null }

    private fun showToggleButton() {
        if (::toggleButton.isInitialized) { toggleButton.visibility = View.VISIBLE; resetHideTimer() }
    }
    private fun resetHideTimer() {
        hideToggleRunnable?.let { handler.removeCallbacks(it) }
        hideToggleRunnable = Runnable {
            if (!isActive && ::toggleButton.isInitialized) toggleButton.visibility = View.GONE }
        handler.postDelayed(hideToggleRunnable!!, 30_000L)
    }

    // ===== 诊断横幅 =====
    fun showDiagBanner(msg: String) {
        handler.post {
            try { diagBanner?.let { windowManager.removeView(it) } } catch (_: Exception) {}
            val b = TextView(this).apply { text = msg; setTextColor(Color.BLACK); setBackgroundColor(0xDDFFFF00.toInt()); textSize = 10f; setPadding(dp(8), dp(4), dp(8), dp(4)); maxLines = 5 }
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
    private fun activateOverlay() {
        try {
            val accSvc = HanyuAccessibilityService.instance
            val sp = getSharedPreferences(HanyuAccessibilityService.PREFS_NAME, 0)
            val connCount = sp.getInt(HanyuAccessibilityService.KEY_CONNECT_COUNT, 0)
            val lastConn = sp.getLong(HanyuAccessibilityService.KEY_CONNECTED_AT, 0)
            val lastKill = sp.getLong(HanyuAccessibilityService.KEY_DESTROYED_AT, 0)
            val sysReg = try { android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "空" } catch (_: Exception) { "读取失败" }

            if (accSvc == null) {
                activateRetry++
                val killInfo = if (lastKill > 0 && lastKill > lastConn) " | 最后被杀:${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(lastKill))}" else ""
                showDiagBanner("无障碍未连接 历史:${connCount}次${killInfo}\n系统注册:${sysReg.take(60)}")
                isActive = true; updateToggle(true); resetHideTimer()
                if (activateRetry < 10) handler.postDelayed({ tryReactivate() }, 1000)
                else { activateRetry = 0; showDiagBanner("10次重试失败，请关闭无障碍开关后重新打开") }
                startPeriodicScan(); return
            }
            activateRetry = 0
            showDiagBanner("无障碍已连接✅ 开始翻译...")
            currentSourceLang = accSvc.let { svc ->
                val root = svc.rootInActiveWindow
                if (root != null) { val sb = StringBuilder(); collectText(root, sb); root.recycle(); TranslationEngine.detectAppLanguage(sb.toString()) } else "en" }
            isActive = true; updateToggle(true); resetHideTimer()
            accSvc.scanAndTranslate()
            handler.postDelayed({ accSvc.scanAndTranslate() }, 300)
            handler.postDelayed({ accSvc.scanAndTranslate() }, 800)
            startPeriodicScan()
        } catch (e: Exception) {
            android.util.Log.e("HanyuSvc", "activateOverlay崩溃", e)
            isActive = true; updateToggle(true)
            showDiagBanner("激活出错: ${e.message?.take(50)}")
        }
    }

    private fun tryReactivate() {
        val accSvc = HanyuAccessibilityService.instance
        if (accSvc != null) {
            activateRetry = 0; showDiagBanner("无障碍重连成功✅")
            currentSourceLang = accSvc.let { svc ->
                val root = svc.rootInActiveWindow
                if (root != null) { val sb = StringBuilder(); collectText(root, sb); root.recycle(); TranslationEngine.detectAppLanguage(sb.toString()) } else "en" }
            accSvc.scanAndTranslate(); startPeriodicScan()
        } else if (isActive && activateRetry < 10) { activateRetry++; handler.postDelayed({ tryReactivate() }, 1000) }
    }

    private fun deactivateOverlay() {
        isActive = false; updateToggle(false); clearAllOverlays(); stopPeriodicScan()
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
    private fun applyOverlays(nodes: List<AccessibilityNodeInfo>, texts: List<Pair<Rect, String>>) {
        if (!isActive || texts.isEmpty()) return
        val newKeys = HashSet<String>(); val apiQueue = mutableListOf<Triple<Rect, String, String>>()
        val nodeList = nodes.toList(); var localHit = 0; var apiQ = 0

        for (i in texts.indices) {
            val (rect, originalText) = texts[i]
            if (originalText.length > 500) continue
            val translated = TranslationEngine.translateToChinese(originalText, currentSourceLang)
            val key = "${rect.left},${rect.top},${rect.width()},${rect.height()}"
            if (translated != null) { localHit++
                if (activeOverlays.containsKey(key)) { updateOverlayPosition(key, rect); (activeOverlays[key] as? TextView)?.let { if (it.text != translated) it.text = translated } }
                else addOverlayView(rect, translated, key)
                newKeys.add(key) }
            else if (originalText.any { it.isLetter() } && originalText !in inflightApi) { inflightApi.add(originalText); apiQueue.add(Triple(rect, originalText, key)); apiQ++ }
        }
        nodeList.forEach { it.recycle() }
        activeOverlays.keys.filter { it !in newKeys }.forEach { try { windowManager.removeView(activeOverlays[it]) } catch (_: Exception) {}; activeOverlays.remove(it) }
        for ((rect, text, key) in apiQueue.take(10)) ApiTranslator.translate(text, currentSourceLang, "zh", object : ApiTranslator.Callback {
            override fun onResult(t: String?) { inflightApi.remove(text); if (t != null && isActive) handler.post { if (isActive && !activeOverlays.containsKey(key)) addOverlayView(rect, t, key) } } })
        if (localHit > 0) showDiagBanner("翻译${localHit}处 | 覆盖层${activeOverlays.size} | API排队${apiQ}")
        android.util.Log.i("Hanyu", "扫描${texts.size}处 词库${localHit} API${apiQ} 总覆盖${activeOverlays.size}")
    }

    private fun addOverlayView(rect: Rect, text: String, key: String) {
        val ov = TextView(this).apply { this.text = text; setTextColor(Color.BLACK); textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setBackgroundColor(Color.WHITE); setPadding(dp(4), dp(2), dp(4), dp(2)); maxLines = 5 }
        val p = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT; width = (rect.width() + dp(4)).coerceAtLeast(dp(40)); height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START; x = rect.left - dp(2); y = rect.top - dp(2) }
        windowManager.addView(ov, p); activeOverlays[key] = ov
    }

    private fun updateOverlayPosition(key: String, nr: Rect) {
        val v = activeOverlays[key] ?: return
        try { windowManager.updateViewLayout(v, WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT; width = (nr.width() + dp(4)).coerceAtLeast(dp(40)); height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START; x = nr.left - dp(2); y = nr.top - dp(2) }) } catch (_: Exception) {}
    }

    private fun clearAllOverlays() { activeOverlays.values.forEach { try { windowManager.removeView(it) } catch (_: Exception) {} }; activeOverlays.clear(); inflightApi.clear() }
    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) { node.text?.let { sb.append(it).append(" ") }; node.contentDescription?.let { sb.append(it).append(" ") }; for (i in 0 until node.childCount) node.getChild(i)?.let { collectText(it, sb); it.recycle() } }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "汉化服务", NotificationManager.IMPORTANCE_LOW).apply { description = "流氓汉语后台运行中"; setShowBadge(false) })
    }
    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("流氓汉语").setContentText("汉化服务运行中").setSmallIcon(android.R.drawable.ic_menu_edit).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
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
