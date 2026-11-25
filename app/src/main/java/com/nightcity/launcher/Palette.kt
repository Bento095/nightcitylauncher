package com.nightcity.launcher

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Define os papéis cromáticos de um tema do launcher:
 * - [y]: cor principal / destaque (títulos, bordas ativas, elementos primários)
 * - [c]: acento frio / secundário (status, indicadores, sub-destaques)
 * - [r]: alerta / glitch (avisos críticos, picos de uso, deslocamento de glitch)
 * - [bg]: fundo principal (base da tela)
 * - [bg2]: fundo secundário (ex.: topo do gradiente de fundo)
 * - [pn]: superfície de painéis e cartões
 * - [dim]: textos secundários ou atenuados
 * - [off]: elementos desativados ou sem foco
 */
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

// Converte um valor RGB de 24 bits (0xRRGGBB) em uma `Color` opaca (alfa `0xFF`).
private fun col(rgb: Long) = Color(0xFF000000L or rgb)

// Construtor auxiliar para declarar paletas de forma compacta usando literais hexadecimais RGB.
private fun pal(
    key: String, name: String,
    y: Long, c: Long, r: Long, bg: Long, bg2: Long, pn: Long, dim: Long, off: Long,
) = NcPalette(key, name, col(y), col(c), col(r), col(bg), col(bg2), col(pn), col(dim), col(off))

/**
 * Catálogo de paletas pré-configuradas disponíveis no launcher.
 */
val Palettes: List<NcPalette> = listOf(
    pal("night", "NIGHT CITY", 0xFCEE09, 0x02D7F2, 0xFF003C, 0x08080C, 0x0D0D14, 0x101018, 0x8A8A52, 0x5C5C3A),
    pal("arasaka", "ARASAKA", 0xFF2A40, 0xFFD1D6, 0xFF7A00, 0x0B0507, 0x150709, 0x1A0B0E, 0x9A5A62, 0x5A3A3E),
    pal("cmatrix", "CMATRIX", 0x00FF41, 0xB6FFCB, 0xE0FFE8, 0x010A03, 0x02120A, 0x04160B, 0x2F8A4A, 0x1F4A2B),
    pal("ultra", "ULTRAVIOLETA", 0xB86BFF, 0xFF9A3D, 0xFF4FD8, 0x0A0612, 0x120A1E, 0x170D27, 0x7D5FAE, 0x483A66),
    pal("dracula", "DRACULA", 0xBD93F9, 0x8BE9FD, 0xFF79C6, 0x1E1F29, 0x282A36, 0x2B2D3B, 0x6272A4, 0x44475A),
    pal("monokai", "MONOKAI", 0xE6DB74, 0x66D9EF, 0xF92672, 0x1E1F1C, 0x272822, 0x2E2F29, 0x75715E, 0x49483E),
    pal("nord", "NORD", 0x88C0D0, 0xA3BE8C, 0xBF616A, 0x242933, 0x2E3440, 0x353C4A, 0x616E88, 0x434C5E),
    pal("solar", "SOLARIZED", 0xB58900, 0x2AA198, 0xDC322F, 0x00212B, 0x002B36, 0x073642, 0x657B83, 0x586E75),
    pal("onedark", "ONE DARK", 0xE5A86B, 0x98C379, 0xE06C75, 0x1A1716, 0x25211F, 0x302A27, 0x81766D, 0x514943),
    pal("gruvbox", "GRUVBOX", 0xFABD2F, 0x8EC07C, 0xFB4934, 0x1D2021, 0x282828, 0x32302F, 0x928374, 0x504945),
    pal("tokyo", "TOKYO NIGHT", 0x7AA2F7, 0xBB9AF7, 0xF7768E, 0x16161E, 0x1A1B26, 0x24283B, 0x565F89, 0x3B4261),
    pal("mocha", "CATPPUCCIN", 0xCBA6F7, 0x89DCEB, 0xF38BA8, 0x11111B, 0x1E1E2E, 0x313244, 0x6C7086, 0x45475A),
    pal("synth", "SYNTHWAVE", 0xFF7EDB, 0x36F9F6, 0xFEDE5D, 0x1A1033, 0x241B2F, 0x2A2139, 0x848BBD, 0x495495),
    pal("github", "GITHUB DARK", 0x58A6FF, 0x3FB950, 0xF85149, 0x0D1117, 0x161B22, 0x1C2128, 0x8B949E, 0x484F58),
    pal("rose", "ROSE PINE", 0xEBBCBA, 0x9CCFD8, 0xEB6F92, 0x191724, 0x1F1D2E, 0x26233A, 0x6E6A86, 0x403D52),
    pal("amber", "AMBAR CRT", 0xFFB000, 0xFFD780, 0xFF5F1F, 0x0A0600, 0x120A00, 0x1A1000, 0x8A6A20, 0x4A3A10),
    pal("ice", "GELO POLAR", 0xDCEBFF, 0x5AC8FA, 0xFF6B6B, 0x060A12, 0x0B111C, 0x101826, 0x6B7C99, 0x34415A),
    pal("chocowave", "CHOCOWAVE", 0xFFB980, 0x80D4FF, 0xFF75B5, 0x181416, 0x221C1F, 0x2D2529, 0x9E7D8A, 0x5C4851),
    pal("psone", "PSONE 1994", 0xA0A6B2, 0x5678B9, 0xFF6B35, 0x22242B, 0x2D3039, 0x3A3E4A, 0x8A8E9A, 0x50545E),
    pal("navycopper", "NAVY COPPER", 0xA7ADB4, 0xB8734F, 0xD18B63, 0x080F19, 0x101B29, 0x1B2938, 0x788999, 0x465563),
    pal("blackops2", "BO-II", 0xFF6A00, 0xDCE8F0, 0xFFD000, 0x030508, 0x0A1018, 0x151F2B, 0x8496A8, 0x394B5E),
    pal("monochrome", "MONOCHROME BW", 0xFFFFFF, 0xC8C8C8, 0x888888, 0x050505, 0x111111, 0x1E1E1E, 0x777777, 0x444444),
    pal("nightshift", "NIGHTSHIFT", 0x00F0FF, 0xA5FCFF, 0x00A6B8, 0x000000, 0x02090C, 0x071318, 0x54858C, 0x29464D),
    pal("p3reload", "P3 RELOAD", 0x00BFFF, 0x7FD8FF, 0xFF3B3B, 0x080B18, 0x0B1630, 0x14294A, 0x6B8CA8, 0x2F4B68),
    pal("pinklemon", "PINKLEMONADE", 0xC8FF00, 0xFF70C5, 0x00E0FF, 0x090D08, 0x141B12, 0x232D1B, 0x899B70, 0x505F42),
    pal("miku", "HATSUNE MIKU", 0x00D8FF, 0x7DEBFF, 0xFF2D8B, 0x0A0F14, 0x101923, 0x1B2D3F, 0x7A9BAA, 0x3F4F5F),
    pal("luka", "MEGURINE LUKA", 0xFF4BD6, 0xFFB3E6, 0x9B2DFF, 0x0B0911, 0x130D17, 0x211A26, 0x7E667B, 0x4A3F4F),
    )

// Busca uma paleta padrão pela `key`; caso não exista, cai para a primeira (`NIGHT CITY`).
fun paletteByKey(key: String): NcPalette = Palettes.firstOrNull { it.key == key } ?: Palettes[0]

// CompositionLocal para propagar a `NcPalette` ativa pela árvore Compose sem passar o objeto manualmente.
val LocalNc = staticCompositionLocalOf { Palettes[0] }

/**
 * Resolve a paleta ativa dando prioridade às paletas criadas pelo usuário (`custom`)
 * e recorrendo às paletas embutidas (`Palettes`) como fallback.
 */
fun paletteFrom(key: String, custom: List<NcPalette>): NcPalette =
    custom.firstOrNull { it.key == key } ?: paletteByKey(key)

/**
 * Converte uma string hexadecimal de 6 dígitos (com ou sem `#` e espaços) em um `Long` RGB.
 * Retorna `null` se o formato for inválido.
 */
fun parseHex(s: String): Long? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)
}

/**
 * Monta uma `NcPalette` a partir de uma lista de 8 valores RGB na ordem:
 * `[destaque (y), acento (c), alerta (r), fundo (bg), fundo2 (bg2), painéis (pn), apagado (dim), desligado (off)]`.
 */
fun paletteOf(key: String, name: String, v: List<Long>): NcPalette =
    NcPalette(key, name, col(v[0]), col(v[1]), col(v[2]), col(v[3]), col(v[4]), col(v[5]), col(v[6]), col(v[7]))

/**
 * Exporta as 8 cores da paleta como strings hexadecimais de 6 dígitos (sem alfa),
 * útil para edição e persistência de paletas customizadas.
 */
fun NcPalette.hexList(): List<String> =
    listOf(y, c, r, bg, bg2, pn, dim, off).map { "%06X".format(it.toArgb() and 0xFFFFFF) }