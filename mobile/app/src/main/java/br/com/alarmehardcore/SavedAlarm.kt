package br.com.alarmehardcore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

data class SavedAlarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7, val minute: Int = 30,
    val label: String = "Meu despertar",
    val days: Set<Int> = emptySet(),
    val sound: String = "builtin:alarm", val soundName: String = "Clássico",
    val enabled: Boolean = true, val nextAt: Long = 0
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("hour", hour).put("minute", minute)
        .put("label", label).put("days", JSONArray(days.toList())).put("sound", sound)
        .put("soundName", soundName).put("enabled", enabled).put("nextAt", nextAt)

    companion object {
        fun from(json: JSONObject): SavedAlarm {
            val days = json.optJSONArray("days") ?: JSONArray()
            return SavedAlarm(json.getString("id"), json.getInt("hour"), json.getInt("minute"),
                json.optString("label", "Meu despertar"), (0 until days.length()).map { days.getInt(it) }.toSet(),
                json.optString("sound", "builtin:alarm"), json.optString("soundName", "Clássico"),
                json.optBoolean("enabled", true), json.optLong("nextAt", 0))
        }
    }
}

class AlarmStore(context: Context) {
    private val state = AlarmState(context)
    init {
        if (!state.prefs.contains("alarmsV2")) {
            // Preserva o único alarme da versão 0.1 durante a atualização.
            val legacy = state.scheduled
            val list = if (legacy > 0) {
                val c = Calendar.getInstance().apply { timeInMillis = legacy }
                listOf(SavedAlarm(hour = c.get(Calendar.HOUR_OF_DAY), minute = c.get(Calendar.MINUTE), nextAt = legacy))
            } else emptyList()
            write(list)
        }
    }
    fun all(): List<SavedAlarm> {
        val array = JSONArray(state.prefs.getString("alarmsV2", "[]"))
        return (0 until array.length()).map { SavedAlarm.from(array.getJSONObject(it)) }
    }
    fun write(alarms: List<SavedAlarm>) {
        val array = JSONArray()
        alarms.forEach { array.put(it.json()) }
        check(state.prefs.edit().putString("alarmsV2", array.toString()).commit()) { "Não foi possível salvar os alarmes." }
    }
    fun put(alarm: SavedAlarm) = write(all().filterNot { it.id == alarm.id } + alarm)
    fun remove(id: String) = write(all().filterNot { it.id == id })
}
