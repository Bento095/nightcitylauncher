package com.nightcity.launcher

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.toArgb

// Ajustes do sistema para o filtro de correção de cor (usados só para o monocromático real).
private const val KEY_EN = "accessibility_display_daltonizer_enabled"
private const val KEY_TYPE = "accessibility_display_daltonizer"

/** Cor (ARGB, sem transparência) e opacidade (0..1) da cobertura desenhada por cima de tudo. */
class FilterSpec(val color: Int, val alpha: Float)

/**
 * Cobertura que o modo atual precisa, ou null quando não precisa de nenhuma.
 *  - pal:   cor de destaque da paleta
 *  - amber: laranja quente (descanso para os olhos)
 *  - dim:   escurece a tela
 *  - mono:  sem permissão por adb, uma cobertura cinza aproxima o preto e branco;
 *           com permissão, o preto e branco real é feito pelo sistema (veja applySystemMono)
 */
fun filterSpec(ctx: Context, prefs: Prefs): FilterSpec? {
    val level = prefs.filterLevel.coerceIn(0, 100) / 100f
    return when (prefs.filterMode) {
        "pal" -> FilterSpec(prefs.currentPalette().y.toArgb() or 0xFF000000.toInt(), level * 0.40f)
        "amber" -> FilterSpec(0xFFFF9A1F.toInt(), level * 0.45f)
        "dim" -> FilterSpec(0xFF000000.toInt(), level * 0.60f)
        "mono" -> if (canWriteSecure(ctx)) null else FilterSpec(0xFF808080.toInt(), level * 0.85f)
        else -> null
    }
}

/**
 * Liga/desliga o preto e branco real usando a correção de cor do sistema.
 * Exige WRITE_SECURE_SETTINGS (concedida por adb). Guarda e restaura a configuração anterior.
 */
fun applySystemMono(ctx: Context, on: Boolean) {
    if (!canWriteSecure(ctx)) return
    try {
        val cr = ctx.contentResolver
        val sp = Prefs(ctx).sp
        val active = sp.getBoolean("monoActive", false)
        if (on) {
            if (!active) {
                sp.edit()
                    .putBoolean("monoActive", true)
                    .putInt("monoPrevEn", Settings.Secure.getInt(cr, KEY_EN, 0))
                    .putInt("monoPrevType", Settings.Secure.getInt(cr, KEY_TYPE, -1))
                    .apply()
            }
            Settings.Secure.putInt(cr, KEY_TYPE, 0) // 0 = monocromacia
            Settings.Secure.putInt(cr, KEY_EN, 1)
        } else if (active) {
            Settings.Secure.putInt(cr, KEY_EN, sp.getInt("monoPrevEn", 0))
            Settings.Secure.putInt(cr, KEY_TYPE, sp.getInt("monoPrevType", -1))
            sp.edit().putBoolean("monoActive", false).apply()
        }
    } catch (e: Exception) {
        Log.w("NcNav", "não foi possível ajustar o monocromático do sistema", e)
    }
}
