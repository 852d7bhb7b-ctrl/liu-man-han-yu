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
 * APP 内更新：从 GitHub raw 拉取 version.json（国内可访问，无需翻墙）
 * APK 通过 GitHub Releases 直链下载
 */
object UpdateManager {

    // raw.githubusercontent.com 在国内可访问
    private const val VERSION_URL = "https://raw.githubusercontent.com/852d7bhb7b-ctrl/liu-man-han-yu/main/version.json"

    // 备用：直接访问 GitHub 源文件
    private const val VERSION_URL_FALLBACK = "https://raw.fastgit.org/852d7bhb7b-ctrl/liu-man-han-yu/main/version.json"

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
                            .setPositiveButton("立即下载") { _, _ -> download(activity, info.url) }
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
            // 如果返回重定向，手动跟随
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

    private fun getVerName(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    private fun getVerCode(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (_: Exception) { 0 }

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
