package com.liumanhanyu.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 检查更新 — 从 GitHub Releases 下载最新 APK 并安装
 * 国内网络环境做了多级兜底
 */
object UpdateManager {

    private const val REPO = "852d7bhb7b-ctrl/liu-man-han-yu"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val API_ALL = "https://api.github.com/repos/$REPO/releases?per_page=3"
    // 兜底直链（发版时同步更新此 URL）
    private const val FALLBACK_URL = "https://github.com/$REPO/releases/latest/download/liumanhanyu.apk"

    fun checkUpdate(context: Context, silent: Boolean = false) {
        thread {
            tryApi(context, silent)
        }
    }

    private fun tryApi(context: Context, silent: Boolean) {
        // 先试 /releases/latest（返回单个对象）
        var url = tryGetLatest(context, API_LATEST)
        // 失败则试全部 releases 列表
        if (url == null) url = tryGetLatest(context, API_ALL)
        // 都失败走直链兜底
        if (url == null) {
            if (!silent) showToast(context, "API 不可用，尝试直链下载...")
            startDownloadOnUi(context, FALLBACK_URL)
            return
        }
        startDownloadOnUi(context, url)
    }

    /** 从 GitHub API 获取最新版本的下载链接 */
    private fun tryGetLatest(context: Context, apiUrl: String): String? {
        try {
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // /latest 返回单个对象，/releases 返回数组
            val assets: JSONArray = if (body.trim().startsWith("[")) {
                JSONArray(body).getJSONObject(0).getJSONArray("assets")
            } else {
                JSONObject(body).getJSONArray("assets")
            }
            if (assets.length() == 0) {
                if (context is android.app.Activity) {
                    context.runOnUiThread {
                        Toast.makeText(context, "暂无新版本", Toast.LENGTH_SHORT).show()
                    }
                }
                return null
            }
            return assets.getJSONObject(0).getString("browser_download_url")
        } catch (e: Exception) {
            return null
        }
    }

    private fun startDownloadOnUi(context: Context, url: String) {
        if (context is android.app.Activity) {
            context.runOnUiThread { startDownload(context, url) }
        } else {
            startDownload(context, url)
        }
    }

    private fun startDownload(context: Context, url: String) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "流氓汉语.apk")
            file.delete()

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("流氓汉语更新")
                setDescription("正在下载最新版...")
                setDestinationUri(Uri.fromFile(file))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                    if (id == downloadId) {
                        context.unregisterReceiver(this)
                        installApk(context, file)
                    }
                }
            }
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

            Toast.makeText(context, "正在下载更新...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } else {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private fun showToast(context: Context, msg: String) {
        (context as? android.app.Activity)?.runOnUiThread {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
