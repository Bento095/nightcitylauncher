package com.nightcity.launcher

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf

class NotifItem(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
    val clearable: Boolean,
    val autoCancel: Boolean = false,
)

/** Lista das notificações ativas, preenchida pelo serviço de leitura de notificações. */
object NotifStore {
    val items = mutableStateListOf<NotifItem>()
    var service: NotificationListenerService? = null

    // chave da notificação -> instante da última publicação (para saber o que é novo)
    private val seen = HashMap<String, Long>()

    fun isNewPost(sbn: StatusBarNotification): Boolean = synchronized(seen) {
        val prev = seen[sbn.key]
        prev == null || prev != sbn.postTime
    }

    /** Dispara o efeito de notificação (se estiver ligado em Config). */
    fun fx(s: NotificationListenerService, sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.isOngoing || sbn.packageName == s.packageName) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (Prefs(s).fxMode == "off") return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val app = try {
            s.packageManager.getApplicationLabel(s.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        NavState.service?.playNotifFx(app, title, key = sbn.key)
    }

    fun reload(s: NotificationListenerService) {
        val list: Array<StatusBarNotification> = try {
            s.activeNotifications ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
        val pm = s.packageManager
        val out = ArrayList<NotifItem>()
        for (sbn in list.sortedByDescending { it.postTime }) {
            if (sbn.isOngoing) continue // mídia, chamadas em andamento etc.
            val n = sbn.notification
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            val app = try {
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) {
                sbn.packageName
            }
            out.add(NotifItem(
                sbn.key, sbn.packageName, app, title, text, sbn.postTime, n.contentIntent, sbn.isClearable,
                (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
            ))
        }
        synchronized(seen) {
            seen.clear()
            for (sbn in list) seen[sbn.key] = sbn.postTime
        }
        items.clear()
        items.addAll(out)
    }

    /**
     * Abre a notificação: dispara o PendingIntent dela (ou o app, se não houver) e, quando a notificação
     * é do tipo "some ao tocar" (AUTO_CANCEL), remove-a também daqui, já que fora da barra do sistema
     * ninguém faz isso por ela.
     */
    fun open(ctx: Context, n: NotifItem): Boolean {
        var ok = false
        val pi = n.intent
        if (pi != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    val o = ActivityOptions.makeBasic()
                    // 1 = MODE_BACKGROUND_ACTIVITY_ALLOWED (a constante não aparece em alguns compileSdk)
                    o.setPendingIntentBackgroundActivityStartMode(1)
                    pi.send(ctx, 0, null, null, null, null, o.toBundle())
                } else {
                    pi.send()
                }
                ok = true
            } catch (e: Exception) { }
        }
        if (!ok) return launchApp(ctx, n.pkg)
        if (n.autoCancel && n.clearable) dismiss(n.key)
        return true
    }

    fun openByKey(ctx: Context, key: String): Boolean {
        val n = items.firstOrNull { it.key == key } ?: return false
        return open(ctx, n)
    }

    fun dismiss(key: String) {
        try { service?.cancelNotification(key) } catch (e: Exception) { }
    }

    fun dismissAll() {
        try { service?.cancelAllNotifications() } catch (e: Exception) { }
    }
}

/** Serviço de leitura de notificações: alimenta o cartão de mídia e o painel NOTIFICAÇÕES. */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        NotifStore.service = this
        NotifStore.reload(this)
    }

    override fun onListenerDisconnected() {
        NotifStore.service = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val novo = sbn != null && NotifStore.isNewPost(sbn)
        NotifStore.reload(this)
        if (novo && sbn != null) NotifStore.fx(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NotifStore.reload(this)
    }
}