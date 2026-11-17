# Night City OS — launcher (protótipo 0.1)

Launcher Android em Kotlin + Jetpack Compose, baseado no protótipo HTML.

## Abrir no Android Studio (Fedora)
1. Extraia esta pasta em `/mnt/arquivos/Dev/Android/projetos/NightCityLauncher`.
2. Android Studio > File > Open > escolha a pasta.
3. Aceite instalar o que o Studio pedir (SDK Platform 37, Build Tools 36, Gradle 9.6.0, JDK 17+).
4. Aguarde o Gradle Sync. Ligue a depuração USB no celular e clique em Run.
5. Aperte Início no celular e escolha **Night City OS** > **Sempre**
   (ou Config > "Definir como launcher padrão").

## No aparelho (Config: segure o relógio, ou app "Config" na gaveta)
- **Acesso a notificações**: libera o controle de mídia no cartão amarelo.
- **Serviço de acessibilidade**: libera os botões de navegação por cima de qualquer app.
  No HyperOS/Android 13+ pode aparecer "configuração restrita": em Configurações > Apps >
  Night City OS > menu ⋮ > "Permitir configurações restritas", e então ative o serviço.
- **Localização**: nome do Wi-Fi (e previsão, se não digitar coordenadas).
- **Estado do telefone**: 4G/5G.
- No HyperOS: Segurança > Economia de bateria > Night City OS > "Sem restrições" e
  ative "Início automático", senão o sistema pode matar os serviços em segundo plano.

## O que funciona
Relógio com glitch, ícones de status (avião, silencioso, não perturbe, hotspot, alarme),
bateria com % e animações (carregando / economia / baixa), rede (Wi-Fi com nome, 4G/5G),
Bluetooth e GPS só mudando de cor, controle de mídia, previsão (Open-Meteo),
QUICKHACKS = apps abertos pelo launcher, diagnóstico (RAM, armazenamento, temperatura),
gaveta semitransparente com busca/abas/índice A-Z, dock editável por arrastar e soltar
(segure um app e solte na dock; segure um da dock e solte fora para remover),
barra de navegação oculta (inferior/esquerda/direita), 9 paletas, ícones reais ou monograma.

## Limites conhecidos
- Código escrito sem compilar neste ambiente: se o Studio mostrar erros de compilação, copie a mensagem.
- Hotspot depende de um broadcast do sistema; pode não funcionar em todo aparelho.
- 5G não autônomo (NSA) aparece como 4G LTE.
- Faixa de gesto nas laterais pode brigar com o gesto de voltar do sistema: aumente o
  "Afastamento da borda" em Config ou use a barra inferior.
- Fora desta versão: tela de bloqueio, AOD, painel de ferramentas de cima para baixo.
