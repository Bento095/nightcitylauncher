package com.nightcity.launcher

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import androidx.compose.ui.graphics.toArgb

fun navServiceEnabled(ctx: Context): Boolean {
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val me = ComponentName(ctx, NavService::class.java).flattenToString()
    return s.split(':').any { it.equals(me, ignoreCase = true) }
}

fun safeStart(ctx: Context, i: Intent) {
    try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: Exception) { }
}

/**
 * Faixa invisível numa borda da tela: deslizar para dentro mostra os botões
 * Voltar / Início / Recentes por cima de qualquer app. Some sozinho em ~3 s.
 */
class NavService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val h = Handler(Looper.getMainLooper())
    private var trigger: View? = null
    private var bar: View? = null
    private val hideRun = Runnable { hideBar() }
    private val keys = setOf("navPos", "palKey", "guide", "edgeInset")
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key in keys) h.post { install() }
    }

    private var sx = 0f
    private var sy = 0f
    private var armed = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(listener)
        install()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }

    override fun onInterrupt() { }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        try { prefs.sp.unregisterOnSharedPreferenceChangeListener(listener) } catch (e: Exception) { }
        remove()
    }

    private fun remove() {
        h.removeCallbacks(hideRun)
        bar?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        bar = null
        trigger?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        trigger = null
    }

    private val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun install() {
        remove()
        val nc = paletteByKey(prefs.palKey)
        val pos = prefs.navPos
        val inset = dp(prefs.edgeInset)
        val v = EdgeView(this, nc.y.toArgb(), prefs.guide, pos)
        v.setOnTouchListener { _, e -> onTouch(e) }
        val w = if (pos == "b") dp(220) else dp(26)
        val hh = if (pos == "b") dp(28) else dp(220)
        val lp = WindowManager.LayoutParams(
            w, hh, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT,
        )
        when (pos) {
            "l" -> { lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL; lp.x = inset }
            "r" -> { lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL; lp.x = inset }
            else -> { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.y = inset }
        }
        try {
            wm.addView(v, lp)
            trigger = v
        } catch (e: Exception) { }
    }

    private fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.rawX
                sy = e.rawY
                armed = true
            }
            MotionEvent.ACTION_MOVE -> if (armed) {
                val d = when (prefs.navPos) {
                    "l" -> e.rawX - sx
                    "r" -> sx - e.rawX
                    else -> sy - e.rawY
                }
                if (d > dp(28)) {
                    armed = false
                    showBar()
                }
            }
            MotionEvent.ACTION_UP -> if (armed) {
                armed = false
                if (bar != null) hideBar() else showBar()
            }
            MotionEvent.ACTION_CANCEL -> armed = false
        }
        return true
    }

    private fun keepBar() {
        h.removeCallbacks(hideRun)
        h.postDelayed(hideRun, 3200)
    }

    private fun showBar() {
        if (bar != null) {
            keepBar()
            return
        }
        val nc = paletteByKey(prefs.palKey)
        val pos = prefs.navPos
        val inset = dp(prefs.edgeInset)
        val ll = LinearLayout(this).apply {
            orientation = if (pos == "b") LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                setColor(nc.bg.copy(alpha = 0.92f).toArgb())
                setStroke(dp(1), nc.y.toArgb())
            }
        }
        for (k in 0..2) {
            val b = NavBtn(this, k, nc.y.toArgb(), nc.bg.toArgb())
            b.layoutParams = LinearLayout.LayoutParams(dp(62), dp(44)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            b.setOnClickListener {
                when (k) {
                    0 -> performGlobalAction(GLOBAL_ACTION_BACK)
                    1 -> performGlobalAction(GLOBAL_ACTION_HOME)
                    else -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
                keepBar()
            }
            ll.addView(b)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
        when (pos) {
            "l" -> { lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL; lp.x = inset + dp(12) }
            "r" -> { lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL; lp.x = inset + dp(12) }
            else -> { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.y = inset + dp(40) }
        }
        try {
            wm.addView(ll, lp)
        } catch (e: Exception) {
            return
        }
        bar = ll
        // entrada com glitch: desliza da borda tremendo
        val off = dp(90).toFloat()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            addUpdateListener { a ->
                val t = a.animatedFraction
                val slide = (1f - t) * off
                val jit = if (t < 0.6f) (if (((t * 12).toInt() % 2) == 0) 7f else -6f) * (1f - t) else 0f
                when (pos) {
                    "b" -> { ll.translationY = slide; ll.translationX = jit }
                    "l" -> { ll.translationX = -slide; ll.translationY = jit }
                    else -> { ll.translationX = slide; ll.translationY = jit }
                }
                ll.alpha = if (t < 0.15f) 0.4f else 1f
            }
            start()
        }
        keepBar()
    }

    private fun hideBar() {
        h.removeCallbacks(hideRun)
        val ll = bar ?: return
        bar = null
        ll.animate().alpha(0f).setDuration(180).withEndAction {
            try { wm.removeView(ll) } catch (e: Exception) { }
        }.start()
    }
}

/** Linha-guia fina na borda (opcional). */
private class EdgeView(ctx: Context, col: Int, val guide: Boolean, val pos: String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col
        alpha = 200
    }

    override fun onDraw(canvas: Canvas) {
        if (!guide) return
        val d = resources.displayMetrics.density
        val t = 3 * d
        val len = 92 * d
        val w = width.toFloat()
        val h = height.toFloat()
        when (pos) {
            "l" -> canvas.drawRect(0f, h / 2 - len * 0.4f, t, h / 2 + len * 0.4f, p)
            "r" -> canvas.drawRect(w - t, h / 2 - len * 0.4f, w, h / 2 + len * 0.4f, p)
            else -> canvas.drawRect(w / 2 - len / 2, h - t - 4 * d, w / 2 + len / 2, h - 4 * d, p)
        }
    }
}

/** Botão do overlay: 0 = voltar, 1 = início, 2 = recentes. */
private class NavBtn(ctx: Context, val kind: Int, val fg: Int, val bgc: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        p.style = Paint.Style.FILL
        p.color = if (isPressed) fg else bgc
        canvas.drawRect(0f, 0f, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = fg
        canvas.drawRect(d / 2, d / 2, w - d / 2, h - d / 2, p)
        p.strokeWidth = 2 * d
        p.color = if (isPressed) bgc else fg
        val cx = w / 2
        val cy = h / 2
        val r = 7 * d
        when (kind) {
            0 -> {
                val path = Path()
                path.moveTo(cx + r * 0.6f, cy - r)
                path.lineTo(cx - r * 0.9f, cy)
                path.lineTo(cx + r * 0.6f, cy + r)
                path.close()
                canvas.drawPath(path, p)
            }
            1 -> canvas.drawCircle(cx, cy, r, p)
            else -> canvas.drawRect(cx - r, cy - r, cx + r, cy + r, p)
        }
    }
}
