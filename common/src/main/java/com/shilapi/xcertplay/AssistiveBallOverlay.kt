package com.shilapi.xcertplay

import com.shilapi.xcertplay.compat.getSystemServiceCompat
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.hypot

/**
 * 桌面快捷悬浮小白点 (Assistive Ball Overlay)
 * 为车机桌面不显示应用图标（需进工程模式）的车主提供全局浮动返回入口。
 * 离开 DiPlay 界面时在屏幕边缘显示半透明小白点，点击即可瞬间返回 DiPlay，
 * 支持随意拖动并贴边保存记忆位置。
 */
object AssistiveBallOverlay {
    private const val TAG = "AssistiveBallOverlay"
    private const val PREFS = "diplay_assistive_ball"
    private const val KEY_X = "ball_x"
    private const val KEY_Y = "ball_y"
    private const val SHOW_DELAY_MILLIS = 300L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ballView: View? = null

    /**
     * 检查是否有悬浮窗权限
     * Android 6.0 (API 23) 以下安装时已通过清单授权，直接返回 true
     */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /**
     * 是否启用了悬浮小白点
     */
    fun isEnabled(context: Context): Boolean =
        AirPlayPersistence.loadAssistiveBallEnabled(context)

    /**
     * 开关控制
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveAssistiveBallEnabled(context, enabled)
        if (enabled) {
            if (!diPlayInFront() && permitted(context)) {
                scheduleShow(context)
            }
        } else {
            mainHandler.removeCallbacksAndMessages(null)
            hide()
        }
    }

    /**
     * 当 DiPlay 的 Activity 进入前台时调用：自动隐藏小白点
     */
    fun onDiPlayForeground() {
        mainHandler.removeCallbacksAndMessages(null)
        hide()
    }

    /**
     * 当 DiPlay 离开前台（退回车机桌面或其他 App）时调用：延时弹出小白点
     */
    fun onDiPlayBackground(context: Context) {
        mainHandler.removeCallbacksAndMessages(null)
        if (!isEnabled(context) || !permitted(context)) return
        mainHandler.postDelayed({
            if (!diPlayInFront() && isEnabled(context) && permitted(context)) {
                show(context)
            }
        }, SHOW_DELAY_MILLIS)
    }

    fun scheduleShow(context: Context) {
        onDiPlayBackground(context)
    }

    /**
     * 显示悬浮小白点
     */
    fun show(context: Context): Boolean {
        if (ballView != null) return true
        if (!permitted(context)) return false

        val windows = context.getSystemServiceCompat(WindowManager::class.java) ?: return false
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val ballSize = (48 * metrics.density).toInt()
        val margin = (12 * metrics.density).toInt()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaultX = screenWidth - ballSize - margin
        val defaultY = screenHeight / 3

        val initialX = prefs.getInt(KEY_X, defaultX).coerceIn(0, (screenWidth - ballSize).coerceAtLeast(0))
        val initialY = prefs.getInt(KEY_Y, defaultY).coerceIn(0, (screenHeight - ballSize).coerceAtLeast(0))

        val overlayType = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
            title = "DiPlay Assistive Ball"
        }

        val view = BallCanvasView(context)

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    isDragging = false
                    view.isPressedState = true
                    view.invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (startX + dx).toInt().coerceIn(0, (screenWidth - ballSize).coerceAtLeast(0))
                        params.y = (startY + dy).toInt().coerceIn(0, (screenHeight - ballSize).coerceAtLeast(0))
                        runCatching { windows.updateViewLayout(view, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressedState = false
                    view.invalidate()
                    if (isDragging) {
                        // 拖动结束：贴近最近的屏幕边缘（左边缘或右边缘）吸附
                        val centerX = params.x + ballSize / 2
                        params.x = if (centerX < screenWidth / 2) margin else screenWidth - ballSize - margin
                        runCatching { windows.updateViewLayout(view, params) }
                        prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        // 单击触发：秒回 DiPlay
                        bringDiPlayToFront(context)
                    }
                }
            }
            true
        }

        return try {
            windows.addView(view, params)
            ballView = view
            Log.i(TAG, "assistive ball shown at ${params.x},${params.y}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "failed to show assistive ball", e)
            false
        }
    }

    /**
     * 隐藏悬浮小白点
     */
    fun hide() {
        val view = ballView ?: return
        ballView = null
        runCatching {
            view.context.getSystemServiceCompat(WindowManager::class.java)?.removeViewImmediate(view)
        }
        Log.i(TAG, "assistive ball hidden")
    }

    /**
     * 拉起 DiPlay 回到前台
     * 若投屏会话正在进行，则直接切回 CarPlayHostActivity；否则切回 DiPlayActivity
     */
    fun bringDiPlayToFront(context: Context) {
        val intent = if (CarPlayBackgroundSession.hasSession()) {
            Intent(context, CarPlayHostActivity::class.java)
        } else {
            Intent(context, DiPlayActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Log.e(TAG, "Failed to bring DiPlay to front", it)
        }
    }

    /**
     * 判断 DiPlay 是否在前台运行
     */
    private fun diPlayInFront(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /**
     * 自定义绘制的精致悬浮小白点视图
     */
    private class BallCanvasView(context: Context) : View(context) {
        var isPressedState = false
            set(value) {
                if (field != value) {
                    field = value
                    invalidate()
                }
            }

        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(cx, cy) - 2f

            // 1. 外层圆形底色（半透明深色玻璃质感）
            outerPaint.color = if (isPressedState) 0xB22D3748.toInt() else 0x8C1A202C.toInt()
            canvas.drawCircle(cx, cy, radius, outerPaint)

            // 2. 外圈细微高光描边
            strokePaint.color = if (isPressedState) 0x99FFFFFF.toInt() else 0x4DFFFFFF.toInt()
            strokePaint.strokeWidth = 2f
            canvas.drawCircle(cx, cy, radius, strokePaint)

            // 3. 核心小白点（白色圆心）
            innerPaint.color = if (isPressedState) Color.WHITE else 0xF0FFFFFF.toInt()
            val innerRadius = radius * 0.44f
            canvas.drawCircle(cx, cy, innerRadius, innerPaint)
        }
    }
}
