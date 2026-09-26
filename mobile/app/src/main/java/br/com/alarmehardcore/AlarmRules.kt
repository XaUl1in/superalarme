package br.com.alarmehardcore

object AlarmRules {
    fun acceptBroadcast(expected: Long, scheduled: Long, token: String?, current: String?, now: Long) =
        expected > 0 && expected == scheduled && !token.isNullOrEmpty() && token == current && now >= expected
    fun atHome(distance: Float?) = distance != null && distance.isFinite() && distance < 150f
    fun inAnySafeZone(distances: Iterable<Float>) = distances.any { atHome(it) }
    fun streak(previous: Int, correct: Boolean) = if (correct) previous + 1 else 0
}
