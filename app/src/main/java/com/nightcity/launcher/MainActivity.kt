package com.nightcity.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Container de estado leve usado para "puxar" eventos do ciclo de vida da Activity
 * para dentro da árvore do Jetpack Compose sem acoplar a UI diretamente à Activity.
 */
class AppState {
    // Incrementado sempre que o app volta ao primeiro plano ou uma resposta de permissão chega,
    // permitindo que composables reajam e atualizem dados sensíveis ao ciclo de vida (ex.: bateria, rede, mídia).
    var resumeTick by mutableIntStateOf(0)

    // Disparado quando o usuário aciona o botão/gesto de Início estando já dentro do launcher,
    // sinalizando para a árvore Compose resetar navegação/scroll/gaveta e voltar à home.
    var homeSignal by mutableIntStateOf(0)
}

/**
 * Ponto de entrada principal do launcher.
 * Gerencia edge-to-edge, ocultação das barras do sistema, callbacks de permissão
 * e a montagem da raiz Compose (`NcRoot`).
 */
class MainActivity : ComponentActivity() {
    // Instância de estado compartilhada entre os callbacks da Activity e a hierarquia Compose.
    private val app = AppState()

    // Launcher moderno de resultados de atividade para pedir múltiplas permissões em tempo de execução.
    // Ao concluir o diálogo do sistema, incrementa `resumeTick` para forçar reavaliação de permissões/estado na UI.
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            app.resumeTick++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Habilita desenho de ponta a ponta (edge-to-edge) com barras transparentes e estilo escuro,
        // evitando que o sistema aplique fundos sólidos indesejados atrás das barras.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // Esconde status bar e navigation bar logo na criação da janela.
        hideSystemBars()

        // Conecta a árvore Jetpack Compose à Activity, passando o `AppState` e um callback
        // para disparar a solicitação de permissões a partir de qualquer ponto da UI.
        setContent {
            NcRoot(app) { perms -> permLauncher.launch(perms) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Notifica os composables de que a tela voltou a ficar ativa (ex.: retornando de um app ou tela de ajustes).
        app.resumeTick++
        // Reaplica a ocultação das barras, já que voltar de outras telas/diálogos pode reexibi-las.
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Quando a janela recupera o foco (ex.: fechar painel rápido, notificação ou diálogo),
        // garante que o modo imersivo continue ativo.
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Com `launchMode` adequado no Manifest, apertar Início estando já no launcher
        // entrega um novo intent aqui em vez de recriar a Activity; sinaliza retorno ao estado inicial.
        app.homeSignal++
    }

    /**
     * Configura o `WindowInsetsControllerCompat` para ocultar as barras do sistema (status e navegação),
     * permitindo que apareçam temporariamente apenas ao deslizar a borda (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
     */
    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}