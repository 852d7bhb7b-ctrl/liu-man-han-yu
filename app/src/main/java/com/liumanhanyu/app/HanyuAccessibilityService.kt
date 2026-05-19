package com.liumanhanyu.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    }

    private val handler = Handler(Looper.getMainLooper())
    private var inputDebounce: Runnable? = null
    private var selfSetText: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isEnabled = true
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
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
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (HanyuFloatingService.isActive) scanAndTranslate()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleInputChange(event)
        }
    }

    fun scanAndTranslate() {
        if (!isEnabled) return
        val root = rootInActiveWindow ?: return
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val textNodes = mutableListOf<Pair<Rect, String>>()
        collectTextNodes(root, nodes, textNodes)
        HanyuFloatingService.updateTranslations(nodes, textNodes)
        root.recycle()
    }

    private fun collectTextNodes(
        node: AccessibilityNodeInfo,
        allNodes: MutableList<AccessibilityNodeInfo>,
        result: MutableList<Pair<Rect, String>>
    ) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.any { it.isLetter() }) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                result.add(rect to text)
                allNodes.add(AccessibilityNodeInfo.obtain(node))
            }
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc.any { it.isLetter() }) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                result.add(rect to desc)
                allNodes.add(AccessibilityNodeInfo.obtain(node))
            }
        }
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
        inputDebounce?.let { handler.removeCallbacks(it) }
        instance = null; isEnabled = false
    }
}
