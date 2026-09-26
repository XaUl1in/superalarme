# Atualização 0.5.0

Os blocos de código alterados estão em **BLOCOS-0.5.0.md**. O restante das fases,
agendamento, TOTP, áudio e histórico da ofensiva foi preservado.

## Android

- **EscapeRoute.kt / AlarmService.kt / AlarmActivity.kt**: removida a consulta
  automática. Cada novo disparo começa no modo PIN. O botão **Não estou em casa**
  chama a consulta manual; o PIN continua disponível enquanto ela acontece.
- **ManualLocation.kt**: pede permissões, verifica `LocationSettingsRequest` e
  abre a resolução oficial para ligar o GPS. Depois consulta uma posição nova,
  sem cache, por até cinco segundos. A verificação das configurações também tem
  prazo de cinco segundos, excluindo o tempo em que o usuário responde ao diálogo.
  Usa [SettingsClient](https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient).
- **SafeZones.kt / SettingsActivity.kt**: lista JSON com ID, nome, latitude e
  longitude. Exibe coordenadas, salva a posição atual e exclui itens. A Casa
  anterior é migrada uma única vez; excluir todas as zonas não a recria.
- Uma distância **menor que 150 m de qualquer zona** mantém o PIN e mostra
  “Você está em uma zona segura, vá para o PC!”. Distâncias a partir de 150 m,
  ausência de zonas ou ausência de posição liberam as contas após o clique.
  Permissão negada e GPS recusado contam como ausência de posição, conforme o
  fallback solicitado. Isso não impede fraude deliberada desligando o GPS.
- Rotação/destruição da tela cancela a consulta sem liberar a fuga. Ao voltar,
  solicite novamente. Resultados atrasados de consultas anteriores são ignorados.
- **DeveloperPanel.kt / MainActivity.kt / AlarmSession.kt**: cinco toques no
  título HARDCORE, com até 700 ms entre eles, habilitam o painel. Na tela do alarme
  há um botão para acessar os controles. O avanço salta à próxima fase e mantém
  o relógio avançando a partir dela; a sessão passa a ser de teste e não pontua.
  Reset zera a ofensiva e as datas de pontuação/falha, preservando o recorde.

## Sincronização opcional

- **StreakSync.kt** executa HTTP fora da interface, com limites de conexão/leitura
  de dois segundos, sem seguir redirecionamentos. Aceita somente IPv4 literal
  privado (10/8, 172.16/12, 192.168/16) configurado pelo usuário.
- Endpoint: `POST http://IP_DO_PC:8080/streak`. JSON com `streak`, `best`,
  `revision` e `sentAt`. A assinatura `X-Alarm-Signature` é HMAC-SHA256 sobre
  `streak-v1\n` seguido dos bytes UTF-8 do corpo, usando a chave já pareada.
  Nenhum PIN, coordenada ou chave é enviado. HTTP não cifra as estatísticas;
  a assinatura autentica a origem e protege a integridade.
- Manifest inclui INTERNET e permite HTTP; o código restringe o destino à LAN.
  Envios ocorrem nas alterações de pontuação, encerramento, abertura da tela
  inicial e ação manual nos Ajustes. A falha não interfere no alarme/PIN.
- **desktop/streak_server.py** usa ThreadingHTTPServer na porta 8080. Valida
  assinatura, formato, limite de corpo de 4096 bytes, horário (±120 s) e revisão
  crescente. Guarda a última mensagem assinada em `streak.json`. Uma troca de
  chave inicia uma nova sequência de revisões.
- **desktop_gui.py** inicia o servidor em thread separada. Uma fila entrega as
  estatísticas ao Tkinter, que as aplica exclusivamente por `root.after` na
  thread da interface. Porta ocupada não impede os desafios nem o PIN.
- Não há reenvio persistente nem descoberta automática do PC no Android.
  Configure o IPv4 mostrado na janela do PC, na mesma rede. Se o PC estiver
  fechado, sincronize novamente depois de abri-lo. O painel mostra a última
  atualização recebida, não uma contagem independente.

## Minijogos do PC

**desktop/minigames.py** sorteia anagramas longos, contas com precedência e
digitação. A conta é calculada diretamente, sem `eval`. Na digitação, a frase
deve coincidir exatamente, incluindo espaços, acentos, maiúsculas e pontuação;
o tempo monotônico deve ser menor que 15 segundos. O tempo começa ao mostrar
o desafio, os atalhos comuns de colar são bloqueados e a expiração exige novo
desafio. Somente `submit()` após validação revela o PIN TOTP existente.

## Uso e verificação

1. Instale `entrega/AlarmeHardcore-0.5.0.apk` por cima da versão anterior.
2. Confira as zonas em Ajustes; a Casa anterior deve aparecer na lista.
3. Abra o programa do PC. Em Ajustes no celular, salve o IPv4 mostrado no PC.
   Se necessário, permita Python na rede privada pelo firewall do Windows.
   Não configure encaminhamento de porta no roteador.
4. Teste fuga dentro da segunda zona da lista, fora de todas e com GPS recusado.
5. Teste o painel oculto, os três minijogos e a atualização da ofensiva no PC.

Resultados locais estão em `VALIDACAO.md`. GPS real, diálogos/permissões,
migração de preferências no aparelho e HTTP entre celular/PC exigem validação
física; os testes locais cobrem regras e servidor/Tkinter no computador.
