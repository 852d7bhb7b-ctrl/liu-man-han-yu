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
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * 检查更新 — 从 GitHub Releases 下载最新 APK 并安装
 */
object UpdateManager {

    private const val REPO = "852d7bhb7b-ctrl/liu-man-han-yu"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases"

    fun checkUpdate(context: Context, silent: Boolean = false) {
        thread {
            try {
                val json = java.net.URL(RELEASES_URL).readText()
                val latest = JSONObject(json.trim().removePrefix("[").removeSuffix("]"))
                val assets = latest.getJSONArray("assets")
                if (assets.length() == 0) {
                    if (!silent) showToast(context, "暂无更新")
                    return@thread
                }
                val asset = assets.getJSONObject(0)
                val downloadUrl = asset.getString("browser_download_url")

                // 在主线程开始下载
                if (context is android.app.Activity) {
                    context.runOnUiThread { startDownload(context, downloadUrl) }
                } else if (!silent) {
                    showToast(context, "开始下载...")
                }
            } catch (e: Exception) {
                if (!silent) showToast(context, "检查更新失败: ${e.message}")
            }
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

            // 下载完成后自动安装
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
            // 如果 FileProvider 不可用，回退到直接打开文件
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
