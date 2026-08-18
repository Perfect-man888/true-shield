package com.trueshield.app.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.trueshield.app.MainActivity

/**
 * 真信盾启动页 Activity。
 *
 * 作为应用入口（LAUNCHER），显示品牌 splash 后跳转 [MainActivity]，
 * 并透传原始 intent 数据，避免 FCM 通知等场景丢失 extras。
 */
class SplashActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        WindowCompat.getInsetsController(
            window,
            window.decorView,
        ).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            SplashScreen(
                onTimeout = { navigateToMain() },
            )
        }
    }

    private fun navigateToMain() {
        val mainIntent = Intent(
            this,
            MainActivity::class.java,
        ).apply {
            // 透传推送、来电守护等跳转参数。
            data = intent?.data
            intent?.extras?.let { putExtras(it) }
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
        }

        startActivity(mainIntent)
        finish()
    }
}
