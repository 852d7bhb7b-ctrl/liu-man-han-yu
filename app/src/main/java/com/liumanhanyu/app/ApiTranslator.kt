package com.liumanhanyu.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * 多级翻译 API 降级链路（双向）
 * ① 百度 → ② MyMemory → ③ Google → ④ Gemini → ⑤ 返回 null
 *
 * @param sourceLang 源语言代码（如 "en", "ja", "zh"）
 * @param targetLang 目标语言代码
 */
object ApiTranslator {

    var geminiKey = ""

    interface Callback {
        fun onResult(translated: String?)
    }

    fun translate(text: String, sourceLang: String, targetLang: String, callback: Callback) {
        thread {
            tryBaidu(text, sourceLang, targetLang, object : Callback {
                override fun onResult(r: String?) {
                    if (r != null) callback.onResult(r)
                    else tryMyMemory(text, sourceLang, targetLang, object : Callback {
                        override fun onResult(r2: String?) {
                            if (r2 != null) callback.onResult(r2)
                            else tryGoogle(text, sourceLang, targetLang, object : Callback {
                                override fun onResult(r3: String?) {
                                    if (r3 != null) callback.onResult(r3)
                                    else tryGemini(text, sourceLang, targetLang, object : Callback {
                                        override fun onResult(r4: String?) { callback.onResult(r4) }
                                    })
                                }
                            })
                        }
                    })
                }
            })
        }
    }

    // ===== ① 百度翻译 =====
    private fun tryBaidu(text: String, from: String, to: String, cb: Callback) {
        try {
            val url = URL("https://fanyi.baidu.com/transapi?from=$from&to=$to&query=${urlEncode(text)}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000; conn.readTimeout = 2000
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(body)
            val dst = json.optJSONObject("trans")?.optJSONObject("result")?.optString("dst")
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (_: Exception) { cb.onResult(null) }
    }

    // ===== ② MyMemory =====
    private fun tryMyMemory(text: String, from: String, to: String, cb: Callback) {
        try {
            val url = URL("https://api.mymemory.translated.net/get?q=${urlEncode(text)}&langpair=$from|$to")
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
    private fun tryGoogle(text: String, from: String, to: String, cb: Callback) {
        try {
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=${urlEncode(text)}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2500; conn.readTimeout = 2500
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONArray(body)
            val first = arr.optJSONArray(0)
            val second = first?.optJSONArray(0)
            val dst = second?.optString(0)
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (_: Exception) { cb.onResult(null) }
    }

    // ===== ④ Gemini =====
    private fun tryGemini(text: String, from: String, to: String, cb: Callback) {
        if (geminiKey.isEmpty()) { cb.onResult(null); return }
        try {
            val fromName = TranslationData.langNames[from] ?: from
            val toName = TranslationData.langNames[to] ?: to
            val prompt = "将以下${fromName}翻译为${toName}。只输出译文：\n$text"
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
