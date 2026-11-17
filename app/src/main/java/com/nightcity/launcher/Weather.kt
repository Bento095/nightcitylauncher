package com.nightcity.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WxSlot(val label: String, val temp: Int, val glyph: String)
data class WxData(val temp: Int, val text: String, val glyph: String, val rain: Int, val slots: List<WxSlot>)

private fun wmo(code: Int): Pair<String, String> = when (code) {
    0 -> "LIMPO" to "☀"
    1, 2 -> "PARCIAL" to "☁"
    3 -> "NUBLADO" to "☁"
    45, 48 -> "NEBLINA" to "☁"
    in 51..57 -> "GAROA" to "☂"
    in 61..67 -> "CHUVA" to "☂"
    in 71..77, 85, 86 -> "NEVE" to "❄"
    in 80..82 -> "PANCADAS" to "☂"
    in 95..99 -> "TEMPESTADE" to "⚡"
    else -> "--" to "·"
}

private var cache: WxData? = null
private var cacheKey = ""
private var cacheAt = 0L

@SuppressLint("MissingPermission")
private fun lastLocation(ctx: Context): Pair<Double, Double>? {
    val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!ok) return null
    return try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
            .maxByOrNull { it.time }
        if (l == null) null else l.latitude to l.longitude
    } catch (e: Exception) {
        null
    }
}

/** Devolve (dados, mensagem de erro). Usa a Open-Meteo (sem chave de API). */
suspend fun fetchWeather(ctx: Context, latTxt: String, lonTxt: String): Pair<WxData?, String> =
    withContext(Dispatchers.IO) {
        try {
            val manual = latTxt.replace(',', '.').trim().toDoubleOrNull()?.let { la ->
                lonTxt.replace(',', '.').trim().toDoubleOrNull()?.let { lo -> la to lo }
            }
            val pos = manual ?: lastLocation(ctx)
                ?: return@withContext null to "DEFINA COORDENADAS EM CONFIG"
            val key = "%.2f,%.2f".format(java.util.Locale.US, pos.first, pos.second)
            if (cache != null && cacheKey == key && System.currentTimeMillis() - cacheAt < 30 * 60 * 1000L) {
                return@withContext cache to ""
            }
            val u = "https://api.open-meteo.com/v1/forecast?latitude=${pos.first}&longitude=${pos.second}" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&daily=temperature_2m_max,weather_code&timezone=auto&forecast_days=2"
            val con = URL(u).openConnection() as HttpURLConnection
            con.connectTimeout = 8000
            con.readTimeout = 8000
            val txt = con.inputStream.bufferedReader().use { it.readText() }
            con.disconnect()
            val j = JSONObject(txt)
            val cur = j.getJSONObject("current")
            val t = Math.round(cur.getDouble("temperature_2m")).toInt()
            val (ct, cg) = wmo(cur.getInt("weather_code"))
            val now = cur.getString("time").take(13)
            val h = j.getJSONObject("hourly")
            val times = h.getJSONArray("time")
            val temps = h.getJSONArray("temperature_2m")
            val pp = h.getJSONArray("precipitation_probability")
            val hc = h.getJSONArray("weather_code")
            var idx = 0
            for (i in 0 until times.length()) if (times.getString(i).take(13) <= now) idx = i
            val slots = ArrayList<WxSlot>()
            slots.add(WxSlot("AGORA", t, cg))
            for (d in intArrayOf(3, 6)) {
                val k = (idx + d).coerceAtMost(times.length() - 1)
                slots.add(WxSlot("+${d}H", Math.round(temps.getDouble(k)).toInt(), wmo(hc.optInt(k, 0)).second))
            }
            val dl = j.getJSONObject("daily")
            val mx = dl.getJSONArray("temperature_2m_max")
            val dc = dl.getJSONArray("weather_code")
            if (mx.length() > 1) {
                slots.add(WxSlot("AMANHÃ", Math.round(mx.getDouble(1)).toInt(), wmo(dc.optInt(1, 0)).second))
            }
            val data = WxData(t, ct, cg, pp.optInt(idx, 0), slots)
            cache = data
            cacheKey = key
            cacheAt = System.currentTimeMillis()
            data to ""
        } catch (e: Exception) {
            null to "SEM CONEXÃO"
        }
    }
