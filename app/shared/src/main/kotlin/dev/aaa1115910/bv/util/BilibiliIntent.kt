package dev.aaa1115910.bv.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object BilibiliIntent {
    private const val BILIBILI_PACKAGE = "tv.danmaku.bili"

    /**
     * Try to open a video in the bilibili client. Falls back to the web URL if the
     * client is not installed. Returns true if an activity was launched.
     */
    fun openVideo(context: Context, aid: Long, bvid: String?): Boolean {
        val webUrl = if (!bvid.isNullOrEmpty()) {
            "https://www.bilibili.com/video/$bvid"
        } else {
            "https://www.bilibili.com/video/av$aid"
        }
        val deepLink = if (!bvid.isNullOrEmpty()) {
            "bilibili://video/$bvid"
        } else {
            "bilibili://video/av$aid"
        }

        // 1) 优先尝试 bilibili 客户端的深链
        try {
            val deepIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(BILIBILI_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(deepIntent)
            return true
        } catch (_: ActivityNotFoundException) {
            // 没装客户端，落到 http
        } catch (_: SecurityException) {
            // Android 11+ 没声明 <queries>，落到 http
        }

        // 2) 退到 https URL，让系统选择器弹出（可能含 bilibili / 浏览器）
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "未找到可用的应用打开该视频",
                Toast.LENGTH_SHORT
            ).show()
            false
        } catch (_: SecurityException) {
            Toast.makeText(
                context,
                "未找到可用的应用打开该视频",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }
}
