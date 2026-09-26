package br.com.alarmehardcore

import android.Manifest
import android.app.Activity
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/** Starts only from a user action. Destruction cancels; no background or automatic restart. */
class ManualLocation(private val activity: Activity, private val result: (Location?) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var request: QuickLocation? = null
    var busy = false; private set
    private var closed = false
    private var generation = 0
    private val timeout = Runnable { complete(null) }
    fun start() {
        if (busy || closed) return
        busy = true
        generation++
        if (!QuickLocation.hasPermission(activity)) activity.requestPermissions(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION)
        else settings()
    }
    fun permissionResult(code: Int) {
        if (code != PERMISSION || !busy || closed) return
        if (QuickLocation.hasPermission(activity)) settings() else complete(null)
    }
    private fun settings() {
        val attempt = generation
        val settings = LocationSettingsRequest.Builder().addLocationRequest(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()).setAlwaysShow(true).build()
        handler.postDelayed(timeout, 5000)
        try {
            LocationServices.getSettingsClient(activity).checkLocationSettings(settings)
                .addOnSuccessListener { if (busy && !closed && attempt == generation) { handler.removeCallbacks(timeout); locate() } }
                .addOnFailureListener { error ->
                    if (!busy || closed || attempt != generation) return@addOnFailureListener
                    handler.removeCallbacks(timeout)
                    if (error is ResolvableApiException) {
                        try { error.startResolutionForResult(activity, RESOLUTION) }
                        catch (_: Exception) { complete(null) }
                    } else complete(null)
                }
        } catch (_: Exception) { complete(null) }
    }
    fun resolutionResult(code: Int, resultCode: Int) {
        if (code != RESOLUTION || !busy || closed) return
        if (resultCode == Activity.RESULT_OK) locate() else complete(null)
    }
    private fun locate() { request = QuickLocation(activity, 5000, ::complete) }
    private fun complete(location: Location?) {
        if (!busy || closed) return
        busy = false; handler.removeCallbacks(timeout); request?.cancel(); request = null
        result(location)
    }
    fun close() { closed = true; busy = false; handler.removeCallbacksAndMessages(null); request?.cancel() }
    companion object { const val PERMISSION = 40; const val RESOLUTION = 41 }
}
