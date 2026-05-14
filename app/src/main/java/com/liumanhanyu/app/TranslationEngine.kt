package com.liumanhanyu.app

/**
 * 多语言翻译引擎
 * - 自动检测源语言
 * - 本地词库 → 云端 API 兜底（TODO）
 */
object TranslationEngine {

    /** 从文本特征检测语言 */
    fun detectLanguage(text: String): String {
        if (text.any { it in 'ぁ'..'ん' || it in 'ァ'..'ン' }) return "ja"
        if (text.any { it in '가'..'힣' }) return "ko"
        if (text.any { it in '฀'..'๿' }) return "th"
        if (text.any { it in '؀'..'ۿ' }) return "ar"
        if (text.any { it in 'А'..'я' }) return "ru"
        if (containsChinese(text)) return "zh"
        return "en"
    }

    /** 正向：源语言文本 → 中文 */
    fun translateToChinese(text: String, sourceLang: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.any { it.isLetter() }) return null

        val dict = TranslationData.toZh[sourceLang] ?: TranslationData.toZh["en"]!!
        return findInDict(trimmed, dict)
    }

    /** 反向：中文 → 目标语言 */
    fun translateToForeign(chinese: String, targetLang: String): String? {
        val trimmed = chinese.trim()
        if (trimmed.isEmpty()) return null
        val dict = TranslationData.zhTo[targetLang] ?: TranslationData.zhTo["en"]!!
        return dict[trimmed]
    }

    /** 从界面文本中检测主语言 */
    fun detectAppLanguage(text: String): String {
        val foreign = text.replace(Regex("[一-鿿\\s\\d]"), "")
        return if (foreign.isNotEmpty()) detectLanguage(foreign) else "en"
    }

    fun containsChinese(text: String): Boolean {
        return text.any { it in '一'..'鿿' }
    }

    // ===== 字典查找（精确 → 忽略大小写 → 去标点 → 部分匹配） =====

    private fun findInDict(text: String, dict: Map<String, String>): String? {
        // 1. 精确匹配
        dict[text]?.let { return it }
        // 2. 忽略大小写
        val lower = text.lowercase()
        for ((k, v) in dict) {
            if (k.lowercase() == lower) return v
        }
        // 3. 去末尾标点
        val stripped = text.replace(Regex("[!！。？,.，]+$"), "")
        if (stripped != text) {
            val s = stripped.lowercase()
            for ((k, v) in dict) {
                if (k.lowercase().replace(Regex("[!！。？,.，]+$"), "") == s) return v
            }
        }
        // 4. 部分匹配
        for ((k, v) in dict) {
            if (k.length >= 4 && lower.contains(k.lowercase())) return v
        }
        return null
    }
}
