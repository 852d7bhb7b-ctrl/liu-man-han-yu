package com.liumanhanyu.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 并行翻译 API 调度器：同时发起所有 API 请求，最快响应的结果被采用
 * ① MyMemory（免费，海外）② 百度（网页版）③ Google ④ Gemini
 * 整体超时 6 秒，超时后返回 null
 */
object ApiTranslator {

    private const val OVERALL_TIMEOUT_MS = 6000L
    private var appContext: Context? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
    }

    val geminiKey: String
        get() {
            val ctx = appContext ?: return ""
            return ctx.getSharedPreferences("hanyu_settings", 0).getString("gemini_key", "") ?: ""
        }

    interface Callback {
        fun onResult(translated: String?)
    }

    fun translate(text: String, sourceLang: String, targetLang: String, callback: Callback) {
        val done = AtomicBoolean(false)

        fun tryApi(name: String, block: (Callback) -> Unit) {
            thread {
                if (done.get()) return@thread
                block(object : Callback {
                    override fun onResult(r: String?) {
                        if (r != null && !done.getAndSet(true)) {
                            callback.onResult(r)
                        }
                    }
                })
            }
        }

        tryApi("MyMemory") { tryMyMemory(text, sourceLang, targetLang, it) }
        tryApi("Baidu") { tryBaidu(text, sourceLang, targetLang, it) }
        tryApi("Google") { tryGoogle(text, sourceLang, targetLang, it) }
        tryApi("Gemini") { tryGemini(text, sourceLang, targetLang, it) }

        // 兜底：2 秒后如果还没结果，用 MyMemory 再试一次
        thread {
            Thread.sleep(2000)
            if (!done.get()) {
                tryMyMemory(text, sourceLang, targetLang, object : Callback {
                    override fun onResult(r: String?) {
                        if (r != null && !done.getAndSet(true)) {
                            callback.onResult(r)
                        }
                    }
                })
            }
        }

        // 整体超时：超时后强制返回 null
        thread {
            Thread.sleep(OVERALL_TIMEOUT_MS)
            if (!done.getAndSet(true)) {
                callback.onResult(null)
            }
        }
    }

    // ===== MyMemory（免费，无需 key）=====
    private fun tryMyMemory(text: String, from: String, to: String, cb: Callback) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://api.mymemory.translated.net/get?q=${urlEncode(text)}&langpair=$from|$to")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 10000
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val dst = json.optJSONObject("responseData")?.optString("translatedText")
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (e: Exception) {
            android.util.Log.w("HanyuAPI", "MyMemory: ${e.message}")
            cb.onResult(null)
        } finally { conn?.disconnect() }
    }

    // ===== 百度翻译（网页版 API）=====
    private fun tryBaidu(text: String, from: String, to: String, cb: Callback) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://fanyi.baidu.com/transapi?from=$from&to=$to&query=${urlEncode(text)}")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val dst = json.optJSONObject("trans")?.optJSONObject("result")?.optString("dst")
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (e: Exception) {
            android.util.Log.w("HanyuAPI", "Baidu: ${e.message}")
            cb.onResult(null)
        } finally { conn?.disconnect() }
    }

    // ===== Google 翻译 =====
    private fun tryGoogle(text: String, from: String, to: String, cb: Callback) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=${urlEncode(text)}")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            val first = arr.optJSONArray(0)
            val second = first?.optJSONArray(0)
            val dst = second?.optString(0)
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (e: Exception) {
            android.util.Log.w("HanyuAPI", "Google: ${e.message}")
            cb.onResult(null)
        } finally { conn?.disconnect() }
    }

    // ===== Gemini =====
    private fun tryGemini(text: String, from: String, to: String, cb: Callback) {
        if (geminiKey.isEmpty()) { cb.onResult(null); return }
        var conn: HttpURLConnection? = null
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
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 10000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val resp = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(resp)
            val candidates = json.optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val dst = parts?.optJSONObject(0)?.optString("text")?.trim()
            cb.onResult(dst?.takeIf { it.isNotEmpty() && it != text })
        } catch (e: Exception) {
            android.util.Log.w("HanyuAPI", "Gemini: ${e.message}")
            cb.onResult(null)
        } finally { conn?.disconnect() }
    }

    private fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")
}
