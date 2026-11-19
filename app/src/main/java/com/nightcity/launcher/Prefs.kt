package com.nightcity.launcher

import android.content.Context
import android.content.SharedPreferences

/** Configurações guardadas no aparelho. Também é lido pelo serviço de acessibilidade. */
class Prefs(ctx: Context) {
    val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("nc", Context.MODE_PRIVATE)

    /** "b" = inferior, "l" = esquerda, "r" = direita */
    var navPos: String
        get() = sp.getString("navPos", "b") ?: "b"
        set(v) { sp.edit().putString("navPos", v).apply() }

    var palKey: String
        get() = sp.getString("palKey", "night") ?: "night"
        set(v) { sp.edit().putString("palKey", v).apply() }

    var guide: Boolean
        get() = sp.getBoolean("guide", true)
        set(v) { sp.edit().putBoolean("guide", v).apply() }

    /** "mono" = monograma, "real" = ícone do app */
    var iconMode: String
        get() = sp.getString("iconMode", "mono") ?: "mono"
        set(v) { sp.edit().putString("iconMode", v).apply() }

    /** Afastamento da faixa de gesto em relação à borda, em dp. */
    var edgeInset: Int
        get() = sp.getInt("edgeInset", 16)
        set(v) { sp.edit().putInt("edgeInset", v).apply() }

    var lat: String
        get() = sp.getString("lat", "") ?: ""
        set(v) { sp.edit().putString("lat", v).apply() }

    var lon: String
        get() = sp.getString("lon", "") ?: ""
        set(v) { sp.edit().putString("lon", v).apply() }

    fun dock(): List<String?> {
        val raw = sp.getString("dock", "") ?: ""
        val parts = raw.split(",")
        return List(4) { i -> parts.getOrNull(i)?.takeIf { it.isNotBlank() } }
    }

    fun saveDock(list: List<String?>) {
        sp.edit().putString("dock", list.joinToString(",") { it ?: "" }).apply()
    }

    /** Pares (pacote, instante do último uso em ms). */
    fun recents(): List<Pair<String, Long>> {
        val raw = sp.getString("recents", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull {
            val p = it.split("|")
            val t = p.getOrNull(1)?.toLongOrNull()
            if (p.size == 2 && t != null) p[0] to t else null
        }
    }

    fun saveRecents(list: List<Pair<String, Long>>) {
        sp.edit().putString("recents", list.joinToString(";") { "${it.first}|${it.second}" }).apply()
    }

    // --- DAEMONS: até DAEMON_SLOTS apps fixados (slot vazio = null)
    fun daemons(): List<String?> {
        val raw = sp.getString("nc_daemons", "") ?: ""
        val parts = raw.split(",")
        return List(DAEMON_SLOTS) { i -> parts.getOrNull(i)?.takeIf { it.isNotBlank() } }
    }

    fun saveDaemons(list: List<String?>) {
        sp.edit().putString("nc_daemons", list.joinToString(",") { it ?: "" }).apply()
    }

    // --- notificações e filtros
    /** "compact" | "detail" | "hidden" */
    var notifStyle: String
        get() = sp.getString("notifStyle", "detail") ?: "detail"
        set(v) { sp.edit().putString("notifStyle", v).apply() }

    var notifMax: Int
        get() = sp.getInt("notifMax", 5)
        set(v) { sp.edit().putInt("notifMax", v).apply() }

    /** "off" | "mono" | "pal" | "amber" | "dim" */
    var filterMode: String
        get() = sp.getString("filterMode", "off") ?: "off"
        set(v) { sp.edit().putString("filterMode", v).apply() }

    var filterLevel: Int
        get() = sp.getInt("filterLevel", 40)
        set(v) { sp.edit().putInt("filterLevel", v).apply() }

    // --- paletas criadas pelo usuário: "chave|nome|y|c|r|bg|bg2|pn|dim|off;..." (cores em hexadecimal)
    fun customPalettes(): List<NcPalette> {
        val raw = sp.getString("customPalettes", "") ?: ""
        if (raw.isBlank()) return emptyList()
        val out = ArrayList<NcPalette>()
        for (item in raw.split(";")) {
            val f = item.split("|")
            if (f.size != 10) continue
            val v = ArrayList<Long>()
            for (h in f.drop(2)) {
                val x = parseHex(h) ?: break
                v.add(x)
            }
            if (v.size == 8) out.add(paletteOf(f[0], f[1], v))
        }
        return out
    }

    fun saveCustomPalettes(list: List<NcPalette>) {
        val raw = list.joinToString(";") { p -> p.key + "|" + p.name + "|" + p.hexList().joinToString("|") }
        sp.edit().putString("customPalettes", raw).apply()
    }

    /** Paleta em uso (de fábrica ou criada). Usado também pelo serviço dos botões. */
    fun currentPalette(): NcPalette = paletteFrom(palKey, customPalettes())
}
