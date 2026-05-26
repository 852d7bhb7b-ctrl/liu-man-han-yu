package com.liumanhanyu.app

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * APP 内更新：Gitee 主源（国内可达）+ GitHub 备用
 */
object UpdateManager {

    // Gitee 主源（中国大陆可直接访问）
    private const val GITEE_API = "https://gitee.com/api/v5/repos/852d7bhb7b-ctrl/liu-man-han-yu/releases/latest"
    // GitHub 备用
    private const val GH_API = "https://api.github.com/repos/852d7bhb7b-ctrl/liu-man-han-yu/releases/latest"

    private fun getVerName(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    data class UpdateInfo(val version: String, val url: String, val body: String)

    fun checkUpdate(activity: Activity) {
        val curVer = getVerName(activity)
        val dlg = AlertDialog.Builder(activity)
            .setTitle("检查更新")
            .setMessage("当前版本: v$curVer\n正在检查...")
            .setCancelable(true).create()
        dlg.show()

        Thread {
            // 优先 Gitee，国内可达
            var info = fetch(GITEE_API, false)
            if (info == null) info = fetch(GH_API, true)
            val finalInfo = info

            activity.runOnUiThread {
                dlg.dismiss()
                when {
                    finalInfo != null && finalInfo.version != curVer ->
                        AlertDialog.Builder(activity)
                            .setTitle("发现新版本 v${finalInfo.version}")
                            .setMessage(finalInfo.body.take(200) + "\n\n当前: v$curVer → 新: v${finalInfo.version}")
                            .setPositiveButton("立即下载") { _, _ -> download(activity, finalInfo.url) }
                            .setNegativeButton("稍后", null).show()
                    finalInfo != null ->
                        AlertDialog.Builder(activity)
                            .setTitle("已是最新版本").setMessage("v$curVer")
                            .setPositiveButton("确定", null).show()
                    else ->
                        AlertDialog.Builder(activity)
                            .setTitle("检查更新失败")
                            .setMessage("无法连接更新服务器\n当前版本: v$curVer\n\n请稍后重试")
                            .setPositiveButton("确定", null).show()
                }
            }
        }.start()
    }

    private fun fetch(apiUrl: String, isGithub: Boolean): UpdateInfo? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(apiUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            if (isGithub) c.setRequestProperty("Accept", "application/vnd.github.v3+json")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = j.optString("tag_name", "").removePrefix("v")
            val assets = j.getJSONArray("assets")
            if (assets.length() == 0) null
            else UpdateInfo(tag, assets.getJSONObject(0).getString("browser_download_url"), j.optString("body", ""))
        } catch (_: Exception) { null }
        finally { c?.disconnect() }
    }

    private fun download(ctx: Context, url: String) {
        try {
            val f = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "流氓汉语.apk"); f.delete()
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("流氓汉语更新"); setDescription("正在下载..."); setDestinationUri(Uri.fromFile(f))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            })
            ctx.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    if (i?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == id) {
                        c?.unregisterReceiver(this)
                        c?.let { rc ->
                            try {
                                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                    FileProvider.getUriForFile(rc, "${rc.packageName}.provider", f) else Uri.fromFile(f)
                                rc.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            } catch (_: Exception) { Toast.makeText(rc, "安装失败", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            Toast.makeText(ctx, "开始下载...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(ctx, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show() }
    }
}
