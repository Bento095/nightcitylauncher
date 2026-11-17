package com.nightcity.launcher

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** y = cor principal, c = acento frio, r = alerta/glitch, bg/bg2/pn = fundos, dim/off = textos apagados. */
data class NcPalette(
    val key: String,
    val name: String,
    val y: Color,
    val c: Color,
    val r: Color,
    val bg: Color,
    val bg2: Color,
    val pn: Color,
    val dim: Color,
    val off: Color,
)

private fun col(rgb: Long) = Color(0xFF000000L or rgb)

private fun pal(
    key: String, name: String,
    y: Long, c: Long, r: Long, bg: Long, bg2: Long, pn: Long, dim: Long, off: Long,
) = NcPalette(key, name, col(y), col(c), col(r), col(bg), col(bg2), col(pn), col(dim), col(off))

val Palettes: List<NcPalette> = listOf(
    pal("night", "NIGHT CITY", 0xFCEE09, 0x02D7F2, 0xFF003C, 0x08080C, 0x0D0D14, 0x101018, 0x8A8A52, 0x5C5C3A),
    pal("arasaka", "ARASAKA", 0xFF2A40, 0xFFD1D6, 0xFF7A00, 0x0B0507, 0x150709, 0x1A0B0E, 0x9A5A62, 0x5A3A3E),
    pal("cmatrix", "CMATRIX", 0x00FF41, 0xB6FFCB, 0xE0FFE8, 0x010A03, 0x02120A, 0x04160B, 0x2F8A4A, 0x1F4A2B),
    pal("ultra", "ULTRAVIOLETA", 0xB86BFF, 0xFF9A3D, 0xFF4FD8, 0x0A0612, 0x120A1E, 0x170D27, 0x7D5FAE, 0x483A66),
    pal("dracula", "DRACULA", 0xBD93F9, 0x8BE9FD, 0xFF79C6, 0x1E1F29, 0x282A36, 0x2B2D3B, 0x6272A4, 0x44475A),
    pal("monokai", "MONOKAI", 0xE6DB74, 0x66D9EF, 0xF92672, 0x1E1F1C, 0x272822, 0x2E2F29, 0x75715E, 0x49483E),
    pal("nord", "NORD", 0x88C0D0, 0xA3BE8C, 0xBF616A, 0x242933, 0x2E3440, 0x353C4A, 0x616E88, 0x434C5E),
    pal("solar", "SOLARIZED", 0xB58900, 0x2AA198, 0xDC322F, 0x00212B, 0x002B36, 0x073642, 0x657B83, 0x586E75),
    pal("onedark", "ONE DARK", 0x61AFEF, 0x98C379, 0xE06C75, 0x21252B, 0x282C34, 0x2C313A, 0x5C6370, 0x3E4451),
)

fun paletteByKey(key: String): NcPalette = Palettes.firstOrNull { it.key == key } ?: Palettes[0]

val LocalNc = staticCompositionLocalOf { Palettes[0] }
