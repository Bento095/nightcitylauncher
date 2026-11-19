# Night City OS: launcher Android

Launcher para Android com visual cyberpunk/terminal, feito em **Kotlin + Jetpack Compose**.
Nasceu como protótipo HTML e funciona em Android 8.0 ou mais novo.

> **Status: versão 0.1.** O código foi escrito sem poder compilar no ambiente de criação, então a
> primeira sincronização no Android Studio pode apontar erros pequenos. Se aparecerem, corrija ou
> cole a mensagem para quem estiver dando manutenção.

---

## Sumário

1. [Recursos](#recursos)
2. [Requisitos e como abrir o projeto](#requisitos-e-como-abrir-o-projeto)
3. [Primeiro uso](#primeiro-uso)
4. [Guia da tela inicial](#guia-da-tela-inicial)
5. [Gestos e atalhos](#gestos-e-atalhos)
6. [Config do launcher](#config-do-launcher)
7. [Permissões e serviços](#permissões-e-serviços)
8. [Dicas de sistema](#dicas-de-sistema)
9. [Privacidade](#privacidade)
10. [Estrutura do projeto](#estrutura-do-projeto)
11. [Limites conhecidos](#limites-conhecidos)
12. [Problemas comuns](#problemas-comuns)
13. [O que ainda não existe](#o-que-ainda-não-existe)

---

## Recursos

- **Topo fixo:** relógio com efeito glitch, ícones de status (só aparecem os ativos), bateria com porcentagem e rede (nome do Wi-Fi ou 4G/5G).
- **Bateria animada:** carregando (brilho que varre o preenchimento), economia (contorno tracejado pulsando) e baixa (vermelha piscando).
- **Cartão de mídia:** controla o áudio que estiver tocando (anterior, pausar, próxima) com barra de progresso.
- **BREACH PROTOCOL:** previsão do tempo real (Open-Meteo), com as próximas horas e o dia seguinte.
- **NOTIFICAÇÕES:** painel próprio com formato configurável.
- **Faixa de data, Bluetooth e GPS:** Bluetooth e GPS só mudam de cor (ligado ou desligado).
- **QUICKHACKS:** os últimos apps abertos pelo launcher.
- **DIAGNÓSTICO:** RAM, armazenamento, temperatura e bateria, com gráfico de RAM. Tocar altera a economia de energia.
- **DAEMONS:** gaveta de até 6 apps fixados por arrastar e soltar.
- **Dock editável:** botão APPS e 4 espaços para apps, também por arrastar e soltar.
- **Gaveta de apps:** semitransparente, com busca, abas (TUDO, TOOLS, GAMES, SOCIAL) e índice A–Z.
- **Barra de navegação oculta:** Voltar, Início e Recentes por cima de qualquer app, revelada ao deslizar a partir da borda (inferior, esquerda ou direita).
- **Paletas:** 25 de fábrica e paletas criadas por você (nome e cor de cada área).
- **Ícones:** monograma estilo terminal ou ícone real do app.
- **Filtros de tela:** monocromático (foco), cor da paleta, âmbar (descanso para os olhos) e escurecer, por cima de qualquer app, com intensidade ajustável.

---

## Requisitos e como abrir o projeto

| Item | Versão usada |
|---|---|
| Android Studio | Rabbit 2 (ou mais novo) |
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.6.0 |
| Plugin do compilador do Compose | 2.4.10 |
| Compose BOM | 2026.09.00 |
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 36 / 26 |
| JDK | 17 ou mais novo (o do Studio serve) |

**Passo a passo (Fedora):**

1. Coloque a pasta do projeto em `/mnt/arquivos/Dev/Android/projetos/NightCityLauncher`.
2. No Android Studio: **File > Open** e escolha a pasta.
3. Aceite o que o Studio pedir para baixar (SDK Platform 37, Build Tools, Gradle 9.6.0).
4. Espere o **Gradle Sync** terminar.
5. No celular, ligue a **depuração USB** e clique em **Run**.

Se o Studio reclamar de versão, abra o aviso e aceite a sugestão de atualização ou ajuste os números em `build.gradle.kts` e `app/build.gradle.kts`.

---

## Primeiro uso

1. Aperte o botão **Início** do celular e escolha **Night City OS** > **Sempre**. Alternativa: Config > Permissões > *Definir como launcher padrão*.
2. Abra a **Config** (segure o relógio, ou use o app "Config" na gaveta, ou o botão `[ SYS // CONFIG ]`).
3. Na seção **Permissões e serviços**, ative o que quiser usar:
   - **Acesso a notificações:** controle de mídia e painel NOTIFICAÇÕES.
   - **Serviço de acessibilidade:** botões de navegação por cima de qualquer app.
   - **Localização:** nome do Wi-Fi.
   - **Estado do telefone:** distinguir 4G e 5G.
4. (Opcional) Digite latitude e longitude em *Previsão: coordenadas* para a previsão não depender da localização.

O launcher esconde as barras do sistema (modo imersivo). Deslize a partir da borda superior ou inferior para vê-las por um instante.

---

## Guia da tela inicial

De cima para baixo:

| Área | O que mostra | Toque |
|---|---|---|
| **Topo fixo** | Relógio, ícones de status ativos, bateria, rede | Relógio: abre o app Relógio. Segurar o relógio: abre a Config |
| **Cartão amarelo** | Mídia em reprodução e controles | Botões ◁◁ ❚❚ ▷▷. Sem acesso, o toque abre a liberação de notificações |
| **BREACH PROTOCOL // PREVISÃO** | Temperatura, condição e chance de chuva | Abre/fecha a previsão das próximas horas |
| **NOTIFICAÇÕES** | Notificações ativas | Abre a notificação. ✕ dispensa. `[ LIMPAR TUDO ]` dispensa todas |
| **Faixa de data** | Data, BLUETOOTH, GPS | Data: abre a Agenda. Bluetooth e GPS só mudam de cor |
| **QUICKHACKS** | Últimos 8 apps abertos pelo launcher e há quanto tempo | Abre o app |
| **DIAGNÓSTICO** | RAM, ARMAZ., TEMP, BAT e gráfico de RAM | Alterna a economia de energia (veja [Energia](#energia)) |
| **DAEMONS** | Até 6 apps fixados | Abre o app. Arrastar reorganiza |
| **Acesso rápido** | `[ SYS // CONFIG ]` `[ MAPS // NAV ]` `[ TERMUX // SHELL ]` | Abre a Config, o app de mapas e o Termux |
| **Dock** (fixa embaixo) | APPS + 4 apps | APPS abre a gaveta. Arrastar edita |

O topo fica parado quando a tela rola; o resto rola por baixo dele.

### Ícones de status (topo)

Aparecem só quando ativos: modo avião, silencioso, não perturbe, hotspot e alarme. São apenas indicadores, não ligam nem desligam nada.

### Rede

- Wi-Fi: mostra `WI-FI // nome-da-rede` (precisa da permissão de localização).
- Dados móveis: `4G LTE`, `5G`, `3G`... (precisa da permissão de estado do telefone). Rede 5G do tipo "não autônomo" aparece como 4G LTE.

---

## Gestos e atalhos

| Gesto | Resultado |
|---|---|
| Puxar para cima **a partir dos 10% inferiores da tela** (ou da dock) | Abre a gaveta de apps |
| Arrastar em qualquer outra região | Só rola a tela inicial |
| Puxar a barrinha do topo da gaveta para baixo | Fecha a gaveta |
| Botão Voltar | Fecha gaveta/Config e volta ao início |
| Botão Início | Volta à tela inicial |
| Segurar o relógio | Abre a Config |
| Tocar no relógio / na data | Abre Relógio / Agenda |
| **Segurar** um app e arrastar | Veja [Arrastar e soltar](#arrastar-e-soltar) |
| Deslizar da borda configurada para dentro | Mostra a barra Voltar / Início / Recentes (some em ~3 s) |

### Arrastar e soltar

Segure um app por meio segundo e arraste:

- **Da gaveta para a dock ou para o DAEMONS:** fixa o app (a tela rola sozinha até o DAEMONS e a linha sob o dedo acende).
- **Dentro de uma lista:** reorganiza (os dois apps trocam de lugar).
- **Entre a dock e o DAEMONS:** copia o app para a outra lista.
- **Soltar fora de qualquer lista:** remove o app da lista de onde ele saiu (da gaveta, só cancela).

### Barra de navegação

Funciona com o serviço de acessibilidade ativo.

- **Posição:** inferior, esquerda ou direita. Nas laterais, ela fica a 45% da altura da tela (segundo terço, um pouco acima do meio).
- **Linha-guia:** fina linha na borda que indica onde deslizar (pode ser ocultada).
- **Afastamento da borda:** 0, 16, 32 ou 48 dp. Aumente se o deslize brigar com o gesto de voltar do sistema.
- **Botões:** ◁ Voltar, ○ Início, □ Recentes (acionam as ações do sistema).

---

## Config do launcher

As seções são retráteis: toque no título para abrir ou fechar.

| Seção | Opções |
|---|---|
| **Barra de navegação** | Posição, linha-guia, afastamento da borda |
| **Ícones dos apps** | Monograma ou ícone real |
| **Notificações** | Formato do painel (compacto, detalhado, oculto) e quantas mostrar (3, 5 ou 8) |
| **Paletas de cores** | 25 paletas de fábrica, criar, editar e apagar paletas próprias |
| **Filtros de tela** | Modo (desligado, monocromático, paleta, âmbar, escuro) e intensidade |
| **Energia** | Estado da permissão direta e botão que copia o comando do adb |
| **Previsão: coordenadas** | Latitude e longitude opcionais |
| **Permissões e serviços** | Atalhos para cada permissão e para o launcher padrão. Se o serviço dos botões parar, aparece **REATIVAR** |

### Criar uma paleta

1. Config > **Paletas de cores** > **+ CRIAR PALETA**.
2. Dê um nome e digite a cor de cada área em hexadecimal de 6 dígitos (ex.: `FCEE09`):

| Área | Para que serve |
|---|---|
| Destaque | Cor principal: textos, bordas, relógio |
| Acento | Cor secundária: previsão, rede, tags |
| Alerta | Bateria baixa, efeitos glitch, modo avião |
| Fundo / Fundo 2 | Degradê do plano de fundo |
| Painéis | Fundo dos painéis |
| Texto apagado | Textos de apoio |
| Desligado | Itens inativos (Bluetooth/GPS desligados) |

3. Confira a prévia e toque em **SALVAR**. A paleta é aplicada na hora.

A janela DAEMONS usa a cor de **acento** da paleta, para se diferenciar dos QUICKHACKS.

### Notificações

O visual das notificações do sistema (barra de cima e pop-ups) é controlado pelo próprio sistema e um launcher não consegue trocá-lo. O que o launcher personaliza é o **painel NOTIFICAÇÕES** da tela inicial:

- **Compacto:** uma linha por notificação (`APP // título`).
- **Detalhado:** app, horário, título e até duas linhas de texto.
- **Oculto:** só o nome do app (útil para privacidade).

### Energia

Tocar no **DIAGNÓSTICO** alterna a economia de bateria do Android, **se** você liberar uma permissão especial uma única vez, com o celular ligado ao computador por USB:

```bash
adb shell pm grant com.nightcity.launcher android.permission.WRITE_SECURE_SETTINGS
```

O botão **COPIAR ADB** em Config > Energia copia esse comando. Sem a permissão, o toque abre a tela de economia de bateria do sistema.

Modos de desempenho próprios do fabricante são controlados pelo sistema e um app comum não consegue trocá-los. Em alguns aparelhos o ajuste direto pode não ter efeito.

### Filtros de tela

Em Config > **Filtros de tela** escolha o modo e a intensidade (20, 40, 60 ou 80%). O efeito aparece na hora, por cima de todos os apps, e não atrapalha os toques.

| Modo | O que faz |
|---|---|
| **Desligado** | Sem filtro |
| **Monocrom.** | Filtro de foco: tira as cores para reduzir distrações |
| **Paleta** | Tinge a tela com a cor de destaque da paleta em uso |
| **Âmbar** | Tom quente para descansar os olhos (menos luz azul percebida) |
| **Escuro** | Escurece a tela |

Como funciona por trás:

- **Âmbar, Paleta e Escuro** são uma camada colorida translúcida desenhada pelo **serviço de acessibilidade**. Ele precisa estar ativo.
- **Monocrom.** tem dois comportamentos:
  - **Com a permissão por adb** (a mesma da economia de bateria): usa a correção de cor do sistema para um preto e branco **real**. A intensidade não se aplica e a configuração anterior de cor do sistema é restaurada ao sair do modo.
  - **Sem a permissão:** usa uma camada cinza que desbota as cores; a intensidade controla o quanto. Não é preto e branco de verdade e reduz o contraste.
- Se o serviço parar (por exemplo, ao limpar os apps recentes), a camada some até ele ser religado; o preto e branco real continua, pois quem o mantém é o sistema.

---

## Permissões e serviços

| Permissão / serviço | Para que serve | Obrigatória? |
|---|---|---|
| `INTERNET` | Previsão do tempo | Para a previsão |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Estado da rede | Sim (concedidas na instalação) |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Nome do Wi-Fi e previsão sem coordenadas | Opcional |
| `READ_PHONE_STATE` | Distinguir 4G e 5G | Opcional |
| **Acesso a notificações** | Cartão de mídia e painel NOTIFICAÇÕES | Opcional |
| **Serviço de acessibilidade** | Botões de navegação por cima de qualquer app | Opcional |
| `WRITE_SECURE_SETTINGS` (por adb) | Alternar economia de bateria direto e ligar o preto e branco real do filtro monocromático | Opcional |

O serviço de acessibilidade **não lê o conteúdo da tela** (`canRetrieveWindowContent` desligado). Ele só desenha a faixa de gesto e a barra e dispara as ações Voltar, Início e Recentes.

---

## Dicas de sistema

- **Configurações restritas:** em versões recentes do Android, o sistema pode bloquear o serviço de acessibilidade de apps instalados fora da loja. Em Configurações > Apps > Night City OS, abra o menu ⋮ e escolha **Permitir configurações restritas**; depois ative o serviço.
- **Bateria:** em alguns sistemas a economia de bateria encerra serviços em segundo plano. Nas configurações de bateria do app, escolha **Sem restrições** e permita o **início automático**, se existir. Sem isso, a leitura de mídia, as notificações e os botões podem parar.
- **Recentes:** trave o app na lista de recentes para o sistema não fechá-lo.
- **Limpar todos os recentes:** esse gesto pode encerrar o processo do launcher e parar o serviço dos botões. O launcher confere o serviço sempre que é aberto e tenta religá-lo sozinho se a permissão por adb (veja [Energia](#energia)) estiver concedida; sem ela, use **REATIVAR** na Config ou desligue e ligue o serviço nas configurações de acessibilidade.
- **Gestos de tela cheia:** a faixa de gesto nas laterais pode competir com o gesto de voltar do sistema. Use a barra inferior ou aumente o afastamento da borda.

---

## Privacidade

- Tudo fica no aparelho (configurações, dock, DAEMONS, recentes e paletas criadas).
- A **única conexão de rede** é a previsão do tempo para a Open-Meteo, que recebe as coordenadas usadas (manuais ou as da última localização).
- O conteúdo das notificações é lido só para exibir no painel; não é salvo nem enviado.
- O app não tem anúncios, análise de uso nem contas.

---

## Estrutura do projeto

```
NightCityLauncher/
├── .gitignore
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/nightcity/launcher/
        │   ├── MainActivity.kt   Activity de início, modo imersivo
        │   ├── NcApp.kt          Raiz do app: estados, camadas, arrastar e soltar
        │   ├── Home.kt           Tela inicial, cartão de mídia, relógio, dock
        │   ├── Drawer.kt         Gaveta de apps (busca, abas, índice A–Z)
        │   ├── Settings.kt       Tela de Config e criador de paletas
        │   ├── Drag.kt           Arrastar e soltar (dock e DAEMONS)
        │   ├── Ui.kt             Componentes: painéis, ícones de status, bateria
        │   ├── Palette.kt        Paletas e ajudantes de cor
        │   ├── Prefs.kt          Configurações guardadas no aparelho
        │   ├── Apps.kt           Lista de apps instalados e ícones
        │   ├── Device.kt         Leitura de bateria, rede, modos, Bluetooth, GPS
        │   ├── Media.kt          Sessão de mídia (controle de áudio)
        │   ├── Notifs.kt         Serviço e lista de notificações
        │   ├── Weather.kt        Previsão do tempo (Open-Meteo)
        │   ├── NavService.kt     Serviço de acessibilidade: barra de navegação e camada de filtro
        │   └── Filters.kt        Regras dos filtros de tela e preto e branco do sistema
        └── res/
            ├── values/ (strings.xml, themes.xml)
            ├── xml/accessibility_service_config.xml
            └── drawable/ic_launcher.xml
```

### Onde mexer para...

| Quero... | Arquivo |
|---|---|
| Adicionar uma paleta de fábrica | `Palette.kt` (uma linha `pal(...)` na lista `Palettes`) |
| Mudar o que aparece na tela inicial | `Home.kt` (`HomeScreen`) |
| Mudar a quantidade de recentes | `Home.kt` (`recents.take(...)`) e `NcApp.kt` (`.take(10)`) |
| Mudar o número de apps do DAEMONS | `Drag.kt` (`DAEMON_SLOTS`) |
| Ajustar a altura da barra lateral | `NavService.kt` (`sideTop`, fator `0.45f`) |
| Ajustar a força de cada filtro | `Filters.kt` (fatores `0.40f`, `0.45f`, `0.60f`, `0.85f`) |
| Ajustar a área de toque da gaveta (10% inferiores) e a sensibilidade | `Home.kt` (fração `0.90f` e `thresholdPx`) |
| Adicionar um novo ajuste | `Prefs.kt` + `NcApp.kt` + `Settings.kt` |
| Trocar o app aberto ao tocar em Maps/Agenda/Relógio | `NcApp.kt` (`openMaps`, `openCalendar`, `openClock`) |

### Dados guardados (`SharedPreferences` "nc")

`navPos`, `palKey`, `guide`, `iconMode`, `edgeInset`, `lat`, `lon`, `dock`, `recents`, `nc_daemons`, `notifStyle`, `notifMax`, `filterMode`, `filterLevel`, `customPalettes`, `monoActive`, `monoPrevEn`, `monoPrevType`.

---

## Limites conhecidos

- **Código não compilado no ambiente de criação.** Pode haver erros de sintaxe a corrigir.
- **Hotspot** depende de um aviso interno do sistema e pode não aparecer em todo aparelho.
- **5G não autônomo** aparece como 4G LTE.
- **Faixa de gesto nas laterais** pode competir com o gesto de voltar do sistema.
- **QUICKHACKS** mostra apenas apps abertos **pelo launcher**, não a lista real de recentes do sistema (apps de terceiros não têm acesso a ela).
- **Previsão** precisa de internet e de coordenadas manuais ou de uma localização conhecida.
- **Economia de energia direta** depende da permissão por adb e pode variar entre aparelhos.

---

## Problemas comuns

| Sintoma | O que fazer |
|---|---|
| Erro de versão ao sincronizar | Aceite a atualização sugerida pelo Studio ou ajuste as versões nos arquivos `.kts` |
| "SDK Platform 37 not found" | SDK Manager > instale a plataforma Android 37 |
| Botões de navegação não aparecem | Ative o serviço de acessibilidade em Config; veja as dicas de configurações restritas |
| Barra some sozinha depois de um tempo | Tire o app das restrições de bateria do sistema |
| Filtro de tela não aparece | Ative o serviço de acessibilidade (Âmbar, Paleta, Escuro e o monocromático aproximado dependem dele) |
| Faixa de gesto some depois de limpar os recentes | Abra o launcher: ele tenta religar o serviço sozinho (com a permissão por adb). Senão, Config > Permissões > **REATIVAR**, ou desligue e ligue o serviço de acessibilidade. Para investigar: `adb logcat -s NcNav` |
| Cartão de mídia pede acesso | Libere o Acesso a notificações (Config > Permissões) |
| Nome do Wi-Fi não aparece | Conceda a permissão de localização e deixe a localização do aparelho ligada |
| Previsão diz "DEFINA COORDENADAS EM CONFIG" | Digite latitude e longitude em Config ou libere a localização |
| Gaveta abre sem querer | Ela só abre puxando a partir dos 10% inferiores; ajuste `thresholdPx` se precisar |
| Voltar o launcher padrão do sistema | Configurações > Apps > Apps padrão > Tela inicial |

---

## O que ainda não existe

Previsto para versões futuras:

- Tela de bloqueio própria com leitor de digital e tela sempre ligada (AOD) com efeito glitch ao notificar.
- Painel de ferramentas ao deslizar de cima para baixo.
- Agendamento dos filtros (ligar e desligar em horários).
- Páginas na tela inicial, pastas, widgets, papel de parede.
- Busca universal (apps, contatos, web) e emblemas de notificação nos ícones.
- Menu de atalhos ao segurar um app (informações, desinstalar, ocultar).
