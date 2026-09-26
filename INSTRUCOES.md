# Alarme Hardcore — versão 0.5.0

O objetivo original está preservado em `readme.md`. O aplicativo agora tem uma
lista de alarmes salvos, repetição por dias da semana e som individual. O PC
tem uma janela gráfica com o mesmo tema escuro e roxo. O PIN funciona offline;
a sincronização opcional da ofensiva usa apenas a rede local.

## Atualizar a versão instalada

Instale `entrega/AlarmeHardcore-0.5.0.apk` por cima da versão anterior, usando a
opção de atualizar do Android. O pacote e a assinatura foram mantidos, então a
chave e as configurações existentes são preservadas. Um alarme pendente da
versão 0.1 é convertido em um item da nova lista. Se ele já tocou na versão
antiga, o aplicativo não tem como recuperar aquele horário apagado.

O novo ícone de lua e estrelas aparece no launcher do Android e na notificação
do despertador. No PC, abra `Alarme Hardcore.lnk` na pasta principal para ver
o mesmo ícone no atalho e na janela. `Configurar Alarme Hardcore.lnk` abre o
pareamento. O Windows não permite alterar o ícone interno de um arquivo `.bat`;
os atalhos executam os `.bat` correspondentes. Mantenha os atalhos junto com a
pasta do projeto. Se mover a pasta, recrie-os com `desktop/criar-atalhos.ps1`.

## 1. Preparar o computador

Requer Python 3.10 ou posterior com Tkinter. Os scripts Tcl/Tk 9.0.4 incluídos
em `desktop/vendor` resolvem os arquivos ausentes no Python 3.14 deste PC.

1. Abra `desktop/configurar.bat`. A janela permite exibir, copiar ou gerar a chave.
   A opção pelo terminal continua disponível: `python desktop/alarm.py --setup`.
2. O programa cria `desktop/config.json` com uma chave aleatória e mostra a chave
   para copiar para o celular. Não envie esse arquivo para repositórios.
3. Abra `desktop/abrir_alarme.bat`: o desafio aparece numa janela e, depois do
   acerto, o PIN aparece em destaque com o tempo restante. Cada novo PIN exige
   outro desafio. O modo terminal permanece em `python desktop/alarm.py`.

Não é preciso configurar uma chave real para executar os testes.
Gerar outra chave exige atualizar também o celular. O programa pede confirmação
antes de substituir uma configuração existente.

## 2. Preparar e compilar o Android

1. Instale o [Android Studio](https://developer.android.com/studio).
2. No SDK Manager, instale **Android SDK Platform 35** e **Build Tools 35.0.0**.
3. Abra a pasta `mobile` como projeto no Studio.
4. Em Settings → Build, Execution, Deployment → Build Tools → Gradle, selecione
   **JDK 17 ou 21** para o Gradle. O JDK 21 já está instalado neste computador;
   o Java 26 global e o Java 25 do Studio não servem para este projeto.
5. Aguarde a sincronização. Conecte um celular Android 8.0 ou posterior com
   depuração USB e use **Run**. Para gerar um APK, use Build → Generate App Bundles
   or APKs → Generate APKs. O APK de debug fica em
   `mobile/app/build/outputs/apk/debug/app-debug.apk`.

Versões fixadas: AGP 8.9.2, Kotlin 2.1.20, Gradle 8.11.1, SDK 35, mínimo SDK 26.
Não há conta nem serviço em nuvem. A permissão INTERNET é usada para enviar a
ofensiva ao PC na rede local, caso seu IP seja configurado. A primeira compilação
precisa de internet para baixar ferramentas e dependências.

Para recriar o APK sem abrir o Android Studio, execute no PowerShell
`powershell -ExecutionPolicy Bypass -File mobile/criar-apk.ps1` a partir da pasta
do projeto. O script usa o JDK 21 instalado, executa os testes Android e cria
um APK de debug em `mobile/app/build/outputs/apk/debug/app-debug.apk`.
Ele cria uma chave local em `mobile/.android/debug.keystore` na primeira vez.
Guarde essa chave se quiser instalar versões futuras por cima da primeira:
com uma chave diferente, o Android exigirá desinstalar a versão anterior.

## 3. Configurar o celular

1. Em **Ajustes**, digite a chave exibida no computador e toque em **Salvar chave**.
2. Permita notificações, alarmes exatos e tela cheia pelos botões de Ajustes.
3. Se desejar, autorize sobreposição e administrador do dispositivo. A ativação
   acontece na tela oficial do Android; essas duas proteções são opcionais.
4. O despertar começa suave: ondas do mar, volume e brilho crescentes por cinco
   minutos. Aos cinco minutos entra o som escolhido; aos oito, sirene, volume
   máximo e sobreposição, se autorizada. O antigo interruptor de volume máximo
   foi substituído por essa escalada.
5. Use **Testar em 10 segundos** em Ajustes. O teste preserva os alarmes salvos.
6. Em **Ajustes → Zonas seguras**, dê um nome e salve a posição atual. Você pode
   visualizar e excluir várias zonas; a Casa antiga é migrada automaticamente.
   O alarme sempre começa exigindo o PIN, sem consulta de localização. Se precisar,
   toque em **Não estou em casa**: o app pede permissão/ativação do GPS e consulta
   a posição por até cinco segundos. A menos de 150 m de qualquer zona, mantém
   o PIN. Fora de todas as zonas ou sem posição, libera cinco contas seguidas.
   Um erro zera o progresso; reabrir a tela não troca a conta. Cancelar a consulta
   ao destruir a tela mantém o PIN; toque novamente para solicitar outra.
7. Na tela inicial, toque em **Novo alarme**. Toque no horário para abrir o seletor,
   escolha o nome, os dias e o som, depois toque em **Salvar alarme**. Rolar a tela
   não muda o horário. Voltar sem salvar descarta as mudanças.
8. Sem dias selecionados, ele toca uma vez e permanece salvo desligado. Com dias
   selecionados, repete automaticamente. O botão de cada cartão ativa ou desativa;
   tocar no horário abre a edição, onde também é possível excluir.
9. Em **Som do alarme**, escolha Clássico, Aurora, Órbita, um toque do celular ou
   um arquivo de áudio. **Ouvir por 5 segundos** permite escutar antes de salvar.
   Não mova nem apague um arquivo pessoal escolhido: se ele ficar indisponível,
   o aplicativo tocará Clássico como alternativa.

Se faltar a chave ou alguma permissão necessária para ativar um alarme, a tela
informa o ajuste pendente e mantém a edição aberta, sem salvar desligado implicitamente.

A consulta manual utiliza Google Play Services. Negar a permissão ou recusar
ligar o GPS também produz ausência de posição e libera as contas, conforme a
regra solicitada. Assim, essa regra é uma barreira comportamental, não uma prova
de que o celular está fora das zonas. Não há rastreamento em segundo plano.

## Rede local e ferramentas de teste

- Abra o programa do PC e informe o IPv4 exibido em **Ajustes → Sincronizar com
  o PC** no Android. Ambos precisam estar na mesma rede e usar a mesma chave de
  pareamento. Se o Windows pedir acesso para Python, permita na rede privada;
  a porta TCP 8080 deve estar acessível na LAN. Não é necessário abrir porta no roteador.
- O Android envia ofensiva/recorde ao mudar a pontuação, encerrar o alarme,
  abrir a tela inicial ou tocar em **Salvar IP e sincronizar**. Erros de rede
  não impedem desligar o alarme. Se o PC estava fechado, abra-o e sincronize
  novamente; não há serviço de nuvem nem fila permanente de reenvio.
- O PC guarda a última atualização em `desktop/streak.json`. O valor mostrado
  é o último recebido; para atualizar dias sem pontuar, abra o app e sincronize.
  Esvaziar o IP desativa os próximos envios. O PIN não é transmitido.
- O programa gráfico sorteia anagrama complexo, conta com precedência ou
  digitação exata em menos de 15 segundos. No teste de digitação, o tempo começa
  quando o desafio aparece e colar é bloqueado. Ao expirar, inicie novo desafio.
- Toque cinco vezes rapidamente no título **HARDCORE** da tela inicial para
  habilitar o painel de desenvolvedor. Na tela do alarme aparecerá **Desenvolvedor · testes**,
  com avanço de fase e reset da ofensiva. Avançar transforma a sessão em teste
  sem pontuação; reset preserva o recorde. O painel é oculto, não protegido por senha.

## Como funciona

- `AlarmManager.setAlarmClock` agenda o próximo evento. Os demais ficam salvos;
  após um disparo, o próximo é rearmado automaticamente. Alarmes coincidentes
  compartilham o mesmo toque ativo; suas repetições são atualizadas individualmente.
- Cada reagendamento tem um identificador exclusivo. Disparos antigos ou antes
  do horário são rejeitados; desativar não volta a ativar um item se o Android
  rejeitar o reagendamento. O agendamento da versão antiga é cancelado na migração.
- Um serviço em primeiro plano mantém áudio e CPU ativos, publica uma notificação
  com intenção de tela cheia e atualiza as fases a cada 250 ms, sem esperas bloqueantes.
- Aos 300 segundos, ondas suaves dão lugar ao som escolhido. Aos 480 segundos,
  começa a sirene com reposição de volume máximo. Os WAVs incluídos são originais,
  acompanham o projeto e podem ser recriados com `python tools/generate_sounds.py`.
- O áudio é preparado em uma thread própria com `prepareAsync()`. A tela altera
  apenas seu brilho local e retoma o brilho normal ao sair. A contagem usa tempo
  monotônico, persistido por sessão, e não reinicia ao reabrir o app.
- A tela **Ofensiva** mostra a meta de 21 dias e o recorde. PIN do PC antes de cinco
  minutos concede no máximo um ponto por dia. A fase 2 zera a sequência e impede
  pontuar novamente naquele dia. Um dia sem ponto interrompe a sequência. A data
  considerada é a do início do alarme. A rota de fuga não pontua; testes não alteram
  a ofensiva. A contagem pode continuar depois de 21 dias.
- O PIN usa TOTP/HMAC-SHA1, contador Unix de 60 segundos e seis dígitos. Uma chave
  hexadecimal de 32 bytes é compartilhada manualmente. O fuso horário não importa,
  mas os relógios precisam estar corretos. O celular aceita a janela atual e as
  duas adjacentes (±60 segundos). Existe espera de cinco segundos após erro.
- O PIN é calculado somente após acertar o puzzle no fluxo normal do desktop.
  Isso é uma barreira comportamental: quem alterar o código ou ler a chave pode
  contornar o desafio. Não é um mecanismo contra um invasor do dispositivo.
- A chave fica nas preferências privadas do Android, sem backup, e em um arquivo
  local no PC. Esta primeira versão não usa cofre de credenciais nem Keystore.
- A lista de alarmes é persistida. Após reiniciar e desbloquear o celular, o app
  rearma os eventos salvos. Um evento atrasado toca logo após a recuperação;
  um alarme que já tocava é retomado em cinco segundos. Mudanças de fuso ou hora
  recalculam o próximo disparo pelo horário local. Não há suporte a disparo antes
  do primeiro desbloqueio.
- Ao inserir o PIN correto, o som, a sobreposição e o serviço terminam, e os
  volumes anteriores são restaurados.

## Limites das proteções

Um app comum não torna o aparelho impossível de desligar. A sobreposição fica
abaixo de janelas críticas do sistema e oferece um atalho para inserir o PIN;
ela não bloqueia o menu de energia. A tela imersiva também pode ser abandonada
pelos gestos do sistema. A administração exige revogar a autorização antes da
desinstalação, mas o usuário ainda pode fazer isso nas configurações.

O Android decide se exibe a tela cheia ou uma notificação, conforme permissões,
estado da tela e fabricante. Não perturbe, canais de notificação bloqueados,
restrições de bateria e roteamento Bluetooth podem afetar o resultado. Não há
garantia de funcionamento com o aparelho desligado, depois de forçar parada,
ou depois de revogar alarmes exatos. Reabra e reagende após revogar permissões.
Se o processo for encerrado abruptamente, a restauração de volume pode esperar
a próxima execução. É necessário testar no aparelho que será utilizado.

Para remover o app, sem alarme tocando, use **Remover administrador** e depois
desinstale normalmente. Se perder a chave enquanto toca, os controles oficiais
do Android continuam disponíveis para forçar parada e remover a administração.

## Verificação

Testes Python: `python -m unittest discover -s desktop -v`.

No Android Studio, rode `TimePinTest` e `AlarmPlanTest`, ou com JDK e SDK configurados:
`cd mobile` e `./gradlew testDebugUnitTest assembleDebug`.

Os testes usam vetores publicados no RFC 6238 e verificam a janela de validade,
formato da chave, liberação do puzzle, repetição semanal e manutenção da lista.
A validação física deve incluir:

- Teste de dez segundos com tela bloqueada e com o app em segundo plano.
- Observar o volume e brilho crescentes; aos cinco minutos, som escolhido e
  ofensiva zerada; aos oito, sirene, reposição do volume e sobreposição autorizada.
- Diminuir o volume na fase 3 e verificar a reposição.
- Inserir PIN incorreto, correto e expirado; conferir relógios dos dispositivos.
- Verificar restauração do volume após PIN correto.
- Reiniciar com um alarme futuro agendado, desbloquear e verificar o disparo.
- Cancelar um agendamento e confirmar que não dispara.
- Criar dois alarmes, fechar e reabrir o app; confirmar que ambos permanecem.
- Editar e voltar sem salvar; conferir que o horário original foi preservado.
- Escolher um som pessoal, ouvir a prévia e testar o disparo.

## Referências de implementação

- [Alarmes exatos](https://developer.android.com/develop/background-work/services/alarms/schedule)
- [Serviços e tipos de primeiro plano](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Limites de sobreposições](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Administrador do dispositivo](https://developer.android.com/work/device-admin)
- [TOTP — RFC 6238](https://www.rfc-editor.org/rfc/rfc6238)

O status dos testes executados nesta máquina está em `VALIDACAO.md`.
