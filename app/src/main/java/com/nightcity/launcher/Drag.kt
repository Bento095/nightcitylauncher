package com.nightcity.launcher

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/** Quantos apps cabem na gaveta DAEMONS. */
const val DAEMON_SLOTS = 8

/** from = -1 vem da gaveta de apps; 0..3 vem da dock; 10..15 vem da gaveta DAEMONS (10 + posição). */
class DragInfo(val pkg: String, val from: Int, val pos: Offset)

class DragHost {
    var info by mutableStateOf<DragInfo?>(null)
    var dockBounds: Rect = Rect.Zero

    /** Linhas da gaveta DAEMONS (preenchidas pela tela inicial) para saber onde o app foi solto. */
    val daemonRows = arrayOfNulls<LayoutCoordinates>(DAEMON_SLOTS)
    var onDrop: (DragInfo) -> Unit = {}
    private var lastEnd = 0L

    /** Evita que soltar o dedo depois de arrastar também conte como toque no app. */
    val blockClicks: Boolean
        get() = info != null || SystemClock.uptimeMillis() - lastEnd < 300

    fun start(pkg: String, from: Int, pos: Offset) {
        info = DragInfo(pkg, from, pos)
    }

    fun move(d: Offset) {
        info = info?.let { DragInfo(it.pkg, it.from, it.pos + d) }
    }

    fun end() {
        val i = info
        info = null
        lastEnd = SystemClock.uptimeMillis()
        if (i != null) onDrop(i)
    }

    /** Posição (0..5) da linha DAEMONS sob o ponto, ou -1. */
    fun daemonSlotAt(p: Offset): Int {
        for (i in daemonRows.indices) {
            val c = daemonRows[i] ?: continue
            if (!c.isAttached) continue
            val r = c.boundsInRoot()
            if (p.y >= r.top && p.y <= r.bottom && p.x >= r.left && p.x <= r.right) return i
        }
        return -1
    }

    fun cancel() {
        info = null
        lastEnd = SystemClock.uptimeMillis()
    }
}

/** Índice (0..3) do espaço da dock sob o ponto, ou -1 se estiver fora da dock. */
fun slotAt(b: Rect, p: Offset): Int {
    if (b.width <= 0f) return -1
    if (p.y < b.top - 60f || p.y > b.bottom + 60f || p.x < b.left || p.x > b.right) return -1
    val col = ((p.x - b.left) / (b.width / 5f)).toInt()
    return (col - 1).coerceIn(0, 3)
}

fun Modifier.dragSource(pkg: String, from: Int, host: DragHost): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(pkg, from) {
            detectDragGesturesAfterLongPress(
                onDragStart = { off -> host.start(pkg, from, origin + off) },
                onDrag = { change, amount ->
                    change.consume()
                    host.move(amount)
                },
                onDragEnd = { host.end() },
                onDragCancel = { host.cancel() },
            )
        }
}