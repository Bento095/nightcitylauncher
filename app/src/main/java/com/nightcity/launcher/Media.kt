package com.nightcity.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

/** Serviço vazio: só existe para o usuário poder liberar o "Acesso a notificações", exigido para ler a mídia. */
class MediaListenerService : NotificationListenerService()

class MediaState(private val ctx: Context) {
    var hasAccess by mutableStateOf(false)
    var hasSession by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var appLabel by mutableStateOf("")
    var playing by mutableStateOf(false)
    var durationMs by mutableLongStateOf(0L)
    private var posMs = 0L
    private var stamp = 0L
    private var speed = 1f

    private var controller: MediaController? = null
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val comp = ComponentName(ctx, MediaListenerService::class.java)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list)
    }

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = applyState(state)
        override fun onMetadataChanged(metadata: MediaMetadata?) = applyMeta(metadata)
        override fun onSessionDestroyed() {
            controller = null
            clear()
        }
    }

    fun start() {
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        if (!hasAccess) {
            detach()
            clear()
            return
        }
        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp)
            pick(msm.getActiveSessions(comp))
        } catch (e: SecurityException) {
            hasAccess = false
            clear()
        } catch (e: Exception) { }
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) { }
        detach()
    }

    private fun detach() {
        try { controller?.unregisterCallback(cb) } catch (e: Exception) { }
        controller = null
    }

    private fun clear() {
        hasSession = false
        title = ""
        artist = ""
        appLabel = ""
        playing = false
        durationMs = 0L
        posMs = 0L
    }

    private fun pick(list: List<MediaController>?) {
        val l = list.orEmpty()
        val best = l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: l.firstOrNull { it.metadata != null }
            ?: l.firstOrNull()
        if (best == null) {
            detach()
            clear()
            return
        }
        if (controller?.sessionToken != best.sessionToken) {
            detach()
            controller = best
            best.registerCallback(cb)
        }
        appLabel = try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(best.packageName, 0)).toString()
        } catch (e: Exception) {
            best.packageName
        }
        hasSession = true
        applyMeta(best.metadata)
        applyState(best.playbackState)
    }

    private fun applyMeta(md: MediaMetadata?) {
        title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    }

    private fun applyState(s: PlaybackState?) {
        playing = s?.state == PlaybackState.STATE_PLAYING
        posMs = s?.position ?: 0L
        stamp = s?.lastPositionUpdateTime ?: 0L
        speed = s?.playbackSpeed ?: 1f
    }

    /** Posição atual da faixa em ms (estimada entre as atualizações do player). */
    fun position(nowElapsed: Long): Long {
        val p = if (playing) posMs + ((nowElapsed - stamp) * speed).toLong() else posMs
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }

    fun playPause() {
        val t = controller?.transportControls ?: return
        if (playing) t.pause() else t.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }
}
