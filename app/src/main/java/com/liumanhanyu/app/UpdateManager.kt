package com.liumanhanyu.app

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * APP 内更新：从 GitHub raw 拉取 version.json（国内可访问，无需翻墙）
 * APK 直连下载（不依赖 DownloadManager，兼容所有 ROM）
 */
object UpdateManager {

    private const val VERSION_URL = "https://raw.githubusercontent.com/852d7bhb7b-ctrl/liu-man-han-yu/main/version.json"
    private const val VERSION_URL_FALLBACK = "https://raw.fastgit.org/852d7bhb7b-ctrl/liu-man-han-yu/main/version.json"
    private const val CHANNEL_ID = "hanyu_update"

    data class UpdateInfo(val version: String, val versionCode: Int, val url: String, val changelog: String)

    fun checkUpdate(activity: Activity) {
        val curVer = getVerName(activity)
        val curCode = getVerCode(activity)
        val dlg = AlertDialog.Builder(activity)
            .setTitle("检查更新")
            .setMessage("当前版本: v$curVer\n正在检查...")
            .setCancelable(true).create()
        dlg.show()

        Thread {
            var info = fetchVersionJson(VERSION_URL)
            if (info == null) info = fetchVersionJson(VERSION_URL_FALLBACK)

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
                            .setPositiveButton("立即下载") { _, _ -> directDownload(activity, info.url) }
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
                url = j.optString("downloadUrl", ""),
                changelog = j.optString("changelog", "")
            )
        } catch (e: Exception) {
            android.util.Log.w("HanyuUpdate", "fetch $url: ${e.message}")
            null
        } finally { c?.disconnect() }
    }

    // ===== 直连下载（不依赖 DownloadManager，兼容所有 ROM）=====

    private fun directDownload(ctx: Context, urlStr: String) {
        val dlg = AlertDialog.Builder(ctx as Activity)
            .setTitle("正在下载")
            .setMessage("连接服务器...")
            .setCancelable(false).create()
        dlg.show()

        Thread {
            var conn: HttpURLConnection? = null
            var fos: FileOutputStream? = null
            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.instanceFollowRedirects = true
                conn.connect()

                // 检查响应（GitHub 会 302 重定向到 S3）
                val code = conn.responseCode
                if (code != 200) {
                    (ctx as Activity).runOnUiThread {
                        dlg.dismiss()
                        Toast.makeText(ctx, "下载失败: HTTP $code", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

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

                // 显示下载进度
                (ctx as Activity).runOnUiThread {
                    dlg.setMessage("下载中... 0%")
                }

                while (input.read(buf).also { read = it } != -1) {
                    fos.write(buf, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val pct = (downloaded * 100 / total).toInt()
                        if (pct != lastPct && pct % 5 == 0) {
                            lastPct = pct
                            (ctx as Activity).runOnUiThread {
                                dlg.setMessage("下载中... $pct% (${formatSize(downloaded)}/${formatSize(total)})")
                            }
                        }
                    }
                }
                fos.close(); fos = null; input.close(); conn.disconnect(); conn = null

                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                (ctx as Activity).runOnUiThread {
                    dlg.dismiss()
                    Toast.makeText(ctx, "下载完成 (${elapsed}s)，正在安装...", Toast.LENGTH_SHORT).show()
                    installApk(ctx, f)
                }
            } catch (e: Exception) {
                android.util.Log.e("HanyuUpdate", "下载失败", e)
                (ctx as Activity).runOnUiThread {
                    dlg.dismiss()
                    Toast.makeText(ctx, "下载失败: ${e.message?.take(40)}", Toast.LENGTH_LONG).show()
                }
            } finally {
                try { fos?.close() } catch (_: Exception) {}
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun installApk(ctx: Context, f: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", f)
            } else {
                Uri.fromFile(f)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "安装失败: ${e.message?.take(30)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    private fun getVerName(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    private fun getVerCode(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (_: Exception) { 0 }
}
