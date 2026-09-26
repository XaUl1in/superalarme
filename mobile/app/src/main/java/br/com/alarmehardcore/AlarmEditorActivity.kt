package br.com.alarmehardcore

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.*
import org.json.JSONObject
import java.util.Locale

class AlarmEditorActivity : Activity() {
    private lateinit var draft: SavedAlarm
    private lateinit var label: EditText
    private lateinit var time: Button
    private lateinit var sound: Button
    private lateinit var enabled: Switch
    private val days = mutableSetOf<Int>()
    private var preview: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draft = savedInstanceState?.getString("draft")?.let { SavedAlarm.from(JSONObject(it)) }
            ?: AlarmStore(this).all().find { it.id == intent.getStringExtra("id") } ?: SavedAlarm()
        days.addAll(draft.days)
        val page = NightUi.page(this)
        page.addView(NightUi.button(this, "‹  Voltar") { finish() })
        page.addView(NightUi.text(this, if (intent.hasExtra("id") && intent.getStringExtra("id") != null) "Editar alarme" else "Novo alarme", 29f))
        page.addView(NightUi.text(this, "Seu horário. Seu ritmo.", 15f, NightUi.muted))
        page.addView(MoonView(this), LinearLayout.LayoutParams(-1, NightUi.dp(this, 120)))
        time = NightUi.button(this, "", true) {
            TimePickerDialog(this, { _, hour, minute ->
                draft = draft.copy(hour = hour, minute = minute); updateTime()
            }, draft.hour, draft.minute, true).apply { setTitle("Horário do alarme"); show() }
        }.apply { textSize = 46f; minHeight = NightUi.dp(this@AlarmEditorActivity, 94) }
        updateTime(); page.addView(time)
        page.addView(NightUi.text(this, "Toque no horário para editar. As mudanças só valem ao salvar.", 13f, NightUi.muted))
        label = NightUi.input(this, "Nome do alarme").apply { setSingleLine(); setText(draft.label) }
        page.addView(label)
        page.addView(NightUi.text(this, "Repetir", 18f))
        val row = LinearLayout(this)
        listOf(2 to "S", 3 to "T", 4 to "Q", 5 to "Q", 6 to "S", 7 to "S", 1 to "D").forEach { (day, letter) ->
            val button = TextView(this).apply {
                text = letter; textSize = 15f; gravity = android.view.Gravity.CENTER
                contentDescription = listOf("", "Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")[day]
                fun paintDay() { background = NightUi.shape(if (day in days) NightUi.purple else NightUi.card, 14f); setTextColor(if (day in days) NightUi.background else NightUi.text) }
                paintDay()
                setOnClickListener { if (!days.add(day)) days.remove(day); paintDay() }
            }
            row.addView(button, LinearLayout.LayoutParams(0, NightUi.dp(this, 48), 1f).apply { setMargins(2, 4, 2, 4) })
        }
        page.addView(row)
        page.addView(NightUi.text(this, "Sem dias selecionados: toca uma vez e fica salvo desligado.", 12f, NightUi.muted))
        sound = NightUi.button(this, "♪  ${draft.soundName}   ›") { chooseSound() }
        page.addView(sound)
        page.addView(NightUi.button(this, "Ouvir por 5 segundos") { listen() })
        val activeRow = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, 24, 0, 12) }
        activeRow.addView(NightUi.text(this, "Ativar ao salvar", 17f), LinearLayout.LayoutParams(0, -2, 1f))
        enabled = NightUi.switch(this, draft.enabled)
        activeRow.addView(enabled); page.addView(activeRow)
        page.addView(NightUi.text(this, "Começa com ondas suaves por 5 minutos. Depois toca o som escolhido; aos 8 minutos, sirene e volume máximo.", 13f, NightUi.muted))
        page.addView(NightUi.button(this, "Salvar alarme", true) { save() })
        if (intent.getStringExtra("id") != null) page.addView(NightUi.button(this, "Excluir alarme") {
            AlertDialog.Builder(this).setTitle("Excluir este alarme?").setMessage("Ele será removido da sua lista.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
                    AlarmStore(this).remove(draft.id); AlarmScheduler.reschedule(this); finish()
                }.show()
        })
    }

    private fun updateTime() { time.text = String.format(Locale.getDefault(), "%02d:%02d", draft.hour, draft.minute) }
    private fun currentDraft() = draft.copy(label = label.text.toString().trim().ifEmpty { "Meu despertar" }, days = days.toSet(), enabled = enabled.isChecked)
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("draft", currentDraft().json().toString()); super.onSaveInstanceState(outState) }
    override fun onPause() { stopPreview(); super.onPause() }
    override fun onResume() {
        super.onResume()
        if (AlarmState(this).activeSince > 0) { startActivity(Intent(this, AlarmActivity::class.java)); finish() }
    }
    private fun save() {
        stopPreview()
        val requested = currentDraft()
        val problem = if (requested.enabled) AlarmReadiness.problem(this) else null
        if (problem != null) { toast("Alarme ainda não salvo. $problem"); return }
        val isEnabled = requested.enabled
        val store = AlarmStore(this)
        runCatching {
            store.put(requested.copy(enabled = isEnabled, nextAt = if (isEnabled) AlarmTime.next(requested.hour, requested.minute, requested.days) else 0))
            AlarmScheduler.reschedule(this)
        }.onSuccess {
            toast("Alarme salvo."); finish()
        }.onFailure { toast("Não foi possível concluir: ${it.message}. Confira a lista e as permissões.") }
    }
    private fun chooseSound() {
        stopPreview()
        val options = (AlarmAudio.names + "Toques do celular…" + "Escolher arquivo de áudio…").toTypedArray()
        AlertDialog.Builder(this).setTitle("Som do alarme").setItems(options) { _, index ->
            when {
                index < 3 -> selectedSound(AlarmAudio.sounds[index], AlarmAudio.names[index])
                index == 3 -> runCatching {
                    startActivityForResult(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (draft.sound.startsWith("builtin:")) null else Uri.parse(draft.sound)), 10)
                }.onFailure { toast("O seletor de toques não está disponível. Escolha um arquivo de áudio.") }
                else -> runCatching {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }, 11)
                }.onFailure { toast("Não foi possível abrir o seletor de arquivos.") }
            }
        }.show()
    }
    private fun selectedSound(uri: String, name: String) { draft = draft.copy(sound = uri, soundName = name); sound.text = "♪  $name   ›" }
    @Deprecated("Compatível com Android 8 e posteriores")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == 10) {
            @Suppress("DEPRECATION") val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) ?: return
            val name = runCatching { RingtoneManager.getRingtone(this, uri)?.getTitle(this) }.getOrNull() ?: "Toque do celular"
            selectedSound(uri.toString(), name)
        } else if (requestCode == 11) {
            val uri = data.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "Meu áudio"
                selectedSound(uri.toString(), name)
            }.onFailure { toast("Não foi possível manter o acesso a esse áudio. Escolha outro arquivo.") }
        }
    }
    private fun listen() {
        stopPreview()
        runCatching { preview = AlarmAudio.open(this, draft.sound, true).apply { start() }; handler.postDelayed({ stopPreview() }, 5000) }
            .onFailure { toast("Não foi possível tocar esse arquivo. Escolha outro som.") }
    }
    private fun stopPreview() { handler.removeCallbacksAndMessages(null); preview?.release(); preview = null }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
