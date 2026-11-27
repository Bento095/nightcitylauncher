package com.nightcity.launcher

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap

fun navServiceEnabled(ctx: Context): Boolean {
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val me = ComponentName(ctx, NavService::class.java).flattenToString()
    return s.split(':').any { it.equals(me, ignoreCase = true) }
}

/** True enquanto o serviço está realmente em execução (cai para false se o processo for encerrado). */
object NavState {
    @Volatile
    var running = false

    /** Instância ativa do serviço (usada pelo painel de notificações e pelo efeito de notificação). */
    @Volatile
    var service: NavService? = null
}

/**
 * Reativa o serviço quando ele está marcado como ligado nas configurações mas parou de rodar
 * (por exemplo, depois de limpar os apps recentes). Só funciona com a permissão opcional
 * WRITE_SECURE_SETTINGS concedida por adb. Devolve false se não pôde tentar.
 */
fun reviveNavService(ctx: Context): Boolean {
    if (!canWriteSecure(ctx)) return false
    return try {
        val cr = ctx.contentResolver
        val me = ComponentName(ctx, NavService::class.java).flattenToString()
        val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val base = cur.split(':').filter { it.isNotBlank() && !it.equals(me, ignoreCase = true) }.joinToString(":")
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, base)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val full = if (base.isEmpty()) me else "$base:$me"
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, full)
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } catch (e: Exception) {
                Log.w("NcNav", "falha ao religar o serviço", e)
            }
        }, 400)
        true
    } catch (e: Exception) {
        false
    }
}

fun safeStart(ctx: Context, i: Intent) {
    try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: Exception) { }
}

/**
 * Faixa invisível numa borda da tela: deslizar para dentro mostra os botões
 * Voltar / Início / Recentes por cima de qualquer app. Some sozinho em ~3 s.
 */
class NavService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val h = Handler(Looper.getMainLooper())
    private var trigger: View? = null
    private var bar: View? = null
    private var filterView: View? = null
    private var fxView: View? = null
    private var bannerView: View? = null
    private var recTrigger: View? = null
    private var recView: View? = null
    private var rsx = 0f
    private var rarmed = false
    private val recHideRun = Runnable { hideRecents() }
    private val hideRun = Runnable { hideBar() }
    private val navKeys = setOf("navPos", "palKey", "guide", "edgeInset", "recSide")
    private val filterKeys = setOf("filterMode", "filterLevel", "palKey")
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key in navKeys) h.post { install() }
        if (key != null && key in filterKeys) h.post { applyFilter() }
    }

    private var sx = 0f
    private var sy = 0f
    private var armed = false
    private var lastInstall = 0L

    // vigia: se a faixa de gesto sumir da tela, recria
    private val watchdog = object : Runnable {
        override fun run() {
            ensureTrigger()
            ensureFilter()
            h.postDelayed(this, 4000)
        }
    }

    private fun ensureTrigger() {
        if (!::wm.isInitialized || !::prefs.isInitialized) return
        if (trigger?.isAttachedToWindow == true && (recSide() == null || recTrigger?.isAttachedToWindow == true)) return
        if (System.currentTimeMillis() - lastInstall < 1500) return
        Log.d("NcNav", "faixa de gesto ausente: recriando")
        install()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Topo da janela para o CENTRO ficar a 45% da altura (segundo terço, um pouco acima do meio). */
    private fun sideTop(h: Int): Int = (resources.displayMetrics.heightPixels * 0.45f).toInt() - h / 2

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(listener)
        NavState.running = true
        NavState.service = this
        Log.d("NcNav", "serviço conectado")
        applyFilter()
        install()
        h.removeCallbacks(watchdog)
        h.postDelayed(watchdog, 4000)
    }

    // qualquer troca de janela (abrir/fechar recentes, voltar ao início...) confere a faixa de gesto
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        ensureTrigger()
    }

    override fun onInterrupt() { }

    // mudança de orientação/densidade: as posições dependem do tamanho da tela
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::prefs.isInitialized && ::wm.isInitialized) h.post { install() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("NcNav", "serviço desconectado (unbind)")
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d("NcNav", "serviço destruído")
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        NavState.running = false
        NavState.service = null
        removeFx()
        h.removeCallbacks(watchdog)
        try { prefs.sp.unregisterOnSharedPreferenceChangeListener(listener) } catch (e: Exception) { }
        remove()
        removeFilter()
    }

    // ---------------------------------------------------------------- efeito de notificação

    private fun dndOn(): Boolean = try {
        val f = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter
        f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    } catch (e: Exception) {
        false
    }

    /** Apaga a tela de novo (usado depois que o efeito acendeu a tela apagada). */
    fun lockScreen() {
        if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    /** Toca o efeito de teste depois de [delayMs] (dá tempo de apagar a tela para testar o modo "acordar"). */
    fun testFx(delayMs: Long) {
        h.postDelayed({ playNotifFx("TESTE", "Efeito de notificação", force = true) }, delayMs)
    }

    /**
     * Tela ligada: desenha o efeito por cima do app atual.
     * Tela apagada: acende a tela com uma tela preta (OLED), mostra o efeito e apaga de novo.
     */
    fun playNotifFx(label: String, title: String, force: Boolean = false, key: String? = null) {
        h.post {
            if (!::prefs.isInitialized || !::wm.isInitialized) return@post
            if (!force && (prefs.fxMode == "off" || dndOn())) return@post
            val mode = if (prefs.fxMode == "off") "glitch" else prefs.fxMode
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isInteractive) {
                if (force || prefs.fxOver) showFxOverlay(mode, label, title, key)
            } else if (force || prefs.fxWake) {
                val i = Intent(this, NotifFxActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra("mode", mode)
                    .putExtra("label", label)
                    .putExtra("title", title)
                    .putExtra("secs", prefs.fxSecs)
                    .putExtra("key", key)
                try {
                    startActivity(i)
                } catch (e: Exception) {
                    Log.w("NcNav", "não foi possível acender a tela para o efeito", e)
                }
            }
        }
    }

    private fun showFxOverlay(mode: String, label: String, title: String, key: String? = null) {
        removeFx()
        removeBanner()
        var ref: View? = null
        val v = FxView(this, mode, prefs.currentPalette(), prefs.fxSecs, label, title, false) {
            h.post { if (fxView === ref) removeFx() }
        }
        ref = v
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            wm.addView(v, lp)
            fxView = v
            if (key != null) showNotifBanner(key, label, title)
        } catch (e: Exception) {
            Log.w("NcNav", "não foi possível desenhar o efeito", e)
        }
    }

    /**
     * Cartão tocável que aparece junto do efeito: toque = abre a notificação; ✕ = dispensa.
     * É uma janela separada e pequena, para o efeito de tela cheia continuar sem interceptar toques.
     */
    private fun showNotifBanner(key: String, label: String, title: String) {
        removeBanner()
        val item = NotifStore.items.firstOrNull { it.key == key } ?: return
        val pal = prefs.currentPalette()
        val d = resources.displayMetrics.density
        val yel = pal.y.toArgb()
        val cyan = pal.c.toArgb()
        val mono = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)

        fun tv(text: String, sp: Float, color: Int) = android.widget.TextView(this).apply {
            this.text = text
            textSize = sp
            setTextColor(color)
            typeface = mono
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv("> " + label.uppercase(), 11f, cyan))
            addView(tv(title.ifBlank { item.title.ifBlank { "(sem título)" } }, 14f, android.graphics.Color.WHITE).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        val close = tv("✕", 16f, yel).apply {
            setPadding((14 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0xEE0A0A0F.toInt())
                setStroke((1.5f * d).toInt(), yel)
                cornerRadius = 3 * d
            }
            addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (item.clearable) addView(close)
            setOnClickListener {
                removeBanner()
                NotifStore.open(this@NavService, item)
            }
        }
        close.setOnClickListener {
            removeBanner()
            NotifStore.dismiss(item.key)
        }

        val dm = resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            (dm.widthPixels - 32 * d).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (dm.heightPixels * 0.19f).toInt()
        try {
            wm.addView(card, lp)
            bannerView = card
            // Fica um pouco além do efeito, para dar tempo de tocar.
            h.postDelayed({ if (bannerView === card) removeBanner() }, prefs.fxSecs.coerceAtLeast(4) * 1000L + 2500L)
        } catch (e: Exception) {
            Log.w("NcNav", "não foi possível mostrar o cartão da notificação", e)
        }
    }

    private fun removeBanner() {
        bannerView?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        bannerView = null
    }

    private fun removeFx() {
        fxView?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        fxView = null
    }

    // ---------------------------------------------------------------- filtro de tela

    /** Aplica o modo escolhido em Config > Filtros (cobertura colorida por cima dos outros apps). */
    private fun applyFilter() {
        if (!::wm.isInitialized || !::prefs.isInitialized) return
        val spec = filterSpec(this, prefs)
        if (spec == null) {
            removeFilter()
            return
        }
        val a = (spec.alpha * 255f).toInt().coerceIn(0, 255)
        val c = (spec.color and 0x00FFFFFF) or (a shl 24)
        val cur = filterView
        if (cur != null && cur.isAttachedToWindow) {
            cur.setBackgroundColor(c)
            return
        }
        removeFilter()
        val v = View(this)
        v.setBackgroundColor(c)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            wm.addView(v, lp)
            filterView = v
        } catch (e: Exception) {
            Log.w("NcNav", "não foi possível desenhar o filtro", e)
        }
    }

    private fun removeFilter() {
        filterView?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        filterView = null
    }

    private fun ensureFilter() {
        if (!::wm.isInitialized || !::prefs.isInitialized) return
        val need = filterSpec(this, prefs) != null
        if (need && filterView?.isAttachedToWindow != true) applyFilter()
    }

    private fun remove() {
        h.removeCallbacks(hideRun)
        bar?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        bar = null
        trigger?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        trigger = null
        recTrigger?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        recTrigger = null
        h.removeCallbacks(recHideRun)
        recView?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        recView = null
    }

    private val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun install() {
        remove()
        val nc = prefs.currentPalette()
        val pos = prefs.navPos
        val inset = dp(prefs.edgeInset)
        val v = EdgeView(this, nc.c.toArgb(), prefs.guide, pos)
        v.setOnTouchListener { _, e -> onTouch(e) }
        val w = if (pos == "b") dp(220) else dp(26)
        val hh = if (pos == "b") dp(28) else dp(220)
        val lp = WindowManager.LayoutParams(
            w, hh, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT,
        )
        when (pos) {
            "l" -> { lp.gravity = Gravity.START or Gravity.TOP; lp.x = inset; lp.y = sideTop(hh) }
            "r" -> { lp.gravity = Gravity.END or Gravity.TOP; lp.x = inset; lp.y = sideTop(hh) }
            else -> { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.y = inset }
        }
        try {
            wm.addView(v, lp)
            trigger = v
            lastInstall = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w("NcNav", "não foi possível desenhar a faixa de gesto", e)
        }
        installRecents()
    }

    private fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.rawX
                sy = e.rawY
                armed = true
            }
            MotionEvent.ACTION_MOVE -> if (armed) {
                val d = when (prefs.navPos) {
                    "l" -> e.rawX - sx
                    "r" -> sx - e.rawX
                    else -> sy - e.rawY
                }
                if (d > dp(28)) {
                    armed = false
                    showBar()
                }
            }
            MotionEvent.ACTION_UP -> if (armed) {
                armed = false
                if (bar != null) hideBar() else showBar()
            }
            MotionEvent.ACTION_CANCEL -> armed = false
        }
        return true
    }

    // ---------------------------------------------------------------- atalho de recentes

    /** Lado do atalho de recentes: "l", "r" ou null (desligado). Automático = oposto à barra; barra embaixo = esquerda. */
    private fun recSide(): String? = when (prefs.recSide) {
        "off" -> null
        "l" -> "l"
        "r" -> "r"
        else -> if (prefs.navPos == "l") "r" else "l"
    }

    private fun installRecents() {
        val side = recSide() ?: return
        val nc = prefs.currentPalette()
        val v = EdgeView(this, nc.r.toArgb(), prefs.guide, side)
        v.setOnTouchListener { _, e -> onRecTouch(e, side) }
        val hh = dp(220)
        val lp = WindowManager.LayoutParams(
            dp(26), hh, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT,
        )
        lp.gravity = (if (side == "l") Gravity.START else Gravity.END) or Gravity.TOP
        lp.x = dp(prefs.edgeInset)
        // se a barra de navegação estiver no mesmo lado, este atalho fica logo abaixo dela
        lp.y = sideTop(hh) + if (prefs.navPos == side) hh + dp(8) else 0
        try {
            wm.addView(v, lp)
            recTrigger = v
        } catch (e: Exception) {
            Log.w("NcNav", "não foi possível desenhar a faixa de recentes", e)
        }
    }

    private fun onRecTouch(e: MotionEvent, side: String): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rsx = e.rawX
                rarmed = true
            }
            MotionEvent.ACTION_MOVE -> if (rarmed) {
                val d = if (side == "l") e.rawX - rsx else rsx - e.rawX
                if (d > dp(28)) {
                    rarmed = false
                    showRecents(side)
                }
            }
            MotionEvent.ACTION_UP -> if (rarmed) {
                rarmed = false
                if (recView != null) hideRecents() else showRecents(side)
            }
            MotionEvent.ACTION_CANCEL -> rarmed = false
        }
        return true
    }

    private fun agoText(ms: Long): String {
        val m = ms / 60000L
        return when {
            m < 1 -> "AGORA"
            m < 60 -> "$m MIN"
            m < 1440 -> "${m / 60} H"
            else -> "${m / 1440} D"
        }
    }

    /** Apps recentes do launcher (até 8), já com nome, tempo e ícone; ignora os que não existem mais. */
    private fun recentItems(): List<RecItem> {
        val pm = packageManager
        val now = System.currentTimeMillis()
        val real = prefs.iconMode == "real"
        val out = ArrayList<RecItem>()
        for ((pkg, t) in prefs.recents()) {
            if (pkg == CONFIG_PKG || pkg == packageName) continue
            if (pm.getLaunchIntentForPackage(pkg) == null) continue
            @Suppress("DEPRECATION")
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                continue
            }
            val icon = if (real) {
                try { pm.getApplicationIcon(pkg).toBitmap(96, 96) } catch (e: Exception) { null }
            } else null
            out.add(RecItem(pkg, label, monogram(label), agoText(now - t), icon))
            if (out.size >= 8) break
        }
        return out
    }

    private fun showRecents(side: String) {
        if (recView != null) return
        hideBar()
        val nc = prefs.currentPalette()
        val col = RecColors(nc.bg.toArgb(), nc.r.toArgb(), nc.c.toArgb(), nc.dim.toArgb(), nc.pn.toArgb())
        val v = RecentsView(
            this, recentItems(), col, side == "l", prefs.recStyle,
            onPick = { pkg ->
                hideRecents()
                openFromRecents(pkg)
            },
            onRemove = { pkg -> prefs.saveRecents(prefs.recents().filter { it.first != pkg }) },
            onClear = { prefs.saveRecents(emptyList()) },
            onClose = { hideRecents() },
            onStyle = { st -> prefs.recStyle = st },
            onActivity = {
                h.removeCallbacks(recHideRun)
                h.postDelayed(recHideRun, 15000)
            },
        )
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            wm.addView(v, lp)
            recView = v
            h.postDelayed(recHideRun, 15000)
        } catch (e: Exception) {
            Log.w("NcNav", "não foi possível mostrar os recentes", e)
        }
    }

    private fun hideRecents() {
        h.removeCallbacks(recHideRun)
        val v = recView ?: return
        recView = null
        v.animate().alpha(0f).setDuration(150).withEndAction {
            try { wm.removeView(v) } catch (e: Exception) { }
        }.start()
    }

    /** Abre o app escolhido e o traz para a frente da lista de recentes. */
    private fun openFromRecents(pkg: String) {
        if (launchApp(this, pkg)) {
            val r = (listOf(pkg to System.currentTimeMillis()) + prefs.recents().filter { it.first != pkg }).take(10)
            prefs.saveRecents(r)
        }
    }

    private fun keepBar() {
        h.removeCallbacks(hideRun)
        h.postDelayed(hideRun, 3200)
    }

    private fun showBar() {
        if (bar != null) {
            keepBar()
            return
        }
        val nc = prefs.currentPalette()
        val pos = prefs.navPos
        val inset = dp(prefs.edgeInset)
        val ll = LinearLayout(this).apply {
            orientation = if (pos == "b") LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                setColor(nc.bg.copy(alpha = 0.92f).toArgb())
                setStroke(dp(1), nc.y.toArgb())
            }
        }
        for (k in 0..2) {
            val b = NavBtn(this, k, nc.c.toArgb(), nc.bg.toArgb())
            b.layoutParams = LinearLayout.LayoutParams(dp(62), dp(44)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            b.setOnClickListener {
                when (k) {
                    0 -> performGlobalAction(GLOBAL_ACTION_BACK)
                    1 -> performGlobalAction(GLOBAL_ACTION_HOME)
                    else -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
                keepBar()
            }
            ll.addView(b)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
        when (pos) {
            "l" -> { lp.gravity = Gravity.START or Gravity.TOP; lp.x = inset + dp(12); lp.y = sideTop(dp(164)) }
            "r" -> { lp.gravity = Gravity.END or Gravity.TOP; lp.x = inset + dp(12); lp.y = sideTop(dp(164)) }
            else -> { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.y = inset + dp(40) }
        }
        try {
            wm.addView(ll, lp)
        } catch (e: Exception) {
            return
        }
        bar = ll
        // entrada com glitch: desliza da borda tremendo
        val off = dp(90).toFloat()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            addUpdateListener { a ->
                val t = a.animatedFraction
                val slide = (1f - t) * off
                val jit = if (t < 0.6f) (if (((t * 12).toInt() % 2) == 0) 7f else -6f) * (1f - t) else 0f
                when (pos) {
                    "b" -> { ll.translationY = slide; ll.translationX = jit }
                    "l" -> { ll.translationX = -slide; ll.translationY = jit }
                    else -> { ll.translationX = slide; ll.translationY = jit }
                }
                ll.alpha = if (t < 0.15f) 0.4f else 1f
            }
            start()
        }
        keepBar()
    }

    private fun hideBar() {
        h.removeCallbacks(hideRun)
        val ll = bar ?: return
        bar = null
        ll.animate().alpha(0f).setDuration(180).withEndAction {
            try { wm.removeView(ll) } catch (e: Exception) { }
        }.start()
    }
}

/** Linha-guia fina na borda (opcional). */
private class EdgeView(ctx: Context, col: Int, val guide: Boolean, val pos: String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col
        alpha = 200
    }

    override fun onDraw(canvas: Canvas) {
        if (!guide) return
        val d = resources.displayMetrics.density
        val t = 3 * d
        val len = 92 * d
        val w = width.toFloat()
        val h = height.toFloat()
        when (pos) {
            "l" -> canvas.drawRect(0f, h / 2 - len * 0.4f, t, h / 2 + len * 0.4f, p)
            "r" -> canvas.drawRect(w - t, h / 2 - len * 0.4f, w, h / 2 + len * 0.4f, p)
            else -> canvas.drawRect(w / 2 - len / 2, h - t - 4 * d, w / 2 + len / 2, h - 4 * d, p)
        }
    }
}

/** Botão do overlay: 0 = voltar, 1 = início, 2 = recentes. */
private class NavBtn(ctx: Context, val kind: Int, val fg: Int, val bgc: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        p.style = Paint.Style.FILL
        p.color = if (isPressed) fg else bgc
        canvas.drawRect(0f, 0f, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = fg
        canvas.drawRect(d / 2, d / 2, w - d / 2, h - d / 2, p)
        p.strokeWidth = 2 * d
        p.color = if (isPressed) bgc else fg
        val cx = w / 2
        val cy = h / 2
        val r = 7 * d
        when (kind) {
            0 -> {
                val path = Path()
                path.moveTo(cx + r * 0.6f, cy - r)
                path.lineTo(cx - r * 0.9f, cy)
                path.lineTo(cx + r * 0.6f, cy + r)
                path.close()
                canvas.drawPath(path, p)
            }
            1 -> canvas.drawCircle(cx, cy, r, p)
            else -> canvas.drawRect(cx - r, cy - r, cx + r, cy + r, p)
        }
    }
}