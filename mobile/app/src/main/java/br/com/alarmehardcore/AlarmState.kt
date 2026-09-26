package br.com.alarmehardcore

import android.content.Context

class AlarmState(context: Context) {
    val prefs = context.getSharedPreferences("alarm", Context.MODE_PRIVATE)
    var secret: String
        get() = prefs.getString("secret", "")!!
        set(value) { prefs.edit().putString("secret", value).commit() }
    var scheduled: Long
        get() = prefs.getLong("scheduled", 0)
        set(value) { prefs.edit().putLong("scheduled", value).commit() }
    var activeSince: Long
        get() = prefs.getLong("activeSince", 0)
        set(value) { prefs.edit().putLong("activeSince", value).commit() }
    var maximumVolume: Boolean
        get() = prefs.getBoolean("maximumVolume", true)
        set(value) { prefs.edit().putBoolean("maximumVolume", value).commit() }
}
