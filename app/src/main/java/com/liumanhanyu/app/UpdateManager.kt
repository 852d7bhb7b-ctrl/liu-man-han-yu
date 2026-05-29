package com.liumanhanyu.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val VERSION_URL = "https://raw.githubusercontent.com/852d7bhb7b-ctrl/liu-man-han-yu/main/version.json"

    data class UpdateInfo(val version: String, val versionCode: Int, val downloadUrl: String, val changelog: String)

    fun checkUpdate(activity: Activity) {
        val curVer = getVerName(activity)
        val curCode = getVerCode(activity)
        val dlg = AlertDialog.Builder(activity)
            .setTitle("检查更新")
            .setMessage("当前版本: v$curVer\n正在检查...")
            .setCancelable(true).create()
        dlg.show()

        Thread {
            val info = fetchVersionJson(VERSION_URL)
            activity.runOnUiThread {
                dlg.dismiss()
                when {
                    info == null ->
                        AlertDialog.Builder(activity)
                            .setTitle("检查更新失败")
                            .setMessage("无法连接更新服务器\n当前版本: v$curVer\n\n请检查网络后重试")
                            .setPositiveButton("确定", null).show()
                    info.versionCode > curCode ->
                        AlertDialog.Builder(activity)
                            .setTitle("发现新版本 v${info.version}")
                            .setMessage("${info.changelog}\n\n当前: v$curVer → 新: v${info.version}")
                            .setPositiveButton("立即下载") { _, _ -> tryDownload(activity, info) }
                            .setNegativeButton("稍后", null).show()
                    else ->
                        AlertDialog.Builder(activity)
                            .setTitle("已是最新版本")
                            .setMessage("当前版本: v$curVer\n无需更新")
                            .setPositiveButton("确定", null).show()
                }
            }
        }.start()
    }

    private fun fetchVersionJson(url: String): UpdateInfo? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            c.instanceFollowRedirects = true
            val body = c.inputStream.bufferedReader().readText()
            val j = JSONObject(body)
            UpdateInfo(
                version = j.optString("version", ""),
                versionCode = j.optInt("versionCode", 0),
                downloadUrl = j.optString("downloadUrl", ""),
                changelog = j.optString("changelog", "")
            )
        } catch (e: Exception) {
            android.util.Log.w("HanyuUpdate", "fetch $url: ${e.message}")
            null
        } finally { c?.disconnect() }
    }

    // ===== 下载：多镜像 + 浏览器兜底 =====

    // 镜像列表：原站 → ghproxy → fastgit
    private fun mirrors(original: String): List<String> {
        // 从 GitHub Release URL 提取 path
        // https://github.com/OWNER/REPO/releases/download/TAG/FILE
        val path = original.removePrefix("https://github.com/")
        return listOf(
            original,
            "https://ghproxy.com/https://github.com/$path",
            "https://hub.fastgit.xyz/$path"
        )
    }

    private fun tryDownload(ctx: Context, info: UpdateInfo) {
        val urls = mirrors(info.downloadUrl)
        val dlg = AlertDialog.Builder(ctx as Activity)
            .setTitle("正在下载")
            .setMessage("连接服务器...")
            .setCancelable(false).create()
        dlg.show()

        Thread {
            var lastError = ""
            var success = false

            for ((idx, url) in urls.withIndex()) {
                if (success) break
                val mirrorLabel = if (idx == 0) "主站" else "镜像${idx}"
                (ctx as Activity).runOnUiThread {
                    dlg.setMessage("尝试 $mirrorLabel...")
                }

                val result = downloadToFile(url, ctx) { pct, dl, total ->
                    (ctx as Activity).runOnUiThread {
                        dlg.setMessage("下载中 $pct% (${formatSize(dl)}/${formatSize(total)})")
                    }
                }

                if (result != null) {
                    success = true
                    val elapsed = result.second
                    (ctx as Activity).runOnUiThread {
                        dlg.dismiss()
                        Toast.makeText(ctx, "下载完成 (${elapsed}s)，安装中...", Toast.LENGTH_SHORT).show()
                        installApk(ctx, result.first)
                    }
                } else {
                    lastError = "$mirrorLabel 失败"
                }
            }

            if (!success) {
                (ctx as Activity).runOnUiThread {
                    dlg.dismiss()
                    // 浏览器兜底
                    AlertDialog.Builder(ctx)
                        .setTitle("下载失败")
                        .setMessage("多个下载源均失败\n\n可在浏览器中直接下载安装")
                        .setPositiveButton("浏览器下载") { _, _ ->
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }.start()
    }

    // 返回 Pair<File, 耗时秒> 或 null
    private fun downloadToFile(urlStr: String, ctx: Context, onProgress: (Int, Long, Long) -> Unit): Pair<File, Long>? {
        var conn: HttpURLConnection? = null
        var fos: FileOutputStream? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000; conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.instanceFollowRedirects = true
            conn.connect()

            val code = conn.responseCode
            if (code != 200) return null

            val total = conn.contentLength.toLong()
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.cacheDir
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "流氓汉语.apk")
            if (f.exists()) f.delete()

            val input = conn.inputStream
            fos = FileOutputStream(f)
            val buf = ByteArray(8192)
            var read: Int
            var downloaded: Long = 0
            var lastPct = -1
            val startTime = System.currentTimeMillis()

            while (input.read(buf).also { read = it } != -1) {
                fos.write(buf, 0, read)
                downloaded += read
                if (total > 0) {
                    val pct = (downloaded * 100 / total).toInt()
                    if (pct != lastPct && pct % 10 == 0) {
                        lastPct = pct
                        onProgress(pct, downloaded, total)
                    }
                }
            }
            fos.close(); fos = null; input.close(); conn.disconnect(); conn = null

            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            Pair(f, elapsed)
        } catch (e: Exception) {
            android.util.Log.e("HanyuUpdate", "download $urlStr: ${e.message}")
            null
        } finally {
            try { fos?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun installApk(ctx: Context, f: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", f)
            } else {
                Uri.fromFile(f)
            }
            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) {
            Toast.makeText(ctx, "安装失败: ${e.message?.take(30)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    private fun getVerName(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    private fun getVerCode(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (_: Exception) { 0 }
}
