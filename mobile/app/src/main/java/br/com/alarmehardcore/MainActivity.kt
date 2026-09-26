package br.com.alarmehardcore

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmReadiness {
    fun problem(context: Context): String? {
        if (AlarmState(context).secret.isEmpty()) return "Pareie o computador em Ajustes."
        if (!AlarmScheduler.canSchedule(context)) return "Permita alarmes exatos em Ajustes."
        val n = context.getSystemService(NotificationManager::class.java)
        if (!n.areNotificationsEnabled()) return "Permita notificações em Ajustes."
        val importance = n.getNotificationChannel(AlarmService.CHANNEL)?.importance
        if (importance != null && importance < NotificationManager.IMPORTANCE_HIGH) return "Ative a prioridade alta do canal Despertador."
        if (Build.VERSION.SDK_INT >= 34 && !n.canUseFullScreenIntent()) return "Permita tela cheia em Ajustes."
        return null
    }
}

class MainActivity : Activity() {
    private var taps = 0
    private var lastTap = 0L
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() {
        super.onResume()
        if (AlarmState(this).activeSince > 0) {
            startForegroundService(Intent(this, AlarmService::class.java))
            startActivity(Intent(this, AlarmActivity::class.java))
        }
        runCatching { AlarmScheduler.reschedule(this) }.onFailure { toast("Não foi possível rearmar: ${it.message}") }
        render()
        StreakSync.send(this)
    }

    private fun render() {
        val page = NightUi.page(this)
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(NightUi.text(this, "HARDCORE", 13f, NightUi.muted).apply {
            letterSpacing = .18f
            setOnClickListener {
                val now = android.os.SystemClock.elapsedRealtime()
                taps = if (now - lastTap <= 700) taps + 1 else 1; lastTap = now
                if (taps >= 5) { taps = 0; DeveloperPanel.show(this@MainActivity) }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(NightUi.button(this, "Ajustes") { startActivity(Intent(this, SettingsActivity::class.java)) }, LinearLayout.LayoutParams(-2, -2))
        page.addView(top)
        page.addView(MoonView(this), LinearLayout.LayoutParams(-1, NightUi.dp(this, 172)))
        val alarms = AlarmStore(this).all().sortedWith(compareBy<SavedAlarm> { it.hour }.thenBy { it.minute })
        val next = alarms.filter { it.enabled }.minByOrNull { it.nextAt }
        page.addView(NightUi.text(this, "PRÓXIMO DESPERTAR", 11f, NightUi.muted).apply { letterSpacing = .16f })
        page.addView(NightUi.text(this, next?.let { String.format(Locale.getDefault(), "%02d:%02d", it.hour, it.minute) } ?: "Boa noite", 48f).apply {
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        })
        val summary = if (next != null) {
            val minutes = ((next.nextAt - System.currentTimeMillis()).coerceAtLeast(0) / 60_000)
            "${next.label} · em ${minutes / 60}h ${minutes % 60}min"
        } else "Prepare seu próximo despertar."
        page.addView(NightUi.text(this, summary, 14f, NightUi.muted))
        val streak = HabitStore(this).progress().visibleCount(java.time.LocalDate.now().toEpochDay())
        page.addView(NightUi.button(this, "Ofensiva · $streak dias / meta 21   ›") { startActivity(Intent(this, HabitActivity::class.java)) })
        page.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 21; progress = streak.coerceAtMost(21)
            progressTintList = android.content.res.ColorStateList.valueOf(NightUi.purple)
            contentDescription = "$streak dias de ofensiva, meta de 21 dias"
        })
        AlarmReadiness.problem(this)?.let { reason ->
            page.addView(NightUi.button(this, reason) { startActivity(Intent(this, SettingsActivity::class.java)) })
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, NightUi.dp(this@MainActivity, 20), 0, 0) }
        heading.addView(NightUi.text(this, "Seus alarmes", 23f), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(NightUi.text(this, "${alarms.size} salvos", 13f, NightUi.muted))
        page.addView(heading)
        if (alarms.isEmpty()) {
            page.addView(NightUi.panel(this).apply {
                addView(NightUi.text(this@MainActivity, "Uma rotina começa aqui", 18f))
                addView(NightUi.text(this@MainActivity, "Adicione um horário. Seus alarmes ficam salvos para usar quando quiser.", 14f, NightUi.muted))
            })
        }
        for (alarm in alarms) {
            val card = NightUi.panel(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            detail.addView(NightUi.text(this, String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute), 34f,
                if (alarm.enabled) NightUi.text else NightUi.muted))
            detail.addView(NightUi.text(this, "${alarm.label} · ${AlarmTime.daysLabel(alarm.days)}", 13f, NightUi.muted))
            detail.addView(NightUi.text(this, "♪ ${alarm.soundName}", 12f, NightUi.purple))
            detail.setOnClickListener { edit(alarm.id) }
            detail.contentDescription = "Editar ${alarm.label}, ${alarm.hour} horas e ${alarm.minute} minutos"
            card.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
            val toggle = NightUi.switch(this, alarm.enabled).apply { contentDescription = "Ativar ${alarm.label}" }
            toggle.setOnCheckedChangeListener { _, enabled ->
                if (enabled && AlarmReadiness.problem(this) != null) {
                    toast(AlarmReadiness.problem(this)!!); render()
                } else {
                    val store = AlarmStore(this)
                    runCatching {
                        store.put(alarm.copy(enabled = enabled, nextAt = if (enabled) AlarmTime.next(alarm.hour, alarm.minute, alarm.days) else 0))
                        AlarmScheduler.reschedule(this)
                    }.onFailure { toast("Não foi possível concluir: ${it.message}. Confira a lista e as permissões.") }
                    render()
                }
            }
            card.addView(toggle); page.addView(card)
        }
        page.addView(NightUi.button(this, "＋  Novo alarme", true) { edit(null) })
        val pendingTest = AlarmState(this).prefs.getLong("testAt", 0)
        if (pendingTest > 0) page.addView(NightUi.text(this, "Teste agendado para ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(pendingTest))}", 13f, NightUi.purple))
        page.addView(NightUi.text(this, "Em casa, PIN do computador.\nFora de casa, cinco contas para despertar.", 12f, NightUi.muted).apply { gravity = Gravity.CENTER; setPadding(0, 36, 0, 8) })
    }
    private fun edit(id: String?) { startActivity(Intent(this, AlarmEditorActivity::class.java).putExtra("id", id)) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
