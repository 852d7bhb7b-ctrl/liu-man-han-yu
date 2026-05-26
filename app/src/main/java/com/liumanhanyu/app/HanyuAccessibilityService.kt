package com.liumanhanyu.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HanyuAccessibilityService : AccessibilityService() {

    companion object {
        var instance: HanyuAccessibilityService? = null
            private set
        var isEnabled = false
            private set
        var currentPackage: String = ""
            private set
        // SharedPreferences 记录（不可变，不依赖内存）
        const val PREFS_NAME = "hanyu_a11y_status"
        const val KEY_CONNECTED_AT = "last_connected_at"
        const val KEY_DESTROYED_AT = "last_destroyed_at"
        const val KEY_CONNECT_COUNT = "connect_count"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var inputDebounce: Runnable? = null
    private var selfSetText: String? = null
    private var lastOcrTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isEnabled = true
        // 写入 SharedPreferences 留下不可抹除的证据
        val sp = getSharedPreferences(PREFS_NAME, 0)
        val count = sp.getInt(KEY_CONNECT_COUNT, 0) + 1
        sp.edit()
            .putLong(KEY_CONNECTED_AT, System.currentTimeMillis())
            .putInt(KEY_CONNECT_COUNT, count)
            .apply()
        android.util.Log.e("HanyuA11y", "onServiceConnected #$count")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.toString()?.let { currentPackage = it }
                if (HanyuFloatingService.isActive) scanAndTranslate()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                if (HanyuFloatingService.isActive) scanAndTranslate()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleInputChange(event)
        }
    }

    private var scanTrace = 0

    fun scanAndTranslate() {
        if (!isEnabled) {
            if (scanTrace < 2) { scanTrace++; HanyuFloatingService.showDiag("步骤2: isEnabled=false❌") }
            return
        }
        tryOcr()

        val root = rootInActiveWindow
        if (root != null) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            val textNodes = mutableListOf<Pair<Rect, String>>()
            collectTextNodes(root, nodes, textNodes)
            root.recycle()

            if (scanTrace < 3) {
                scanTrace++
                if (textNodes.isNotEmpty())
                    HanyuFloatingService.showDiag("步骤3: root✅ 扫到${textNodes.size}处外文 → 翻译中")
                else {
                    // 深度诊断
                    val root2 = rootInActiveWindow
                    if (root2 != null) {
                        var total = 0; var withText = 0
                        fun deep(n: AccessibilityNodeInfo) { total++; if (!n.text.isNullOrEmpty()) withText++; for (i in 0 until n.childCount) n.getChild(i)?.let { deep(it) } }
                        deep(root2); root2.recycle()
                        HanyuFloatingService.showDiag("步骤3: root✅ ${total}节点 ${withText}有文字 0外文→检查过滤条件")
                    } else HanyuFloatingService.showDiag("步骤3: root✅ 0外文(二次检查root=null)")
                }
            }
            if (textNodes.isNotEmpty()) HanyuFloatingService.updateTranslations(nodes, textNodes)
            return
        }

        // root 为 null，尝试 windows
        val wins = windows
        if (wins.isNotEmpty()) {
            for (w in wins) {
                val wRoot = w.root ?: continue
                val nodes = mutableListOf<AccessibilityNodeInfo>()
                val textNodes = mutableListOf<Pair<Rect, String>>()
                collectTextNodes(wRoot, nodes, textNodes)
                wRoot.recycle()
                if (textNodes.isNotEmpty()) {
                    if (scanTrace < 2) { scanTrace++; HanyuFloatingService.showDiag("步骤3: windows✅ 扫到${textNodes.size}处外文") }
                    HanyuFloatingService.updateTranslations(nodes, textNodes)
                    return
                }
            }
            if (scanTrace < 2) { scanTrace++; HanyuFloatingService.showDiag("步骤3: ${wins.size}个窗口均无外文") }
        } else {
            if (scanTrace < 2) { scanTrace++; HanyuFloatingService.showDiag("步骤3: root=null windows=空❌") }
        }
    }

    private fun tryOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val now = System.currentTimeMillis()
        if (now - lastOcrTime < 5000) return
        lastOcrTime = now
        val uiExecutor = java.util.concurrent.Executor { command -> handler.post(command) }
        val displayId = windows.firstOrNull()?.displayId ?: Display.DEFAULT_DISPLAY
        takeScreenshot(displayId, uiExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                try {
                    val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer, screenshot.colorSpace)
                    if (bitmap != null) {
                        OcrEngine.extractForeignText(bitmap) { ocrResults ->
                            if (ocrResults.isNotEmpty()) {
                                HanyuFloatingService.updateTranslations(emptyList(), ocrResults)
                            }
                        }
                    }
                } finally { screenshot.hardwareBuffer?.close() }
            }
            override fun onFailure(errorCode: Int) {}
        })
    }

    private fun collectTextNodes(
        node: AccessibilityNodeInfo,
        allNodes: MutableList<AccessibilityNodeInfo>,
        result: MutableList<Pair<Rect, String>>
    ) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 采集所有文本属性，不跳过空 bounds 的节点
        fun tryAdd(text: CharSequence?) {
            val s = text?.toString()?.trim() ?: return
            if (s.isEmpty() || s.length > 500) return
            if (!s.any { it.isLetter() }) return
            // 空 bounds 用默认位置（屏幕左上角偏移），后续 applyOverlays 会处理
            val useRect = if (rect.isEmpty) Rect(100, 100, 300, 130) else rect
            result.add(useRect to s)
            allNodes.add(AccessibilityNodeInfo.obtain(node))
        }
        tryAdd(node.text)
        tryAdd(node.contentDescription)
        tryAdd(node.hintText)
        tryAdd(node.error)
        if (Build.VERSION.SDK_INT >= 30) tryAdd(node.stateDescription)
        if (Build.VERSION.SDK_INT >= 28) tryAdd(node.tooltipText)
        if (Build.VERSION.SDK_INT >= 28) tryAdd(node.paneTitle)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTextNodes(child, allNodes, result)
                child.recycle()
            }
        }
    }

    /** 尝试直接替换节点文字（真正隐形，无覆盖层） */
    fun tryDirectReplace(node: AccessibilityNodeInfo, translated: String): Boolean {
        return try {
            val currentText = node.text?.toString() ?: ""
            if (currentText == translated) return true
            selfSetText = translated
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        translated
                    )
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    // ===== 输入反向翻译 =====

    private fun handleInputChange(event: AccessibilityEvent) {
        if (!HanyuFloatingService.isActive) return
        val source = event.source ?: return
        if (source.className?.toString()?.contains("EditText") != true) {
            source.recycle(); return
        }
        val text = source.text?.toString() ?: ""
        source.recycle()

        if (text == selfSetText) {
            selfSetText = null
            return
        }

        if (!TranslationEngine.containsChinese(text)) return

        val lang = HanyuFloatingService.currentSourceLang

        val localResult = TranslationEngine.translateToForeign(text, lang)
        if (localResult != null) {
            replaceInputText(text, localResult)
            return
        }

        inputDebounce?.let { handler.removeCallbacks(it) }
        inputDebounce = Runnable {
            doApiInputTranslation(text, lang)
        }
        handler.postDelayed(inputDebounce!!, 600)
    }

    private fun doApiInputTranslation(originalText: String, targetLang: String) {
        val svc = instance ?: return
        val root = svc.rootInActiveWindow ?: return
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        root.recycle()

        if (focusedNode == null) return
        val currentText = focusedNode.text?.toString() ?: ""
        if (currentText != originalText) {
            focusedNode.recycle()
            return
        }

        ApiTranslator.translate(originalText, "zh", targetLang, object : ApiTranslator.Callback {
            override fun onResult(translated: String?) {
                if (translated != null && translated != originalText) {
                    replaceInputText(originalText, translated)
                }
            }
        })
        focusedNode.recycle()
    }

    private fun replaceInputText(originalText: String, newText: String) {
        val svc = instance ?: return
        val root = svc.rootInActiveWindow ?: return
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        root.recycle()

        if (focusedNode == null) return
        val currentText = focusedNode.text?.toString() ?: ""
        if (currentText != originalText) {
            focusedNode.recycle()
            return
        }

        selfSetText = newText
        focusedNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText
                )
            }
        )
        focusedNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newText.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newText.length)
            }
        )
        focusedNode.recycle()
    }

    override fun onInterrupt() { isEnabled = false }
    override fun onDestroy() {
        super.onDestroy()
        getSharedPreferences(PREFS_NAME, 0).edit()
            .putLong(KEY_DESTROYED_AT, System.currentTimeMillis())
            .apply()
        android.util.Log.e("HanyuA11y", "onDestroy called")
        inputDebounce?.let { handler.removeCallbacks(it) }
        instance = null; isEnabled = false
    }
}
