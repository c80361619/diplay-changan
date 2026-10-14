package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 车机全屏与导航栏强力隐藏控制助手
 * 针对比亚迪 DiLink、掌讯 (TS)、方易通 (FYT)、鼎微、通用车机及 AOSP 原生导航栏，
 * 提供全屏沉浸模式与车机左侧/底部导航栏强力隐藏联动控制。
 */
object CarFullscreenHelper {
    private const val TAG = "CarFullscreenHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 基础沉浸式全屏 Flag 组合
     */
    private const val IMMERSIVE_STICKY_FLAGS = (
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_FULLSCREEN
        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    )

    /**
     * 对 Activity 应用全屏沉浸模式并隐藏车机导航栏
     */
    fun applyFullscreen(
        activity: Activity,
        hideStatusBar: Boolean = true,
        hideNavBar: Boolean = true
    ) {
        val window = activity.window ?: return

        // 1. 设置 Window 基础全屏标志
        runCatching {
            if (hideStatusBar) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            WindowCompat.setDecorFitsSystemWindows(window, !(hideStatusBar && hideNavBar))
        }

        // 2. Android 9.0+ 适配异形屏/车机屏幕切口延伸
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
        }

        // 3. AndroidX WindowInsetsControllerCompat 兼容层调用
        runCatching {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
            else controller.show(WindowInsetsCompat.Type.statusBars())

            if (hideNavBar) controller.hide(WindowInsetsCompat.Type.navigationBars())
            else controller.show(WindowInsetsCompat.Type.navigationBars())

            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 4. 底层原生 DecorView systemUiVisibility 强行置位（兼容 Android 4.4 到高版本车机系统）
        runCatching {
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            if (hideStatusBar) {
                flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
            if (hideNavBar) {
                flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
            if (hideStatusBar || hideNavBar) {
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            decorView.systemUiVisibility = flags
        }

        // 5. 广播通知车机定制 SystemUI 隐藏左侧/底部导航栏
        if (hideNavBar) {
            broadcastHeadUnitNavBarState(activity, hide = true)
        }
    }

    /**
     * 保持全屏状态不被系统或用户触摸破坏
     * 监听 SystemUiVisibilityChange，并在被重置时自动延时重新隐藏
     */
    fun setupContinuousFullscreen(activity: Activity) {
        runCatching {
            val decorView = activity.window?.decorView ?: return
            decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                // 当导航栏或状态栏可见度变为显示时，延时 800ms 重新强制隐藏
                if ((visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0 ||
                    (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
                ) {
                    mainHandler.removeCallbacksAndMessages(decorView)
                    mainHandler.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            applyFullscreen(activity, hideStatusBar = true, hideNavBar = true)
                        }
                    }, 800L)
                }
            }
        }
    }

    /**
     * 恢复车机导航栏与状态栏显示
     */
    fun restoreSystemBars(activity: Activity) {
        val window = activity.window ?: return
        runCatching {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        broadcastHeadUnitNavBarState(activity, hide = false)
    }

    /**
     * 针对各大品牌/方案车机广播隐藏或恢复导航栏指令
     */
    fun broadcastHeadUnitNavBarState(context: Context, hide: Boolean) {
        val ctx = context.applicationContext ?: context

        // 1. 比亚迪 DiLink 全屏广播
        sendSafeBroadcast(ctx, Intent("com.byd.intent.action.FULLSCREEN_ACTION").apply {
            putExtra("isFullScreen", hide)
        })
        if (hide) {
            sendSafeBroadcast(ctx, Intent("com.byd.navigation.bar.hide"))
        } else {
            sendSafeBroadcast(ctx, Intent("com.byd.navigation.bar.show"))
        }
        sendSafeBroadcast(ctx, Intent("com.byd.systemui.fullscreen").apply {
            putExtra("fullscreen", hide)
        })

        // 2. 掌讯车机 (TS / AutoChips / ZhanXun)
        if (hide) {
            sendSafeBroadcast(ctx, Intent("com.android.internal.policy.impl.hideNavigationBar"))
            sendSafeBroadcast(ctx, Intent("com.ts.car.fullscreen"))
            sendSafeBroadcast(ctx, Intent("com.ts.main.fullscreen"))
        } else {
            sendSafeBroadcast(ctx, Intent("com.android.internal.policy.impl.showNavigationBar"))
        }

        // 3. 方易通车机 (FYT)
        if (hide) {
            sendSafeBroadcast(ctx, Intent("fyt.systemui.action.HIDE_BAR"))
            sendSafeBroadcast(ctx, Intent("com.fyt.systemui.hide_nav"))
        } else {
            sendSafeBroadcast(ctx, Intent("fyt.systemui.action.SHOW_BAR"))
            sendSafeBroadcast(ctx, Intent("com.fyt.systemui.show_nav"))
        }

        // 4. 鼎微 / 诺威达 / 亿联等通用车机广播
        if (hide) {
            sendSafeBroadcast(ctx, Intent("android.intent.action.NAVBAR_HIDE"))
            sendSafeBroadcast(ctx, Intent("action.hidenav"))
            sendSafeBroadcast(ctx, Intent("com.microntek.full"))
            sendSafeBroadcast(ctx, Intent("com.zjinnova.zlink").apply { putExtra("fullScreen", true) })
            sendSafeBroadcast(ctx, Intent("com.android.systemui.fullscreen").apply { putExtra("isFullScreen", true) })
            sendSafeBroadcast(ctx, Intent("com.car.ui.hide_navigation"))
        } else {
            sendSafeBroadcast(ctx, Intent("android.intent.action.NAVBAR_SHOW"))
            sendSafeBroadcast(ctx, Intent("action.shownav"))
            sendSafeBroadcast(ctx, Intent("com.microntek.nofull"))
            sendSafeBroadcast(ctx, Intent("com.zjinnova.zlink").apply { putExtra("fullScreen", false) })
            sendSafeBroadcast(ctx, Intent("com.android.systemui.fullscreen").apply { putExtra("isFullScreen", false) })
            sendSafeBroadcast(ctx, Intent("com.car.ui.show_navigation"))
        }

        // 5. 尝试系统属性写入（若有权限）
        runCatching {
            Settings.System.putInt(ctx.contentResolver, "hide_navigation_bar", if (hide) 1 else 0)
        }
        runCatching {
            Settings.System.putInt(ctx.contentResolver, "navigation_bar_hide", if (hide) 1 else 0)
        }
    }

    private fun sendSafeBroadcast(context: Context, intent: Intent) {
        runCatching {
            context.sendBroadcast(intent)
        }.onFailure {
            Log.d(TAG, "sendBroadcast ignored: ${intent.action}")
        }
    }
}
