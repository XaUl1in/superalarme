package br.com.alarmehardcore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class AlarmActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var instructions: TextView
    private lateinit var challenge: TextView
    private lateinit var answer: EditText
    private lateinit var submit: Button
    private var displayedMode = ""
    private lateinit var locationRequest: ManualLocation
    private lateinit var away: Button
    private var requestedSession = 0L
    private var originalBrightness = -1f
    private val update = object : Runnable {
        override fun run() {
            val since = AlarmState(this@AlarmActivity).activeSince
            if (since == 0L) { finish(); return }
            refreshChallenge()
            val elapsed = AlarmSession.elapsed(this@AlarmActivity)
            val frame = AlarmPhases.frame(elapsed)
            window.attributes = window.attributes.apply { screenBrightness = frame.brightness }
            val remaining = ((if (frame.phase == AlarmPhase.ZEN) AlarmPhases.ZEN_END else AlarmPhases.PUNISHMENT_START) - elapsed + 999).coerceAtLeast(0) / 1000
            val countdown = "${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
            status.text = when (frame.phase) {
                AlarmPhase.ZEN -> "Fase 1 · Zen\nPIN em até $countdown para pontuar"
                AlarmPhase.HARDCORE -> "Fase 2 · Hardcore\nSirene em $countdown"
                AlarmPhase.PUNISHMENT -> "Fase 3 · Punição\nSirene e volume máximo"
            }
            if (!AlarmState(this@AlarmActivity).prefs.getBoolean("habitEligible", false))
                status.append("\nTeste: não altera a ofensiva")
            handler.postDelayed(this, 250)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationRequest = ManualLocation(this) { location ->
            runCatching { EscapeRoute.resolve(this, requestedSession, location) }.onSuccess { mode ->
                if (mode == EscapeRoute.HOME) Toast.makeText(this, "Você está em uma zona segura, vá para o PC!", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(this, "Não foi possível verificar as zonas salvas. Tente novamente.", Toast.LENGTH_LONG).show() }
            refreshChallenge()
        }
        originalBrightness = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = AlarmPhases.frame(AlarmSession.elapsed(this@AlarmActivity)).brightness }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        val page = NightUi.page(this)
        page.gravity = Gravity.CENTER_HORIZONTAL
        page.addView(NightUi.text(this, "É HORA DE DESPERTAR", 12f, NightUi.purple).apply { letterSpacing = .15f })
        page.addView(MoonView(this), LinearLayout.LayoutParams(-1, NightUi.dp(this, 180)))
        page.addView(NightUi.text(this, "Bom dia.", 46f))
        page.addView(NightUi.text(this, AlarmState(this).prefs.getString("activeLabel", "Hora de levantar")!!, 20f))
        instructions = NightUi.text(this, "Resolva o desafio no PC e digite o PIN.", 16f, NightUi.muted).apply { gravity = Gravity.CENTER }
        page.addView(instructions)
        challenge = NightUi.text(this, "", 26f, NightUi.purple)
        page.addView(challenge)
        status = NightUi.text(this, "", 14f, NightUi.purple); page.addView(status)
        answer = NightUi.input(this, "Resposta").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 30f; gravity = Gravity.CENTER; letterSpacing = .2f
            filters = arrayOf(InputFilter.LengthFilter(6)); setSingleLine()
        }
        page.addView(answer, LinearLayout.LayoutParams(-1, NightUi.dp(this, 76)))
        val feedback = NightUi.text(this, "", 14f, NightUi.muted)
        submit = NightUi.button(this, "Confirmar", true) {
            if (EscapeRoute.mode(this) == EscapeRoute.MATH) {
                when (EscapeRoute.answer(this, answer.text.toString())) {
                    "done" -> finish()
                    "correct" -> feedback.text = "Correto. Continue!"
                    "wrong" -> feedback.text = "Errou. Contagem zerada: acerte 5 seguidas."
                }
                answer.text.clear(); refreshChallenge()
            } else when (AlarmService.dismiss(this, answer.text.toString())) {
                "" -> finish()
                "wait" -> feedback.text = "Aguarde 5 segundos antes de tentar novamente."
                else -> { feedback.text = "PIN inválido. Confira o código e a hora dos aparelhos."; answer.text.clear() }
            }
        }
        page.addView(submit)
        page.addView(feedback)
        away = NightUi.button(this, "Não estou em casa") {
            requestedSession = AlarmState(this).activeSince
            locationRequest.start(); refreshChallenge()
        }
        page.addView(away)
        if (DeveloperPanel.enabled(this)) page.addView(NightUi.button(this, "Desenvolvedor · testes") { DeveloperPanel.show(this) })
        refreshChallenge()
    }
    private fun refreshChallenge() {
        val mode = EscapeRoute.mode(this)
        away.visibility = if (mode == EscapeRoute.MATH) android.view.View.GONE else android.view.View.VISIBLE
        away.isEnabled = !locationRequest.busy
        away.text = if (locationRequest.busy) "Verificando localização…" else "Não estou em casa"
        if (displayedMode != mode) {
            displayedMode = mode; answer.text.clear()
            answer.isEnabled = true; submit.isEnabled = true
            instructions.text = when (mode) {
                EscapeRoute.HOME -> "Resolva o desafio no PC e digite o PIN."
                EscapeRoute.MATH -> "Rota de fuga: acerte 5 contas seguidas. Um erro zera a contagem."
                else -> "Resolva o desafio no PC e digite o PIN."
            }
            answer.hint = if (mode == EscapeRoute.HOME) "PIN de 6 dígitos" else "Resultado da conta"
        }
        challenge.text = if (mode == EscapeRoute.MATH)
            "${AlarmState(this).prefs.getInt("mathStreak", 0)} / 5\n${EscapeRoute.question(this)}" else ""
    }
    override fun onResume() {
        super.onResume()
        handler.post(update)
    }
    override fun onPause() {
        handler.removeCallbacks(update)
        window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
        super.onPause()
    }
    override fun onDestroy() { locationRequest.close(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationRequest.permissionResult(requestCode)
    }
    @Deprecated("Compatibilidade com resolução de localização do Google Play Services")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        locationRequest.resolutionResult(requestCode, resultCode)
    }
    @Deprecated("Voltar não encerra um alarme ativo")
    override fun onBackPressed() { if (AlarmState(this).activeSince == 0L) super.onBackPressed() }
}
