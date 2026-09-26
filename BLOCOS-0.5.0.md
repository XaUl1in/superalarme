# Blocos atualizados — versão 0.5.0

Trechos extraídos dos arquivos implementados. Os novos arquivos de apoio aparecem completos; os arquivos existentes mostram apenas as integrações relevantes. Veja ATUALIZACAO-0.5.0.md para comportamento, montagem e configuração.

## EscapeRoute.kt: modo inicial e decisão manual

```kotlin
object EscapeRoute {
    const val HOME = "home"
    const val MATH = "math"
    private fun prefs(context: Context) = AlarmState(context).prefs
    fun begin(context: Context) {
        prefs(context).edit().putString("escapeMode", HOME).putInt("mathStreak", 0)
            .remove("mathQuestion").remove("locationDeadline").commit()
    }
    fun mode(context: Context) = if (prefs(context).getString("escapeMode", HOME) == MATH) MATH else HOME
    fun resolve(context: Context, session: Long, location: Location?): String {
        val state = AlarmState(context)
        if (session == 0L || state.activeSince != session || mode(context) == MATH) return "ignored"
        val safe = location != null && SafeZones(context).contains(location)
        if (safe) return HOME
        prefs(context).edit().putString("escapeMode", MATH).commit()
        question(context)
        return MATH
    }
    // question() e answer() mantêm o desafio matemático existente.
}
```

## ManualLocation.kt: permissões, GPS e captura sob demanda

```kotlin
package br.com.alarmehardcore

import android.Manifest
import android.app.Activity
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/** Starts only from a user action. Destruction cancels; no background or automatic restart. */
class ManualLocation(private val activity: Activity, private val result: (Location?) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var request: QuickLocation? = null
    var busy = false; private set
    private var closed = false
    private var generation = 0
    private val timeout = Runnable { complete(null) }
    fun start() {
        if (busy || closed) return
        busy = true
        generation++
        if (!QuickLocation.hasPermission(activity)) activity.requestPermissions(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION)
        else settings()
    }
    fun permissionResult(code: Int) {
        if (code != PERMISSION || !busy || closed) return
        if (QuickLocation.hasPermission(activity)) settings() else complete(null)
    }
    private fun settings() {
        val attempt = generation
        val settings = LocationSettingsRequest.Builder().addLocationRequest(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()).setAlwaysShow(true).build()
        handler.postDelayed(timeout, 5000)
        try {
            LocationServices.getSettingsClient(activity).checkLocationSettings(settings)
                .addOnSuccessListener { if (busy && !closed && attempt == generation) { handler.removeCallbacks(timeout); locate() } }
                .addOnFailureListener { error ->
                    if (!busy || closed || attempt != generation) return@addOnFailureListener
                    handler.removeCallbacks(timeout)
                    if (error is ResolvableApiException) {
                        try { error.startResolutionForResult(activity, RESOLUTION) }
                        catch (_: Exception) { complete(null) }
                    } else complete(null)
                }
        } catch (_: Exception) { complete(null) }
    }
    fun resolutionResult(code: Int, resultCode: Int) {
        if (code != RESOLUTION || !busy || closed) return
        if (resultCode == Activity.RESULT_OK) locate() else complete(null)
    }
    private fun locate() { request = QuickLocation(activity, 5000, ::complete) }
    private fun complete(location: Location?) {
        if (!busy || closed) return
        busy = false; handler.removeCallbacks(timeout); request?.cancel(); request = null
        result(location)
    }
    fun close() { closed = true; busy = false; handler.removeCallbacksAndMessages(null); request?.cancel() }
    companion object { const val PERMISSION = 40; const val RESOLUTION = 41 }
}
```

## SafeZones.kt: lista e migração da Casa

```kotlin
package br.com.alarmehardcore

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SafeZone(val id: String, val name: String, val latitude: Double, val longitude: Double)

class SafeZones(context: Context) {
    private val prefs = AlarmState(context).prefs
    init {
        if (!prefs.contains("safeZones")) {
            val migrated = if (prefs.contains("homeLatitude") && prefs.contains("homeLongitude"))
                listOf(SafeZone(UUID.randomUUID().toString(), "Casa", Double.fromBits(prefs.getLong("homeLatitude", 0)),
                    Double.fromBits(prefs.getLong("homeLongitude", 0)))) else emptyList()
            write(migrated)
        }
    }
    fun all(): List<SafeZone> {
        val array = JSONArray(prefs.getString("safeZones", "[]"))
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            SafeZone(it.getString("id"), it.getString("name"), it.getDouble("latitude"), it.getDouble("longitude"))
        } }
    }
    private fun write(zones: List<SafeZone>) {
        val array = JSONArray()
        zones.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)
            .put("latitude", it.latitude).put("longitude", it.longitude)) }
        check(prefs.edit().putString("safeZones", array.toString()).commit())
    }
    fun add(name: String, location: Location) {
        require(name.isNotBlank())
        write(all() + SafeZone(UUID.randomUUID().toString(), name.trim().take(60), location.latitude, location.longitude))
    }
    fun remove(id: String) = write(all().filterNot { it.id == id })
    fun contains(location: Location): Boolean = AlarmRules.inAnySafeZone(all().map { zone ->
        val distance = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, zone.latitude, zone.longitude, distance)
        distance[0]
    })
}
```

## SettingsActivity.kt: interface de zonas e IP do PC (dentro de onCreate)

```kotlin
        page.addView(NightUi.text(this, "Zonas seguras", 22f))
        page.addView(NightUi.text(this, "O alarme sempre pede o PIN. Ao tocar em Não estou em casa, o GPS verifica todas as zonas salvas: a menos de 150 m de qualquer uma, o PC continua obrigatório.", 14f, NightUi.muted))
        val zoneName = NightUi.input(this, "Nome: Casa, Trabalho…").apply { setSingleLine() }
        page.addView(zoneName)
        homeStatus = NightUi.text(this, "", 14f, NightUi.muted); page.addView(homeStatus)
        homeRequest = ManualLocation(this) { location ->
            if (location == null) homeStatus.text = "Sem posição disponível. Nenhuma zona foi alterada."
            else runCatching { SafeZones(this).add(pendingName, location) }.onSuccess {
                homeStatus.text = "Zona salva."; zoneName.text.clear(); renderZones()
            }.onFailure { homeStatus.text = "Não foi possível salvar a zona." }
        }
        page.addView(NightUi.button(this, "Salvar localização atual", true) {
            if (!homeRequest.busy) {
                pendingName = zoneName.text.toString().trim()
                if (pendingName.isEmpty()) toast("Dê um nome à zona.")
                else { homeStatus.text = "Obtendo localização…"; homeRequest.start() }
            }
        })
        zoneList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(zoneList); renderZones()
        page.addView(NightUi.text(this, "Sincronizar com o PC", 22f))
        val pc = NightUi.input(this, "IP local do PC, ex.: 192.168.1.20").apply {
            setSingleLine(); setText(state.prefs.getString("pcAddress", ""))
        }
        page.addView(pc)
        page.addView(NightUi.text(this, "Abra o programa no PC. A ofensiva é enviada à porta 8080 usando a chave já pareada. O PIN continua offline.", 13f, NightUi.muted))
        page.addView(NightUi.button(this, "Salvar IP e sincronizar") {
            val address = pc.text.toString().trim()
            if (address.isNotEmpty() && !SyncProtocol.isLocalAddress(address)) toast("Informe um IPv4 de rede local válido.")
            else {
                state.prefs.edit().putString("pcAddress", address).commit()
                StreakSync.send(this)
                toast(if (address.isEmpty()) "Sincronização desativada." else "Envio solicitado. Confira o painel do PC.")
            }
        })
        page.addView(NightUi.button(this, "Ver status da sincronização") {
            toast(state.prefs.getString("syncStatus", "Nenhum envio realizado.")!!)
        })
```

## SettingsActivity.kt: lista, exclusão e retorno das permissões

```kotlin
    private fun renderZones() {
        zoneList.removeAllViews()
        val zones = SafeZones(this).all()
        if (zones.isEmpty()) zoneList.addView(NightUi.text(this, "Nenhuma zona segura cadastrada.", 14f, NightUi.muted))
        zones.forEach { zone ->
            zoneList.addView(NightUi.panel(this).apply {
                addView(NightUi.text(this@SettingsActivity, zone.name, 18f))
                addView(NightUi.text(this@SettingsActivity, String.format(java.util.Locale.US, "%.5f, %.5f", zone.latitude, zone.longitude), 12f, NightUi.muted))
                addView(NightUi.button(this@SettingsActivity, "Excluir zona") {
                    android.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Excluir ${zone.name}?")
                        .setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
                            SafeZones(this@SettingsActivity).remove(zone.id); renderZones()
                        }.show()
                })
            })
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        homeRequest.permissionResult(requestCode)
    }
    @Deprecated("Compatibilidade com resolução de localização")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        homeRequest.resolutionResult(requestCode, resultCode)
    }
    override fun onDestroy() { homeRequest.close(); super.onDestroy() }
```

## AlarmActivity.kt: resultado manual (dentro de onCreate)

```kotlin
        locationRequest = ManualLocation(this) { location ->
            runCatching { EscapeRoute.resolve(this, requestedSession, location) }.onSuccess { mode ->
                if (mode == EscapeRoute.HOME) Toast.makeText(this, "Você está em uma zona segura, vá para o PC!", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(this, "Não foi possível verificar as zonas salvas. Tente novamente.", Toast.LENGTH_LONG).show() }
            refreshChallenge()
        }
```

## AlarmActivity.kt: botão e painel (dentro de onCreate)

```kotlin
        away = NightUi.button(this, "Não estou em casa") {
            requestedSession = AlarmState(this).activeSince
            locationRequest.start(); refreshChallenge()
        }
        page.addView(away)
        if (DeveloperPanel.enabled(this)) page.addView(NightUi.button(this, "Desenvolvedor · testes") { DeveloperPanel.show(this) })
```

AlarmService.kt não possui mais locationRequest, nem chamada a EscapeRoute.locate. AlarmActivity.onResume apenas reinicia a atualização visual; não inicia GPS. Os retornos de permissões/resolução são encaminhados a ManualLocation e onDestroy chama close().

## DeveloperPanel.kt: controles de teste

```kotlin
package br.com.alarmehardcore

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

object DeveloperPanel {
    fun enabled(context: Context) = AlarmState(context).prefs.getBoolean("developerMode", false)
    fun show(activity: Activity) {
        val state = AlarmState(activity)
        state.prefs.edit().putBoolean("developerMode", true).apply()
        AlertDialog.Builder(activity).setTitle("Desenvolvedor · testes")
            .setItems(arrayOf("Pular fase ativa", "Resetar ofensiva (preserva recorde)", "Ocultar painel")) { _, index ->
                val message = when (index) {
                    0 -> if (state.activeSince == 0L) "Nenhum alarme tocando." else {
                        val elapsed = AlarmSession.elapsed(activity)
                        val target = when (AlarmPhases.frame(elapsed).phase) {
                            AlarmPhase.ZEN -> AlarmPhases.ZEN_END
                            AlarmPhase.HARDCORE -> AlarmPhases.PUNISHMENT_START
                            AlarmPhase.PUNISHMENT -> elapsed
                        }
                        state.prefs.edit().putLong("debugAdvance", state.prefs.getLong("debugAdvance", 0) + target - elapsed)
                            .putBoolean("habitEligible", false).commit()
                        "Fase avançada. Esta sessão não altera a ofensiva."
                    }
                    1 -> { HabitStore(activity).reset(); "Ofensiva zerada." }
                    else -> { state.prefs.edit().putBoolean("developerMode", false).apply(); "Painel oculto na próxima abertura." }
                }
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }.setNegativeButton("Fechar", null).show()
    }
}
```

## StreakSync.kt: POST assíncrono e assinatura

```kotlin
package br.com.alarmehardcore

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SyncProtocol {
    fun isLocalAddress(value: String): Boolean {
        if (!value.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) return false
        val p = value.split('.').map { it.toInt() }
        if (p.any { it !in 0..255 }) return false
        if (p.joinToString(".") != value) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }
    fun signature(key: ByteArray, body: ByteArray): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal("streak-v1\n".toByteArray(Charsets.UTF_8) + body).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

object StreakSync {
    private val executor = Executors.newSingleThreadExecutor()
    // Called from main-thread state transitions: capture a coherent snapshot before enqueueing IO.
    fun send(context: Context) {
        val app = context.applicationContext
        val prefs = AlarmState(app).prefs
        val address = prefs.getString("pcAddress", "")!!
        if (!SyncProtocol.isLocalAddress(address)) return
        val key = runCatching { TimePin.decode(AlarmState(app).secret) }.getOrNull() ?: return
        val progress = HabitStore(app).progress()
        val revision = maxOf(System.currentTimeMillis(), prefs.getLong("syncRevision", 0) + 1)
        prefs.edit().putLong("syncRevision", revision).putString("syncStatus", "Envio pendente…").apply()
        val count = progress.visibleCount(LocalDate.now().toEpochDay())
        executor.execute {
            val body = JSONObject().put("streak", count).put("best", progress.best).put("revision", revision)
                .put("sentAt", System.currentTimeMillis() / 1000).toString().toByteArray(Charsets.UTF_8)
            val result = runCatching {
                val connection = URL("http://$address:8080/streak").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.connectTimeout = 2000; connection.readTimeout = 2000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("X-Alarm-Signature", SyncProtocol.signature(key, body))
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                    check(connection.responseCode == 200) { "PC recusou o envio (${connection.responseCode}). Confira a chave e os relógios." }
                } finally { connection.disconnect() }
            }
            prefs.edit().putString("syncStatus", if (result.isSuccess) "Ofensiva sincronizada com $address."
                else "Sem sincronização: confira IP, rede, firewall e programa do PC. O alarme continua funcionando.").apply()
        }
    }
}
```

HabitStore chama StreakSync.send após reset/encerramento e transição real; MainActivity chama ao abrir. O Manifest adiciona INTERNET e usesCleartextTraffic=true.

## minigames.py: jogos e validação

```python
"""Mini-jogos offline; sem eval e com tempo monotônico para digitação."""
from dataclasses import dataclass
import random
from alarm import normalize


@dataclass(frozen=True)
class MiniGame:
    kind: str
    prompt: str
    answer: str
    time_limit: float | None = None

    def accepts(self, value, elapsed=None):
        if self.kind == "typing":
            return elapsed is not None and 0 <= elapsed < 15 and value == self.answer
        return normalize(value) == normalize(self.answer)


def make_game(rng=None, kind=None):
    rng = rng or random.SystemRandom()
    kind = kind or rng.choice(("anagram", "math", "typing"))
    if kind == "anagram":
        word, hint = rng.choice([
            ("extraordinario", "fora do comum"), ("responsabilidade", "dever de responder pelos próprios atos"),
            ("desenvolvimento", "processo de crescimento"), ("perseveranca", "persistência diante das dificuldades"),
            ("planejamento", "preparação organizada para um objetivo"),
        ])
        letters = list(word)
        rng.shuffle(letters)
        if "".join(letters) == word:
            letters = letters[1:] + letters[:1]
        return MiniGame(kind, f"Desembaralhe: {' '.join(letters).upper()}\nDica: {hint}.", word)
    if kind == "math":
        a, b, c, d = rng.randint(12, 40), rng.randint(3, 9), rng.randint(8, 15), rng.randint(2, 7)
        return MiniGame(kind, f"Respeite a precedência:\n{a} + {b} × ({c} − {d}) = ?", str(a + b * (c - d)))
    if kind == "typing":
        phrase = rng.choice(("Hoje eu escolho sair da cama.", "Um passo de cada vez, sem desistir.",
                             "Minha rotina começa com uma escolha."))
        return MiniGame(kind, f"Digite exatamente em menos de 15 segundos:\n{phrase}", phrase, 15)
    raise ValueError("Mini-jogo desconhecido")
```

## streak_server.py: receptor HTTP

```python
"""Recebe apenas estatísticas assinadas na LAN. Nunca recebe ou revela PIN/chave."""
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import os
from pathlib import Path
import queue
import threading
import time


def signature(secret, body):
    return hmac.new(secret, b"streak-v1\n" + body, hashlib.sha256).hexdigest()


def decode(secret, body, supplied, fresh=True):
    if not hmac.compare_digest(signature(secret, body), supplied):
        raise ValueError("Assinatura inválida")
    data = json.loads(body)
    if not isinstance(data, dict):
        raise ValueError("Formato inválido")
    for key in ("streak", "best", "revision", "sentAt"):
        if type(data.get(key)) is not int:
            raise ValueError("Formato inválido")
    if not 0 <= data["streak"] <= data["best"] <= 1_000_000 or not 0 < data["revision"] < 2**63:
        raise ValueError("Valores inválidos")
    if fresh and abs(time.time() - data["sentAt"]) > 120:
        raise ValueError("Envio expirado")
    return data


class StreakServer:
    def __init__(self, read_key, path=None, host="0.0.0.0", port=8080):
        self.read_key = read_key
        self.path = Path(path) if path else Path(__file__).with_name("streak.json")
        self.events = queue.Queue(maxsize=1)
        self.lock = threading.Lock()
        self.latest = {}
        self.key_id = None
        try:
            saved = json.loads(self.path.read_text(encoding="utf-8"))
            key = read_key()
            self.latest = decode(key, saved["body"].encode("utf-8"), saved["signature"], fresh=False)
            self.key_id = hashlib.sha256(key).digest()
            self.events.put_nowait(self.latest)
        except (OSError, ValueError, TypeError, KeyError):
            pass
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def setup(self):
                super().setup()
                self.connection.settimeout(3)

            def log_message(self, *_):
                pass

            def do_POST(self):
                if self.path != "/streak":
                    self.send_error(404); return
                peer = ipaddress.ip_address(self.client_address[0])
                if not (peer.is_private or peer.is_loopback):
                    self.send_error(403); return
                try:
                    length = int(self.headers.get("Content-Length", "0"))
                    if not 0 < length <= 4096:
                        self.send_error(413); return
                    body = self.rfile.read(length)
                    supplied = self.headers.get("X-Alarm-Signature", "")
                    with owner.lock:
                        key = owner.read_key()
                        data = decode(key, body, supplied)
                        key_id = hashlib.sha256(key).digest()
                        previous = owner.latest if owner.key_id == key_id else {}
                        if data["revision"] <= previous.get("revision", 0):
                            self.send_error(409); return
                        temp = owner.path.with_suffix(".tmp")
                        temp.write_text(json.dumps({"body": body.decode("utf-8"), "signature": supplied}), encoding="utf-8")
                        os.replace(temp, owner.path)
                        owner.latest = data; owner.key_id = key_id
                        try:
                            owner.events.get_nowait()
                        except queue.Empty:
                            pass
                        owner.events.put_nowait(data)
                except (ValueError, TypeError, KeyError, UnicodeError):
                    self.send_error(403); return
                except OSError:
                    self.send_error(503); return
                self.send_response(200)
                self.send_header("Content-Length", "2")
                self.end_headers(); self.wfile.write(b"OK")

        self.http = ThreadingHTTPServer((host, port), Handler)
        self.http.daemon_threads = True
        self.thread = threading.Thread(target=self.http.serve_forever, kwargs={"poll_interval": .1}, daemon=True)
        self.thread.start()

    def close(self):
        self.http.shutdown(); self.http.server_close(); self.thread.join(timeout=2)
```

## desktop_gui.py: sorteio, cronômetro e liberação do PIN

```python
    def challenge(self):
        try:
            self.secret = self.read_key()
        except (OSError, ValueError, TypeError) as exc:
            self.configure()
            self.notice.config(text=f"Configure a chave para começar. {exc}")
            return
        self.clear()
        self.puzzle = make_game()
        self.label(self.body, "01  /  DESPERTE A MENTE", 11, PURPLE).pack(anchor="w")
        self.label(self.body, "Um desafio antes\ndo seu dia.", 29).pack(anchor="w", pady=(16, 8))
        self.label(self.body, "Acerte para revelar o código do celular.", 13, MUTED).pack(anchor="w", pady=(0, 24))
        card = tk.Frame(self.body, bg=PANEL, padx=22, pady=22)
        card.pack(fill="x")
        self.label(card, self.puzzle.prompt, 19, bg=PANEL, wraplength=450).pack(anchor="w", fill="x")
        self.answer = tk.Entry(self.body, font=("Segoe UI", 20), fg=TEXT, bg=PANEL,
                               insertbackground=PURPLE, relief="flat", highlightthickness=1,
                               highlightbackground="#584466", highlightcolor=PURPLE)
        self.answer.pack(fill="x", ipady=12, pady=(24, 12))
        self.notice = self.label(self.body, "Sua resposta", 12, MUTED, wraplength=450)
        self.notice.pack(anchor="w", pady=(0, 16))
        self.button(self.body, "Revelar meu PIN  →", self.submit).pack(fill="x")
        self.answer.focus_set()
        self.root.bind("<Return>", lambda _: self.submit())
        self.game_started = time.monotonic()
        if self.puzzle.kind == "typing":
            for event in ("<<Paste>>", "<Control-v>", "<Control-V>", "<Shift-Insert>"):
                self.answer.bind(event, lambda _: "break")
            self.typing_tick()

    def submit(self):
        if self.puzzle is None:
            return
        elapsed = time.monotonic() - self.game_started
        if not self.puzzle.accepts(self.answer.get(), elapsed):
            if self.puzzle.kind == "typing" and elapsed >= 15:
                self.notice.config(text="Tempo esgotado. Comece um novo desafio.", fg="#ffb7b7")
                return
            self.notice.config(text="Ainda não. Confira sua resposta e tente de novo.", fg="#ffb7b7")
            self.answer.select_range(0, "end")
            return
        now = time.time()
        pin = totp(self.secret, now)
        self.pin_expires = (int(now) // PERIOD + 2) * PERIOD
        self.clear()
        self.puzzle = None
        self.label(self.body, "02  /  PRONTO PARA DESPERTAR", 11, PURPLE).pack(anchor="w")
        self.label(self.body, "Você conseguiu.", 30).pack(anchor="w", pady=(22, 12))
        self.label(self.body, "Digite este PIN no seu celular.", 15, MUTED).pack(anchor="w", pady=(0, 28))
        card = tk.Frame(self.body, bg=PANEL, padx=20, pady=24)
        card.pack(fill="x")
        self.label(card, "CÓDIGO DE DESATIVAÇÃO", 11, PURPLE, bg=PANEL).pack(anchor="center")
        self.pin_label = self.label(card, pin[:3] + " " + pin[3:], 48, bg=PANEL)
        self.pin_label.pack(anchor="center", pady=(10, 4))
        self.countdown = self.label(self.body, "", 14, PURPLE)
        self.countdown.pack(anchor="w", pady=22)
        self.label(self.body, "Mantenha a hora automática no PC e no celular.\nCada novo código exige outro desafio.", 12, MUTED).pack(anchor="w", pady=(0, 28))
        self.button(self.body, "Novo desafio", self.challenge).pack(fill="x")
        self.tick()
```

## desktop_gui.py: atualização segura da interface e encerramento

```python
    def typing_tick(self):
        if self.puzzle is None or self.puzzle.kind != "typing":
            return
        remaining = max(0, 15 - (time.monotonic() - self.game_started))
        self.notice.config(text=f"Digite sem colar · {remaining:.1f}s restantes")
        if remaining <= 0:
            self.notice.config(text="Tempo esgotado. Comece um novo desafio.", fg="#ffb7b7")
            self.answer.config(state="disabled")
            self.timer = None
        else:
            self.timer = self.root.after(100, self.typing_tick)

    def find_addresses(self):
        try:
            values = sorted({row[4][0] for row in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)
                             if not row[4][0].startswith("127.")})
            self.addresses.put("IP do PC: " + (", ".join(values) or "consulte a rede") + "\nPorta 8080")
        except OSError:
            self.addresses.put("Consulte o IPv4 do PC na rede.\nPorta 8080")

    def poll_sync(self):
        if self.closed or self.server is None:
            return
        try:
            data = self.server.events.get_nowait()
            self.streak_label.config(text=f"OFENSIVA · {data['streak']} / 21 dias\nRecorde: {data['best']} dias")
            self.streak_label.config(fg=PURPLE)
        except queue.Empty:
            pass
        try:
            self.network_label.config(text=self.addresses.get_nowait())
        except queue.Empty:
            pass
        self.sync_timer = self.root.after(250, self.poll_sync)

    def close(self):
        if self.closed:
            return
        self.closed = True
        if self.timer is not None:
            self.root.after_cancel(self.timer)
        if self.sync_timer is not None:
            self.root.after_cancel(self.sync_timer)
        if self.server is not None:
            threading.Thread(target=self.server.close, daemon=True).start()
        self.root.destroy()
```

No construtor, a GUI cria StreakServer(self.read_key, CONFIG.with_name("streak.json")), inicializa a fila de endereços e agenda poll_sync(). Falha ao ocupar a porta é exibida sem impedir o PIN. O código integrado completo permanece em desktop/desktop_gui.py.
