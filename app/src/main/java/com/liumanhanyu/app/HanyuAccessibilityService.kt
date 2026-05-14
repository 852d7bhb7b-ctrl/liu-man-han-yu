package com.liumanhanyu.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务 — 读取界面文字 + 输入拦截
 */
class HanyuAccessibilityService : AccessibilityService() {

    companion object {
        var instance: HanyuAccessibilityService? = null
            private set
        var isEnabled = false
            private set
        var currentPackage: String = ""
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isEnabled = true
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
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
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (HanyuFloatingService.isActive) scanAndTranslate()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleInputChange(event)
        }
    }

    fun scanAndTranslate() {
        if (!isEnabled) return
        val root = rootInActiveWindow ?: return
        val textNodes = mutableListOf<Pair<Rect, String>>()
        collectTextNodes(root, textNodes)
        HanyuFloatingService.updateTranslations(textNodes)
        root.recycle()
    }

    private fun collectTextNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<Pair<Rect, String>>
    ) {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.any { it.isLetter() }) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) result.add(rect to text)
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc.any { it.isLetter() }) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) result.add(rect to desc)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTextNodes(child, result)
                child.recycle()
            }
        }
    }

    private fun handleInputChange(event: AccessibilityEvent) {
        if (!HanyuFloatingService.isActive) return
        val source = event.source ?: return
        if (source.className?.toString()?.contains("EditText") != true) {
            source.recycle(); return
        }
        val text = source.text?.toString() ?: ""
        if (TranslationEngine.containsChinese(text)) {
            val lang = HanyuFloatingService.currentSourceLang
            TranslationEngine.translateToForeign(text, lang)?.let { foreign ->
                source.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            foreign
                        )
                    }
                )
            }
        }
        source.recycle()
    }

    override fun onInterrupt() { isEnabled = false }
    override fun onDestroy() {
        super.onDestroy()
        instance = null; isEnabled = false
    }
}
