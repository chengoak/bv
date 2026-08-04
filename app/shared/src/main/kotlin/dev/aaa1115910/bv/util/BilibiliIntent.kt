package dev.aaa1115910.bv.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object BilibiliIntent {
    private const val BILIBILI_PACKAGE = "tv.danmaku.bili"

    /**
     * 启动 B 站/浏览器并保证按返回键先回到 BV：
     *  1. 先把 BV 自己的主 Activity 拉到前台（CLEAR_TOP + SINGLE_TOP，避免重复创建），
     *     让 Android task 切换器把它留在 B 站 task 之前。
     *  2. 然后用 NEW_TASK 启动 target（哔哩哔哩/浏览器）。
     * 用户在 B 站里按返回，Android 会把上一个 visible task（BV）拉到前台。
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

        val bvMain = createBvMainIntent(context)

        // 把 BV 拉到前台（CLEAR_TOP + SINGLE_TOP 不重建栈）
        runCatching {
            context.startActivity(bvMain)
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

    /**
     * 构造 BV 主 Activity 的 Intent。
     * 用 ACTION_MAIN 模拟「从桌面点图标」的效果。
     */
    private fun createBvMainIntent(context: Context): Intent {
        val pm = context.packageManager
        val isTv = pm.hasSystemFeature("android.hardware.type.television") ||
            pm.hasSystemFeature("android.software.leanback")
        val targetClassName = if (isTv) {
            "dev.aaa1115910.bv.tv.activities.MainActivity"
        } else {
            "dev.aaa1115910.bv.mobile.activities.MainActivity"
        }
        return Intent().apply {
            setClassName(context.packageName, targetClassName)
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            // CLEAR_TOP + SINGLE_TOP：BV 已经在的话不重建，直接拉到前台
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }
}
