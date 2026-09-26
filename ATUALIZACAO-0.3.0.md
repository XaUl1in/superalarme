# Alterações da versão 0.3.0

## Rota de fuga por localização

Arquivos em `mobile/app/src/main/java/br/com/alarmehardcore/`:

- `EscapeRoute.kt` (novo): consulta pontual por `FusedLocationProviderClient`,
  sem aceitar posição antiga em cache, com cancelamento em até cinco segundos.
  Salva a decisão da sessão em SharedPreferences: `checking`, `home` ou `math`.
  A menos de 150 metros exige PIN; a partir de 150 metros, sem casa cadastrada
  ou sem posição disponível, exige contas. Uma resposta atrasada não troca o
  desafio escolhido. As perguntas e o progresso persistem ao recriar a tela.
- `SettingsActivity.kt`: botão **Salvar localização atual como Casa**, pedido
  conjunto de FINE/COARSE e gravação das coordenadas em SharedPreferences.
  A captura da casa exige autorização precisa; uma falha preserva a casa anterior.
- `AlarmService.kt`: inicia a consulta durante o disparo. Mantém som e sirene
  enquanto o usuário resolve o desafio. PIN somente no modo casa; as contas
  encerram o serviço apenas após cinco acertos consecutivos.
- `AlarmActivity.kt`: exibe a consulta, o campo de PIN ou a conta atual com
  progresso. Errar zera a sequência e gera outra conta.
- `AlarmRules.kt` (novo): regras de raio, progresso e validação do disparo.

Configuração: `AndroidManifest.xml` recebe `ACCESS_FINE_LOCATION` e
`ACCESS_COARSE_LOCATION`; `app/build.gradle.kts` inclui
`com.google.android.gms:play-services-location:21.3.0`; `gradle.properties`
habilita AndroidX exigido pelas dependências.

Não solicitamos acesso permanente em segundo plano. Se o Android impedir a
consulta com a tela bloqueada ou o Google Play Services estiver indisponível,
o app usa as contas, inclusive em casa. Essa limitação é prevista na
[documentação do FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient).

## Agendamento

- `AlarmScheduler.kt`: identificador exclusivo por reagendamento no endereço
  do PendingIntent; invalidação e cancelamento do anterior, inclusive o legado.
  O receptor verifica identificador, horário salvo e hora real antes de consumir
  ocorrências. O recebimento de um disparo antigo não pode antecipar o novo.
  Falhar ao agendar o próximo evento não impede iniciar o som do evento atual.
- `MainActivity.kt` e `AlarmEditorActivity.kt`: removido o retorno automático
  ao estado anterior após erro de agendamento, que podia reativar um alarme
  desativado. Falta de permissão agora mantém a edição aberta, em vez de salvar
  um novo alarme desligado implicitamente.
- `AlarmRulesTest.kt`: regressões para alarme antigo desativado e novo mais
  tarde, identificador vencido no mesmo horário, tentativa de disparo antecipado,
  raio de 150 metros, posição ausente e sequência de cinco respostas.

Esses defeitos foram identificados no código. Sem registros do aparelho, não
é possível atribuir com certeza a ocorrência relatada a um deles.

## Conferência no celular

Instale o APK por cima da versão anterior e abra o app uma vez. Confira os
horários salvos e cadastre a casa em Ajustes. Teste:

1. Em casa com localização precisa, confirmar que solicita o PIN do PC.
2. Fora de casa ou com localização indisponível, confirmar as cinco contas.
3. Errar após quatro acertos e conferir retorno a zero; reabrir a tela e
   conferir que a pergunta e o progresso são preservados.
4. Agendar A, desligá-lo e agendar B mais tarde. Bloquear a tela: A não deve
   tocar e B deve tocar. Repetir após fechar/reabrir o app e após reiniciar.

O resultado das verificações locais está em `VALIDACAO.md`.
