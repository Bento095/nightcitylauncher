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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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

/**
 * Destinos possíveis dentro do launcher:
 * - HOME: tela principal com mídia, clima, quickhacks, diagnóstico e daemons
 * - DRAWER: gaveta completa de aplicativos instalados
 * - SETTINGS: tela de configurações de tema, navegação, notificações, filtros e efeitos
 */
enum class Scr { HOME, DRAWER, SETTINGS, VAULT }

// Helper rápido para checar se uma permissão perigosa/runtime está concedida no contexto atual.
private fun granted(ctx: android.content.Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

/**
 * Composable raiz do launcher.
 * Centraliza preferências persistidas, estados de hardware/mídia/clima,
 * ações de navegação/atalhos, lógica de drag-and-drop e camadas de overlay.
 */
@Composable
fun NcRoot(app: AppState, requestPerms: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    // Instância de preferências memorizada para evitar recriar SharedPreferences a cada recomposição.
    val prefs = remember { Prefs(ctx) }

    // --- Estados reativos espelhando as configurações salvas em `Prefs` ---
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
    var wpTarget by remember { mutableStateOf(prefs.wpTarget) }

    // Resolve a paleta ativa (padrão ou customizada) a partir da chave atual.
    val nc = paletteFrom(palKey, customPals)

    // Atualiza automaticamente o wallpaper da tela de bloqueio com a paleta ativa.
    LaunchedEffect(palKey, customPals, wpTarget) {
        applyNcLockWallpaper(ctx, nc, wpTarget)
    }

    // --- Estados de hardware (RAM, bateria, rede, sensores) e controle de mídia ---
    val device = remember { DeviceState(ctx) }
    val media = remember { MediaState(ctx) }

    // Inicia o monitoramento do dispositivo ao montar e encerra listeners/recursos ao desmontar.
    DisposableEffect(Unit) {
        device.start()
        onDispose {
            device.stop()
            media.stop()
        }
    }

    // Sempre que `app.resumeTick` muda (ex.: retorno ao app ou resposta de permissão),
    // atualiza os dados do dispositivo e reativa o listener de mídia.
    LaunchedEffect(app.resumeTick) {
        device.refresh()
        media.start()
    }

    // Garante que o filtro monocromático do sistema reflita a configuração `filterMode`.
    LaunchedEffect(app.resumeTick) {
        applySystemMono(ctx, filterMode == "mono")
    }

    // Serviço dos botões marcado como ligado mas parado (ex.: depois de limpar os recentes): tenta religar.
    LaunchedEffect(app.resumeTick) {
        delay(1500)
        if (navServiceEnabled(ctx) && !NavState.running) reviveNavService(ctx)
    }

    // --- Lista de aplicativos instalados + app virtual de configurações ---
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(app.resumeTick) {
        // Carrega os pacotes em thread de I/O para não travar a UI.
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
    }
    val allApps = remember(apps) { (apps + ConfigApp).sortedBy { it.label.lowercase() } }
    // Mapa de acesso rápido por packageName para resolver nomes/ícones em O(1).
    val byPkg = remember(allApps) { allApps.associateBy { it.pkg } }

    // --- Navegação interna entre telas e interceptação do botão Voltar ---
    var scr by remember { mutableStateOf(Scr.HOME) }
    // Ao receber sinal de Home (ex.: pressionar Home estando no launcher), retorna para `Scr.HOME`.
    LaunchedEffect(app.homeSignal) { scr = Scr.HOME }
    // Bloqueia saída acidental da home pelo botão Voltar; se estiver em sub-tela, volta para a home.
    BackHandler(enabled = true) { if (scr != Scr.HOME) scr = Scr.HOME }

    // --- Listas persistidas de atalhos: Dock, Recentes (Quickhacks) e Daemons ---
    var dock by remember { mutableStateOf(prefs.dock()) }
    var recents by remember { mutableStateOf(prefs.recents()) }
    var daemons by remember { mutableStateOf(prefs.daemons()) }
    var vault by remember { mutableStateOf(prefs.vaultEntries()) }
    val homeScroll = rememberScrollState()

    // Abre um aplicativo pelo packageName; se for o app virtual de config, abre `Scr.SETTINGS`,
    // caso contrário registra nos recentes (até 10 itens) e persiste.
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

    // Toque no relógio: abre o app Relógio/Alarmes (e, se não houver, tenta os pacotes conhecidos).
    fun openClock() {
        try {
            val i = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            listOf("com.android.deskclock", "com.google.android.deskclock").firstOrNull { launchApp(ctx, it) }
        }
    }

    // Toque na data: abre o app Agenda padrão.
    fun openCalendar() {
        try {
            val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            listOf("com.google.android.calendar", "com.android.calendar").firstOrNull { launchApp(ctx, it) }
        }
    }

    // [ MAPS // NAV ]: abre o app de mapas padrão (com fallback para URI `geo:` e Google Maps).
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

    // Toque no DIAGNÓSTICO: alterna a economia de bateria (se o adb liberou) ou abre a tela do sistema.
    fun powerAction() {
        if (!setBatterySaver(ctx, !device.saver)) {
            safeStart(ctx, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
        device.refresh()
    }

    // Histórico de uso de RAM (últimas 30 amostras, coletadas a cada 2s) para o sparkline gráfico.
    val ramHist = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) {
        while (true) {
            device.pollStats()
            ramHist.add(device.ramPct)
            if (ramHist.size > 30) ramHist.removeAt(0)
            delay(2000)
        }
    }

    // Previsão do tempo (atualiza a cada 30 min com base na latitude/longitude configuradas).
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

    // --- Sistema unificado de arrastar e soltar (Dock e Daemons) ---
    // Convenção de `from`: < 0 = veio da gaveta; 0..3 = slot da Dock; >= 10 = `10 + slotIndex` nos Daemons.
    val host = remember { DragHost() }
    host.onDrop = { i ->
        val ds = host.daemonSlotAt(i.pos)
        val ks = slotAt(host.dockBounds, i.pos)
        val fromDock = i.from in 0..3
        val fromDaemon = i.from >= 10
        val dk = dock.toMutableList()
        val dm = daemons.toMutableList()
        when {
            // Soltou na gaveta DAEMONS: reordena entre slots ou fixa/move um app para o slot alvo.
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
            // Soltou na dock: reordena entre slots da dock ou fixa um app vindo de fora.
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
            // Soltou fora de qualquer alvo válido: remove do slot de origem (se veio da Dock ou Daemons).
            fromDock -> dk[i.from] = null
            fromDaemon -> dm[i.from - 10] = null
        }
        dock = dk
        daemons = dm
        prefs.saveDock(dk)
        prefs.saveDaemons(dm)
    }

    // Ao começar a arrastar, rola a tela inicial até o fim para deixar a gaveta DAEMONS visível.
    val dragging = host.info != null
    LaunchedEffect(dragging) {
        if (dragging) homeScroll.animateScrollTo(homeScroll.maxValue)
    }

    // Índice do slot da Dock sob o dedo durante o arrasto (-1 se fora).
    val hover = host.info?.let { slotAt(host.dockBounds, it.pos) } ?: -1
    val realIcons = iconMode == "real"

    // --- Pacote de estado e callbacks para a tela de Configurações ---
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
        wpTarget = wpTarget,
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
            // Preto e branco real (se a permissão por adb existir); os demais modos são desenhados pelo serviço.
            applySystemMono(ctx, it == "mono")
        },
        onFilterLevel = { filterLevel = it; prefs.filterLevel = it },
        onFxMode = { fxMode = it; prefs.fxMode = it },
        onFxSecs = { fxSecs = it; prefs.fxSecs = it },
        onFxWake = { fxWake = it; prefs.fxWake = it },
        onFxOver = { fxOver = it; prefs.fxOver = it },
        onWpTarget = { wpTarget = it; prefs.wpTarget = it },
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

    // Resolve os pares (AppInfo, timestamp) dos recentes ignorando pacotes não encontrados.
    val recentApps = recents.mapNotNull { (p, t) -> byPkg[p]?.let { it to t } }

    // --- Fornece a paleta ativa via CompositionLocal e monta as camadas da UI ---
    CompositionLocalProvider(LocalNc provides nc) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(nc.bg2, nc.bg)))) {
            // 1. Tela inicial principal
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
                onSwipeLeft = { scr = Scr.VAULT },
                daemons = daemons,
                apps = byPkg,
                host = host,
                scroll = homeScroll,
                onCalendar = { openCalendar() },
                onMaps = { openMaps() },
                onPower = { powerAction() },
                onStorage = { openStorageSettings(ctx) },
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

            // 2. Gaveta de apps (desliza de baixo para cima; fica semitransparente durante drag)
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
                        onManage = { openAppInfo(ctx, it) },
                    )
                }
            }

            // 3. Dock fixa na base (fica por cima da gaveta apenas enquanto um app está sendo arrastado)
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

            // 4. Tela de configurações
            AnimatedVisibility(
                visible = scr == Scr.SETTINGS,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                SettingsScreen(settings, actions)
            }

            // 4b. Cofre (Vault): abre deslizando a Home para a esquerda
            AnimatedVisibility(
                visible = scr == Scr.VAULT,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                VaultScreen(
                    entries = vault,
                    onSaveEntry = { e ->
                        val l = listOf(e) + vault.filter { it.id != e.id }
                        vault = l
                        prefs.saveVaultEntries(l)
                    },
                    onDeleteEntry = { id ->
                        val l = vault.filter { it.id != id }
                        vault = l
                        prefs.saveVaultEntries(l)
                    },
                    onBackHome = { scr = Scr.HOME },
                )
            }

            // 5. Ícone fantasma que acompanha o dedo durante um gesto de arrastar
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

            // 6. Overlay decorativo de scanlines (linhas horizontais sutis, sem interceptar toques)
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