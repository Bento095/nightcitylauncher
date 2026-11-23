package com.nightcity.launcher

import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Formata a hora atual no padrão 24h ("HH:mm") usando o locale padrão do dispositivo.
private fun timeNow(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

// Monta a linha de data exibida na faixa de status inferior do topo rolável.
private fun dateLine(): String {
    val d = java.time.LocalDate.now()
    return "DATA %02d %02d %04d".format(d.dayOfMonth, d.monthValue, d.year)
}

// Converte um timestamp em milissegundos em tempo decorrido compacto ("AGORA", "15M", "3H").
private fun ago(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60000
    return if (m < 1) "AGORA" else if (m < 60) "${m}M" else "${m / 60}H"
}

// Formata duração/posição de mídia em milissegundos como "MM:SS".
private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * Relógio digital com animação periódica de glitch (deslocamento horizontal em vermelho/ciano)
 * e suporte a toque simples (abre alarmes/relógio) e toque longo (abre configurações).
 */
@Composable
fun GlitchClock(onClick: () -> Unit, onLongPress: () -> Unit) {
    val nc = LocalNc.current
    // Atualiza a string da hora a cada 10 segundos.
    val time by produceState(timeNow()) {
        while (true) {
            value = timeNow()
            delay(10_000)
        }
    }

    // Offset horizontal do glitch; `0` indica estado normal sem deslocamento.
    var gl by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            // Executa 3 pulsos rápidos alternando esquerda/direita antes de estabilizar.
            repeat(3) { i ->
                gl = if (i % 2 == 0) 2 else -2
                delay(70)
            }
            gl = 0
        }
    }

    Box(Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) }) {
        if (gl != 0) {
            Tx(time, nc.r, 34, true, modifier = Modifier.offset(x = (-gl).dp))
            Tx(time, nc.c, 34, true, modifier = Modifier.offset(x = gl.dp))
        }
        Tx(time, nc.y, 34, true)
    }
}

/**
 * Cartão de controle de mídia com barra de progresso em tempo real, nome da faixa/artista,
 * etiqueta do player e botões de anterior, play/pause e próxima.
 */
@Composable
fun MediaCard(media: MediaState, onNeedAccess: () -> Unit) {
    val nc = LocalNc.current
    // Relógio monotônico atualizado a cada segundo para calcular a posição atual da reprodução.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.playing) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    val pos = media.position(now)
    val dur = media.durationMs
    val title = when {
        !media.hasAccess -> "ATIVAR CONTROLE DE MÍDIA"
        media.hasSession -> media.title.ifBlank { "SEM TÍTULO" }.uppercase()
        else -> "NENHUMA MÍDIA TOCANDO"
    }
    val tag = when {
        !media.hasAccess -> "TOQUE"
        media.hasSession -> media.appLabel.uppercase()
        else -> "PLAYER"
    }
    val info = when {
        !media.hasAccess -> "acesso a notificações"
        media.hasSession -> (if (media.artist.isNotBlank()) media.artist + "  " else "") + fmt(pos) + "/" + fmt(dur)
        else -> "--:--"
    }
    // Fração de progresso (0f..1f) usada para preencher a barra horizontal.
    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f

    var mediaGl by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(6500)
            repeat(3) { i ->
                mediaGl = if (i % 2 == 0) 2 else -2
                delay(65)
            }
            mediaGl = 0
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(CutShape(12.dp))
            .background(nc.y)
            .then(if (!media.hasAccess) Modifier.clickable { onNeedAccess() } else Modifier)
            .padding(8.dp)
    ) {
        Row {
            // Badge hexagonal à esquerda com símbolo musical.
            Box(
                Modifier.size(40.dp, 46.dp).clip(HexShape).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) { Tx("♪", nc.y, 26, true) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (mediaGl != 0) {
                            Tx(title, nc.r, 20, true, modifier = Modifier.offset(x = (-mediaGl).dp))
                            Tx(title, nc.c, 20, true, modifier = Modifier.offset(x = mediaGl.dp))
                        }
                        Tx(title, Color.Black, 20, true)
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(Color.Black).padding(horizontal = 5.dp)) {
                        Tx(tag, nc.y, 10, mono = true)
                    }
                }
                Spacer(Modifier.height(5.dp))
                // Barra de progresso da faixa em execução.
                Box(Modifier.fillMaxWidth().height(5.dp).background(Color.Black.copy(alpha = 0.27f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Color.Black))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { Tx(info, Color.Black, 11, mono = true) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MediaBtn("◁◁") { media.prev() }
                        MediaBtn(if (media.playing) "❚❚" else "▶") { media.playPause() }
                        MediaBtn("▷▷") { media.next() }
                    }
                }
            }
        }
        var mediaGl by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(6500)
                repeat(3) { i ->
                    mediaGl = if (i % 2 == 0) 2 else -2
                    delay(65)
                }
                mediaGl = 0
            }
        }
    }
}

// Botão compacto de transporte de mídia usado dentro do `MediaCard`.
@Composable
private fun MediaBtn(t: String, onClick: () -> Unit) {
    val nc = LocalNc.current
    Box(
        Modifier.background(Color.Black).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 2.dp)
    ) { Tx(t, nc.y, 10, mono = true) }
}

/**
 * Tela inicial do launcher.
 * Divide-se em um cabeçalho fixo (relógio, ícones de status, bateria e rede)
 * e uma coluna rolável com mídia, clima expansível, notificações, faixa de sensores/data,
 * Quickhacks (recentes), Diagnóstico (RAM/armazenamento/temp/bateria/sparkline),
 * Daemons (slots fixados por drag-and-drop) e barra de atalhos rápidos.
 */
@Composable
fun HomeScreen(
    device: DeviceState,
    media: MediaState,
    wx: WxData?,
    wxMsg: String,
    recents: List<Pair<AppInfo, Long>>,
    ramHist: List<Int>,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onClock: () -> Unit,
    onMediaAccess: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeLeft: () -> Unit = {},
    daemons: List<String?>,
    apps: Map<String, AppInfo>,
    host: DragHost,
    scroll: ScrollState,
    onCalendar: () -> Unit,
    onMaps: () -> Unit,
    onPower: () -> Unit,
    notifs: List<NotifItem>,
    notifStyle: String,
    notifMax: Int,
    onNotifOpen: (NotifItem) -> Unit,
    onNotifDismiss: (String) -> Unit,
    onNotifClear: () -> Unit,
) {
    val nc = LocalNc.current
    // Controla se a grade de previsão detalhada por slots está expandida ou recolhida.
    var wxOpen by remember { mutableStateOf(false) }

    // Abrir a gaveta de apps: só vale puxando para cima a PARTIR DOS 10% INFERIORES da tela.
    // Em qualquer outro ponto, arrastar apenas rola o conteúdo.
    val swipeUp by rememberUpdatedState(onSwipeUp)
    val swipeLeft by rememberUpdatedState(onSwipeLeft)
    val flag = remember { SwipeFlag() }
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val conn = remember {
        object : NestedScrollConnection {
            var acc = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || !flag.bottom) return Offset.Zero
                if (available.y < 0f) {
                    acc += available.y
                    // Consome o deslocamento para segurar o scroll enquanto o dedo puxa a gaveta.
                    return Offset(0f, available.y)
                }
                acc = (acc + available.y).coerceAtMost(0f)
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val open = flag.bottom && acc < -thresholdPx
                acc = 0f
                if (open) {
                    swipeUp()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Detecta deslize horizontal para abrir o Vault.
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -90f) onSwipeLeft() },
                    onDragCancel = { total = 0f },
                    onHorizontalDrag = { _, delta -> total += delta },
                )
            }
            .pointerInput(Unit) {
                // Observa apenas a posição inicial do toque na fase Initial sem consumir o evento.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    flag.bottom = down.position.y > size.height * 0.90f
                }
            }
            .systemBarsPadding()
            .padding(start = 11.dp, end = 11.dp, top = 8.dp),
    ) {
        // --- Topo FIXO (não rola): relógio, ícones de status ativos, bateria e rede ---
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                GlitchClock(onClick = onClock, onLongPress = onSettings)
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (device.airplane) StatusIcon(Ico.AIR, true)
                        if (device.muted) StatusIcon(Ico.MUTE, true)
                        if (device.dnd) StatusIcon(Ico.DND, true)
                        if (device.hotspot) StatusIcon(Ico.HOT, true)
                        if (device.alarm) StatusIcon(Ico.ALM, true)
                        Battery(device.battLevel, device.charging, device.saver)
                    }
                    Spacer(Modifier.height(3.dp))
                    Row {
                        val line = if (device.airplane) "NET: MODO AVIÃO" else "NET: " + device.netText
                        Tx(line, nc.y, 11, true, mono = true)
                        val bars = if (device.airplane) 0 else device.netBars
                        Tx(" " + "▮".repeat(bars) + "▯".repeat(4 - bars), nc.c, 11, mono = true)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(nc.y))
        }
        Spacer(Modifier.height(8.dp))

        // --- Conteúdo rolável por baixo do topo fixo ---
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(conn)
                .verticalScroll(scroll)
                // Espaço inferior extra para que os últimos itens não fiquem escondidos atrás da Dock.
                .padding(bottom = 86.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // --- Cartão de mídia ---
            MediaCard(media, onMediaAccess)

            // --- Previsão do tempo (toque expande/recolhe os slots horários) ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CutShape(12.dp))
                    .background(nc.bg)
                    .border(1.dp, nc.c, CutShape(12.dp))
                    .clickable { wxOpen = !wxOpen }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tx("BREACH PROTOCOL // PREVISÃO", nc.c, 14, true, spacing = 2f)
                val right = if (wx != null) "%+d°C %s %s %d%%".format(wx.temp, wx.glyph, wx.text, wx.rain) else wxMsg
                Tx(right, nc.c, 11, mono = true)
            }
            if (wxOpen && wx != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .border(1.dp, nc.c.copy(alpha = 0.33f))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    wx.slots.forEach { s ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Tx("%+d°".format(s.temp), nc.y, 13, mono = true)
                            Tx(s.label + " " + s.glyph, nc.c, 10, mono = true)
                        }
                    }
                }
            }

            // --- Painel de notificações (estilo e limite configuráveis) ---
            NotifPanel(
                hasAccess = media.hasAccess,
                items = notifs,
                style = notifStyle,
                max = notifMax,
                onAccess = onMediaAccess,
                onOpen = onNotifOpen,
                onDismiss = onNotifDismiss,
                onClear = onNotifClear,
            )

            // --- Faixa de data (abre agenda), Bluetooth, GPS e NFC ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, nc.y.copy(alpha = 0.27f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Tx(
                    dateLine(),
                    nc.dim,
                    10,
                    mono = true,
                    modifier = Modifier.clickable { onCalendar() }.padding(vertical = 2.dp),
                )
                Tx("BLUETOOTH", if (device.bt) nc.c else nc.off, 10, mono = true)
                Tx("GPS", if (device.gps) nc.c else nc.off, 10, mono = true)
                if (device.nfcPresent) Tx("NFC", if (device.nfc) nc.c else nc.off, 10, mono = true)
            }

            // --- Quickhacks: até 8 aplicativos abertos recentemente ---
            Panel("QUICKHACKS", "RECENTES") {
                if (recents.isEmpty()) {
                    Tx("> nenhum app recente", nc.dim, 12, mono = true, modifier = Modifier.padding(4.dp))
                }
                recents.take(8).forEachIndexed { i, (app, t) ->
                    val indexLabel = (i + 1).toString().padStart(2, '0')
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (i == 0) Modifier.background(nc.y.copy(alpha = 0.2f)) else Modifier)
                            .clickable { onOpen(app.pkg) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tx(indexLabel, if (i == 0) nc.r else nc.dim, 11, mono = true, modifier = Modifier.width(24.dp))
                        Box(Modifier.weight(1f)) {
                            Tx(app.label.uppercase(), if (i == 0) Color.White else nc.y, 15, true, spacing = 1f)
                        }
                        Box(Modifier.border(1.dp, nc.c).padding(horizontal = 5.dp)) {
                            Tx(ago(t), nc.c, 11, mono = true)
                        }
                    }
                }
            }

            // --- Diagnóstico do sistema (toque alterna economia de energia / abre ajustes) ---
            Panel(
                "DIAGNÓSTICO",
                "ENERGIA: " + (if (device.saver) "ECONOMIA" else "NORMAL") + " ▸",
                modifier = Modifier.clickable { onPower() },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("RAM", "${device.ramPct}%", device.ramPct, device.ramPct > 85, Modifier.weight(1f))
                    Stat("ARMAZ.", "${device.diskPct}%", device.diskPct, device.diskPct > 90, Modifier.weight(1f))
                }
                Spacer(Modifier.height(5.dp))
                // Temperatura da bateria vem em décimos de grau Celsius (ex.: 320 -> 32°C).
                val tc = device.battTemp / 10
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("TEMP", "${tc}°C", tc * 100 / 50, tc > 40, Modifier.weight(1f))
                    Stat("BAT", "${device.battLevel}%", device.battLevel, device.battLevel <= 20, Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Spark(ramHist)
            }

            // --- Daemons: slots fixados com suporte a drag-and-drop (`from = 10 + i`) ---
            val dHover = host.info?.let { host.daemonSlotAt(it.pos) } ?: -1
            val dragFrom = host.info?.from ?: -2
            val filled = daemons.count { it != null }
            Panel("DAEMONS", "FIXADOS $filled/$DAEMON_SLOTS", accent = nc.c, tagColor = nc.y) {
                for (i in 0 until DAEMON_SLOTS) {
                    val app = daemons.getOrNull(i)?.let { apps[it] }
                    val hv = dHover == i
                    val fg = if (hv) Color.Black else nc.c
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Registra as coordenadas da linha no `DragHost` para detectar soltura sobre este slot.
                            .onGloballyPositioned { host.daemonRows[i] = it }
                            .then(if (hv) Modifier.background(nc.c) else Modifier)
                            .alpha(if (dragFrom == 10 + i) 0.3f else 1f)
                            .then(
                                if (app != null) {
                                    Modifier
                                        .dragSource(app.pkg, 10 + i, host)
                                        .clickable { if (!host.blockClicks) onOpen(app.pkg) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tx(
                            (i + 1).toString().padStart(2, '0'),
                            if (hv) Color.Black else nc.dim,
                            11,
                            mono = true,
                            modifier = Modifier.width(24.dp),
                        )
                        Box(Modifier.weight(1f)) {
                            Tx(
                                app?.label?.uppercase() ?: "[ LIVRE ]",
                                if (app != null) fg else if (hv) Color.Black else nc.off,
                                15,
                                true,
                                spacing = 1f,
                            )
                        }
                        if (app != null) {
                            Box(Modifier.border(1.dp, if (hv) Color.Black else nc.y).padding(horizontal = 5.dp)) {
                                Tx("RUN", if (hv) Color.Black else nc.y, 11, mono = true)
                            }
                        }
                    }
                }
                if (filled == 0) {
                    Tx(
                        "> segure um app na gaveta e solte aqui",
                        nc.dim,
                        10,
                        mono = true,
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                    )
                }
            }

            // --- Faixa de acesso rápido: Config do Launcher, Mapas e Termux ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, nc.y.copy(alpha = 0.27f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tx(
                    "[SYS//CONFIG]",
                    nc.y,
                    10,
                    true,
                    mono = true,
                    spacing = 0f,
                    modifier = Modifier
                        .clickable { onSettings() }
                        .padding(vertical = 2.dp)
                )
                Tx(
                    "[MAPS//NAV]",
                    nc.r,
                    10,
                    true,
                    mono = true,
                    spacing = 0f,
                    modifier = Modifier
                        .clickable { onMaps() }
                        .padding(vertical = 2.dp)
                )
                Tx(
                    "[TERMUX//SHELL]",
                    nc.c,
                    10,
                    true,
                    mono = true,
                    spacing = 0f,
                    modifier = Modifier
                        .clickable { onOpen("com.termux") }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

// Bloco individual de métrica (rótulo, valor textual e barra segmentada `Segs`).
@Composable
private fun Stat(label: String, value: String, pct: Int, hot: Boolean, modifier: Modifier) {
    val nc = LocalNc.current
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Tx(label, nc.y, 11, mono = true)
            Tx(value, nc.c, 11, mono = true)
        }
        Segs(pct, hot)
    }
}

// ---------------------------------------------------------------- dock

/**
 * Barra inferior fixa com botão para abrir a gaveta de apps ("APPS"), 4 slots fixáveis
 * por arrastar e soltar e suporte a gesto de arrastar para cima para abrir a gaveta.
 */
@Composable
fun Dock(
    slots: List<String?>,
    apps: Map<String, AppInfo>,
    hover: Int,
    host: DragHost,
    realIcons: Boolean,
    onApps: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeUp: () -> Unit = {},
) {
    val dragFrom = host.info?.from ?: -2
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { host.dockBounds = it.boundsInRoot() }
            .pointerInput(Unit) {
                var acc = 0f
                detectVerticalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = { if (acc < -120f) onSwipeUp() },
                    onDragCancel = { },
                    onVerticalDrag = { _, d -> acc += d },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        DockCell(Modifier.weight(1f), "▦", "APPS", null, false, false, realIcons) { onApps() }
        for (i in 0 until 4) {
            val pkg = slots.getOrNull(i)
            val app = pkg?.let { apps[it] }
            val m = if (app != null) {
                Modifier.weight(1f).alpha(if (dragFrom == i) 0.3f else 1f).dragSource(app.pkg, i, host)
            } else {
                Modifier.weight(1f)
            }
            DockCell(m, app?.mono ?: "+", app?.label ?: "LIVRE", app?.pkg, app == null, hover == i, realIcons) {
                if (app != null && !host.blockClicks) onOpen(app.pkg)
            }
        }
    }
}

// Célula visual de cada item da Dock (suporta ícone real do sistema ou caractere monoespaçado).
@Composable
private fun DockCell(
    modifier: Modifier,
    mono: String,
    label: String,
    pkg: String?,
    empty: Boolean,
    hover: Boolean,
    realIcons: Boolean,
    onClick: () -> Unit,
) {
    val nc = LocalNc.current
    val fg = if (hover) Color.Black else if (empty) nc.y.copy(alpha = 0.4f) else nc.y
    val shape = CutShape(7.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (hover) nc.y else nc.bg)
            .border(1.dp, fg, shape)
            .clickable { onClick() }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            if (realIcons && pkg != null && !hover) {
                AppIcon(pkg, 26.dp) { Tx(mono, fg, 17, true) }
            } else {
                Tx(mono, fg, 17, true)
            }
        }
        Tx(label.uppercase(), fg, 9, true, spacing = 1f, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

// Flag mutável compartilhada entre o detector inicial de toque e o `NestedScrollConnection`.
private class SwipeFlag {
    var bottom = false
}

/**
 * Painel de notificações ativas com três modos visuais ("compact", "hidden" e detalhado padrão),
 * descarte individual (`✕`), abertura do `PendingIntent`/app e limpeza em lote.
 */
@Composable
private fun NotifPanel(
    hasAccess: Boolean,
    items: List<NotifItem>,
    style: String,
    max: Int,
    onAccess: () -> Unit,
    onOpen: (NotifItem) -> Unit,
    onDismiss: (String) -> Unit,
    onClear: () -> Unit,
) {
    val nc = LocalNc.current
    Panel("NOTIFICAÇÕES", if (hasAccess) "${items.size} ATIVAS" else "SEM ACESSO") {
        if (!hasAccess) {
            Tx(
                "> toque para liberar o acesso a notificações",
                nc.dim, 11, mono = true, maxLines = 2,
                modifier = Modifier.clickable { onAccess() }.padding(4.dp),
            )
        } else if (items.isEmpty()) {
            Tx("> nenhuma notificação", nc.dim, 12, mono = true, modifier = Modifier.padding(4.dp))
        }
        items.take(max).forEachIndexed { i, n ->
            val hot = i == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (hot) Modifier.background(nc.y.copy(alpha = 0.2f)) else Modifier)
                    .clickable { onOpen(n) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    when (style) {
                        "compact" -> Tx(
                            n.app.uppercase() + (if (n.title.isNotBlank()) " // " + n.title else ""),
                            if (hot) Color.White else nc.y, 13, true, spacing = 1f,
                        )
                        "hidden" -> Tx(n.app.uppercase(), if (hot) Color.White else nc.y, 13, true, spacing = 1f)
                        else -> Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Tx(n.app.uppercase(), nc.c, 10, mono = true)
                                Tx(ago(n.time), nc.dim, 10, mono = true)
                            }
                            Tx(n.title.ifBlank { "(sem título)" }, if (hot) Color.White else nc.y, 14, true)
                            if (n.text.isNotBlank()) Tx(n.text, nc.dim, 10, mono = true, maxLines = 2)
                        }
                    }
                }
                if (n.clearable) {
                    Tx("✕", nc.c, 12, true, mono = true, modifier = Modifier.clickable { onDismiss(n.key) }.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        if (items.size > max) {
            Tx("+${items.size - max} ...", nc.dim, 10, mono = true, modifier = Modifier.padding(start = 6.dp))
        }
        if (items.size > 1) {
            Tx("[ LIMPAR TUDO ]", nc.c, 10, true, mono = true, modifier = Modifier.clickable { onClear() }.padding(6.dp))
        }
    }
}