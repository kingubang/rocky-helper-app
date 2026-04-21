package com.rockyhelper

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 悬浮窗管理器
 * 负责创建、管理悬浮球和悬浮窗的显示与交互
 */
class FloatWindowManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // 悬浮球相关
    private var floatBallView: View? = null
    private var floatBallParams: WindowManager.LayoutParams? = null

    // 悬浮窗相关
    private var floatWindowView: View? = null
    private var floatWindowParams: WindowManager.LayoutParams? = null
    private var webView: WebView? = null

    // 状态
    var isWindowShowing = false
        private set

    // 拖动相关
    private var isDraggingBall = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var hasMoved = false

    // 悬浮窗拖动
    private var isDraggingWindow = false
    private var windowLastX = 0f
    private var windowLastY = 0f
    private var windowInitialX = 0
    private var windowInitialY = 0

    // 屏幕尺寸（使用真实尺寸，避免旋转干扰）
    private val realScreenWidth: Int
    private val realScreenHeight: Int
    private val isLandscape: Boolean

    // 悬浮球尺寸
    private val ballSize: Int // dp 转像素

    // 悬浮窗尺寸（自适应）
    private val windowWidth: Int
    private val windowHeight: Int

    init {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        realScreenWidth = displayMetrics.widthPixels
        realScreenHeight = displayMetrics.heightPixels

        // 检测当前是否横屏
        isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        ballSize = dpToPx(48)

        if (isLandscape) {
            // 横屏：正方形悬浮窗，适配屏幕高度的70%，但不超过420dp
            val maxSquare = dpToPx(420)
            val heightBased = (realScreenHeight * 0.70).toInt()
            val side = if (heightBased > maxSquare) maxSquare else heightBased
            windowWidth = side
            windowHeight = side
        } else {
            // 竖屏：紧凑矩形，宽度85%屏幕，高度70%屏幕
            windowWidth = (realScreenWidth * 0.88).toInt().coerceAtMost(dpToPx(400))
            windowHeight = (realScreenHeight * 0.65).toInt().coerceAtMost(dpToPx(520))
        }
    }

    /**
     * 重新显示悬浮球（供外部调用，如从悬浮窗最小化后恢复）
     */
    fun restoreFloatBall() {
        if (floatBallView != null) return
        showFloatBallInternal()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatBallInternal() {
        if (floatBallView != null) return

        // 创建悬浮球视图（使用 FrameLayout 以支持添加子 View）
        val ballView = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_float_ball)
            elevation = dpToPx(8).toFloat()
        }
        floatBallView = ballView

        // 悬浮球内添加 emoji 文字
        val emojiView = TextView(context).apply {
            text = "🔮"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }
        ballView.addView(emojiView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 初始位置：屏幕右侧中间
            x = realScreenWidth - ballSize - dpToPx(8)
            y = realScreenHeight / 2 - ballSize / 2
        }
        floatBallParams = params

        // 触摸事件处理
        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    hasMoved = false
                    isDraggingBall = false

                    // 缩小动画
                    ballView.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!hasMoved && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                        hasMoved = true
                        isDraggingBall = true
                    }

                    if (isDraggingBall) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        // 限制在屏幕范围内
                        params.x = params.x.coerceIn(0, realScreenWidth - ballSize)
                        params.y = params.y.coerceIn(0, realScreenHeight - ballSize)
                        windowManager.updateViewLayout(ballView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    ballView.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(OvershootInterpolator(2f)).start()

                    if (isDraggingBall) {
                        // 靠边吸附
                        snapToEdge(params)
                        windowManager.updateViewLayout(ballView, params)
                    } else {
                        // 点击事件 - 打开悬浮窗
                        showFloatWindow()
                    }
                    isDraggingBall = false
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(ballView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 靠边吸附动画
     */
    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val centerX = params.x + ballSize / 2
        val targetX = if (centerX < realScreenWidth / 2) {
            dpToPx(4) // 吸附到左边
        } else {
            realScreenWidth - ballSize - dpToPx(4) // 吸附到右边
        }

        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 250
        animator.interpolator = OvershootInterpolator(1.5f)
        animator.addUpdateListener { anim ->
            params.x = anim.animatedValue as Int
            try {
                windowManager.updateViewLayout(floatBallView, params)
            } catch (e: Exception) {
                // view 可能已被移除
            }
        }
        animator.start()
    }

    /**
     * 显示悬浮窗
     */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun showFloatWindow() {
        if (floatWindowView != null) return
        removeFloatBall()
        isWindowShowing = true

        // 创建悬浮窗布局
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_float_window)
            elevation = dpToPx(12).toFloat()
        }

        // --- 标题栏 ---
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_window_header)
            setPadding(dpToPx(14), dpToPx(12), dpToPx(10), dpToPx(12))
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(context).apply {
            text = "🔮 洛克百宝箱"
            setTextColor(0xFFA29BFE.toInt())
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(titleView)

        // 最小化按钮
        val btnMinimize = createHeaderButton("−") {
            hideFloatWindow()
            restoreFloatBall()
        }
        headerLayout.addView(btnMinimize)

        // 关闭按钮
        val btnClose = createHeaderButton("✕") {
            hideFloatWindow()
            restoreFloatBall()
        }
        headerLayout.addView(btnClose)

        rootLayout.addView(headerLayout)

        // --- WebView ---
        webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                // 适配屏幕
                layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
            }

            // 启用自适应
            setInitialScale(100)

            // 根据屏幕方向通知页面
            val orientationValue = if (isLandscape) "landscape" else "portrait"
            evaluateJavascript("window.__ROCKY_ORIENTATION__ = '$orientationValue'", null)

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            setBackgroundColor(0xFF1A1B2E.toInt())

            // 从 assets 加载页面
            loadUrl("file:///android_asset/index.html")
        }
        rootLayout.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        floatWindowView = rootLayout

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 居中显示
            x = (realScreenWidth - windowWidth) / 2
            y = (realScreenHeight - windowHeight) / 2
        }
        floatWindowParams = params

        // 点击WebView区域时切换为可聚焦，允许输入
        val webViewInstance = webView
        webViewInstance?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                try {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    windowManager.updateViewLayout(floatWindowView, params)
                } catch (e: Exception) {
                    // view may have been removed
                }
            }
            false // 让WebView处理触摸事件
        }

        // 点击标题栏区域时切换回不可聚焦，避免影响游戏
        headerLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    windowLastX = event.rawX
                    windowLastY = event.rawY
                    windowInitialX = params.x
                    windowInitialY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - windowLastX
                    val dy = event.rawY - windowLastY
                    params.x = (windowInitialX + dx).toInt()
                        .coerceIn(0, realScreenWidth - windowWidth)
                    params.y = (windowInitialY + dy).toInt()
                        .coerceIn(0, realScreenHeight - windowHeight)
                    try {
                        windowManager.updateViewLayout(floatWindowView, params)
                    } catch (e: Exception) {
                        // view 可能已被移除
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(rootLayout, params)
        } catch (e: Exception) {
            e.printStackTrace()
            isWindowShowing = false
            restoreFloatBall()
        }
    }

    /**
     * 创建标题栏按钮
     */
    private fun createHeaderButton(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(0xFFB2BEC3.toInt())
            gravity = Gravity.CENTER
            val padding = dpToPx(8)
            setPadding(padding, padding, padding, padding)
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginStart = dpToPx(4)
            }
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
            background?.alpha = 80

            setOnClickListener { onClick() }

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        setTextColor(0xFFFFFFFF.toInt())
                        animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        setTextColor(0xFFB2BEC3.toInt())
                        animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }
                }
                false // 不消费事件，让 onClick 也能触发
            }
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun hideFloatWindow() {
        floatWindowView?.let {
            try {
                webView?.destroy()
                webView = null
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatWindowView = null
        }
        isWindowShowing = false
    }

    /**
     * 隐藏并移除悬浮球
     */
    private fun removeFloatBall() {
        floatBallView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatBallView = null
        }
    }

    /**
     * 销毁所有悬浮窗
     */
    fun destroyAll() {
        hideFloatWindow()
        removeFloatBall()
    }

    /**
     * dp 转 px
     */
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
