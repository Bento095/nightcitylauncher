package com.nightcity.launcher

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Estilos da animação de progressão; o toque na tela cicla entre eles. */
private val STYLE_NAMES = listOf("CASSETE", "CD", "FUTURISTA")
const val PLAYER_STYLE_COUNT = 3

/**
 * Player em tela cheia, com dois formatos que se alternam pelo botão "↻":
 * - Horizontal: desenhado de lado (girado 90° para a esquerda) — vire o celular para a esquerda.
 * - Vertical: tela normal, com a animação no topo.
 * Toque na tela troca a animação (cassete → CD → futurista); a barra inferior busca na faixa
 * (quando o app de música permite). Sair: Voltar, "✕" ou deslizar para baixo.
 */
@Composable
fun PlayerScreen(
    media: MediaState,
    style: Int,
    landscape: Boolean,
    onStyle: (Int) -> Unit,
    onToggleOrientation: () -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(media.hasSession) { if (!media.hasSession) onClose() }
    Layout(
        content = { PlayerContent(media, style, landscape, onStyle, onToggleOrientation, onClose) },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) { measurables, c ->
        val w = c.maxWidth
        val h = c.maxHeight
        if (landscape) {
            // Conteúdo medido com largura e altura trocadas e girado 90° em torno do centro.
            val p = measurables.first().measure(Constraints.fixed(h, w))
            layout(w, h) {
                p.placeWithLayer((w - h) / 2, (h - w) / 2) { rotationZ = 90f }
            }
        } else {
            val p = measurables.first().measure(Constraints.fixed(w, h))
            layout(w, h) { p.place(0, 0) }
        }
    }
}

@Composable
private fun PlayerContent(
    media: MediaState,
    style: Int,
    landscape: Boolean,
    onStyle: (Int) -> Unit,
    onToggleOrientation: () -> Unit,
    onClose: () -> Unit,
) {
    val nc = LocalNc.current
    val styleNow by rememberUpdatedState(style)
    val onStyleNow by rememberUpdatedState(onStyle)
    val onCloseNow by rememberUpdatedState(onClose)
    val onToggleNow by rememberUpdatedState(onToggleOrientation)

    // Relógio, fase de giro e glitch vivem em estados lidos só na fase de desenho (evita recompor a 30 fps).
    val nowS = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val spinS = remember { mutableFloatStateOf(0f) }
    val dragS = remember { mutableStateOf<Float?>(null) }
    // Mesmo glitch do relógio: a cada 5 s, 3 pulsos de 70 ms alternando esquerda/direita (+1 / -1).
    val glS = remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var last = SystemClock.elapsedRealtime()
        while (true) {
            val t = SystemClock.elapsedRealtime()
            nowS.longValue = t
            if (media.playing) spinS.floatValue += (t - last).toFloat()
            last = t
            // ~30 quadros por segundo tocando; bem mais devagar parado.
            delay(if (media.playing) 33L else 250L)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            repeat(3) { i ->
                glS.intValue = if (i % 2 == 0) 1 else -1
                delay(70)
            }
            glS.intValue = 0
        }
    }

    fun fracNow(): Float {
        dragS.value?.let { return it }
        val d = media.durationMs
        return if (d > 0) (media.position(nowS.longValue).toFloat() / d).coerceIn(0f, 1f) else 0f
    }

    val posSec by remember {
        derivedStateOf {
            val f = dragS.value
            (if (f != null) (f * media.durationMs).toLong() else media.position(nowS.longValue)) / 1000
        }
    }
    val pct by remember { derivedStateOf { (fracNow() * 100).toInt() } }
    val durSec = media.durationMs / 1000

    // ---- Blocos reaproveitados nos dois formatos ----
    val topBar: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { Tx("> " + media.appLabel.uppercase(), nc.c, 12, mono = true) }
            Box(Modifier.clickable { onToggleNow() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Tx("↻", nc.c, 20, true)
            }
            Box(Modifier.clickable { onCloseNow() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Tx("✕", nc.y, 18, true)
            }
        }
    }

    val animBox: @Composable (Modifier) -> Unit = { m ->
        Box(m, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawAnimation(styleNow, fracNow(), spinS.floatValue, nc, glS.intValue)
            }
            if (styleNow == 2) {
                Box(Modifier.fillMaxSize().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
                    val g = glS.intValue
                    if (g != 0) {
                        Tx("$pct%", nc.r, 40, true, mono = true, modifier = Modifier.offset(x = (-2 * g).dp))
                        Tx("$pct%", nc.c, 40, true, mono = true, modifier = Modifier.offset(x = (2 * g).dp))
                    }
                    Tx("$pct%", nc.y, 40, true, mono = true)
                }
            }
        }
    }

    val infoBlock: @Composable () -> Unit = {
        Column {
            Tx(media.title.ifBlank { "SEM TÍTULO" }.uppercase(), nc.y, 24, true, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            if (media.artist.isNotBlank()) Tx(media.artist, Color.White, 14, mono = true, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Tx("ANIMAÇÃO: ${STYLE_NAMES[styleNow]}  ·  TOQUE PARA TROCAR", nc.dim, 9, mono = true)
        }
    }

    val controlsBlock: @Composable () -> Unit = {
        Column {
            SeekBar(
                canSeek = media.canSeek,
                frac = { fracNow() },
                onDragFrac = { dragS.value = it },
                onSeek = { f -> media.seekTo((f * media.durationMs).toLong()) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Tx(playerFmt(posSec), nc.y, 12, mono = true)
                Tx(if (!media.canSeek) "SEM BUSCA  " + playerFmt(durSec) else playerFmt(durSec), nc.dim, 12, mono = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                PlayerBtn("◁◁") { media.prev() }
                PlayerBtn(if (media.playing) "❚❚" else "▶") { media.playPause() }
                PlayerBtn("▷▷") { media.next() }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(nc.bg)
            .pointerInput(Unit) { detectTapGestures { onStyleNow((styleNow + 1) % PLAYER_STYLE_COUNT) } }
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onVerticalDrag = { _, d -> dy += d },
                    onDragEnd = { if (dy > 140f) onCloseNow() },
                )
            },
    ) {
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 16.dp)) {
                animBox(Modifier.weight(1.15f).fillMaxHeight())
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    topBar()
                    infoBlock()
                    controlsBlock()
                }
            }
        } else {
            // Vertical: animação no topo, informações e controles embaixo.
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp)) {
                topBar()
                Spacer(Modifier.height(8.dp))
                animBox(Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(12.dp))
                infoBlock()
                Spacer(Modifier.height(16.dp))
                controlsBlock()
            }
        }
    }
}

private fun playerFmt(sec: Long): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

@Composable
private fun PlayerBtn(t: String, onClick: () -> Unit) {
    val nc = LocalNc.current
    Box(
        Modifier
            .border(1.5.dp, nc.y)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(t, nc.y, 16, true, mono = true) }
}

/** Barra de busca: toque salta para o ponto; arrastar mostra o alvo e aplica ao soltar. */
@Composable
private fun SeekBar(
    canSeek: Boolean,
    frac: () -> Float,
    onDragFrac: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
) {
    val nc = LocalNc.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            // Sempre consome o toque aqui, para não trocar a animação sem querer.
            .pointerInput(canSeek) {
                detectTapGestures { off -> if (canSeek) onSeek((off.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(canSeek) {
                if (canSeek) {
                    var last = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { off ->
                            last = (off.x / size.width).coerceIn(0f, 1f)
                            onDragFrac(last)
                        },
                        onHorizontalDrag = { ch, _ ->
                            ch.consume()
                            last = (ch.position.x / size.width).coerceIn(0f, 1f)
                            onDragFrac(last)
                        },
                        onDragEnd = {
                            onSeek(last)
                            onDragFrac(null)
                        },
                        onDragCancel = { onDragFrac(null) },
                    )
                }
            },
    ) {
        val p = frac()
        val cy = size.height / 2f
        val th = 4.dp.toPx()
        drawRect(nc.dim.copy(alpha = 0.45f), Offset(0f, cy - th / 2f), Size(size.width, th))
        drawRect(nc.y, Offset(0f, cy - th / 2f), Size(size.width * p, th))
        for (i in 0..10) {
            val x = size.width * i / 10f
            drawLine(nc.dim.copy(alpha = 0.7f), Offset(x, cy + th * 1.4f), Offset(x, cy + th * 2.4f), 1.dp.toPx())
        }
        // Marcador (losango).
        val tx = size.width * p
        val r = 7.dp.toPx()
        val d = Path().apply {
            moveTo(tx, cy - r); lineTo(tx + r, cy); lineTo(tx, cy + r); lineTo(tx - r, cy); close()
        }
        drawPath(d, if (canSeek) nc.c else nc.dim)
    }
}

// ---------------------------------------------------------------------------------------------
// Animações de progressão (Canvas puro: sem sombra, blur nem shader, para gastar pouco).
// p = progresso 0..1; spin = tempo acumulado de reprodução em ms (congela quando pausado).
// ---------------------------------------------------------------------------------------------

/** Cores usadas pelas animações; as camadas "fantasma" do glitch usam uma versão toda tingida. */
private class AnimColors(
    val y: Color,
    val c: Color,
    val dim: Color,
    val pn: Color,
    val black: Color,
    val white: Color,
)

private fun ghostColors(t: Color) =
    AnimColors(t, t, t.copy(alpha = 0.5f), Color.Transparent, Color.Transparent, t.copy(alpha = 0.3f))

/**
 * Desenha a animação escolhida. Durante um pulso de glitch (gl = ±1), desenha antes duas cópias tingidas
 * de vermelho e ciano deslocadas para os lados, como no relógio, e a versão normal por cima.
 */
private fun DrawScope.drawAnimation(style: Int, p: Float, spin: Float, nc: NcPalette, gl: Int) {
    if (gl != 0) {
        val dx = gl * 3.dp.toPx()
        translate(left = -dx) { drawStyle(style, p, spin, ghostColors(nc.r)) }
        translate(left = dx) { drawStyle(style, p, spin, ghostColors(nc.c)) }
    }
    drawStyle(style, p, spin, AnimColors(nc.y, nc.c, nc.dim, nc.pn, Color.Black, Color.White))
}

private fun DrawScope.drawStyle(style: Int, p: Float, spin: Float, k: AnimColors) {
    when (style) {
        0 -> drawCassette(p, spin, k)
        1 -> drawDisc(p, spin, k)
        else -> drawFuturist(p, spin, k)
    }
}

private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = deg * PI.toFloat() / 180f
    return Offset(c.x + r * cos(a), c.y + r * sin(a))
}

private fun DrawScope.drawCassette(p: Float, spin: Float, k: AnimColors) {
    val cw = min(size.width, size.height * 1.62f) * 0.96f
    val ch = cw / 1.62f
    val x0 = (size.width - cw) / 2f
    val y0 = (size.height - ch) / 2f
    val sw = 2.5.dp.toPx()
    val corner = CornerRadius(cw * 0.04f)

    // Corpo.
    drawRoundRect(k.pn, Offset(x0, y0), Size(cw, ch), corner)
    drawRoundRect(k.y, Offset(x0, y0), Size(cw, ch), corner, style = Stroke(sw))

    // Etiqueta com linhas de escrita.
    drawRoundRect(
        k.y.copy(alpha = 0.92f), Offset(x0 + cw * 0.07f, y0 + ch * 0.08f),
        Size(cw * 0.86f, ch * 0.34f), CornerRadius(cw * 0.015f),
    )
    for (i in 0..1) {
        val yy = y0 + ch * (0.2f + i * 0.1f)
        drawLine(k.black.copy(alpha = 0.55f), Offset(x0 + cw * 0.11f, yy), Offset(x0 + cw * 0.89f, yy), 1.5.dp.toPx())
    }

    // Janela da fita.
    val wx = x0 + cw * 0.2f
    val wy = y0 + ch * 0.5f
    val ww = cw * 0.6f
    val wh = ch * 0.34f
    drawRoundRect(k.black, Offset(wx, wy), Size(ww, wh), CornerRadius(wh / 2f))
    drawRoundRect(k.dim, Offset(wx, wy), Size(ww, wh), CornerRadius(wh / 2f), style = Stroke(sw * 0.6f))

    // Carretéis: o da esquerda esvazia e o da direita enche conforme o progresso.
    val cy = wy + wh / 2f
    val c1 = Offset(wx + ww * 0.27f, cy)
    val c2 = Offset(wx + ww * 0.73f, cy)
    val rMax = wh / 2f * 0.95f
    val rMin = rMax * 0.42f
    val rL = rMin + (rMax - rMin) * (1f - p)
    val rR = rMin + (rMax - rMin) * p
    for ((c, r) in listOf(c1 to rL, c2 to rR)) {
        drawCircle(k.dim, r, c)
        val hub = rMin * 0.7f
        drawCircle(k.y, hub, c)
        val ang = spin * 0.18f * (rMax / r)
        for (i in 0 until 6) {
            drawLine(k.black, c, polar(c, hub * 0.95f, ang + i * 60f), 2.dp.toPx())
        }
        drawCircle(k.black, hub * 0.42f, c)
    }
    // Fita esticada entre os carretéis.
    drawLine(k.dim, Offset(c1.x, cy + rL), Offset(c2.x, cy + rR), 2.dp.toPx())

    // Base inferior e parafusos.
    drawRoundRect(
        k.y.copy(alpha = 0.7f), Offset(x0 + cw * 0.3f, y0 + ch * 0.87f), Size(cw * 0.4f, ch * 0.1f),
        CornerRadius(cw * 0.01f), style = Stroke(sw * 0.6f),
    )
    for (dx in listOf(0.04f, 0.96f)) for (dy in listOf(0.07f, 0.93f)) {
        drawCircle(k.y.copy(alpha = 0.8f), cw * 0.012f, Offset(x0 + cw * dx, y0 + ch * dy))
    }
}

private fun DrawScope.drawDisc(p: Float, spin: Float, k: AnimColors) {
    val half = min(size.width, size.height) / 2f
    val c = center
    val rd = half * 0.80f
    val ro = half * 0.93f
    val a = spin * 0.25f

    drawCircle(k.pn, rd, c)
    for (ring in 1..10) drawCircle(k.dim.copy(alpha = 0.35f), rd * (0.30f + ring * 0.065f), c, style = Stroke(1.dp.toPx()))

    // Reflexos giratórios.
    val tl = Offset(c.x - rd, c.y - rd)
    val sz = Size(rd * 2f, rd * 2f)
    for (i in 0..1) {
        drawArc(k.white.copy(alpha = 0.10f), a + i * 180f, 28f, true, tl, sz)
        drawArc(k.c.copy(alpha = 0.10f), a + 60f + i * 180f, 18f, true, tl, sz)
    }
    drawCircle(k.y, rd, c, style = Stroke(2.5.dp.toPx()))

    // Cubo central com marcas que giram.
    drawCircle(k.dim.copy(alpha = 0.6f), rd * 0.26f, c)
    drawCircle(k.y.copy(alpha = 0.85f), rd * 0.26f, c, style = Stroke(2.dp.toPx()))
    for (i in 0 until 12) {
        drawLine(k.y, polar(c, rd * 0.15f, a + i * 30f), polar(c, rd * 0.21f, a + i * 30f), 1.5.dp.toPx())
    }
    drawCircle(k.black, rd * 0.09f, c)

    // Anel de progresso ao redor do disco.
    val st = 5.dp.toPx()
    val atl = Offset(c.x - ro, c.y - ro)
    val asz = Size(ro * 2f, ro * 2f)
    drawArc(k.dim.copy(alpha = 0.35f), -90f, 360f, false, atl, asz, style = Stroke(st))
    if (p > 0f) drawArc(k.c, -90f, 360f * p, false, atl, asz, style = Stroke(st, cap = StrokeCap.Round))
    drawCircle(k.y, 6.dp.toPx(), polar(c, ro, -90f + 360f * p))
}

private fun DrawScope.drawFuturist(p: Float, spin: Float, k: AnimColors) {
    val n = 36
    val bh = size.height * 0.26f
    val top = (size.height - bh) / 2f + 14.dp.toPx()
    val slant = bh * 0.28f
    val padX = slant * 0.5f + 8.dp.toPx()
    val bw = size.width - 2f * padX - slant
    val segW = bw / n
    val gap = segW * 0.2f
    val filled = floor(p * n).toInt().coerceIn(0, n)
    val part = (p * n - filled).coerceIn(0f, 1f)

    for (i in 0 until n) {
        val x = padX + i * segW
        val path = Path().apply {
            moveTo(x + slant, top); lineTo(x + segW - gap + slant, top)
            lineTo(x + segW - gap, top + bh); lineTo(x, top + bh); close()
        }
        val color = when {
            i < filled -> k.y.copy(alpha = 0.6f + 0.4f * ((sin(i * 0.5f - spin * 0.006f) + 1f) / 2f))
            i == filled && p < 1f -> k.c.copy(alpha = 0.25f + 0.75f * part)
            else -> k.dim.copy(alpha = 0.22f)
        }
        drawPath(path, color)
    }

    // Escala de 0–100% acima da barra.
    for (i in 0..4) {
        val x = padX + slant * 0.5f + bw * i / 4f
        drawLine(k.y.copy(alpha = 0.6f), Offset(x, top - 14.dp.toPx()), Offset(x, top - 6.dp.toPx()), 1.5.dp.toPx())
    }
    // Colchetes nos cantos.
    val l = 12.dp.toPx()
    val s = 2.dp.toPx()
    val left = padX - 6.dp.toPx()
    val right = padX + bw + slant + 6.dp.toPx()
    val t2 = top - 4.dp.toPx()
    val b2 = top + bh + 4.dp.toPx()
    for ((cx, dx) in listOf(left to 1f, right to -1f)) {
        drawLine(k.y, Offset(cx, t2), Offset(cx + dx * l, t2), s)
        drawLine(k.y, Offset(cx, t2), Offset(cx, t2 + l), s)
        drawLine(k.y, Offset(cx, b2), Offset(cx + dx * l, b2), s)
        drawLine(k.y, Offset(cx, b2), Offset(cx, b2 - l), s)
    }
}