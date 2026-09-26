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
