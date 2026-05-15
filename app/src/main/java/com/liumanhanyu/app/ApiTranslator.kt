package com.liumanhanyu.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * 多级翻译 API 降级链路
 * ① 百度 → ② MyMemory → ③ Google → ④ Gemini → ⑤ 返回 null
 */
object ApiTranslator {

    /** Gemini API Key（可选，填了启用 LLM 级翻译） */
    var geminiKey = ""

    /** 翻译回调 */
    interface Callback {
        fun onResult(translated: String?)
    }

    /** 翻译中文到指定语言，各级降级 */
    fun translate(text: String, targetLang: String, callback: Callback) {
        thread {
            val cb1: Callback = object : Callback {
                override fun onResult(r: String?) {
                    if (r != null) callback.onResult(r)
                    else tryMyMemory(text, targetLang, object : Callback {
                        override fun onResult(r2: String?) {
                            if (r2 != null) callback.onResult(r2)
                            else tryGoogle(text, targetLang, object : Callback {
                                override fun onResult(r3: String?) {
                                    if (r3 != null) callback.onResult(r3)
                                    else tryGemini(text, targetLang, object : Callback {
                                        override fun onResult(r4: String?) {
                                            callback.onResult(r4)
                                        }
                                    })
                                }
                            })
                        }
                    })
                }
            }
            tryBaidu(text, targetLang, cb1)
        }
    }

    // ===== ① 百度翻译 =====
    private fun tryBaidu(text: String, lang: String, cb: Callback) {
        try {
            val url = URL("https://fanyi.baidu.com/transapi?from=zh&to=$lang&query=${urlEncode(text)}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000; conn.readTimeout = 2000
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val dst = json.optJSONObject("trans")?.optJSONObject("result")?.optString("dst")
            cb.onResult(dst?.takeIf { it.isNotEmpty() })
        } catch (_: Exception) { cb.onResult(null) }
    }

    // ===== ② MyMemory =====
    private fun tryMyMemory(text: String, lang: String, cb: Callback) {
        try {
            val url = URL("https://api.mymemory.translated.net/get?q=${urlEncode(text)}&langpair=zh|$lang")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2500; conn.readTimeout = 2500
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val dst = json.optJSONObject("responseData")?.optString("translatedText")
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (_: Exception) { cb.onResult(null) }
    }

    // ===== ③ Google 翻译 =====
    private fun tryGoogle(text: String, lang: String, cb: Callback) {
        try {
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh&tl=$lang&dt=t&q=${urlEncode(text)}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2500; conn.readTimeout = 2500
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            // 返回格式: [[["译文","原文",...]], ...]
            val arr = JSONArray(body)
            val first = arr.optJSONArray(0)
            val second = first?.optJSONArray(0)
            val dst = second?.optString(0)
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (_: Exception) { cb.onResult(null) }
    }

    // ===== ④ Gemini =====
    private fun tryGemini(text: String, lang: String, cb: Callback) {
        if (geminiKey.isEmpty()) { cb.onResult(null); return }
        try {
            val langName = TranslationData.langNames[lang] ?: lang
            val prompt = "将以下中文翻译为$langName。只输出译文：\n$text"
            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000; conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val resp = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(resp)
            val candidates = json.optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val dst = parts?.optJSONObject(0)?.optString("text")?.trim()
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (_: Exception) { cb.onResult(null) }
    }

    private fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")
}
