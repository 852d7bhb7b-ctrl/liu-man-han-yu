package com.liumanhanyu.app

/**
 * 多语言翻译引擎
 * - 自动检测源语言
 * - 本地词库 → 云端 API 自动兜底
 */
object TranslationEngine {

    /** 翻译缓存（避免重复查词库），null 值表示已查过但未找到 */
    private const val CACHE_MAX = 500
    private val cache = object : LinkedHashMap<String, String?>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean {
            return size > CACHE_MAX
        }
    }

    fun clearCache() { cache.clear() }

    /** 从文本特征检测语言 */
    fun detectLanguage(text: String): String {
        if (text.any { it in 'ぁ'..'ん' || it in 'ァ'..'ン' }) return "ja"
        if (text.any { it in '가'..'힣' }) return "ko"
        if (text.any { it in '฀'..'๿' }) return "th"
        if (text.any { it in '؀'..'ۿ' }) return "ar"
        if (text.any { it in 'А'..'я' }) return "ru"
        if (containsChinese(text)) return "zh"
        // 拉丁语系细分（检测变音字母）
        if (text.any { it in "éèêëàâîïôûùçÉÈÊËÀÂÎÏÔÛÙÇ" }) return "fr"
        if (text.any { it in "áéíóúüñÁÉÍÓÚÜÑ¿¡" }) return "es"
        if (text.any { it in "áéíóúâêôãõçÁÉÍÓÚÂÊÔÃÕÇ" }) return "pt"
        if (text.any { it in "äöüßÄÖÜ" }) return "de"
        return "en"
    }

    /** 正向：源语言文本 → 中文 */
    fun translateToChinese(text: String, sourceLang: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.any { it.isLetter() }) return null

        val cacheKey = "zh|$sourceLang|$trimmed"
        if (cache.containsKey(cacheKey)) return cache[cacheKey]

        val dict = TranslationData.toZh[sourceLang] ?: TranslationData.toZh["en"]!!
        val result = findInDict(trimmed, dict)
        cache[cacheKey] = result
        return result
    }

    /** 正向 + API 兜底：本地找不到自动走云端 */
    fun translateToChineseWithFallback(text: String, sourceLang: String, callback: (String?) -> Unit) {
        val local = translateToChinese(text, sourceLang)
        if (local != null) { callback(local); return }
        ApiTranslator.translate(text, sourceLang, "zh", object : ApiTranslator.Callback {
            override fun onResult(translated: String?) {
                if (translated != null) {
                    val cacheKey = "zh|$sourceLang|${text.trim()}"
                    cache[cacheKey] = translated
                }
                callback(translated)
            }
        })
    }

    /** 反向：中文 → 目标语言 */
    fun translateToForeign(chinese: String, targetLang: String): String? {
        val trimmed = chinese.trim()
        if (trimmed.isEmpty()) return null

        val cacheKey = "$targetLang|zh|$trimmed"
        if (cache.containsKey(cacheKey)) return cache[cacheKey]

        val dict = TranslationData.zhTo[targetLang] ?: TranslationData.zhTo["en"]!!
        val result = dict[trimmed]
        cache[cacheKey] = result
        return result
    }

    /** 反向 + API 兜底：本地找不到自动走云端 */
    fun translateToForeignWithFallback(chinese: String, targetLang: String, callback: (String?) -> Unit) {
        val local = translateToForeign(chinese, targetLang)
        if (local != null) { callback(local); return }
        ApiTranslator.translate(chinese, "zh", targetLang, object : ApiTranslator.Callback {
            override fun onResult(translated: String?) {
                if (translated != null) {
                    val cacheKey = "$targetLang|zh|${chinese.trim()}"
                    cache[cacheKey] = translated
                }
                callback(translated)
            }
        })
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
        // 4. 部分匹配（优先匹配最长键，避免短词覆盖长词）
        for ((k, v) in dict.entries.sortedByDescending { it.key.length }) {
            if (k.length >= 4 && lower.contains(k.lowercase())) return v
        }
        return null
    }
}
