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
