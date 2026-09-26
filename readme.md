# Especificações do Projeto: Alarme "Hardcore"

> A primeira versão do código está em `mobile/` e `desktop/`. Consulte
> [INSTRUCOES.md](INSTRUCOES.md) para configurar e usar, e
> [VALIDACAO.md](VALIDACAO.md) para ver o que foi testado. Escopo atualizado na
> versão 0.5.0: fuga manual, múltiplas zonas seguras, painel de testes, minijogos
> e sincronização local. A arquitetura está em [ATUALIZACAO-0.5.0.md](ATUALIZACAO-0.5.0.md). O prompt original no final
> é histórico; os limites reais das proteções do Android estão nas instruções.

## 1. Visão Geral
O objetivo deste projeto é desenvolver um sistema de alarme à prova de falhas humanas (e de sono profundo). O sistema é composto por um aplicativo mobile (Android) e um script desktop (Python). Para desligar o alarme, o usuário é forçado a levantar da cama, ligar o computador, resolver um desafio mental (puzzle) e inserir o código de desativação gerado no aplicativo.

## 2. Arquitetura do Sistema
O projeto será dividido em duas frentes independentes que se comunicam através de um algoritmo de geração de PINs baseado em tempo (Time-Based PIN), eliminando a necessidade de conexão via internet ou rede local para funcionar.

*   **Mobile (Client-side):** Aplicativo Android nativo construído com Kotlin.
*   **Desktop (Server/Logic-side):** Script executável em Python.

---

## 3. Requisitos e Restrições (Aplicativo Android)

### 3.1. Core do Alarme
*   Interface simples para definição de horário do alarme.
*   Tela de bloqueio ativa quando o alarme disparar, contendo apenas um campo de texto (Numpad) para inserção do código de desativação de 4 a 6 dígitos.

### 3.2. Escalada progressiva
* **Fase 1 — Zen (0 até antes de 5 minutos):** ondas do mar sintetizadas e incluídas
  no APK. Volume parte suave e cresce até 100%; brilho da janela sobe de 0 a 100%.
* **Fase 2 — Hardcore (5 até antes de 8 minutos):** toca o alarme padrão Clássico,
  ou o som que o usuário já escolheu para esse alarme. A ofensiva é zerada.
* **Fase 3 — Punição (8 minutos em diante):** sirene, reposição do volume do alarme
  no máximo e sobreposição, caso a permissão tenha sido concedida.
* O Service controla as fases usando tempo monotônico; o áudio usa um HandlerThread
  e `MediaPlayer.prepareAsync()`. Recriar a tela ou o serviço não reinicia as fases.
* Brilho é local à janela; ao encerrar, o volume anterior é restaurado.

### 3.3. Sistema Anti-Sabotagem
* **Bloqueio de Volume:** somente na Fase 3, o serviço repõe o canal de alarme
  no máximo a cada atualização. As fases iniciais permitem ajustes entre os
  passos de aumento gradual. O canal de mídia não é alterado.
* **Sobreposição:** `SYSTEM_ALERT_WINDOW` oferece um atalho para o desafio a partir
  da Fase 3. Não impede desligar/reiniciar nem cobre interfaces protegidas do sistema.
*   **Prevenção de Desinstalação (Anti-Uninstall):** Implementação da API de `Device Policy Manager` (Administrador do Dispositivo). O aplicativo exigirá privilégios de administrador na primeira inicialização. Uma vez concedidos, o aplicativo não poderá ser desinstalado convencionalmente sem antes navegar por configurações profundas do sistema para remover a permissão (o que é inviável fazer com o alarme tocando).

---

### 3.4. Ofensiva de 21 dias
* PIN do PC válido antes de 5 minutos: um ponto na data local do início do alarme.
* No máximo um ponto por dia. Dias sem pontuar quebram a sequência.
* Chegar a 5 minutos zera a sequência e impede pontuar novamente naquele dia.
* Recorde é preservado; a sequência pode continuar além da meta de 21 dias.
* SharedPreferences guarda pontuação, datas e sessão; a tela inicial mostra
  progresso e uma tela dedicada detalha os 21 dias e as regras.
* Testes de dez segundos não concedem pontos nem zeram a ofensiva.

### 3.5. Rota de fuga manual e zonas seguras
* O disparo sempre exige PIN do PC. Nenhuma consulta automática de localização.
* **Não estou em casa** solicita permissão, ativação via LocationSettingsRequest
  e uma posição pelo FusedLocationProviderClient com prazo de cinco segundos.
* A menos de 150 m de qualquer zona salva, mantém o PIN; fora das zonas ou sem
  posição, libera cinco contas consecutivas, sem ponto na ofensiva.
* Lista de zonas em SharedPreferences/JSON: nome, latitude, longitude e ID.
  Permite salvar, visualizar e excluir; migra a Casa da versão anterior.

### 3.6. Testes e sincronização local
* Cinco toques rápidos em HARDCORE habilitam painel com avanço de fase ativa
  e reset da ofensiva. Sessão avançada manualmente não altera pontuação.
* Android envia estatísticas por POST assíncrono ao IPv4 privado configurado,
  porta 8080. Mensagem autenticada com HMAC usando a chave já pareada.
* Tkinter mantém servidor HTTP em thread separada e aplica as mensagens na
  interface por fila e `after`. Falhas de rede não interferem no alarme/PIN.

## 4. Requisitos do Script (Desktop Python)

### 4.1. Lógica do Puzzle
Na GUI 0.5.0, há sorteio entre anagramas complexos, operações com precedência e
digitação exata em menos de quinze segundos. Só o sucesso libera o PIN. O modo
terminal continua compatível com seus desafios existentes.
*   Ao ser executado, o script via terminal gera um desafio mental aleatório para "acordar o cérebro".
*   *Tipos de Puzzles Sugeridos:*
    *   Matemática (ex: `(15 * 4) - 12`).
    *   Anagramas (ex: Desembaralhe a palavra `A R O D R O C A`).
    *   Sequência lógica.

### 4.2. Geração do PIN
*   O script só avança se a resposta do puzzle estiver correta (em caso de erro, gera um novo puzzle ou exige que tente novamente).
*   Após a resolução, o script gera o **Código de Desativação**.
*   *Sincronização:* O código gerado no Python e o código esperado no Android devem ser calculados com base na data e hora atual (ex: um hash simples do dia + hora), para que ambos saibam a senha do dia sem precisarem estar na mesma rede Wi-Fi.
