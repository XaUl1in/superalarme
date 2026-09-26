package br.com.alarmehardcore

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import java.time.LocalDate

class HabitActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() {
        super.onResume()
        if (AlarmState(this).activeSince > 0) {
            startActivity(Intent(this, AlarmActivity::class.java)); finish(); return
        }
        val progress = HabitStore(this).progress()
        val count = progress.visibleCount(LocalDate.now().toEpochDay())
        val page = NightUi.page(this)
        page.addView(NightUi.button(this, "‹  Voltar") { finish() })
        page.addView(NightUi.text(this, "Sua ofensiva", 32f))
        page.addView(NightUi.text(this, "$count dias", 46f, NightUi.purple))
        page.addView(NightUi.text(this, if (count >= 21) "Meta de 21 dias conquistada! Continue sua sequência." else "Uma manhã de cada vez. Meta: 21 dias seguidos.", 16f))
        repeat(3) { row ->
            page.addView(LinearLayout(this).apply {
                repeat(7) { column ->
                    val day = row * 7 + column + 1
                    addView(NightUi.text(this@HabitActivity, if (day <= count) "✓" else day.toString(), 18f,
                        if (day <= count) NightUi.background else NightUi.muted).apply {
                        gravity = Gravity.CENTER
                        background = NightUi.shape(if (day <= count) NightUi.purple else NightUi.card, 12f)
                        contentDescription = "Dia $day: ${if (day <= count) "conquistado" else "pendente"}"
                    }, LinearLayout.LayoutParams(0, NightUi.dp(this@HabitActivity, 46), 1f).apply { setMargins(3, 6, 3, 6) })
                }
            })
        }
        page.addView(NightUi.text(this, "Seu recorde: ${progress.best} dias", 18f, NightUi.purple))
        page.addView(NightUi.text(this, "Como pontuar", 22f))
        page.addView(NightUi.text(this, "Digite o PIN do PC antes de 5 minutos em um alarme real. Vale no máximo 1 ponto por dia, na data em que o alarme começou.", 15f, NightUi.muted))
        page.addView(NightUi.text(this, "Chegar à fase 2 zera a ofensiva e impede pontuar naquele dia. Um dia sem pontuar interrompe a sequência. O recorde é preservado.", 15f, NightUi.muted))
        page.addView(NightUi.text(this, "A rota de fuga continua disponível: resolver as contas encerra o alarme, mas não concede ponto. Testes de 10 segundos não alteram a ofensiva.", 15f, NightUi.muted))
    }
}
