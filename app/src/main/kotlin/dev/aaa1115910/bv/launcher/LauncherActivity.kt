package dev.aaa1115910.bv.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isTv = isTvDevice()
        val targetClassName = if (isTv) {
            "dev.aaa1115910.bv.tv.activities.MainActivity"
        } else {
            "dev.aaa1115910.bv.mobile.activities.MainActivity"
        }

        runCatching {
            startActivity(
                Intent().setClassName(packageName, targetClassName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }.onFailure {
            // Fallback to tv activity if mobile not available
            if (!isTv) {
                startActivity(
                    Intent().setClassName(packageName, "dev.aaa1115910.bv.tv.activities.MainActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
        }

        finish()
    }

    private fun isTvDevice(): Boolean {
        val pm = packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("android.hardware.type.television") ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY))
    }
}
