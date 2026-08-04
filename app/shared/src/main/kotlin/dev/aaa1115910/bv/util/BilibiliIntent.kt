package dev.aaa1115910.bv.util

import android.app.Activity
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
     *
     * Back stack:
     *   [BV MainActivity]  ←  bvMain
     *   [哔哩哔哩/浏览器]   ←  target
     * 用户在哔哩哔哩里按返回键，会先回到 BV，再按一次才退出。
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

        // 1) 优先尝试 bilibili 客户端的深链
        try {
            val deepIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(BILIBILI_PACKAGE)
            }
            launchWithBvBackStack(context, bvMain, deepIntent)
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
            }
            launchWithBvBackStack(context, bvMain, webIntent)
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
     * 构造 BV 主 Activity 的 Intent，作为返回栈底。
     * 模拟「从桌面点图标」的效果，避免被当成深链再次处理。
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
            // 用 ACTION_MAIN 模拟「任务启动器入口」
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            // 不带 NEW_TASK：在当前 task 内启动
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    /**
     * 启动 [target]（哔哩哔哩/浏览器）并把 [bvMain] 压入任务栈底，
     * 这样用户在哔哩哔哩里按返回会先回到 BV。
     *
     * 用 [Activity.startActivities] 在同一 task 内顺序启动两个 intent。
     * bvMain 先启动，target 后启动并置于栈顶。
     */
    private fun launchWithBvBackStack(
        context: Context,
        bvMain: Intent,
        target: Intent
    ) {
        // target 加 NEW_TASK 让它跑在独立 task（外部应用），bvMain 不带 NEW_TASK
        // 跟它走同 task 作为返回栈底
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = context as? Activity
        if (activity == null) {
            // 非 Activity Context（如 service/receiver）只能把 NEW_TASK 加上
            bvMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivities(arrayOf(bvMain, target))
        } else {
            // 在 BV 自己的 task 里顺序启动两个 intent
            activity.startActivities(arrayOf(bvMain, target))
        }
    }
}
