# Validação da versão 0.5.0

Data: 26/09/2026.

- `testDebugUnitTest assembleDebug lintDebug`: concluído com sucesso.
- **28 testes Kotlin aprovados, zero falhas**. Incluem a segunda zona segura
  dentro do raio, limite de 150 m, validação de IP privado literal e vetor de
  assinatura HMAC-SHA256 compatível com o receptor Python.
- **20 testes Python aprovados**, incluindo widgets reais Tkinter (janelas de
  teste ocultas), os três tipos de jogo, digitação expirada, PIN apenas após
  sucesso, consumo da fila de estatísticas na UI e servidor HTTP real em
  localhost/porta efêmera. Testados assinatura inválida, mensagem vencida,
  repetição, formato inválido, persistência e troca da chave de pareamento.
- Lint: **zero erros, 51 avisos**, sem ocultar verificações.
- APK: `entrega/AlarmeHardcore-0.5.0.apk`, 3.442.018 bytes.
- SHA-256: `D1C2A0C5C1AC59B03C7E4CDB43348E7CD967A71DEFB318884BA0830B25B8D091`.
- Assinatura verificada com apksigner; mesmo certificado anterior:
  `942b60c31e864107f1670a2305f1a9bf59358a6862677d49dbdc4d447008fae8`.
- Pacote `br.com.alarmehardcore`, versão `0.5.0`, código 6, mínimo Android 26,
  alvo 35. A compilação inclui INTERNET e permissão de HTTP para o POST local.

Não houve instalação ou acionamento de alarme em celular nesta sessão. Não
foram exercitados no Android real os diálogos de GPS/permissões, a migração
de SharedPreferences, as telas de zonas/painel oculto ou o transporte HTTP
entre celular e PC. O servidor foi testado somente em loopback, sem abrir uma
regra de firewall. Essas verificações físicas estão em `ATUALIZACAO-0.5.0.md`.

---

## Histórico: validação da versão 0.4.0

Data: 26/09/2026.

- `testDebugUnitTest assembleDebug lintDebug`: concluído com sucesso.
- **25 testes Kotlin aprovados, zero falhas**. Onze novos testes cobrem limites
  de fase em milissegundos, rampa de ganho/brilho, relógio civil alterado,
  recuperação após reboot, prazo para o PIN, pontos diários, dias perdidos,
  falha e recorde, exclusão dos testes e do fallback, e conclusão da meta.
- Lint: **zero erros, 48 avisos**. Persistem sugestões sobre traduções,
  preferências síncronas, APIs/recursos antigos e manutenção de dependências.
- APK: `entrega/AlarmeHardcore-0.4.0.apk`, 3.344.206 bytes.
- SHA-256: `CC69FB9533C60E2F8DB5C6C65A587F201CB638C3FE0CC26F5A90626ECF5B6F52`.
- Assinatura verificada com apksigner; mesmo certificado das versões anteriores:
  `942b60c31e864107f1670a2305f1a9bf59358a6862677d49dbdc4d447008fae8`.
- Pacote `br.com.alarmehardcore`, versão `0.4.0`, código 5, mínimo Android 26,
  alvo 35. Manifest e recursos compilados, incluindo a tela de ofensiva.
- `zen.wav`: ondas sintetizadas, PCM mono de 12 segundos a 22.050 Hz, sem
  clipping numérico. O arquivo foi inspecionado programaticamente, não ouvido.

Não houve teste em aparelho ou emulador. Os testes automatizados exercitam
as regras puras de tempo e pontuação, não os callbacks reais do MediaPlayer,
SharedPreferences, áudio/brilho físicos ou sobreposição. Esses pontos, além
de rotação/reabertura e retomada do serviço, estão no roteiro de
`ATUALIZACAO-0.4.0.md`. O desktop não foi alterado nem retestado nesta versão.

---

## Histórico: validação da versão 0.3.0

Data: 26/09/2026.

- `testDebugUnitTest assembleDebug lintDebug`: concluído com sucesso.
- **14 testes Kotlin aprovados, zero falhas**: sete de agendamento, cinco de
  regressão/regras da rota de fuga e dois de PIN. Os testes de localização
  verificam a decisão pelo raio e a ausência de posição; não simulam o GPS real.
- Lint: **zero erros, 48 avisos**, incluindo traduções, gravações síncronas de
  preferências, APIs antigas, recursos visuais e sugestão de atualizar dependência.
- APK: `entrega/AlarmeHardcore-0.3.0.apk`, 2.865.376 bytes.
- SHA-256: `DD4AA029A465804DA355AA9A7CDF7CC7253CEB17265D6FC3673815544252868E`.
- Assinatura verificada com apksigner; mesmo certificado da versão 0.2.1:
  `942b60c31e864107f1670a2305f1a9bf59358a6862677d49dbdc4d447008fae8`.
- Pacote `br.com.alarmehardcore`, versão `0.3.0`, código 4, mínimo Android 26,
  alvo 35. O manifest compilado contém FINE e COARSE LOCATION.

Não houve instalação ou testes em aparelho/emulador nesta sessão. A consulta
real, o prazo sob condições do aparelho, as permissões, a tela bloqueada, a
persistência após encerramento do processo e o ciclo desativar A/criar B ainda
precisam da conferência física descrita em `ATUALIZACAO-0.3.0.md`.
O acesso local ao ADB falhou ao criar seu diretório de configuração; não foi
possível consultar dispositivos por ele. Nenhum alarme do celular foi acionado.

Não foi possível confirmar a causa exata do incidente relatado sem registros
do aparelho. Foram corrigidas fragilidades identificadas no agendamento e no
retorno ao estado anterior após erro. O desktop não foi alterado nem retestado.

---

## Histórico: validação da versão 0.2.1

Data: 23/09/2026.

O APK de debug foi gerado e copiado para
`entrega/AlarmeHardcore-0.2.1.apk` (1.240.476 bytes).
SHA-256: `C6861E969F939BD7FBE05FCEE546E8D704473A9B4A15FA1132CE5FEE2A1051E8`.

- Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, JDK 21.0.12, Android SDK 35 e
  Build Tools 35.0.0: `testDebugUnitTest assembleDebug` **concluído com sucesso**.
- Testes Kotlin: **9 aprovados, 0 falhas**, incluindo PIN, repetição semanal,
  mudança de horário de verão, preservação de alarmes após disparar e teste
  rápido sem apagar os outros alarmes.
- Testes Python: **11 aprovados**, incluindo o fluxo de puzzle, vetores TOTP e
  interação com a nova janela Tkinter (resposta incorreta, PIN após acerto,
  expiração e leitura de chave existente em configuração temporária).
- `apksigner verify`: APK com assinatura v2 válida e um signatário.
- `aapt dump badging`: pacote `br.com.alarmehardcore`, `minSdkVersion=26`,
  `targetSdkVersion=35`, versão `0.2.1`, código de versão 3;
  ícone do launcher aponta para `res/mipmap-anydpi-v26/ic_launcher.xml`.
- O atalho `Alarme Hardcore.lnk` aponta para `desktop/abrir_alarme.bat` e
  usa `desktop/assets/hardcore.ico`. O segundo atalho abre a configuração.
- Manifest e recursos XML compilados. Os quatro WAVs são PCM mono de
  dois segundos a 22.050 Hz.

O APK foi assinado com uma chave de debug local em
`mobile/.android/debug.keystore`. Não publique essa chave nem a use para uma
versão de produção. Para instalar uma atualização por cima da versão de debug,
é preciso usar a mesma chave. Para recriar o APK, use `mobile/criar-apk.ps1`.

Ainda falta verificar em um celular a instalação por cima da versão anterior,
a aparência em diferentes tamanhos de tela, edição sem salvar, persistência
após reabrir, seleção de áudio pessoal, alarme com tela bloqueada, sirene após
três minutos, controle de volume e recuperação após reinicialização.
A migração do alarme antigo e a persistência Android foram implementadas, mas
não exercitadas em aparelho ou emulador nesta sessão. Nenhum alarme real foi
ativado no celular durante este trabalho.
