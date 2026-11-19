package com.nightcity.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val ADB_CMD = "adb shell pm grant com.nightcity.launcher android.permission.WRITE_SECURE_SETTINGS"

class SettingsState(
    val palKey: String,
    val custom: List<NcPalette>,
    val navPos: String,
    val guide: Boolean,
    val iconMode: String,
    val edgeInset: Int,
    val lat: String,
    val lon: String,
    val notifStyle: String,
    val notifMax: Int,
    val filterMode: String,
    val filterLevel: Int,
    val notifOk: Boolean,
    val a11yOk: Boolean,
    val a11yRunning: Boolean,
    val locOk: Boolean,
    val phoneOk: Boolean,
    val secureOk: Boolean,
)

class SettingsActions(
    val onClose: () -> Unit,
    val onPal: (String) -> Unit,
    val onSavePalette: (NcPalette) -> Unit,
    val onDeletePalette: (String) -> Unit,
    val onPos: (String) -> Unit,
    val onGuide: (Boolean) -> Unit,
    val onIconMode: (String) -> Unit,
    val onInset: (Int) -> Unit,
    val onLat: (String) -> Unit,
    val onLon: (String) -> Unit,
    val onNotifStyle: (String) -> Unit,
    val onNotifMax: (Int) -> Unit,
    val onFilterMode: (String) -> Unit,
    val onFilterLevel: (Int) -> Unit,
    val copyAdb: () -> Unit,
    val openNotif: () -> Unit,
    val openA11y: () -> Unit,
    val reviveNav: () -> Unit,
    val askLoc: () -> Unit,
    val askPhone: () -> Unit,
    val openHome: () -> Unit,
)

// ---------------------------------------------------------------- blocos reutilizáveis

/** Seção retrátil: toque no título para abrir/fechar. */
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

@Composable
private fun Note(text: String) {
    val nc = LocalNc.current
    Tx(text, nc.dim, 10, mono = true, maxLines = 4)
}

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

private fun hexColor(s: String, fallback: Color): Color =
    parseHex(s)?.let { Color(0xFF000000L or it) } ?: fallback

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

@Composable
private fun HexField(label: String, value: String, onChange: (String) -> Unit, swatch: Color?) {
    val nc = LocalNc.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Tx(label, nc.dim, 10, mono = true, modifier = Modifier.width(104.dp))
        TextBox(value, onChange, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(24.dp).background(swatch ?: Color.Transparent).border(1.dp, nc.y.copy(alpha = 0.4f)))
    }
}

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
            )
        }
        Note("Cores em hexadecimal de 6 dígitos, ex.: FCEE09")

        // pré-visualização com as cores digitadas
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

        Fold("BARRA DE NAVEGAÇÃO", startOpen = true, summary = when (s.navPos) { "l" -> "ESQUERDA"; "r" -> "DIREITA"; else -> "INFERIOR" }) {
            Opts(listOf("INFERIOR" to "b", "ESQUERDA" to "l", "DIREITA" to "r"), s.navPos, a.onPos)
            Opts(listOf("LINHA VISÍVEL" to true, "LINHA OCULTA" to false), s.guide, a.onGuide)
            Tx("AFASTAMENTO DA BORDA (dp)", nc.dim, 10, mono = true)
            Opts(listOf("0" to 0, "16" to 16, "32" to 32, "48" to 48), s.edgeInset, a.onInset)
            Note("Aumente se a faixa de gesto brigar com o gesto do sistema. Nas laterais a barra fica um pouco acima do meio da tela.")
        }

        Fold("ÍCONES DOS APPS", startOpen = true, summary = if (s.iconMode == "real") "REAL" else "MONOGRAMA") {
            Opts(listOf("MONOGRAMA" to "mono", "REAL" to "real"), s.iconMode, a.onIconMode)
        }

        Fold("NOTIFICAÇÕES", startOpen = false, summary = when (s.notifStyle) { "compact" -> "COMPACTO"; "hidden" -> "OCULTO"; else -> "DETALHADO" }) {
            Tx("FORMATO DO PAINEL", nc.dim, 10, mono = true)
            Opts(listOf("COMPACTO" to "compact", "DETALHADO" to "detail", "OCULTO" to "hidden"), s.notifStyle, a.onNotifStyle)
            Tx("QUANTAS MOSTRAR", nc.dim, 10, mono = true)
            Opts(listOf("3" to 3, "5" to 5, "8" to 8), s.notifMax, a.onNotifMax)
            Note("Vale só para o painel NOTIFICAÇÕES da tela inicial. O visual das notificações do sistema (barra de cima e pop-ups) é controlado pelo próprio sistema e um launcher não consegue trocá-lo. OCULTO mostra só o nome do app.")
        }

        Fold("PALETAS DE CORES", startOpen = false, summary = current.name) {
            PalettesBody(s, a, current)
        }

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

        Fold("ENERGIA (TOQUE NO DIAGNÓSTICO)", startOpen = false, summary = if (s.secureOk) "DIRETO" else "ABRE AJUSTES") {
            StatusLine("Alternar economia de bateria direto", s.secureOk, "COPIAR ADB", a.copyAdb)
            Note(ADB_CMD)
            Note("Sem essa permissão, tocar no DIAGNÓSTICO abre a tela de economia de bateria do sistema. Modos de desempenho próprios do fabricante são controlados pelo sistema e um app comum não consegue trocá-los.")
        }

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
