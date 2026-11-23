package com.nightcity.launcher

import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- tipografia

// Fonte condensada em negrito para títulos, rótulos e elementos cyberpunk/táticos.
val Cond: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

// Fonte monoespaçada padrão do sistema para métricas, horários, logs e tags técnicas.
val Mono: FontFamily = FontFamily.Monospace

/**
 * Componente de texto base do launcher baseado em `BasicText` para controle direto de estilo,
 * espaçamento entre letras, truncamento por reticências e alternância entre `Cond` e `Mono`.
 */
@Composable
fun Tx(
    text: String,
    color: Color,
    size: Int = 12,
    bold: Boolean = false,
    mono: Boolean = false,
    spacing: Float = 1f,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = if (mono) Mono else Cond,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = spacing.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------- formas

/**
 * Forma geométrica com chanfro diagonal no canto superior direito e inferior esquerdo,
 * reforçando o visual industrial/cyberdeck dos cartões, dock e botões.
 */
class CutShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            close()
        }
        return Outline.Generic(p)
    }
}

/**
 * Forma hexagonal simétrica usada em badges e molduras (como o indicador de mídia).
 */
object HexShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.25f)
            lineTo(w, h * 0.75f)
            lineTo(w * 0.5f, h)
            lineTo(0f, h * 0.75f)
            lineTo(0f, h * 0.25f)
            close()
        }
        return Outline.Generic(p)
    }
}

// ---------------------------------------------------------------- painéis

/**
 * Contêiner padrão em estilo terminal/painel com borda sutil, etiqueta de título destacada
 * no canto superior esquerdo e tag opcional à direita.
 */
@Composable
fun Panel(
    title: String,
    tag: String? = null,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    tagColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nc = LocalNc.current
    // Usa cor de destaque personalizada se fornecida; caso contrário, assume `nc.y` da paleta ativa.
    val a = accent ?: nc.y
    val tc = tagColor ?: nc.c
    Box(
        modifier
            .fillMaxWidth()
            .background(nc.pn)
            .border(1.dp, a.copy(alpha = 0.33f))
    ) {
        // Conteúdo interno deslocado no topo para não sobrepor a barra do título.
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 19.dp, bottom = 6.dp), content = content)
        // Etiqueta superior esquerda com o nome do painel.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .background(a)
                .padding(horizontal = 8.dp, vertical = 1.dp)
        ) { Tx(title, Color.Black, 11, bold = true, spacing = 2f) }
        // Tag informativa opcional no canto superior direito.
        if (tag != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(end = 7.dp, top = 2.dp)) {
                Tx(tag, tc, 10, mono = true)
            }
        }
    }
}

/**
 * Barra segmentada de 14 blocos para métricas percentuais (ex.: RAM, armazenamento, bateria, temp).
 * Muda para a cor de alerta (`nc.r`) quando `hot` é verdadeiro.
 */
@Composable
fun Segs(pct: Int, hot: Boolean, modifier: Modifier = Modifier) {
    val nc = LocalNc.current
    // Converte a porcentagem (0..100) na quantidade de segmentos acesos entre 0 e 14.
    val n = pct.coerceIn(0, 100) * 14 / 100
    Row(
        modifier.fillMaxWidth().height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 0 until 14) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (i < n) (if (hot) nc.r else nc.y) else nc.y.copy(alpha = 0.13f))
            )
        }
    }
}

/**
 * Gráfico de linha contínuo (sparkline) para exibir o histórico recente de uma métrica (ex.: RAM).
 */
@Composable
fun Spark(values: List<Int>, modifier: Modifier = Modifier) {
    val nc = LocalNc.current
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        // Precisa de pelo menos 2 pontos para traçar um segmento de reta.
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            // Normaliza o valor (0..100) para a altura disponível, deixando pequena margem superior/inferior.
            val y = size.height - v.coerceIn(0, 100) / 100f * size.height * 0.9f - 1f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, nc.c, style = Stroke(width = 1.5f))
    }
}

// ---------------------------------------------------------------- ícones de status

// Tipos de ícones de estado do sistema desenhados vetorialmente no Canvas.
enum class Ico { AIR, MUTE, DND, HOT, ALM }

// Helper para desenhar polígonos fechados escalados a partir de coordenadas em grade 24x24.
private fun DrawScope.poly(s: Float, col: Color, vararg p: Float) {
    val path = Path()
    path.moveTo(p[0] * s, p[1] * s)
    var i = 2
    while (i < p.size) {
        path.lineTo(p[i] * s, p[i + 1] * s)
        i += 2
    }
    path.close()
    drawPath(path, col, style = Stroke(width = 2f * s))
}

// Helper para desenhar linhas escaladas a partir de coordenadas em grade 24x24.
private fun DrawScope.ln(s: Float, col: Color, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(col, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = 2f * s)
}

/**
 * Renderiza um ícone vetorial minimalista de status do aparelho (modo avião, mudo,
 * não perturbe, hotspot ou alarme) na grade de referência 24x24.
 */
@Composable
fun StatusIcon(kind: Ico, on: Boolean) {
    val nc = LocalNc.current
    // Modo avião usa cor de alerta (`nc.r`) quando ativo; demais usam `nc.y` ou versão esmaecida quando inativos.
    val col = if (on) (if (kind == Ico.AIR) nc.r else nc.y) else nc.y.copy(alpha = 0.4f)
    Canvas(Modifier.size(15.dp)) {
        val s = size.width / 24f
        when (kind) {
            Ico.AIR -> poly(s, col, 12f, 2f, 14f, 9f, 22f, 14f, 22f, 16f, 14f, 13f, 14f, 19f, 16f, 21f, 16f, 22f,
                12f, 21f, 8f, 22f, 8f, 21f, 10f, 19f, 10f, 13f, 2f, 16f, 2f, 14f, 10f, 9f)
            Ico.MUTE -> {
                poly(s, col, 4f, 9f, 8f, 9f, 12f, 5f, 12f, 19f, 8f, 15f, 4f, 15f)
                ln(s, col, 3f, 3f, 21f, 21f)
            }
            Ico.DND -> {
                drawCircle(col, radius = 8f * s, center = Offset(12f * s, 12f * s), style = Stroke(width = 2f * s))
                ln(s, col, 8f, 12f, 16f, 12f)
            }
            Ico.HOT -> {
                // Três arcos concêntricos simulando ondas de hotspot + ponto central.
                for (r in floatArrayOf(4f, 8f, 12f)) {
                    drawArc(
                        col, 225f, 90f, false,
                        topLeft = Offset((12f - r) * s, (19f - r) * s),
                        size = Size(2f * r * s, 2f * r * s),
                        style = Stroke(width = 2f * s),
                    )
                }
                drawCircle(col, radius = 1.6f * s, center = Offset(12f * s, 19f * s))
            }
            Ico.ALM -> {
                drawCircle(col, radius = 7f * s, center = Offset(12f * s, 13f * s), style = Stroke(width = 2f * s))
                ln(s, col, 12f, 9f, 12f, 13f)
                ln(s, col, 12f, 13f, 15f, 15f)
                ln(s, col, 4f, 4f, 2f, 6f)
                ln(s, col, 20f, 4f, 22f, 6f)
            }
        }
    }
}

/**
 * Indicador visual e textual de bateria com estados visuais para carregamento (ciano + brilho varrendo),
 * economia de energia (`ECO` tracejado/pulsante) e bateria baixa (vermelho piscante).
 */
@Composable
fun Battery(level: Int, charging: Boolean, saver: Boolean) {
    val nc = LocalNc.current
    val low = level <= 20 && !charging
    val eco = saver && !charging
    val col = when {
        charging -> nc.c
        low -> nc.r
        else -> nc.y
    }

    // Animações contínuas usadas conforme o estado atual da bateria.
    val inf = rememberInfiniteTransition(label = "bat")
    val sweep by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "sweep")
    val pulse by inf.animateFloat(1f, 0.4f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    val blink by inf.animateFloat(1f, 0.35f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (low) blink else 1f),
    ) {
        Canvas(Modifier.size(31.dp, 13.dp).alpha(if (eco) pulse else 1f)) {
            val sw = 1.dp.toPx()
            val capW = 2.dp.toPx()
            val bw = size.width - capW - 1.dp.toPx()
            // Borda tracejada quando o modo economia de bateria está ativo.
            val dash = if (eco) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null
            drawRect(col, Offset(sw / 2, sw / 2), Size(bw - sw, size.height - sw), style = Stroke(sw, pathEffect = dash))
            // Polo positivo ("cabeça") da bateria à direita.
            drawRect(col, Offset(bw, size.height * 0.3f), Size(capW, size.height * 0.4f))

            val inL = 2.dp.toPx()
            val inT = 2.dp.toPx()
            val inW = bw - 4.dp.toPx()
            val inH = size.height - 4.dp.toPx()
            val fw = inW * level.coerceIn(0, 100) / 100f

            if (eco) {
                // Preenchimento em pequenos blocos espaçados no modo ECO.
                var x = 0f
                while (x < fw) {
                    drawRect(col, Offset(inL + x, inT), Size(minOf(3.dp.toPx(), fw - x), inH))
                    x += 5.dp.toPx()
                }
            } else {
                // Preenchimento sólido proporcional ao nível da bateria.
                drawRect(col, Offset(inL, inT), Size(fw, inH))
            }

            // Faixa de brilho deslizando sobre o nível atual enquanto carrega.
            if (charging && fw > 0f) {
                val hw = fw * 0.35f
                val x = inL + (fw + hw) * sweep - hw
                clipRect(inL, inT, inL + fw, inT + inH) {
                    drawRect(Color.White.copy(alpha = 0.7f), Offset(x, inT), Size(hw, inH))
                }
            }
        }
        Spacer(Modifier.width(5.dp))
        Tx("$level%" + (if (charging) " ⚡" else if (eco) " ECO" else ""), col, 12, mono = true)
    }
}

// ---------------------------------------------------------------- ícone de app

/**
 * Carrega de forma assíncrona o ícone de um aplicativo pelo `pkg` usando `IconCache`,
 * exibindo imediatamente um valor em cache se já disponível e caindo no `fallback` enquanto carrega.
 */
@Composable
fun AppIcon(pkg: String, size: Dp, fallback: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(IconCache.peek(pkg), pkg) {
        value = withContext(Dispatchers.IO) { IconCache.get(ctx, pkg) }
    }
    val b = bmp
    if (b != null) Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size)) else fallback()
}