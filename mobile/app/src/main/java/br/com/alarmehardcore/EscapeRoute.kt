package br.com.alarmehardcore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.random.Random

/** One-shot request: never uses an old cached fix, and always terminates within the deadline. */
class QuickLocation(context: Context, timeout: Long = 5000, result: (Location?) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val cancellation = CancellationTokenSource()
    private var finished = false
    private val deadline = SystemClock.elapsedRealtime() + timeout
    private val expired = Runnable { finish(null) }
    private var callback: ((Location?) -> Unit)? = result
    init {
        handler.postDelayed(expired, timeout)
        if (!hasPermission(context)) finish(null)
        else try {
            val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0).setDurationMillis(timeout.coerceAtLeast(1)).build()
            LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { finish(if (SystemClock.elapsedRealtime() < deadline) it else null) }
                .addOnFailureListener { finish(null) }
                .addOnCanceledListener { finish(null) }
        } catch (_: SecurityException) { finish(null) }
        catch (_: Exception) { finish(null) }
    }
    private fun finish(location: Location?) {
        if (finished) return
        finished = true
        handler.removeCallbacks(expired)
        val listener = callback; callback = null
        cancellation.cancel()
        listener?.invoke(location)
    }
    fun cancel() { callback = null; finish(null) }
    companion object {
        fun hasPermission(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

/** The route and challenge belong to the ringing session, surviving screen/process recreation. */
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
    fun question(context: Context): String {
        val p = prefs(context)
        p.getString("mathQuestion", null)?.let { return it }
        val a = Random.nextInt(25, 100); val b = Random.nextInt(15, 100); val c = Random.nextInt(10, 40)
        val question = "$a + $b − $c = ?"
        p.edit().putString("mathQuestion", question).putInt("mathAnswer", a + b - c).commit()
        return question
    }
    fun answer(context: Context, value: String): String {
        if (AlarmState(context).activeSince == 0L || mode(context) != MATH) return "invalid"
        question(context)
        val p = prefs(context)
        val correct = value.toIntOrNull() == p.getInt("mathAnswer", Int.MIN_VALUE)
        val streak = AlarmRules.streak(p.getInt("mathStreak", 0), correct)
        p.edit().putInt("mathStreak", streak).remove("mathQuestion").commit()
        if (streak >= 5) { AlarmService.finishAlarm(context); return "done" }
        question(context)
        return if (correct) "correct" else "wrong"
    }
}
