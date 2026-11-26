package com.nightcity.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Comando ADB necessário para conceder `WRITE_SECURE_SETTINGS` ao pacote do launcher
// (permite alternar economia de bateria ou correção monocromática do sistema programaticamente).
const val ADB_CMD = "adb shell pm grant com.nightcity.launcher android.permission.WRITE_SECURE_SETTINGS"

/**
 * Snapshot imutável com todos os valores atuais e status de permissões/serviços
 * consumidos pela tela de configurações.
 */
class SettingsState(
    val palKey: String,
    val custom: List<NcPalette>,
    val navPos: String,
    val recSide: String,
    val recStyle: Int,
    val guide: Boolean,
    val iconMode: String,
    val edgeInset: Int,
    val lat: String,
    val lon: String,
    val notifStyle: String,
    val notifMax: Int,
    val filterMode: String,
    val filterLevel: Int,
    val fxMode: String,
    val fxSecs: Int,
    val fxWake: Boolean,
    val fxOver: Boolean,
    val wpTarget: String,
    val notifOk: Boolean,
    val a11yOk: Boolean,
    val a11yRunning: Boolean,
    val locOk: Boolean,
    val phoneOk: Boolean,
    val secureOk: Boolean,
)

/**
 * Conjunto de callbacks disparados pela UI de configurações para alterar preferências,
 * gerenciar paletas, testar efeitos OLED, copiar comandos ADB e abrir telas/permissões do sistema.
 */
class SettingsActions(
    val onClose: () -> Unit,
    val onPal: (String) -> Unit,
    val onSavePalette: (NcPalette) -> Unit,
    val onDeletePalette: (String) -> Unit,
    val onPos: (String) -> Unit,
    val onRecSide: (String) -> Unit,
    val onRecStyle: (Int) -> Unit,
    val onGuide: (Boolean) -> Unit,
    val onIconMode: (String) -> Unit,
    val onInset: (Int) -> Unit,
    val onLat: (String) -> Unit,
    val onLon: (String) -> Unit,
    val onNotifStyle: (String) -> Unit,
    val onNotifMax: (Int) -> Unit,
    val onFilterMode: (String) -> Unit,
    val onFilterLevel: (Int) -> Unit,
    val onFxMode: (String) -> Unit,
    val onFxSecs: (Int) -> Unit,
    val onFxWake: (Boolean) -> Unit,
    val onFxOver: (Boolean) -> Unit,
    val onWpTarget: (String) -> Unit,
    val testFx: (Int) -> Unit,
    val copyAdb: () -> Unit,
    val openNotif: () -> Unit,
    val openA11y: () -> Unit,
    val reviveNav: () -> Unit,
    val askLoc: () -> Unit,
    val askPhone: () -> Unit,
    val openHome: () -> Unit,
)

// ---------------------------------------------------------------- blocos reutilizáveis

/** Seção retrátil (accordion): toque no cabeçalho expande ou recolhe o conteúdo interno. */
@Composable
private fun Fold(
    title: String,
    startOpen: Boolean,
    summary: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    val nc = LocalNc.current
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth().border(1.dp, nc.y.copy(alpha = 0.27f))) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tx(if (open) "▾" else "▸", nc.y, 14, true, modifier = Modifier.width(18.dp))
            Box(Modifier.weight(1f)) { Tx(title, nc.c, 12, true, spacing = 2f) }
            if (summary.isNotEmpty()) Tx(summary, nc.dim, 10, mono = true)
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

// Nota explicativa discreta em fonte monoespaçada para orientações técnicas ou limitações do Android.
@Composable
private fun Note(text: String) {
    val nc = LocalNc.current
    Tx(text, nc.dim, 10, mono = true, maxLines = 4)
}

// Linha de botões seletora genérica para um conjunto tipado de opções (`List<Pair<Rótulo, Valor>>`).
@Composable
private fun <T> Opts(options: List<Pair<String, T>>, selected: T, onSel: (T) -> Unit) {
    val nc = LocalNc.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, v) ->
            val on = v == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) nc.y else Color.Transparent)
                    .border(1.dp, nc.y.copy(alpha = if (on) 1f else 0.33f))
                    .clickable { onSel(v) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Tx(label, if (on) Color.Black else nc.y, 11, true, spacing = 1f) }
        }
    }
}

// Linha de status para permissões e serviços, exibindo estado atual (LIGADO/DESLIGADO) e botão de ação.
@Composable
private fun StatusLine(label: String, ok: Boolean?, action: String, onClick: () -> Unit) {
    val nc = LocalNc.current
    Row(
        Modifier.fillMaxWidth().border(1.dp, nc.y.copy(alpha = 0.27f)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Tx(label, nc.y, 12, true, spacing = 1f, maxLines = 2)
            if (ok != null) Tx(if (ok) "LIGADO" else "DESLIGADO", if (ok) nc.c else nc.r, 10, mono = true)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.border(1.dp, nc.y).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Tx(action, nc.y, 11, true, spacing = 2f)
        }
    }
}

// Campo de texto em linha única estilizado como terminal monoespaçado.
@Composable
private fun TextBox(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val nc = LocalNc.current
    Box(modifier.background(Color.Black).border(1.dp, nc.c.copy(alpha = 0.5f)).padding(8.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = nc.c, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(nc.c),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- paletas

// Converte uma string hex em `Color` opaca ou devolve `fallback` se o hex for inválido.
private fun hexColor(s: String, fallback: Color): Color =
    parseHex(s)?.let { Color(0xFF000000L or it) } ?: fallback

// Cartão de seleção de paleta com amostra das 3 cores principais (y, c, r) e ações de editar/excluir para temas customizados.
@Composable
private fun PalCell(
    p: NcPalette,
    selected: Boolean,
    custom: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val nc = LocalNc.current
    Row(
        modifier
            .background(nc.pn)
            .border(if (selected) 2.dp else 1.dp, if (selected) nc.y else nc.y.copy(alpha = 0.27f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(p.y, p.c, p.r).forEach { Box(Modifier.size(9.dp, 20.dp).background(it)) }
        }
        Box(Modifier.weight(1f)) { Tx(p.name, nc.y, 12, true, spacing = 1f) }
        if (custom) {
            Tx("✎", nc.c, 14, true, modifier = Modifier.clickable { onEdit() }.padding(2.dp))
            Tx("✕", nc.r, 14, true, modifier = Modifier.clickable { onDelete() }.padding(2.dp))
        }
    }
}

// Converte valores HSV (matiz 0..360, saturação 0..1, brilho/valor 0..1) em `Color` Compose.
private fun hsvColor(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

/**
 * Seletor visual de cores interativo com plano Saturação x Brilho e barra de Matiz (Hue)
 * com suporte a toque e arrasto.
 */
@Composable
private fun ColorPicker(value: String, onChange: (String) -> Unit) {
    val parsed = parseHex(value)?.let { Color(0xFF000000L or it) } ?: Color.White
    val hsv = remember(value) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(parsed.toArgb(), it) }
    }
    var hue by remember(value) { mutableStateOf(hsv[0]) }
    var sat by remember(value) { mutableStateOf(hsv[1]) }
    var brightness by remember(value) { mutableStateOf(hsv[2]) }

    // Formata a cor atual em `#RRGGBB` e envia para o campo/editor pai.
    fun publish() {
        val rgb = hsvColor(hue, sat, brightness).toArgb() and 0xFFFFFF
        onChange("#%06X".format(rgb))
    }

    Column(
        Modifier.fillMaxWidth().padding(start = 104.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Quadrante Saturação (eixo X) x Brilho (eixo Y invertido: topo = 100%, base = 0%).
        Canvas(
            Modifier.fillMaxWidth().height(172.dp)
                .pointerInput(hue) {
                    fun update(point: Offset) {
                        sat = (point.x / size.width).coerceIn(0f, 1f)
                        brightness = (1f - point.y / size.height).coerceIn(0f, 1f)
                        publish()
                    }
                    detectTapGestures { update(it) }
                }
                .pointerInput(hue) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        brightness = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        publish()
                    }
                }
        ) {
            val base = hsvColor(hue, 1f, 1f)
            drawRect(base)
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val marker = Offset(sat * size.width, (1f - brightness) * size.height)
            drawCircle(Color.Black, radius = 8.dp.toPx(), center = marker, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawCircle(Color.White, radius = 6.dp.toPx(), center = marker, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }

        // Barra horizontal contínua de matiz (0° a 360°).
        Canvas(
            Modifier.fillMaxWidth().height(18.dp)
                .pointerInput(Unit) {
                    fun update(point: Offset) {
                        hue = (point.x / size.width * 360f).coerceIn(0f, 360f)
                        publish()
                    }
                    detectTapGestures { update(it) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                        publish()
                    }
                }
        ) {
            val colors = (0..6).map { hsvColor(it * 60f, 1f, 1f) }
            drawRect(brush = Brush.horizontalGradient(colors))
            val x = hue / 360f * size.width
            drawRect(Color.White, topLeft = Offset(x - 1.dp.toPx(), 0f), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).background(hsvColor(hue, sat, brightness)).border(1.dp, Color.White))
            Spacer(Modifier.width(8.dp))
            Tx("MATIZ ${hue.toInt()}°  •  SAT ${(sat * 100).toInt()}%  •  BRILHO ${(brightness * 100).toInt()}%", LocalNc.current.dim, 9, mono = true)
        }
    }
}

// Campo de cor hexadecimal acompanhado de amostra visual (`swatch`) que abre/fecha o `ColorPicker`.
@Composable
private fun HexField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    swatch: Color?,
    pickerOpen: Boolean,
    onSwatchClick: () -> Unit,
) {
    val nc = LocalNc.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tx(label, nc.dim, 10, mono = true, modifier = Modifier.width(104.dp))
            TextBox(value, onChange, Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(30.dp).background(swatch ?: Color.Transparent)
                    .border(if (pickerOpen) 2.dp else 1.dp, if (pickerOpen) nc.c else nc.y.copy(alpha = 0.55f))
                    .clickable { onSwatchClick() },
                contentAlignment = Alignment.Center,
            ) { if (pickerOpen) Tx("✓", Color.Black, 13, true) }
        }
        if (pickerOpen) ColorPicker(value, onChange)
    }
}

/**
 * Editor completo para criar uma nova paleta ou editar uma paleta customizada existente,
 * com validação de nome e hexadecimais, prévia em tempo real e botões de salvar/cancelar.
 */
@Composable
private fun PaletteEditor(
    initial: NcPalette?,
    base: NcPalette,
    onSave: (NcPalette) -> Unit,
    onCancel: () -> Unit,
) {
    val nc = LocalNc.current
    val src = initial ?: base
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    val labels = listOf("DESTAQUE", "ACENTO", "ALERTA", "FUNDO", "FUNDO 2", "PAINÉIS", "TEXTO APAGADO", "DESLIGADO")
    val vals = remember(initial) { mutableStateListOf<String>().apply { addAll(src.hexList()) } }
    var err by remember(initial) { mutableStateOf("") }
    // Armazena o índice do campo de cor que está com o `ColorPicker` aberto (ou `null`).
    var activePicker by remember(initial) { mutableStateOf<Int?>(null) }

    Column(
        Modifier.fillMaxWidth().background(Color.Black).border(1.dp, nc.c.copy(alpha = 0.5f)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tx(if (initial == null) "NOVA PALETA" else "EDITAR PALETA", nc.c, 12, true, spacing = 2f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tx("NOME", nc.dim, 10, mono = true, modifier = Modifier.width(104.dp))
            TextBox(name, { name = it.take(20) }, Modifier.weight(1f))
        }
        labels.forEachIndexed { i, l ->
            HexField(
                l, vals[i],
                { vals[i] = it.filter { c -> c.isLetterOrDigit() || c == '#' }.take(7) },
                parseHex(vals[i])?.let { Color(0xFF000000L or it) },
                pickerOpen = activePicker == i,
                onSwatchClick = { activePicker = if (activePicker == i) null else i },
            )
        }
        Note("Cores em hexadecimal de 6 dígitos, ex.: FCEE09")

        // Prévia ao vivo combinando fundo, destaque, acento e alerta.
        val pBg = hexColor(vals[3], Color.Black)
        val pBg2 = hexColor(vals[4], Color.DarkGray)
        val pY = hexColor(vals[0], Color.White)
        val pC = hexColor(vals[1], Color.White)
        val pR = hexColor(vals[2], Color.White)
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(pBg2, pBg)))
                .border(1.dp, pY.copy(alpha = 0.4f))
                .padding(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Tx(if (name.isBlank()) "PRÉVIA" else name.uppercase(), pY, 16, true, spacing = 2f)
                Tx("acento", pC, 12, mono = true)
                Tx("alerta", pR, 12, mono = true)
            }
        }

        if (err.isNotEmpty()) Tx(err, nc.r, 11, true, maxLines = 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .background(nc.y)
                    .clickable {
                        // Remove caracteres reservadas da serialização (`|` e `;`) antes de salvar.
                        val clean = name.trim().replace("|", "").replace(";", "")
                        val bad = vals.indexOfFirst { parseHex(it) == null }
                        when {
                            clean.isEmpty() -> err = "DÊ UM NOME À PALETA"
                            bad >= 0 -> err = "COR INVÁLIDA EM ${labels[bad]}: use 6 dígitos hexadecimais"
                            else -> onSave(
                                paletteOf(
                                    initial?.key ?: ("c_" + System.currentTimeMillis()),
                                    clean,
                                    vals.map { parseHex(it)!! },
                                )
                            )
                        }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Tx("SALVAR", Color.Black, 12, true, spacing = 2f) }
            Box(
                Modifier
                    .weight(1f)
                    .border(1.dp, nc.y)
                    .clickable { onCancel() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Tx("CANCELAR", nc.y, 12, true, spacing = 2f) }
        }
    }
}

// Grade de paletas (padrão + customizadas em linhas de 2) e alternância para o `PaletteEditor`.
@Composable
private fun PalettesBody(s: SettingsState, a: SettingsActions, current: NcPalette) {
    val nc = LocalNc.current
    var editing by remember { mutableStateOf<NcPalette?>(null) }
    var open by remember { mutableStateOf(false) }
    val customKeys = s.custom.map { it.key }.toSet()
    (Palettes + s.custom).chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { p ->
                PalCell(
                    p, p.key == s.palKey, p.key in customKeys, Modifier.weight(1f),
                    onClick = { a.onPal(p.key) },
                    onEdit = { editing = p; open = true },
                    onDelete = { a.onDeletePalette(p.key) },
                )
            }
            if (row.size < 2) Spacer(Modifier.weight(1f))
        }
    }
    if (!open) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, nc.c)
                .clickable { editing = null; open = true }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) { Tx("+ CRIAR PALETA", nc.c, 12, true, spacing = 2f) }
    } else {
        PaletteEditor(
            initial = editing,
            base = current,
            onSave = { a.onSavePalette(it); open = false; editing = null },
            onCancel = { open = false; editing = null },
        )
    }
}

// ---------------------------------------------------------------- tela

/**
 * Tela completa de configurações do launcher, organizada em seções retráteis (`Fold`)
 * para navegação sobreposta, ícones, notificações, efeitos OLED, paletas, filtros de tela,
 * economia de energia, coordenadas climáticas e permissões/serviços.
 */
@Composable
fun SettingsScreen(s: SettingsState, a: SettingsActions) {
    val nc = LocalNc.current
    val current = (Palettes + s.custom).firstOrNull { it.key == s.palKey } ?: Palettes[0]
    Column(
        Modifier
            .fillMaxSize()
            .background(nc.bg.copy(alpha = 0.92f))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Cabeçalho com título e botão de fechar.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tx("CONFIG // LAUNCHER", nc.y, 20, true, spacing = 2f)
            Box(Modifier.border(1.dp, nc.y).clickable { a.onClose() }.padding(horizontal = 10.dp, vertical = 3.dp)) {
                Tx("FECHAR", nc.y, 11, true, spacing = 2f)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(nc.y))

        // Posição, visibilidade da linha guia e afastamento da borda da barra de navegação sobreposta.
        Fold("BARRA DE NAVEGAÇÃO", startOpen = true, summary = when (s.navPos) { "l" -> "ESQUERDA"; "r" -> "DIREITA"; else -> "INFERIOR" }) {
            Opts(listOf("INFERIOR" to "b", "ESQUERDA" to "l", "DIREITA" to "r"), s.navPos, a.onPos)
            Opts(listOf("LINHA VISÍVEL" to true, "LINHA OCULTA" to false), s.guide, a.onGuide)
            Tx("AFASTAMENTO DA BORDA (dp)", nc.dim, 10, mono = true)
            Opts(listOf("0" to 0, "16" to 16, "32" to 32, "48" to 48), s.edgeInset, a.onInset)
            Note("Aumente se a faixa de gesto brigar com o gesto do sistema. Nas laterais a barra fica um pouco acima do meio da tela.")
        }

        // Segunda faixa de gesto, na lateral oposta, que abre os apps recentes em cascata ou dial.
        Fold(
            "ATALHO DE RECENTES",
            startOpen = false,
            summary = when (s.recSide) { "off" -> "DESLIGADO"; "l" -> "ESQUERDA"; "r" -> "DIREITA"; else -> "AUTO" },
        ) {
            Tx("LADO", nc.dim, 10, mono = true)
            Opts(listOf("AUTO" to "auto", "ESQ." to "l", "DIR." to "r", "DESLIG." to "off"), s.recSide, a.onRecSide)
            Tx("ESTILO", nc.dim, 10, mono = true)
            Opts(listOf("CASCATA" to 0, "DIAL" to 1), s.recStyle, a.onRecStyle)
            Note("Deslize para dentro a partir da borda para abrir. AUTO usa o lado oposto ao da barra de navegação (com a barra embaixo, fica à esquerda). Toque num app para abrir, segure para tirar da lista. Mostra os apps abertos pelo launcher.")
        }

        // Alterna entre ícones monocromáticos/monogramas estilizados e ícones originais dos apps.
        Fold("ÍCONES DOS APPS", startOpen = true, summary = if (s.iconMode == "real") "REAL" else "MONOGRAMA") {
            Opts(listOf("MONOGRAMA" to "mono", "REAL" to "real"), s.iconMode, a.onIconMode)
        }

        // Estilo e quantidade máxima de notificações no painel da tela inicial.
        Fold("NOTIFICAÇÕES", startOpen = false, summary = when (s.notifStyle) { "compact" -> "COMPACTO"; "hidden" -> "OCULTO"; else -> "DETALHADO" }) {
            Tx("FORMATO DO PAINEL", nc.dim, 10, mono = true)
            Opts(listOf("COMPACTO" to "compact", "DETALHADO" to "detail", "OCULTO" to "hidden"), s.notifStyle, a.onNotifStyle)
            Tx("QUANTAS MOSTRAR", nc.dim, 10, mono = true)
            Opts(listOf("3" to 3, "5" to 5, "8" to 8), s.notifMax, a.onNotifMax)
            Note("Vale só para o painel NOTIFICAÇÕES da tela inicial. O visual das notificações do sistema (barra de cima e pop-ups) é controlado pelo próprio sistema e um launcher não consegue trocá-lo. OCULTO mostra só o nome do app.")
        }

        // Onde o papel de parede do tema é aplicado.
        Fold(
            "PAPEL DE PAREDE DO TEMA",
            startOpen = false,
            summary = when (s.wpTarget) { "both" -> "BLOQUEIO + INÍCIO"; "lock" -> "BLOQUEIO"; else -> "DESLIGADO" },
        ) {
            Tx("APLICAR EM", nc.dim, 10, mono = true)
            Opts(
                listOf("BLOQUEIO + INÍCIO" to "both", "SÓ BLOQUEIO" to "lock", "DESLIGADO" to "off"),
                s.wpTarget, a.onWpTarget,
            )
            Note("A imagem é gerada com as cores da paleta ativa e muda quando você troca de paleta. DESLIGADO não altera o papel de parede do sistema.")
        }

        // Configuração e testes de animações/efeitos visuais de notificação para telas OLED.
        Fold(
            "EFEITO DE NOTIFICAÇÃO (OLED)",
            startOpen = false,
            summary = when (s.fxMode) { "glitch" -> "GLITCH"; "crt" -> "CRT"; "matrix" -> "MATRIX"; else -> "DESLIGADO" },
        ) {
            Tx("EFEITO", nc.dim, 10, mono = true)
            Opts(
                listOf("DESLIGADO" to "off", "GLITCH" to "glitch", "CRT" to "crt", "MATRIX" to "matrix"),
                s.fxMode, a.onFxMode,
            )
            Tx("DURAÇÃO (s)", nc.dim, 10, mono = true)
            Opts(listOf("3" to 3, "5" to 5, "8" to 8), s.fxSecs, a.onFxSecs)
            Tx("COM A TELA LIGADA", nc.dim, 10, mono = true)
            Opts(listOf("POR CIMA DOS APPS" to true, "NÃO MOSTRAR" to false), s.fxOver, a.onFxOver)
            Tx("COM A TELA APAGADA", nc.dim, 10, mono = true)
            Opts(listOf("ACENDER E MOSTRAR" to true, "NÃO ACENDER" to false), s.fxWake, a.onFxWake)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.weight(1f).border(1.dp, nc.c).clickable { a.testFx(0) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Tx("TESTAR AGORA", nc.c, 11, true, spacing = 1f) }
                Box(
                    Modifier.weight(1f).border(1.dp, nc.c).clickable { a.testFx(8) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Tx("TESTAR EM 8 s", nc.c, 11, true, spacing = 1f) }
            }
            Note("TESTAR EM 8 s: toque e apague a tela com o botão lateral; ela deve acender com o efeito e apagar de novo.")
            Note("Usa o serviço de acessibilidade e o acesso a notificações. O efeito é desenhado por cima: não distorce o conteúdo dos apps. Com a tela apagada, o fundo preto deixa os pixels de uma tela OLED desligados. Não aparece com o modo Não Perturbe ligado. Tocar na tela durante o efeito fecha o efeito e não apaga a tela.")
            if (!s.a11yRunning) Note("O serviço de acessibilidade não está rodando: ative-o em Permissões e serviços.")
        }

        // Seleção e criação/edição de paletas de cores.
        Fold("PALETAS DE CORES", startOpen = false, summary = current.name) {
            PalettesBody(s, a, current)
        }

        // Filtros de cor/escurecimento (monocromático, paleta, âmbar e escurecimento) e intensidade.
        Fold(
            "FILTROS DE TELA",
            startOpen = false,
            summary = when (s.filterMode) {
                "mono" -> "MONOCROM."
                "pal" -> "PALETA"
                "amber" -> "ÂMBAR"
                "dim" -> "ESCURO"
                else -> "DESLIGADO"
            } + if (s.filterMode == "off") "" else " " + s.filterLevel + "%",
        ) {
            Tx("MODO", nc.dim, 10, mono = true)
            Opts(
                listOf("DESLIGADO" to "off", "MONOCROM." to "mono", "PALETA" to "pal"),
                s.filterMode, a.onFilterMode,
            )
            Opts(
                listOf("ÂMBAR" to "amber", "ESCURO" to "dim"),
                s.filterMode, a.onFilterMode,
            )
            Tx("INTENSIDADE (%)", nc.dim, 10, mono = true)
            Opts(listOf("20" to 20, "40" to 40, "60" to 60, "80" to 80), s.filterLevel, a.onFilterLevel)
            Note("MONOCROM.: reduz distrações (foco). PALETA: tinge a tela com a cor de destaque da paleta. ÂMBAR: tom quente para descansar os olhos. ESCURO: escurece a tela.")
            if (s.filterMode == "mono") {
                StatusLine("Preto e branco real (sistema)", s.secureOk, "COPIAR ADB", a.copyAdb)
                Note(
                    if (s.secureOk) {
                        "Preto e branco real ligado pela correção de cor do sistema (a intensidade não se aplica)."
                    } else {
                        "Sem a permissão por adb, o monocromático é aproximado por uma cobertura cinza (a intensidade controla o quanto desbota)."
                    }
                )
            }
            if (s.filterMode != "off" && s.filterMode != "mono" || (s.filterMode == "mono" && !s.secureOk)) {
                if (!s.a11yRunning) {
                    Note("Este filtro usa o serviço de acessibilidade: ative-o em Permissões e serviços para o filtro aparecer por cima dos outros apps.")
                }
            }
        }

        // Controle direto ou via ajustes do modo economia de energia ao tocar no painel DIAGNÓSTICO.
        Fold("ENERGIA (TOQUE NO DIAGNÓSTICO)", startOpen = false, summary = if (s.secureOk) "DIRETO" else "ABRE AJUSTES") {
            StatusLine("Alternar economia de bateria direto", s.secureOk, "COPIAR ADB", a.copyAdb)
            Note(ADB_CMD)
            Note("Sem essa permissão, tocar no DIAGNÓSTICO abre a tela de economia de bateria do sistema. Modos de desempenho próprios do fabricante são controlados pelo sistema e um app comum não consegue trocá-los.")
        }

        // Coordenadas manuais ou automáticas para consulta da previsão do tempo.
        Fold("PREVISÃO: COORDENADAS", startOpen = false, summary = if (s.lat.isBlank()) "AUTO" else "MANUAL") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Tx("LATITUDE", nc.dim, 10, mono = true)
                    TextBox(s.lat, a.onLat)
                }
                Column(Modifier.weight(1f)) {
                    Tx("LONGITUDE", nc.dim, 10, mono = true)
                    TextBox(s.lon, a.onLon)
                }
            }
            Note("Vazio = usa a última localização (precisa da permissão de localização).")
        }

        // Atalhos para launcher padrão, notificações, acessibilidade (incluindo reativação se parado) e permissões.
        Fold("PERMISSÕES E SERVIÇOS", startOpen = true) {
            StatusLine("Definir como launcher padrão", null, "ABRIR", a.openHome)
            StatusLine("Acesso a notificações (mídia e painel)", s.notifOk, "ABRIR", a.openNotif)
            val stalled = s.a11yOk && !s.a11yRunning
            StatusLine(
                "Serviço de acessibilidade (botões de navegação)",
                if (stalled) false else s.a11yOk,
                if (stalled) "REATIVAR" else "ABRIR",
                if (stalled) a.reviveNav else a.openA11y,
            )
            if (stalled) {
                Note("O serviço está marcado como ligado, mas parou de rodar (acontece ao limpar os apps recentes). REATIVAR tenta religar sozinho se a permissão por adb estiver concedida; senão abre a tela de acessibilidade, onde basta desligar e ligar de novo.")
            }
            StatusLine("Localização (nome do Wi-Fi)", s.locOk, "PEDIR", a.askLoc)
            StatusLine("Estado do telefone (4G/5G)", s.phoneOk, "PEDIR", a.askPhone)
        }
        Spacer(Modifier.height(30.dp))
    }
}