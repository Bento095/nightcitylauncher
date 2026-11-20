package com.nightcity.launcher

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Lê o estado real do aparelho (bateria, modos, rede, Bluetooth, GPS...).
 * Tudo aqui é só LEITURA: os indicadores não ligam nem desligam nada.
 */
class DeviceState(private val ctx: Context) {
    var battLevel by mutableIntStateOf(0)
    var charging by mutableStateOf(false)
    var saver by mutableStateOf(false)
    var battTemp by mutableIntStateOf(0) // décimos de °C
    var airplane by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var dnd by mutableStateOf(false)
    var hotspot by mutableStateOf(false)
    var alarm by mutableStateOf(false)
    var bt by mutableStateOf(false)
    var gps by mutableStateOf(false)
    var nfc by mutableStateOf(false)
    var nfcPresent by mutableStateOf(false)
    var netText by mutableStateOf("OFFLINE")
    var netBars by mutableIntStateOf(0)
    var ramPct by mutableIntStateOf(0)
    var diskPct by mutableIntStateOf(0)

    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var netCb: ConnectivityManager.NetworkCallback? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == "android.net.wifi.WIFI_AP_STATE_CHANGED") {
                // 13 = hotspot ligado, 11 = desligado
                hotspot = i.getIntExtra("wifi_state", 11) % 10 == 3
            }
            refresh()
        }
    }

    fun start() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        }
        try {
            ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) { /* ignora */ }
        try {
            val cb = makeCallback()
            netCb = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) { /* ignora */ }
        refresh()
    }

    fun stop() {
        try { ctx.unregisterReceiver(receiver) } catch (e: Exception) { }
        netCb?.let { try { cm.unregisterNetworkCallback(it) } catch (e: Exception) { } }
        netCb = null
    }

    fun refresh() {
        try {
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (b != null) {
                val lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (lv >= 0 && sc > 0) battLevel = lv * 100 / sc
                val st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                    st == BatteryManager.BATTERY_STATUS_FULL
                battTemp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            }
        } catch (e: Exception) { }
        try {
            saver = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        } catch (e: Exception) { }
        try {
            airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) { }
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            muted = am.ringerMode != AudioManager.RINGER_MODE_NORMAL
        } catch (e: Exception) { }
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val f = nm.currentInterruptionFilter
            dnd = f != NotificationManager.INTERRUPTION_FILTER_ALL &&
                f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        } catch (e: Exception) { }
        try {
            val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bt = bm.adapter?.isEnabled == true
        } catch (e: Exception) { bt = false }
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            gps = LocationManagerCompat.isLocationEnabled(lm)
        } catch (e: Exception) { }
        try {
            alarm = (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock != null
        } catch (e: Exception) { }
        try {
            val a = NfcAdapter.getDefaultAdapter(ctx)
            nfcPresent = a != null
            nfc = a?.isEnabled == true
        } catch (e: Exception) { }
    }

    /** Chamado a cada poucos segundos pela tela inicial. */
    fun pollStats() {
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            if (mi.totalMem > 0) ramPct = ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt()
        } catch (e: Exception) { }
        try {
            val s = StatFs(Environment.getDataDirectory().path)
            val total = s.blockCountLong
            if (total > 0) diskPct = ((total - s.availableBlocksLong) * 100 / total).toInt()
        } catch (e: Exception) { }
        // o tipo de rede móvel pode mudar sem aviso (4G <-> 5G)
        try {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                netText = mobileType()
            }
        } catch (e: Exception) { }
    }

    private fun makeCallback(): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(caps)
                override fun onLost(network: Network) = onNone()
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(caps)
                override fun onLost(network: Network) = onNone()
            }
        }
    }

    private fun onNone() {
        netText = "OFFLINE"
        netBars = 0
    }

    private fun onCaps(c: NetworkCapabilities) {
        when {
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                var ssid: String? = null
                var bars = 3
                if (Build.VERSION.SDK_INT >= 29) {
                    val wi = c.transportInfo as? WifiInfo
                    if (wi != null) {
                        val s = wi.ssid
                        if (s != null && s != WifiManager.UNKNOWN_SSID && s.isNotEmpty()) ssid = s.trim('"')
                        val r = wi.rssi
                        bars = if (r > -55) 4 else if (r > -65) 3 else if (r > -75) 2 else 1
                    }
                }
                netText = if (ssid != null) "WI-FI // $ssid" else "WI-FI"
                netBars = bars
            }
            c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                netText = mobileType()
                netBars = 3
            }
            c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                netText = "ETHERNET"
                netBars = 4
            }
            else -> {
                netText = "ONLINE"
                netBars = 2
            }
        }
    }

    @Suppress("MissingPermission")
    private fun mobileType(): String {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return "MÓVEL"
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "MÓVEL"
            }
        } catch (e: Exception) {
            "MÓVEL"
        }
    }
}

// ---------------------------------------------------------------- energia

/** Só vale depois de: adb shell pm grant com.nightcity.launcher android.permission.WRITE_SECURE_SETTINGS */
fun canWriteSecure(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, "android.permission.WRITE_SECURE_SETTINGS") ==
        PackageManager.PERMISSION_GRANTED

/** Liga/desliga a economia de bateria do Android. Devolve false se não tem permissão ou o sistema recusou. */
fun setBatterySaver(ctx: Context, on: Boolean): Boolean {
    if (!canWriteSecure(ctx)) return false
    return try {
        Settings.Global.putInt(ctx.contentResolver, "low_power", if (on) 1 else 0)
    } catch (e: Exception) {
        false
    }
}
