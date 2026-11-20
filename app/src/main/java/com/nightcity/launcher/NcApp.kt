package com.nightcity.launcher

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class Scr { HOME, DRAWER, SETTINGS }

private fun granted(ctx: android.content.Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

@Composable
fun NcRoot(app: AppState, requestPerms: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    var palKey by remember { mutableStateOf(prefs.palKey) }
    var navPos by remember { mutableStateOf(prefs.navPos) }
    var guide by remember { mutableStateOf(prefs.guide) }
    var iconMode by remember { mutableStateOf(prefs.iconMode) }
    var edgeInset by remember { mutableStateOf(prefs.edgeInset) }
    var lat by remember { mutableStateOf(prefs.lat) }
    var lon by remember { mutableStateOf(prefs.lon) }
    var customPals by remember { mutableStateOf(prefs.customPalettes()) }
    var notifStyle by remember { mutableStateOf(prefs.notifStyle) }
    var notifMax by remember { mutableStateOf(prefs.notifMax) }
    var filterMode by remember { mutableStateOf(prefs.filterMode) }
    var filterLevel by remember { mutableStateOf(prefs.filterLevel) }
    var fxMode by remember { mutableStateOf(prefs.fxMode) }
    var fxSecs by remember { mutableStateOf(prefs.fxSecs) }
    var fxWake by remember { mutableStateOf(prefs.fxWake) }
    var fxOver by remember { mutableStateOf(prefs.fxOver) }
    val nc = paletteFrom(palKey, customPals)

    val device = remember { DeviceState(ctx) }
    val media = remember { MediaState(ctx) }
    DisposableEffect(Unit) {
        device.start()
        onDispose {
            device.stop()
            media.stop()
        }
    }
    LaunchedEffect(app.resumeTick) {
        device.refresh()
        media.start()
    }
    // garante que o preto e branco do sistema esteja de acordo com o modo escolhido
    LaunchedEffect(app.resumeTick) {
        applySystemMono(ctx, filterMode == "mono")
    }
    // serviço dos botões marcado como ligado mas parado (ex.: depois de limpar os recentes): tenta religar
    LaunchedEffect(app.resumeTick) {
        delay(1500)
        if (navServiceEnabled(ctx) && !NavState.running) reviveNavService(ctx)
    }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(app.resumeTick) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
    }
    val allApps = remember(apps) { (apps + ConfigApp).sortedBy { it.label.lowercase() } }
    val byPkg = remember(allApps) { allApps.associateBy { it.pkg } }

    var scr by remember { mutableStateOf(Scr.HOME) }
    LaunchedEffect(app.homeSignal) { scr = Scr.HOME }
    BackHandler(enabled = true) { if (scr != Scr.HOME) scr = Scr.HOME }

    var dock by remember { mutableStateOf(prefs.dock()) }
    var recents by remember { mutableStateOf(prefs.recents()) }
    var daemons by remember { mutableStateOf(prefs.daemons()) }
    val homeScroll = rememberScrollState()

    fun openApp(pkg: String) {
        if (pkg == CONFIG_PKG) {
            scr = Scr.SETTINGS
            return
        }
        if (launchApp(ctx, pkg)) {
            val r = (listOf(pkg to System.currentTimeMillis()) + recents.filter { it.first != pkg }).take(10)
            recents = r
            prefs.saveRecents(r)
        }
    }

    // toque no relógio: abre o app Relógio/Alarmes (e, se não houver, tenta os pacotes conhecidos)
    fun openClock() {
        try {
            val i = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            listOf("com.android.deskclock", "com.google.android.deskclock").firstOrNull { launchApp(ctx, it) }
        }
    }

    // toque na data: abre o app Agenda padrão
    fun openCalendar() {
        try {
            val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            listOf("com.google.android.calendar", "com.android.calendar").firstOrNull { launchApp(ctx, it) }
        }
    }

    // [ MAPS // NAV ]: abre o app de mapas padrão
    fun openMaps() {
        try {
            val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            try {
                val g = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(g)
            } catch (e2: Exception) {
                launchApp(ctx, "com.google.android.apps.maps")
            }
        }
    }

    // toque no DIAGNÓSTICO: alterna a economia de bateria (se o adb liberou) ou abre a tela do sistema
    fun powerAction() {
        if (!setBatterySaver(ctx, !device.saver)) {
            safeStart(ctx, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
        device.refresh()
    }

    // histórico de RAM para o gráfico
    val ramHist = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) {
        while (true) {
            device.pollStats()
            ramHist.add(device.ramPct)
            if (ramHist.size > 30) ramHist.removeAt(0)
            delay(2000)
        }
    }

    // previsão do tempo (atualiza a cada 30 min)
    var wx by remember { mutableStateOf<WxData?>(null) }
    var wxMsg by remember { mutableStateOf("CARREGANDO...") }
    LaunchedEffect(app.resumeTick, lat, lon) {
        delay(500)
        while (true) {
            val (d, m) = fetchWeather(ctx, lat, lon)
            if (d != null) wx = d
            wxMsg = if (d == null) m else ""
            delay(30 * 60 * 1000L)
        }
    }

    // arrastar e soltar na dock
    val host = remember { DragHost() }
    host.onDrop = { i ->
        val ds = host.daemonSlotAt(i.pos)
        val ks = slotAt(host.dockBounds, i.pos)
        val fromDock = i.from in 0..3
        val fromDaemon = i.from >= 10
        val dk = dock.toMutableList()
        val dm = daemons.toMutableList()
        when {
            // soltou na gaveta DAEMONS
            ds >= 0 -> {
                if (fromDaemon) {
                    val src = i.from - 10
                    val o = dm[ds]
                    dm[ds] = i.pkg
                    if (ds != src) dm[src] = o
                } else {
                    val p = dm.indexOf(i.pkg)
                    if (p >= 0) dm[p] = null
                    dm[ds] = i.pkg
                }
                if (i.from < 0) scr = Scr.HOME
            }
            // soltou na dock
            ks >= 0 -> {
                if (fromDock) {
                    val o = dk[ks]
                    dk[ks] = i.pkg
                    if (ks != i.from) dk[i.from] = o
                } else {
                    val p = dk.indexOf(i.pkg)
                    if (p >= 0) dk[p] = null
                    dk[ks] = i.pkg
                    if (i.from < 0) scr = Scr.HOME
                }
            }
            // soltou fora: remove da lista de onde saiu
            fromDock -> dk[i.from] = null
            fromDaemon -> dm[i.from - 10] = null
        }
        dock = dk
        daemons = dm
        prefs.saveDock(dk)
        prefs.saveDaemons(dm)
    }
    // ao começar a arrastar, rola a tela inicial até a gaveta DAEMONS para ela ficar à vista
    val dragging = host.info != null
    LaunchedEffect(dragging) {
        if (dragging) homeScroll.animateScrollTo(homeScroll.maxValue)
    }
    val hover = host.info?.let { slotAt(host.dockBounds, it.pos) } ?: -1
    val realIcons = iconMode == "real"

    val settings = SettingsState(
        palKey = palKey,
        custom = customPals,
        navPos = navPos,
        guide = guide,
        iconMode = iconMode,
        edgeInset = edgeInset,
        lat = lat,
        lon = lon,
        notifStyle = notifStyle,
        notifMax = notifMax,
        filterMode = filterMode,
        filterLevel = filterLevel,
        fxMode = fxMode,
        fxSecs = fxSecs,
        fxWake = fxWake,
        fxOver = fxOver,
        notifOk = media.hasAccess,
        a11yOk = remember(app.resumeTick) { navServiceEnabled(ctx) },
        a11yRunning = NavState.running,
        locOk = remember(app.resumeTick) { granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) },
        phoneOk = remember(app.resumeTick) { granted(ctx, Manifest.permission.READ_PHONE_STATE) },
        secureOk = remember(app.resumeTick) { canWriteSecure(ctx) },
    )
    val actions = SettingsActions(
        onClose = { scr = Scr.HOME },
        onPal = { palKey = it; prefs.palKey = it },
        onSavePalette = { p ->
            val l = customPals.filter { it.key != p.key } + p
            customPals = l
            prefs.saveCustomPalettes(l)
            palKey = p.key
            prefs.palKey = p.key
        },
        onDeletePalette = { k ->
            val l = customPals.filter { it.key != k }
            customPals = l
            prefs.saveCustomPalettes(l)
            if (palKey == k) {
                palKey = "night"
                prefs.palKey = "night"
            }
        },
        onPos = { navPos = it; prefs.navPos = it },
        onGuide = { guide = it; prefs.guide = it },
        onIconMode = { iconMode = it; prefs.iconMode = it },
        onInset = { edgeInset = it; prefs.edgeInset = it },
        onLat = { lat = it; prefs.lat = it },
        onLon = { lon = it; prefs.lon = it },
        onNotifStyle = { notifStyle = it; prefs.notifStyle = it },
        onNotifMax = { notifMax = it; prefs.notifMax = it },
        onFilterMode = {
            filterMode = it
            prefs.filterMode = it
            // preto e branco real (se a permissão por adb existir); os demais modos são desenhados pelo serviço
            applySystemMono(ctx, it == "mono")
        },
        onFilterLevel = { filterLevel = it; prefs.filterLevel = it },
        onFxMode = { fxMode = it; prefs.fxMode = it },
        onFxSecs = { fxSecs = it; prefs.fxSecs = it },
        onFxWake = { fxWake = it; prefs.fxWake = it },
        onFxOver = { fxOver = it; prefs.fxOver = it },
        testFx = { sec -> NavState.service?.testFx(sec * 1000L) },
        copyAdb = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("adb", ADB_CMD))
        },
        openNotif = { safeStart(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        openA11y = { safeStart(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        reviveNav = {
            if (!reviveNavService(ctx)) safeStart(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            app.resumeTick++
        },
        askLoc = { requestPerms(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
        askPhone = { requestPerms(arrayOf(Manifest.permission.READ_PHONE_STATE)) },
        openHome = { safeStart(ctx, Intent(Settings.ACTION_HOME_SETTINGS)) },
    )

    val recentApps = recents.mapNotNull { (p, t) -> byPkg[p]?.let { it to t } }

    CompositionLocalProvider(LocalNc provides nc) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(nc.bg2, nc.bg)))) {
            HomeScreen(
                device = device,
                media = media,
                wx = wx,
                wxMsg = wxMsg,
                recents = recentApps,
                ramHist = ramHist,
                onOpen = { openApp(it) },
                onSettings = { scr = Scr.SETTINGS },
                onClock = { openClock() },
                onMediaAccess = { safeStart(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                onSwipeUp = { scr = Scr.DRAWER },
                daemons = daemons,
                apps = byPkg,
                host = host,
                scroll = homeScroll,
                onCalendar = { openCalendar() },
                onMaps = { openMaps() },
                onPower = { powerAction() },
                notifs = NotifStore.items,
                notifStyle = notifStyle,
                notifMax = notifMax,
                onNotifOpen = { n ->
                    try {
                        n.intent?.send() ?: launchApp(ctx, n.pkg)
                    } catch (e: Exception) {
                        launchApp(ctx, n.pkg)
                    }
                },
                onNotifDismiss = { NotifStore.dismiss(it) },
                onNotifClear = { NotifStore.dismissAll() },
            )

            AnimatedVisibility(
                visible = scr == Scr.DRAWER,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Box(Modifier.alpha(if (host.info != null) 0.16f else 1f)) {
                    Drawer(
                        apps = allApps,
                        host = host,
                        realIcons = realIcons,
                        onClose = { scr = Scr.HOME },
                        onOpen = { openApp(it) },
                    )
                }
            }

            // a dock fica por cima da gaveta só enquanto um app está sendo arrastado
            if (scr == Scr.HOME || host.info != null) {
                Dock(
                    slots = dock,
                    apps = byPkg,
                    hover = hover,
                    host = host,
                    realIcons = realIcons,
                    onApps = { scr = Scr.DRAWER },
                    onSwipeUp = { scr = Scr.DRAWER },
                    onOpen = { openApp(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .systemBarsPadding()
                        .padding(horizontal = 11.dp, vertical = 12.dp),
                )
            }

            AnimatedVisibility(
                visible = scr == Scr.SETTINGS,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                SettingsScreen(settings, actions)
            }

            // ícone fantasma durante o arrasto
            host.info?.let { i ->
                val a = byPkg[i.pkg]
                Box(
                    Modifier
                        .offset { IntOffset((i.pos.x - 28.dp.toPx()).roundToInt(), (i.pos.y - 28.dp.toPx()).roundToInt()) }
                        .size(56.dp)
                        .clip(CutShape(9.dp))
                        .background(nc.y),
                    contentAlignment = Alignment.Center,
                ) { Tx(a?.mono ?: "??", Color.Black, 18, true) }
            }

            // linhas de varredura (decorativas, não recebem toque)
            Canvas(Modifier.fillMaxSize()) {
                val step = 3.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawRect(Color(0x44000000), Offset(0f, y), Size(size.width, 1f))
                    y += step
                }
            }
        }
    }
}