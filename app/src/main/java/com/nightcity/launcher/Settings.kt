package com.nightcity.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsState(
    val palKey: String,
    val navPos: String,
    val guide: Boolean,
    val iconMode: String,
    val edgeInset: Int,
    val lat: String,
    val lon: String,
    val notifOk: Boolean,
    val a11yOk: Boolean,
    val locOk: Boolean,
    val phoneOk: Boolean,
)

class SettingsActions(
    val onClose: () -> Unit,
    val onPal: (String) -> Unit,
    val onPos: (String) -> Unit,
    val onGuide: (Boolean) -> Unit,
    val onIconMode: (String) -> Unit,
    val onInset: (Int) -> Unit,
    val onLat: (String) -> Unit,
    val onLon: (String) -> Unit,
    val openNotif: () -> Unit,
    val openA11y: () -> Unit,
    val askLoc: () -> Unit,
    val askPhone: () -> Unit,
    val openHome: () -> Unit,
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val nc = LocalNc.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Tx(title, nc.c, 11, true, spacing = 2f)
        content()
    }
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
            ) { Tx(label, if (on) Color.Black else nc.y, 12, true, spacing = 2f) }
        }
    }
}

@Composable
private fun PalCell(p: NcPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
        Tx(p.name, nc.y, 12, true, spacing = 2f)
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
        Box(
            Modifier
                .border(1.dp, nc.y)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) { Tx(action, nc.y, 11, true, spacing = 2f) }
    }
}

@Composable
private fun CoordField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val nc = LocalNc.current
    Column(modifier) {
        Tx(label, nc.dim, 10, mono = true)
        Box(Modifier.fillMaxWidth().background(Color.Black).border(1.dp, nc.c.copy(alpha = 0.5f)).padding(8.dp)) {
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
}

@Composable
fun SettingsScreen(s: SettingsState, a: SettingsActions) {
    val nc = LocalNc.current
    Column(
        Modifier
            .fillMaxSize()
            .background(nc.bg.copy(alpha = 0.9f))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

        Section("BARRA DE NAVEGAÇÃO: POSIÇÃO") {
            Opts(listOf("INFERIOR" to "b", "ESQUERDA" to "l", "DIREITA" to "r"), s.navPos, a.onPos)
        }
        Section("LINHA-GUIA DA BORDA") {
            Opts(listOf("VISÍVEL" to true, "OCULTA" to false), s.guide, a.onGuide)
        }
        Section("AFASTAMENTO DA BORDA (dp)") {
            Opts(listOf("0" to 0, "16" to 16, "32" to 32, "48" to 48), s.edgeInset, a.onInset)
            Tx("Aumente se a faixa de gesto brigar com o gesto do sistema.", nc.dim, 10, mono = true, maxLines = 2)
        }
        Section("ÍCONES DOS APPS") {
            Opts(listOf("MONOGRAMA" to "mono", "REAL" to "real"), s.iconMode, a.onIconMode)
        }
        Section("PALETA DE CORES") {
            Palettes.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { p -> PalCell(p, p.key == s.palKey, Modifier.weight(1f)) { a.onPal(p.key) } }
                    if (row.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
        Section("PREVISÃO: COORDENADAS (opcional)") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoordField("LATITUDE", s.lat, a.onLat, Modifier.weight(1f))
                CoordField("LONGITUDE", s.lon, a.onLon, Modifier.weight(1f))
            }
            Tx("Vazio = usa a última localização (precisa da permissão de localização).", nc.dim, 10, mono = true, maxLines = 2)
        }
        Section("PERMISSÕES E SERVIÇOS") {
            StatusLine("Definir como launcher padrão", null, "ABRIR", a.openHome)
            StatusLine("Acesso a notificações (controle de mídia)", s.notifOk, "ABRIR", a.openNotif)
            StatusLine("Serviço de acessibilidade (botões de navegação)", s.a11yOk, "ABRIR", a.openA11y)
            StatusLine("Localização (nome do Wi-Fi)", s.locOk, "PEDIR", a.askLoc)
            StatusLine("Estado do telefone (4G/5G)", s.phoneOk, "PEDIR", a.askPhone)
        }
        Spacer(Modifier.height(30.dp))
    }
}
