package com.nightcity.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val TAG = "NcLockWp"

// Mude este valor quando alterar o desenho, para forçar a regravação do wallpaper.
private const val DESIGN_VERSION = "v3"

/**
 * Gera o wallpaper do tema e aplica conforme [target]:
 * "lock" = só bloqueio, "both" = bloqueio + tela inicial, "off" = não mexe.
 */
suspend fun applyNcLockWallpaper(context: Context, palette: NcPalette, target: String): Boolean = withContext(Dispatchers.IO) {
    val appCtx = context.applicationContext
    try {
        if (target == "off") return@withContext true
        val wm = WallpaperManager.getInstance(appCtx)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return@withContext false
        if (!wm.isWallpaperSupported) {
            Log.w(TAG, "isWallpaperSupported = false"); return@withContext false
        }
        if (!wm.isSetWallpaperAllowed) {
            Log.w(TAG, "isSetWallpaperAllowed = false"); return@withContext false
        }

        // Só refaz se a paleta (ou a versão do desenho) mudou.
        val sig = DESIGN_VERSION + "|" + target + "|" + listOf(palette.bg, palette.bg2, palette.y, palette.c, palette.r)
            .joinToString(",") { it.toArgb().toString() }
        val sp = appCtx.getSharedPreferences("nc_lockwp", Context.MODE_PRIVATE)
        if (sp.getString("sig", null) == sig) return@withContext true

        // Usa o tamanho real da tela (o desiredMinimum pode ser largo demais na tela inicial e cortar o desenho).
        val dm = android.content.res.Resources.getSystem().displayMetrics
        val w = maxOf(720, dm.widthPixels.takeIf { it > 0 } ?: 1080)
        val h = maxOf(1280, dm.heightPixels.takeIf { it > 0 } ?: 2400)
        val scale = min(1f, 1440f / w)
        val width = (w * scale).toInt().coerceAtLeast(720)
        val height = (h * scale).toInt().coerceAtLeast(1280)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawNcWallpaper(Canvas(bitmap), width, height, palette)

        val flags = if (target == "both") WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
        else WallpaperManager.FLAG_LOCK
        val id = wm.setBitmap(bitmap, null, true, flags)
        bitmap.recycle()
        if (id > 0) {
            sp.edit().putString("sig", sig).apply()
            true
        } else {
            Log.w(TAG, "setBitmap(flags=$flags) retornou $id")
            false
        }
    } catch (e: Throwable) {
        Log.e(TAG, "Falha ao aplicar wallpaper da tela de bloqueio", e)
        false
    }
}

/** Borda do painel amarelo (x, y normalizados 0..1), com degraus chanfrados como na referência. */
private val EDGE = arrayOf(
    0.650f to 0.000f, 0.650f to 0.105f, 0.590f to 0.145f, 0.590f to 0.190f,
    0.610f to 0.205f, 0.610f to 0.300f, 0.640f to 0.325f, 0.640f to 0.405f,
    0.600f to 0.427f, 0.600f to 0.575f, 0.628f to 0.595f, 0.628f to 0.618f,
    0.575f to 0.655f, 0.575f to 0.715f, 0.615f to 0.745f, 0.615f to 0.805f,
    0.612f to 1.000f,
)

/** Quais segmentos da borda recebem a linha neon (a referência tem trechos sem linha). */
private val CYAN_ON = booleanArrayOf(
    false, false, false, true, true, true, true, false,
    false, true, true, true, true, true, false, true,
)

private fun drawNcWallpaper(canvas: Canvas, w: Int, h: Int, pal: NcPalette) {
    val W = w.toFloat()
    val H = h.toFloat()
    val bg = pal.bg.toArgb()
    val yel = pal.y.toArgb()
    val cold = pal.c.toArgb()
    val base = ColorUtils.blendARGB(bg, Color.BLACK, 0.60f)
    val band = ColorUtils.blendARGB(bg, Color.BLACK, 0.45f)
    val ink = ColorUtils.blendARGB(bg, Color.BLACK, 0.70f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun edgePath(dx: Float, from: Int = 0, to: Int = EDGE.size - 1): Path = Path().apply {
        for (i in from..to) {
            val (x, y) = EDGE[i]
            if (i == from) moveTo((x + dx) * W, y * H) else lineTo((x + dx) * W, y * H)
        }
    }

    // 1) Fundo escuro
    canvas.drawColor(base)

    // 2) Brilho ciano suave sangrando para o lado escuro
    paint.shader = LinearGradient(
        W * 0.40f, 0f, W * 0.62f, 0f,
        ColorUtils.setAlphaComponent(cold, 0), ColorUtils.setAlphaComponent(cold, 45), Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, W * 0.65f, H, paint)
    paint.shader = null

    // 3) Streaks horizontais (glitch) que somem para a esquerda
    fun streak(y: Float, x0: Float, x1: Float, a: Int) {
        paint.shader = LinearGradient(
            x0 * W, 0f, x1 * W, 0f,
            ColorUtils.setAlphaComponent(cold, 0), ColorUtils.setAlphaComponent(cold, a), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(x0 * W, y * H, x1 * W, y * H + max(2f, W * 0.003f), paint)
        paint.shader = null
    }
    streak(0.055f, 0.36f, 0.55f, 200); streak(0.138f, 0.00f, 0.08f, 150)
    streak(0.165f, 0.00f, 0.26f, 160); streak(0.345f, 0.37f, 0.57f, 200)
    streak(0.391f, 0.40f, 0.59f, 180); streak(0.620f, 0.50f, 0.60f, 170)
    streak(0.757f, 0.38f, 0.57f, 200); streak(0.803f, 0.00f, 0.13f, 140)
    streak(0.825f, 0.00f, 0.10f, 110); streak(0.930f, 0.40f, 0.585f, 200)

    // 4) Faixa escura ao longo da borda
    val bandPath = Path().apply {
        for (i in EDGE.indices) {
            val (x, y) = EDGE[i]
            if (i == 0) moveTo((x - 0.034f) * W, y * H) else lineTo((x - 0.034f) * W, y * H)
        }
        for (i in EDGE.indices.reversed()) lineTo(EDGE[i].first * W, EDGE[i].second * H)
        close()
    }
    paint.color = band
    canvas.drawPath(bandPath, paint)

    // 5) Linha neon ciano (brilho + núcleo) na borda esquerda da faixa
    val neon = Path().apply {
        for (i in 0 until EDGE.size - 1) if (CYAN_ON[i]) {
            moveTo((EDGE[i].first - 0.034f) * W, EDGE[i].second * H)
            lineTo((EDGE[i + 1].first - 0.034f) * W, EDGE[i + 1].second * H)
        }
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.SQUARE
    }
    stroke.color = ColorUtils.setAlphaComponent(cold, 150)
    stroke.strokeWidth = W * 0.014f
    stroke.maskFilter = BlurMaskFilter(W * 0.014f, BlurMaskFilter.Blur.NORMAL)
    canvas.drawPath(neon, stroke)
    stroke.maskFilter = null
    stroke.color = cold
    stroke.strokeWidth = max(3f, W * 0.0042f)
    canvas.drawPath(neon, stroke)

    // 6) Filete amarelo fino dentro da faixa, só na parte de cima
    stroke.color = ColorUtils.setAlphaComponent(yel, 210)
    stroke.strokeWidth = max(2f, W * 0.0030f)
    canvas.drawPath(edgePath(-0.014f, 0, 3), stroke)

    // 7) Painel amarelo
    val yellowPath = Path().apply {
        for (i in EDGE.indices) {
            val (x, y) = EDGE[i]
            if (i == 0) moveTo(x * W, y * H) else lineTo(x * W, y * H)
        }
        lineTo(W, H); lineTo(W, 0f); close()
    }
    paint.color = yel
    canvas.drawPath(yellowPath, paint)

    // 8) Textura suave (manchas) e linhas finas no painel
    canvas.save()
    canvas.clipPath(yellowPath)
    val rnd = Random(11)
    repeat(40) {
        val cx = (0.62f + rnd.nextFloat() * 0.45f) * W
        val cy = rnd.nextFloat() * H
        val r = W * (0.05f + rnd.nextFloat() * 0.18f)
        paint.color = if (rnd.nextBoolean()) Color.argb(14, 255, 255, 255) else Color.argb(14, 0, 0, 0)
        canvas.drawCircle(cx, cy, r, paint)
    }
    paint.color = ColorUtils.setAlphaComponent(ink, 170)
    canvas.drawRect(0.675f * W, 0.040f * H, 0.84f * W, 0.040f * H + max(2f, W * 0.0025f), paint)
    canvas.drawRect(0.92f * W, 0.800f * H, W, 0.800f * H + max(2f, W * 0.0025f), paint)

    // Hexágonos parciais no canto direito (contorno)
    stroke.color = ColorUtils.setAlphaComponent(ink, 150)
    stroke.strokeWidth = max(2f, W * 0.0022f)
    stroke.strokeJoin = Paint.Join.MITER
    val hex1 = Path().apply {
        moveTo(W, 0.340f * H); lineTo(0.948f * W, 0.368f * H)
        lineTo(0.948f * W, 0.535f * H); lineTo(W, 0.565f * H)
    }
    val hex2 = Path().apply {
        moveTo(W, 0.885f * H); lineTo(0.962f * W, 0.908f * H); lineTo(0.962f * W, H)
    }
    canvas.drawPath(hex1, stroke)
    canvas.drawPath(hex2, stroke)
    canvas.restore()

    // 9) Quadradinhos escuros (marcadores) sobre o amarelo
    paint.color = ColorUtils.setAlphaComponent(ink, 225)
    val sq = W * 0.011f
    fun ticks(x: Float, y0: Float, n: Int, gap: Float) {
        for (i in 0 until n) {
            val yy = (y0 + i * gap) * H
            canvas.drawRect(x * W, yy, x * W + sq, yy + sq, paint)
        }
    }
    ticks(0.638f, 0.173f, 3, 0.017f)
    ticks(0.650f, 0.517f, 3, 0.020f)
    ticks(0.595f, 0.664f, 3, 0.013f)

    // 10) Bloco amarelo com listras de alerta saindo da borda (como na referência)
    paint.color = yel
    canvas.drawRect(0.560f * W, 0.445f * H, 0.600f * W, 0.575f * H, paint)
    fun hazard(x0: Float, y0: Float, x1: Float, y1: Float) {
        canvas.save()
        canvas.clipRect(x0 * W, y0 * H, x1 * W, y1 * H)
        paint.color = ColorUtils.setAlphaComponent(ink, 235)
        val sw = (x1 - x0) * W
        val step = sw * 0.95f
        val th = step * 0.45f
        var yy = y0 * H - sw
        while (yy < y1 * H) {
            val p = Path().apply {
                moveTo(x0 * W, yy + sw); lineTo(x1 * W, yy)
                lineTo(x1 * W, yy + th); lineTo(x0 * W, yy + th + sw); close()
            }
            canvas.drawPath(p, paint)
            yy += step
        }
        canvas.restore()
    }
    hazard(0.560f, 0.470f, 0.600f, 0.570f)
    hazard(0.622f, 0.935f, 0.650f, 1.000f)

    // 11) Marcas amarelas pequenas do lado escuro (perto do topo)
    paint.color = ColorUtils.setAlphaComponent(yel, 220)
    for (i in 0..5) {
        val yy = (0.117f + i * 0.0075f) * H
        canvas.drawRect(0.540f * W, yy, 0.540f * W + W * 0.007f, yy + H * 0.004f, paint)
    }
}