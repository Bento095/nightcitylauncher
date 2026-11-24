package com.nightcity.launcher

import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/** Quantidade total de slots disponíveis na gaveta DAEMONS. */
const val DAEMON_SLOTS = 8

/**
 * Snapshot imutável de um arrasto em andamento:
 * - [pkg]: packageName do aplicativo sendo arrastado
 * - [from]: origem do item (`< 0` / `-1` = gaveta de apps; `0..3` = slot da Dock; `10 + i` = slot `i` dos Daemons)
 * - [pos]: coordenada atual do ponteiro em relação à raiz da janela/tela
 */
class DragInfo(val pkg: String, val from: Int, val pos: Offset)

/**
 * Coordenador central de drag-and-drop entre a Gaveta de Apps, a Dock e os Daemons.
 * Expõe estados reativos para o Compose desenhar o ícone fantasma, destacar alvos (hover)
 * e resolver onde o item foi solto ao encerrar o gesto.
 */
class DragHost {
    // Estado ativo do arrasto; `null` quando nenhum item está sendo arrastado no momento.
    var info by mutableStateOf<DragInfo?>(null)

    // Limites retangulares da Dock em coordenadas da raiz, atualizados via `onGloballyPositioned`.
    var dockBounds: Rect = Rect.Zero

    /** Coordenadas de cada linha da gaveta DAEMONS (preenchidas pela HomeScreen) para hit-testing na soltura. */
    val daemonRows = arrayOfNulls<LayoutCoordinates>(DAEMON_SLOTS)

    // Callback disparado quando o gesto termina normalmente com um `DragInfo` válido.
    var onDrop: (DragInfo) -> Unit = {}

    // Instante (`uptimeMillis`) em que o último arrasto terminou ou foi cancelado.
    private var lastEnd = 0L

    /**
     * Bloqueia cliques acidentais enquanto o dedo ainda está arrastando ou nos primeiros 300ms
     * após soltar, evitando que o final do gesto abra o aplicativo sem querer.
     */
    val blockClicks: Boolean
        get() = info != null || SystemClock.uptimeMillis() - lastEnd < 300

    // Inicia um novo arrasto na posição inicial `pos` (em coordenadas da raiz).
    fun start(pkg: String, from: Int, pos: Offset) {
        info = DragInfo(pkg, from, pos)
    }

    // Atualiza a posição do item arrastado somando o delta `d` do movimento do ponteiro.
    fun move(d: Offset) {
        info = info?.let { DragInfo(it.pkg, it.from, it.pos + d) }
    }

    // Finaliza o arrasto, limpa o estado visual e notifica `onDrop` se havia um arrasto ativo.
    fun end() {
        val i = info
        info = null
        lastEnd = SystemClock.uptimeMillis()
        if (i != null) onDrop(i)
    }

    /**
     * Retorna o índice (`0 until DAEMON_SLOTS`) da linha DAEMONS que contém o ponto `p`,
     * ou `-1` se o ponto não estiver sobre nenhuma linha anexada à composição.
     */
    fun daemonSlotAt(p: Offset): Int {
        for (i in daemonRows.indices) {
            val c = daemonRows[i] ?: continue
            if (!c.isAttached) continue
            val r = c.boundsInRoot()
            if (p.y >= r.top && p.y <= r.bottom && p.x >= r.left && p.x <= r.right) return i
        }
        return -1
    }

    // Cancela o arrasto (ex.: gesto interrompido pelo sistema) sem disparar `onDrop`.
    fun cancel() {
        info = null
        lastEnd = SystemClock.uptimeMillis()
    }
}

/**
 * Calcula qual dos 4 slots fixáveis da Dock (`0..3`) está sob o ponto `p` (em coordenadas da raiz),
 * ou `-1` se estiver fora da área da Dock (com tolerância vertical de 60px acima/abaixo).
 * A Dock possui 5 colunas no total: coluna `0` é o botão "APPS" e colunas `1..4` são os 4 slots.
 */
fun slotAt(b: Rect, p: Offset): Int {
    if (b.width <= 0f) return -1
    if (p.y < b.top - 60f || p.y > b.bottom + 60f || p.x < b.left || p.x > b.right) return -1
    val col = ((p.x - b.left) / (b.width / 5f)).toInt()
    return (col - 1).coerceIn(0, 3)
}

/** Tempo total (desde o toque) que o dedo precisa ficar parado para disparar `onHold`. */
private const val HOLD_MS = 2000L

/**
 * Modifier que transforma um composable em origem de arrasto após toque longo (`long press`).
 * Calcula a posição absoluta do componente na raiz da tela e converte os toques locais
 * para coordenadas globais (`origin + off`) antes de repassar ao `DragHost`.
 *
 * Se [onHold] for informado: ao segurar o item PARADO por cerca de 2 s (sem mover além da folga de toque),
 * o arrasto é cancelado, o aparelho vibra e [onHold] é chamado com o `pkg`. Se o dedo se mover antes disso,
 * funciona como arrasto normal.
 */
fun Modifier.dragSource(
    pkg: String,
    from: Int,
    host: DragHost,
    onHold: ((String) -> Unit)? = null,
): Modifier = composed {
    // Posição do canto superior esquerdo deste componente na raiz da tela.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val vc = LocalViewConfiguration.current
    val slop = vc.touchSlop * 2f
    val holdAfterLongPress = (HOLD_MS - vc.longPressTimeoutMillis).coerceAtLeast(0L)
    val holdCb by rememberUpdatedState(onHold)
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(pkg, from) {
            var holdJob: Job? = null
            var held = false
            var net = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = { off ->
                    held = false
                    net = Offset.Zero
                    host.start(pkg, from, origin + off)
                    if (holdCb != null) {
                        holdJob = scope.launch {
                            delay(holdAfterLongPress)
                            if (!held) {
                                held = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                host.cancel()
                                holdCb?.invoke(pkg)
                            }
                        }
                    }
                },
                onDrag = { change, amount ->
                    // Consome o evento para evitar que listas/containers pai rolem durante o arrasto.
                    change.consume()
                    if (!held) {
                        net += amount
                        if (net.getDistance() > slop) holdJob?.cancel()
                        host.move(amount)
                    }
                },
                onDragEnd = { holdJob?.cancel(); host.end() },
                onDragCancel = { holdJob?.cancel(); host.cancel() },
            )
        }
}