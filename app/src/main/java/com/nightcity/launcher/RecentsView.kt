package com.nightcity.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Um app da lista de recentes (ícone real opcional; sem ele, desenha o monograma). */
class RecItem(val pkg: String, val label: String, val mono: String, val ago: String, val icon: Bitmap?)

/** Cores (ARGB) vindas da paleta ativa. */
class RecColors(val bg: Int, val accent: Int, val cyan: Int, val dim: Int, val panel: Int)

val REC_STYLE_NAMES = listOf("CASCATA", "DIAL")
const val REC_STYLE_COUNT = 2

/**
 * Janela de apps recentes desenhada por cima de qualquer app (pelo NavService), ancorada na borda
 * esquerda ou direita. Dois estilos: 0 = cascata, 1 = dial (disco de telefone).
 *  - Toque num app: abre.
 *  - Segurar num app (~0,5 s): tira da lista.
 *  - Dial: arraste o anel para girar; toque no centro abre o app em foco.
 *  - Toque fora, ou 15 s parado: fecha.
 */
class RecentsView(
    ctx: Context,
    items: List<RecItem>,
    private val col: RecColors,
    private val left: Boolean,
    style: Int,
    private val onPick: (String) -> Unit,
    private val onRemove: (String) -> Unit,
    private val onClear: () -> Unit,
    private val onClose: () -> Unit,
    private val onStyle: (Int) -> Unit,
    private val onActivity: () -> Unit,
) : View(ctx) {

    private val list = items.toMutableList()
    // Compatibilidade: estilo antigo 1 (Cascata) vira 0; estilo antigo 2 (Dial) vira 1.
    private var style = style.coerceIn(0, REC_STYLE_COUNT - 1)
    private val d = resources.displayMetrics.density
    private val sgn = if (left) 1f else -1f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.FILL
    }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
    }
    private val icp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val txt = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var appear = 0f
    private var animator: ValueAnimator? = null

    private var dialRot = 0f
    private val styleBtn = RectF()
    private val clearBtn = RectF()

    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var pressed = -1
    private var longFired = false
    private var lastAng = 0f

    private val longRun = Runnable {
        if (pressed in list.indices && !moved) {
            longFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val gone = list.removeAt(pressed)
            pressed = -1
            dialRot = wrapDialRotation(dialRot)
            onRemove(gone.pkg)
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            addUpdateListener { a ->
                appear = a.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        removeCallbacks(longRun)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        styleBtn.set(w / 2f - 85 * d, 64 * d, w / 2f + 85 * d, 100 * d)
        clearBtn.set(w / 2f - 65 * d, h - 96 * d, w / 2f + 65 * d, h - 60 * d)
    }

    // ------------------------------------------------------------------ utilidades

    private fun withA(color: Int, a: Int) = ColorUtils.setAlphaComponent(color, a.coerceIn(0, 255))

    private fun baseY(r: RectF, p: Paint) = r.centerY() - (p.ascent() + p.descent()) / 2f

    private fun stagger(i: Int) = (appear * 1.4f - i * 0.06f).coerceIn(0f, 1f)

    private fun ellipsize(s: String, p: TextPaint, w: Float): String =
        TextUtils.ellipsize(s, p, max(0f, w), TextUtils.TruncateAt.END).toString()

    /** Ícone real do app, ou o monograma numa caixa de destaque. */
    private fun drawIconIn(c: Canvas, item: RecItem, r: RectF, a: Int) {
        val bmp = item.icon
        if (bmp != null) {
            icp.alpha = a
            c.drawBitmap(bmp, null, r, icp)
        } else {
            fill.color = withA(col.accent, a)
            c.drawRect(r, fill)
            txt.textAlign = Paint.Align.CENTER
            txt.color = withA(col.bg, a)
            txt.textSize = r.height() * 0.42f
            c.drawText(item.mono, r.centerX(), baseY(r, txt), txt)
            txt.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawCard(
        c: Canvas, r: RectF, item: RecItem, hl: Boolean, a: Int,
        iconRight: Boolean, innerClear: Float, twoLine: Boolean,
    ) {
        fill.color = withA(col.panel, a * 235 / 255)
        c.drawRect(r, fill)
        line.color = withA(if (hl) col.cyan else col.accent, a)
        line.strokeWidth = (if (hl) 2.5f else 1.5f) * d
        c.drawRect(r, line)

        val pad = 6 * d
        val isz = r.height() - 2 * pad
        val ix = if (iconRight) r.right - pad - isz else r.left + pad
        drawIconIn(c, item, RectF(ix, r.top + pad, ix + isz, r.bottom - pad), a)

        val tl = if (iconRight) r.left + innerClear else ix + isz + 8 * d
        val tr = if (iconRight) ix - 8 * d else r.right - innerClear
        val avail = tr - tl
        if (avail < 24 * d) return

        txt.textAlign = Paint.Align.LEFT
        if (twoLine) {
            txt.color = withA(0xFFFFFFFF.toInt(), a)
            txt.textSize = 12.5f * d
            c.drawText(ellipsize(item.label.uppercase(), txt, avail), tl, r.top + 24 * d, txt)
            txt.color = withA(col.cyan, a)
            txt.textSize = 10 * d
            c.drawText(item.ago, tl, r.top + 40 * d, txt)
        } else {
            txt.textSize = 10 * d
            val agoW = txt.measureText(item.ago)
            txt.textSize = 12.5f * d
            val lab = ellipsize(item.label.uppercase(), txt, avail - agoW - 6 * d)
            val by = r.top + r.height() * 0.46f
            txt.color = withA(0xFFFFFFFF.toInt(), a)
            c.drawText(lab, tl, by, txt)
            txt.color = withA(col.cyan, a)
            txt.textSize = 10 * d
            c.drawText(item.ago, tl + txt.measureText(lab) + 6 * d, by, txt)
        }
    }

    // ------------------------------------------------------------------ desenho

    override fun onDraw(c: Canvas) {
        val ap = min(1f, appear * 1.5f)
        // Sem overlay: mantém o fundo original sem escurecer a tela.

        if (list.isEmpty()) {
            txt.textAlign = Paint.Align.CENTER
            txt.color = withA(col.accent, 255)
            txt.textSize = 14 * d
            c.drawText("SEM APPS RECENTES", width / 2f, height / 2f, txt)
            txt.color = withA(col.dim, 255)
            txt.textSize = 10 * d
            c.drawText("OS APPS ABERTOS PELO LAUNCHER APARECEM AQUI", width / 2f, height / 2f + 22 * d, txt)
            txt.textAlign = Paint.Align.LEFT
        } else {
            when (style) {
                0 -> drawCascade(c)
                else -> drawDial(c)
            }
        }
        drawControls(c, (255 * ap).toInt())
    }

    private fun drawControls(c: Canvas, a: Int) {
        fill.color = withA(col.panel, a * 235 / 255)
        c.drawRect(styleBtn, fill)
        line.color = withA(col.accent, a)
        line.strokeWidth = 1.5f * d
        c.drawRect(styleBtn, line)
        txt.textAlign = Paint.Align.CENTER
        txt.color = withA(col.accent, a)
        txt.textSize = 12 * d
        c.drawText("< " + REC_STYLE_NAMES[style] + " >", styleBtn.centerX(), baseY(styleBtn, txt), txt)

        if (list.isNotEmpty()) {
            fill.color = withA(col.panel, a * 235 / 255)
            c.drawRect(clearBtn, fill)
            line.color = withA(col.cyan, a)
            c.drawRect(clearBtn, line)
            txt.color = withA(col.cyan, a)
            c.drawText("LIMPAR", clearBtn.centerX(), baseY(clearBtn, txt), txt)
        }
        txt.textAlign = Paint.Align.LEFT
    }

    // ---- Cascata ----

    private fun cascadeRect(i: Int, n: Int): RectF {
        val w = min(205 * d, width * 0.64f)
        val h = 54 * d
        val yFront = min(height * 0.62f, height - 130 * d - h)
        val step = if (n > 1) min(46 * d, (yFront - 110 * d) / (n - 1)) else 0f
        val x0 = if (left) 14 * d else width - 14 * d - w
        val x = x0 + sgn * i * 14 * d
        val y = yFront - i * step - 120 * d
        return RectF(x, y, x + w, y + h)
    }

    private fun drawCascade(c: Canvas) {
        val n = minOf(list.size, 5)
        for (i in n - 1 downTo 0) {
            val ap = stagger(i)
            if (ap <= 0f) continue
            val r = cascadeRect(i, n)
            c.save()
            c.translate(-sgn * (1f - ap) * (r.width() + 20 * d), 0f)
            drawCard(c, r, list[i], i == pressed, (255 * ap).toInt(), iconRight = !left, innerClear = 10 * d, twoLine = true)
            c.restore()
        }
    }

    private fun hitCascade(x: Float, y: Float): Int {
        val n = minOf(list.size, 5)
        for (i in 0 until n) if (cascadeRect(i, n).contains(x, y)) return i
        return -1
    }

    // ---- Dial ----

    /** Mantém a rotação dentro de um ciclo completo da lista, permitindo loop infinito. */
    private fun wrapDialRotation(value: Float): Float {
        val cycle = list.size.coerceAtLeast(1) * 30f
        return ((value % cycle) + cycle) % cycle
    }

    /**
     * Ângulo relativo à posição atual. O índice é calculado em ciclo, então
     * o primeiro e o último recente também são vizinhos durante a rotação.
     */
    private fun dialAngle(i: Int): Float {
        val n = list.size
        if (n == 0) return 0f
        var delta = i - dialRot / 30f
        val half = n / 2f
        while (delta > half) delta -= n
        while (delta < -half) delta += n
        return delta * 30f
    }

    private fun dialFocus(): Int {
        var focus = -1
        var best = 999f
        for (i in list.indices) {
            val a = abs(dialAngle(i))
            if (a <= 22f && a < best) {
                best = a
                focus = i
            }
        }
        return focus
    }

    private fun drawDial(c: Canvas) {
        val n = list.size
        val cx = if (left) 0f else width.toFloat()
        val cy = height * 0.55f - 120 * d
        val rr = min(width * 0.60f, 155 * d)
        val hole = 23 * d
        val hub = rr * 0.50f

        line.strokeWidth = 1.5f * d
        line.color = withA(col.dim, 170)
        c.drawCircle(cx, cy, rr, line)
        line.color = withA(col.dim, 90)
        c.drawCircle(cx, cy, rr + hole + 8 * d, line)
        c.drawCircle(cx, cy, rr - hole - 8 * d, line)

        // Cubo central.
        fill.color = withA(col.panel, 245)
        c.drawCircle(cx, cy, hub, fill)
        line.color = withA(col.accent, 255)
        line.strokeWidth = 2 * d
        c.drawCircle(cx, cy, hub, line)

        // Ponteiro (o app em foco fica alinhado com ele).
        val tipX = cx + sgn * (rr + hole + 6 * d)
        val tri = Path().apply {
            moveTo(tipX, cy)
            lineTo(tipX + sgn * 14 * d, cy - 8 * d)
            lineTo(tipX + sgn * 14 * d, cy + 8 * d)
            close()
        }
        fill.color = withA(col.cyan, 255)
        c.drawPath(tri, fill)

        val focus = dialFocus()
        for (i in 0 until n) {
            val ap = stagger(i)
            if (ap <= 0f) continue
            val a = dialAngle(i) + (1f - ap) * 50f
            if (abs(a) > 115f) continue
            val rad = Math.toRadians(a.toDouble())
            val px = cx + sgn * rr * cos(rad).toFloat()
            val py = cy + rr * sin(rad).toFloat()
            val isFocus = i == focus
            val hr = hole * (if (isFocus) 1.18f else 1f)
            val al = (255 * ap).toInt()
            fill.color = withA(col.panel, al)
            c.drawCircle(px, py, hr, fill)
            line.color = withA(if (isFocus || i == pressed) col.cyan else col.accent, al)
            line.strokeWidth = (if (isFocus) 2.5f else 1.5f) * d
            c.drawCircle(px, py, hr, line)
            drawIconIn(c, list[i], RectF(px - hr * 0.68f, py - hr * 0.68f, px + hr * 0.68f, py + hr * 0.68f), al)
        }

        // Nome do app em foco, dentro do cubo.
        if (focus >= 0) {
            val item = list[focus]
            val tx = cx + sgn * hub * 0.5f
            txt.textAlign = Paint.Align.CENTER
            txt.color = 0xFFFFFFFF.toInt()
            txt.textSize = 11 * d
            c.drawText(ellipsize(item.label.uppercase(), txt, hub * 0.9f), tx, cy, txt)
            txt.color = col.cyan
            txt.textSize = 9 * d
            c.drawText(item.ago, tx, cy + 15 * d, txt)
            txt.textAlign = Paint.Align.LEFT
        }
        if (n > 5) {
            txt.textAlign = Paint.Align.CENTER
            txt.color = withA(col.dim, 255)
            txt.textSize = 9 * d
            c.drawText("ARRASTE O ANEL", cx + sgn * rr * 0.55f, cy + rr + hole + 28 * d, txt)
            txt.textAlign = Paint.Align.LEFT
        }
    }

    private fun hitDial(x: Float, y: Float): Int {
        val cx = if (left) 0f else width.toFloat()
        val cy = height * 0.55f - 120 * d
        val rr = min(width * 0.60f, 155 * d)
        val hole = 23 * d
        for (i in list.indices) {
            val a = dialAngle(i)
            if (abs(a) > 100f) continue
            val rad = Math.toRadians(a.toDouble())
            val px = cx + sgn * rr * cos(rad).toFloat()
            val py = cy + rr * sin(rad).toFloat()
            if (hypot(x - px, y - py) <= hole * 1.2f) return i
        }
        return -1
    }

    private fun touchAngle(x: Float, y: Float): Float {
        val cx = if (left) 0f else width.toFloat()
        val cy = height * 0.55f - 120 * d
        return Math.toDegrees(atan2((y - cy).toDouble(), ((x - cx) * sgn).toDouble())).toFloat()
    }

    // ------------------------------------------------------------------ toque

    private fun hit(x: Float, y: Float): Int = when (style) {
        0 -> hitCascade(x, y)
        else -> hitDial(x, y)
    }

    private fun tap(x: Float, y: Float) {
        if (styleBtn.contains(x, y)) {
            style = (style + 1) % REC_STYLE_COUNT
            onStyle(style)
            invalidate()
            return
        }
        if (list.isNotEmpty() && clearBtn.contains(x, y)) {
            list.clear()
            onClear()
            invalidate()
            return
        }
        val i = hit(x, y)
        if (i >= 0) {
            onPick(list[i].pkg)
            return
        }
        if (style == 1 && list.isNotEmpty()) {
            val cx = if (left) 0f else width.toFloat()
            val cy = height * 0.55f - 120 * d
            val hub = min(width * 0.60f, 155 * d) * 0.50f
            val f = dialFocus()
            if (f >= 0 && hypot(x - cx, y - cy) <= hub) {
                onPick(list[f].pkg)
                return
            }
        }
        onClose()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onActivity()
                downX = e.x
                downY = e.y
                moved = false
                longFired = false
                pressed = hit(e.x, e.y)
                lastAng = touchAngle(e.x, e.y)
                removeCallbacks(longRun)
                if (pressed >= 0) postDelayed(longRun, 550)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.x - downX, e.y - downY) > 10 * d) {
                    moved = true
                    removeCallbacks(longRun)
                }
                if (moved && style == 1) {
                    val a = touchAngle(e.x, e.y)
                    var dl = a - lastAng
                    while (dl > 180f) dl -= 360f
                    while (dl < -180f) dl += 360f
                    lastAng = a
                    dialRot = wrapDialRotation(dialRot - dl)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRun)
                when {
                    longFired -> Unit
                    moved -> if (style == 1) {
                        dialRot = wrapDialRotation((dialRot / 30f).roundToInt() * 30f)
                    }
                    else -> tap(e.x, e.y)
                }
                pressed = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longRun)
                pressed = -1
                invalidate()
            }
        }
        return true
    }
}