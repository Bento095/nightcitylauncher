package com.nightcity.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun firstLetter(a: AppInfo): Char {
    val c = a.label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c.isLetter()) c else '#'
}

@Composable
fun Drawer(
    apps: List<AppInfo>,
    host: DragHost,
    realIcons: Boolean,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val nc = LocalNc.current
    var q by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("tudo") }
    val list = remember(apps, q, tab) {
        apps.filter { (tab == "tudo" || it.cat == tab) && it.label.lowercase().contains(q.lowercase().trim()) }
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var idxH by remember { mutableIntStateOf(1) }
    val letters = remember(list) { list.map { firstLetter(it) }.distinct() }

    fun jump(y: Float) {
        if (letters.isEmpty()) return
        val k = (y / idxH * letters.size).toInt().coerceIn(0, letters.size - 1)
        val target = list.indexOfFirst { firstLetter(it) == letters[k] }
        if (target >= 0) scope.launch { gridState.scrollToItem(target) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(nc.bg.copy(alpha = 0.78f))
            .systemBarsPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
            // barra de puxar: arraste para baixo para fechar
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectVerticalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = { if (acc > 60f) onClose() },
                            onDragCancel = { },
                            onVerticalDrag = { _, d -> acc += d },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(56.dp, 4.dp).background(nc.y)) }

            // busca
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(CutShape(10.dp))
                    .background(Color.Black)
                    .border(1.dp, nc.c, CutShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (q.isEmpty()) Tx("BREACH PROTOCOL // BUSCAR APP", nc.c.copy(alpha = 0.6f), 16, true, spacing = 2f)
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = nc.c, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                    cursorBrush = SolidColor(nc.c),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))

            // abas
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("tudo", "tools", "games", "social").forEach { t ->
                    val on = tab == t
                    Box(
                        Modifier
                            .background(if (on) nc.y else Color.Transparent)
                            .border(1.dp, nc.y.copy(alpha = if (on) 1f else 0.33f))
                            .clickable { tab = t }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) { Tx(t.uppercase(), if (on) Color.Black else nc.y, 11, true, spacing = 2f) }
                }
            }
            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(list, key = { it.pkg }) { app ->
                    AppTile(app, host, realIcons, onOpen)
                }
            }
        }

        // índice A-Z
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .fillMaxHeight(0.6f)
                .onSizeChanged { idxH = it.height.coerceAtLeast(1) }
                .pointerInput(letters) { detectTapGestures(onPress = { jump(it.y) }) }
                .pointerInput(letters) {
                    detectDragGestures(
                        onDragStart = { jump(it.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            jump(change.position.y)
                        },
                    )
                }
                .padding(horizontal = 5.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { Tx(it.toString(), nc.c, 10, mono = true) }
        }
    }
}

@Composable
private fun AppTile(app: AppInfo, host: DragHost, realIcons: Boolean, onOpen: (String) -> Unit) {
    val nc = LocalNc.current
    val shape = CutShape(9.dp)
    Column(
        Modifier
            .dragSource(app.pkg, -1, host)
            .clickable { if (!host.blockClicks) onOpen(app.pkg) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(shape)
                .background(nc.bg.copy(alpha = 0.8f))
                .border(1.dp, nc.y, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (realIcons && app.pkg != CONFIG_PKG) {
                AppIcon(app.pkg, 40.dp) { Tx(app.mono, nc.y, 18, true) }
            } else {
                Tx(app.mono, nc.y, 18, true)
            }
        }
        Tx(app.label.uppercase(), nc.y, 10, true, spacing = 1.5f, modifier = Modifier.width(66.dp))
    }
}
