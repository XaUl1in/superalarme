# Conforto e gamificação — 0.4.0

## Fases

| Tempo desde o disparo | Áudio | Volume e brilho | Ofensiva |
| --- | --- | --- | --- |
| 0 a menos de 5 minutos | Ondas suaves sintetizadas, incluídas no APK | Aumento gradual até o máximo | PIN do PC concede um ponto |
| 5 a menos de 8 minutos | Clássico, ou o som escolhido no alarme | Brilho máximo; volume ainda ajustável | Zera ao entrar na fase |
| 8 minutos em diante | Sirene | Volume do alarme reposto no máximo; sobreposição autorizada | Continua zerada |

O brilho é o da janela do despertador, de 0 (mínimo permitido pelo aparelho)
a 1 (máximo). Não altera a configuração global nem exige WRITE_SETTINGS.
A sobreposição só é adicionada na fase 3; não bloqueia controles protegidos
do Android. O volume anterior é restaurado ao encerrar.

## Estrutura Kotlin

Arquivos em `mobile/app/src/main/java/br/com/alarmehardcore/`:

1. **AlarmPhases.kt**: política pura de tempo, ganho e brilho. Limites exatos em
   300.000 e 480.000 ms; também contém a regra de pontuação, isolada para testes.
2. **AlarmSession.kt**: início monotônico persistido, identificação do boot,
   data local do início, elegibilidade e maior fase atingida. No mesmo boot,
   mudanças no relógio civil não afetam o tempo decorrido. Após reboot, usa
   o relógio civil como recuperação e não volta abaixo da fase já registrada.
3. **AlarmService.kt**: mantém serviço e áudio ativos; usa `Handler.postDelayed`
   a cada 250 ms para consultar o tempo real e aplicar a fase. Não soma ticks:
   atrasos do sistema não acumulam atraso nas transições. O volume do sistema
   sobe por passos, e o ganho do player compensa esses passos para suavizar a rampa.
4. **EscalatingAudio.kt**: criação, acesso, callbacks e descarte do MediaPlayer
   ficam no mesmo HandlerThread, fora da interface. `prepareAsync()` prepara
   sem bloquear; `onPrepared` aplica o ganho atual e inicia. A fase só recria o
   player quando muda. Atualizações pendentes são agrupadas; callbacks antigos
   ou posteriores ao encerramento não voltam a tocar. Erros de áudio pessoal
   usam o toque incluído; uma preparação sem retorno é abandonada após cinco
   segundos, quando a thread consegue processar a próxima atualização.
5. **AlarmActivity.kt**: mostra fase e tempo restante; usa a mesma sessão para
   aplicar brilho. Reabrir ou girar a tela não reinicia os cinco minutos.
6. **HabitStore**, em `AlarmSession.kt`: SharedPreferences guarda contagem,
   melhor sequência, última data pontuada e última data de falha. Transições
   gravam somente quando necessário. A finalização grava resultado e encerra
   a sessão na mesma transação, evitando pontuação duplicada. A verificação
   do prazo também ocorre ao aceitar o PIN, mesmo se um tick atrasar.
7. **HabitActivity.kt / MainActivity.kt**: tela de 21 marcas, recorde e barra
   de progresso na página inicial. Manifest registra a nova tela como privada.

O ciclo de áudio é, resumidamente:

```text
Service consulta sessão → calcula fase/ganho → atualiza alvo do áudio
HandlerThread lê alvo → libera player antigo se mudou a fase
                    → configura fonte → prepareAsync()
onPrepared → verifica alvo/sessão ainda ativos → ajusta ganho → start()
onDestroy → impede novos callbacks → release() → quitSafely()
```

Essa separação segue a recomendação do Android de preparar mídia de forma
[assíncrona no serviço](https://developer.android.com/media/platform/mediaplayer/background)
e manter os acessos ao [MediaPlayer na mesma thread](https://developer.android.com/reference/android/media/MediaPlayer).
O relógio utilizado é [elapsedRealtime](https://developer.android.com/reference/android/os/SystemClock),
que inclui o tempo em repouso do aparelho.

## Regras da ofensiva

- Um ponto por dia local, usando a data do início do alarme, somente com PIN
  válido do PC antes de 300.000 ms. Exatamente cinco minutos já é fase 2.
- Alarmes adicionais no mesmo dia não somam pontos. Qualquer alarme real que
  atinja a fase 2 zera a sequência e impede recuperar ponto naquele dia.
- Um dia sem ponto quebra a sequência. O recorde permanece e a contagem pode
  continuar acima da meta de 21 dias.
- A rota de fuga GPS continua funcionando em todas as fases. Resolver as cinco
  contas não pontua; se chegar à fase 2 também zera a sequência.
- O teste de dez segundos percorre as mesmas fases, mas nunca altera a ofensiva.

Não há temporizadores de oito minutos bloqueando uma thread, preparação de
áudio no Service principal, nem gravação de preferências em todo frame.
Operações de persistência são pequenas e restritas ao início, transições e
encerramento. O desktop e o algoritmo do PIN permanecem compatíveis.

## Conferência física pendente

Instalar por cima da versão anterior e conferir em um alarme real: som baixo
no começo, rampa de brilho/volume, troca aos 5 minutos, sirene e overlay aos 8,
restauração ao encerrar, PIN correto antes e depois dos 5 minutos, repetição
no mesmo dia, rotação/reabertura e retomada após reiniciar. Testar também sem
permissão de sobreposição e com o fallback GPS. As regras e os limites de fase
têm testes automatizados; hardware, permissões e MediaPlayer exigem o aparelho.
