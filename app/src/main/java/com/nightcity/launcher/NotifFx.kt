package com.nightcity.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun argb(c: Int, a: Float): Int =
    (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

/**
 * Efeito animado de notificação: "glitch", ondas de CRT ou chuva estilo Matrix.
 * Tudo é desenhado por cima (não distorce o conteúdo de outros apps).
 * opaque = true pinta o fundo de preto (tela OLED: pixels pretos ficam apagados).
 */
class FxView(
    ctx: Context,
    private val mode: String,
    private val pal: NcPalette,
    private val secs: Int,
    private val label: String,
    private val title: String,
    private val opaque: Boolean,
    private val onDone: () -> Unit,
) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private var start = 0L
    private var done = false
    private var colY = FloatArray(0)
    private var colSpeed = FloatArray(0)
    private var colLen = IntArray(0)
    private val glyphs = "01ABCDEF<>/\\|=+*#%\$アイウエオカキクケコ".map { it.toString() }

    init {
        txt.typeface = Typeface.MONOSPACE
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val n = (w / (13 * d)).toInt().coerceAtLeast(1)
        val r = Random(7)
        colY = FloatArray(n) { r.nextFloat() * h }
        colSpeed = FloatArray(n) { 90f + r.nextFloat() * 220f }
        colLen = IntArray(n) { 8 + r.nextInt(14) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        val dur = secs.toFloat()
        if (t >= dur) {
            if (!done) {
                done = true
                post { onDone() }
            }
            return
        }
        // entra e sai suavemente
        val env = min(1f, t / 0.35f) * min(1f, (dur - t) / 0.6f)
        if (opaque) c.drawColor(Color.BLACK)
        when (mode) {
            "crt" -> drawCrt(c, t, env)
            "matrix" -> drawMatrix(c, t, env)
            else -> drawGlitch(c, t, env)
        }
        drawLabel(c, t, env)
        postInvalidateOnAnimation()
    }

    // ondas de CRT: linhas de varredura que ondulam e uma faixa brilhante descendo
    private fun drawCrt(c: Canvas, t: Float, env: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val base = pal.y.toArgb()
        p.style = Paint.Style.FILL
        val step = 3f * d
        var y = 0f
        while (y < h) {
            val off = sin(y / (40f * d) + t * 3f) * 5f * d * env
            val a = (0.10f + 0.12f * (0.5f + 0.5f * sin(y / (7f * d) - t * 9f))) * env
            p.color = argb(base, a)
            c.drawRect(off, y, w + off, y + 1.3f * d, p)
            y += step
        }
        val bandH = 160f * d
        val by = ((t * 0.5f) % 1.3f - 0.15f) * (h + bandH) - bandH
        p.shader = LinearGradient(
            0f, by, 0f, by + bandH,
            intArrayOf(argb(base, 0f), argb(base, 0.28f * env), argb(base, 0f)),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, by, w, by + bandH, p)
        p.shader = null
    }

    // glitch suave: blocos horizontais em cores da paleta que tremem e somem
    private fun drawGlitch(c: Canvas, t: Float, env: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val burst = if (t < 0.9f) 1f else 0.35f + 0.25f * max(0f, sin(t * 11f))
        val n = (5 + 11 * burst * env).toInt()
        p.style = Paint.Style.FILL
        val tick = (t * 11f).toInt()
        for (i in 0 until n) {
            val r = Random((tick * 131 + i * 977).toLong())
            val y = r.nextFloat() * h
            val bh = (2f + r.nextFloat() * 16f) * d
            val x = r.nextFloat() * w * 0.6f
            val bw = (0.15f + r.nextFloat() * 0.7f) * w
            val col = when (r.nextInt(3)) {
                0 -> pal.y
                1 -> pal.c
                else -> pal.r
            }.toArgb()
            p.color = argb(col, (0.10f + 0.26f * r.nextFloat()) * env)
            c.drawRect(x, y, x + bw, y + bh, p)
        }
        // linhas finas deslocadas, lembrando a separação de canais de cor
        for (k in 0 until 3) {
            val r = Random((tick * 53 + k * 331).toLong())
            val y = r.nextFloat() * h
            val sh = (r.nextFloat() - 0.5f) * 14f * d
            p.color = argb(pal.c.toArgb(), 0.30f * env)
            c.drawRect(sh, y, w + sh, y + 1.2f * d, p)
            p.color = argb(pal.r.toArgb(), 0.30f * env)
            c.drawRect(-sh, y + 2f * d, w - sh, y + 3.2f * d, p)
        }
    }

    // chuva de caracteres estilo Matrix
    private fun drawMatrix(c: Canvas, t: Float, env: Float) {
        val cw = 13f * d
        val chh = 15f * d
        val h = height.toFloat()
        txt.textSize = 13f * d
        txt.textAlign = Paint.Align.LEFT
        for (i in colY.indices) {
            val len = colLen[i]
            val span = h + len * chh
            val head = (colY[i] + t * colSpeed[i] * d) % span
            val x = i * cw
            for (j in 0 until len) {
                val y = head - j * chh
                if (y < -chh || y > h + chh) continue
                val idx = ((i * 31 + j * 17 + (t * 9f).toInt() * (j + 2)) and 0x7FFFFFFF) % glyphs.size
                val a = (if (j == 0) 1f else (1f - j.toFloat() / len)) * env
                txt.color = if (j == 0) argb(pal.c.toArgb(), a) else argb(pal.y.toArgb(), a * 0.85f)
                c.drawText(glyphs[idx], x, y, txt)
            }
        }
    }

    // texto "digitado": app e título da notificação
    private fun drawLabel(c: Canvas, t: Float, env: Float) {
        if (label.isEmpty() && title.isEmpty()) return
        txt.textSize = 15f * d
        txt.textAlign = Paint.Align.CENTER
        val l1 = "> " + label.uppercase()
        val l2 = title.take(34)
        val n1 = min(l1.length, (t * 45f).toInt())
        val n2 = min(l2.length, (max(0f, t - 0.3f) * 45f).toInt())
        val cx = width / 2f
        val y0 = if (opaque) height * 0.46f else height * 0.12f
        val shift = if (((t * 14f).toInt() % 9) == 0) 3f * d else 0f
        if (!opaque) {
            val bw = max(txt.measureText(l1), txt.measureText(l2)) + 28f * d
            p.style = Paint.Style.FILL
            p.color = argb(Color.BLACK, 0.6f * env)
            c.drawRect(cx - bw / 2f, y0 - 22f * d, cx + bw / 2f, y0 + 34f * d, p)
        }
        txt.color = argb(pal.c.toArgb(), 0.55f * env)
        c.drawText(l1.substring(0, n1), cx + shift, y0, txt)
        txt.color = argb(pal.y.toArgb(), env)
        c.drawText(l1.substring(0, n1), cx, y0, txt)
        if (l2.isNotEmpty()) {
            txt.color = argb(pal.c.toArgb(), 0.55f * env)
            c.drawText(l2.substring(0, n2), cx - shift, y0 + 24f * d, txt)
            txt.color = argb(Color.WHITE, 0.9f * env)
            c.drawText(l2.substring(0, n2), cx, y0 + 24f * d, txt)
        }
    }
}

/**
 * Tela cheia preta (boa para OLED) que acende a tela apagada, mostra o efeito por alguns
 * segundos e fecha. O serviço de acessibilidade apaga a tela de novo depois.
 */
class NotifFxActivity : Activity() {
    private var touched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = Prefs(this)
        val v = FxView(
            this,
            intent.getStringExtra("mode") ?: prefs.fxMode,
            prefs.currentPalette(),
            intent.getIntExtra("secs", prefs.fxSecs),
            intent.getStringExtra("label").orEmpty(),
            intent.getStringExtra("title").orEmpty(),
            true,
        ) { finishFx() }
        val key = intent.getStringExtra("key")
        v.setOnTouchListener { view, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                touched = true
                // Tocar na faixa onde o app e o título aparecem abre a notificação.
                val onLabel = e.y > view.height * 0.38f && e.y < view.height * 0.58f
                finishFx()
                if (onLabel && key != null) NotifStore.openByKey(applicationContext, key)
            }
            true
        }
        setContentView(v)
        WindowCompat.getInsetsController(window, v).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun finishFx() {
        if (isFinishing) return
        finish()
        // se ninguém tocou na tela, ela volta a apagar
        if (!touched) NavState.service?.lockScreen()
    }
}