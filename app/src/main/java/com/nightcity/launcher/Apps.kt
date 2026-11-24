package com.nightcity.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

const val CONFIG_PKG = "nc:config"

data class AppInfo(
    val pkg: String,
    val label: String,
    val cat: String, // tools | games | social
    val mono: String,
)

val ConfigApp = AppInfo(CONFIG_PKG, "Config", "tools", "CF")

fun monogram(label: String): String {
    val s = label.filter { it.isLetterOrDigit() }.take(2).uppercase()
    return if (s.isEmpty()) "??" else s
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val list = pm.queryIntentActivities(intent, 0)
    return list.asSequence()
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { ri ->
            val label = ri.loadLabel(pm).toString()
            val cat = when (ri.activityInfo.applicationInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> "games"
                ApplicationInfo.CATEGORY_SOCIAL -> "social"
                else -> "tools"
            }
            AppInfo(ri.activityInfo.packageName, label, cat, monogram(label))
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
        .toList()
}

fun launchApp(ctx: Context, pkg: String): Boolean {
    return try {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }
}

/** Abre a tela padrão do sistema "Informações do app" (permissões, armazenamento, limpar dados, desinstalar). */
fun openAppInfo(ctx: Context, pkg: String): Boolean = try {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (e: Exception) {
    false
}

/** Abre o gerenciamento de armazenamento do sistema, com alternativas caso o aparelho não tenha a primeira tela. */
fun openStorageSettings(ctx: Context): Boolean {
    val actions = listOf(
        StorageManager.ACTION_MANAGE_STORAGE,
        Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        Settings.ACTION_SETTINGS,
    )
    for (a in actions) {
        try {
            ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: Exception) { }
    }
    return false
}

/** Cache simples dos ícones reais (usado só quando a opção "ÍCONES: REAL" está ligada). */
object IconCache {
    private val map = HashMap<String, ImageBitmap>()

    fun peek(pkg: String): ImageBitmap? = synchronized(map) { map[pkg] }

    fun get(ctx: Context, pkg: String): ImageBitmap? {
        peek(pkg)?.let { return it }
        return try {
            val bmp = ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            synchronized(map) { map[pkg] = bmp }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}