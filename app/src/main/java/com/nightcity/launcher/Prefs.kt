package com.nightcity.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Camada de persistência leve baseada em `SharedPreferences` (`"nc"`).
 * Centraliza todas as preferências do launcher e também é consultada por serviços
 * (como o serviço de acessibilidade/botões sobrepostos) para manter o mesmo estado.
 */
class Prefs(ctx: Context) {
    // Usa `applicationContext` para evitar vazamento de memória caso a classe sobreviva a uma Activity.
    val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("nc", Context.MODE_PRIVATE)

    /** Posição da navegação sobreposta: `"b"` = inferior, `"l"` = esquerda, `"r"` = direita. */
    var navPos: String
        get() = sp.getString("navPos", "b") ?: "b"
        set(v) { sp.edit().putString("navPos", v).apply() }

    // Chave identificador da paleta ativa (padrão `"night"` -> NIGHT CITY).
    var palKey: String
        get() = sp.getString("palKey", "night") ?: "night"
        set(v) { sp.edit().putString("palKey", v).apply() }

    // Indica se guias visuais de apoio/navegação devem ser exibidos.
    var guide: Boolean
        get() = sp.getBoolean("guide", true)
        set(v) { sp.edit().putBoolean("guide", v).apply() }

    /** Modo de exibição de ícones: `"mono"` = monograma estilizado, `"real"` = ícone do app. */
    var iconMode: String
        get() = sp.getString("iconMode", "mono") ?: "mono"
        set(v) { sp.edit().putString("iconMode", v).apply() }

    /** Afastamento da faixa de gesto em relação à borda da tela, em dp. */
    var edgeInset: Int
        get() = sp.getInt("edgeInset", 16)
        set(v) { sp.edit().putInt("edgeInset", v).apply() }

    // Coordenadas geográficas (latitude e longitude) usadas para buscar a previsão do tempo.
    var lat: String
        get() = sp.getString("lat", "") ?: ""
        set(v) { sp.edit().putString("lat", v).apply() }

    var lon: String
        get() = sp.getString("lon", "") ?: ""
        set(v) { sp.edit().putString("lon", v).apply() }

    /**
     * Retorna os 4 slots da Dock como lista de tamanho fixo (`null` representa slot vazio).
     * Os pacotes são serializados separados por vírgula.
     */
    fun dock(): List<String?> {
        val raw = sp.getString("dock", "") ?: ""
        val parts = raw.split(",")
        return List(4) { i -> parts.getOrNull(i)?.takeIf { it.isNotBlank() } }
    }

    // Persiste os 4 slots da Dock mantendo posições vazias como string vazia entre vírgulas.
    fun saveDock(list: List<String?>) {
        sp.edit().putString("dock", list.joinToString(",") { it ?: "" }).apply()
    }

    /**
     * Histórico de aplicativos recentes no formato `pares (packageName, timestampMs)`.
     * Serializado como `"pkg1|ms1;pkg2|ms2"` e ignora entradas malformadas.
     */
    fun recents(): List<Pair<String, Long>> {
        val raw = sp.getString("recents", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull {
            val p = it.split("|")
            val t = p.getOrNull(1)?.toLongOrNull()
            if (p.size == 2 && t != null) p[0] to t else null
        }
    }

    // Salva a lista de aplicativos recentes no formato `"pacote|timestamp"`.
    fun saveRecents(list: List<Pair<String, Long>>) {
        sp.edit().putString("recents", list.joinToString(";") { "${it.first}|${it.second}" }).apply()
    }

    // --- DAEMONS: até `DAEMON_SLOTS` aplicativos fixados (slot vazio = `null`) ---
    fun daemons(): List<String?> {
        val raw = sp.getString("nc_daemons", "") ?: ""
        val parts = raw.split(",")
        return List(DAEMON_SLOTS) { i -> parts.getOrNull(i)?.takeIf { it.isNotBlank() } }
    }

    // Persiste a lista de Daemons respeitando os slots vazios (`null`).
    fun saveDaemons(list: List<String?>) {
        sp.edit().putString("nc_daemons", list.joinToString(",") { it ?: "" }).apply()
    }

    // --- Notificações e filtros de tela ---

    /** Estilo visual das notificações na Home: `"compact"`, `"detail"` ou `"hidden"`. */
    var notifStyle: String
        get() = sp.getString("notifStyle", "detail") ?: "detail"
        set(v) { sp.edit().putString("notifStyle", v).apply() }

    // Quantidade máxima de notificações exibidas no painel antes de resumir o restante.
    var notifMax: Int
        get() = sp.getInt("notifMax", 5)
        set(v) { sp.edit().putInt("notifMax", v).apply() }

    /** Modo de filtro de cor/luminância: `"off"`, `"mono"`, `"pal"`, `"amber"` ou `"dim"`. */
    var filterMode: String
        get() = sp.getString("filterMode", "off") ?: "off"
        set(v) { sp.edit().putString("filterMode", v).apply() }

    // Intensidade/nível numérico do filtro aplicado.
    var filterLevel: Int
        get() = sp.getInt("filterLevel", 40)
        set(v) { sp.edit().putInt("filterLevel", v).apply() }

    // --- Efeito visual de notificação (focado em telas OLED) ---

    /** Tipo de animação/efeito ao receber notificação: `"off"`, `"glitch"`, `"crt"` ou `"matrix"`. */
    var fxMode: String
        get() = sp.getString("fxMode", "off") ?: "off"
        set(v) { sp.edit().putString("fxMode", v).apply() }

    // Duração do efeito de notificação em segundos.
    var fxSecs: Int
        get() = sp.getInt("fxSecs", 4)
        set(v) { sp.edit().putInt("fxSecs", v).apply() }

    /** Se `true`, acende a tela apagada ao chegar notificação e a desliga em seguida. */
    var fxWake: Boolean
        get() = sp.getBoolean("fxWake", false)
        set(v) { sp.edit().putBoolean("fxWake", v).apply() }

    /** Se `true`, exibe o efeito por cima de outros aplicativos quando a tela está ligada. */
    var fxOver: Boolean
        get() = sp.getBoolean("fxOver", true)
        set(v) { sp.edit().putBoolean("fxOver", v).apply() }

    // --- Paletas criadas pelo usuário ---
    // Formato por item: "chave|nome|y|c|r|bg|bg2|pn|dim|off" (8 cores em hex RGB), separados por ";".
    fun customPalettes(): List<NcPalette> {
        val raw = sp.getString("customPalettes", "") ?: ""
        if (raw.isBlank()) return emptyList()
        val out = ArrayList<NcPalette>()
        for (item in raw.split(";")) {
            val f = item.split("|")
            // Exige exatamente 10 campos: chave, nome + 8 cores hexadecimais.
            if (f.size != 10) continue
            val v = ArrayList<Long>()
            for (h in f.drop(2)) {
                val x = parseHex(h) ?: break
                v.add(x)
            }
            // Só adiciona a paleta se todas as 8 cores forem válidas.
            if (v.size == 8) out.add(paletteOf(f[0], f[1], v))
        }
        return out
    }

    // Serializa e salva todas as paletas personalizadas do usuário.
    fun saveCustomPalettes(list: List<NcPalette>) {
        val raw = list.joinToString(";") { p -> p.key + "|" + p.name + "|" + p.hexList().joinToString("|") }
        sp.edit().putString("customPalettes", raw).apply()
    }

    /**
     * Retorna a `NcPalette` atualmente ativa (seja customizada ou de fábrica).
     * Usado também pelo serviço de botões/acessibilidade para manter consistência visual.
     */
    fun currentPalette(): NcPalette = paletteFrom(palKey, customPalettes())
}