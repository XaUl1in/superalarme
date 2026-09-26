package br.com.alarmehardcore

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SyncProtocol {
    fun isLocalAddress(value: String): Boolean {
        if (!value.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) return false
        val p = value.split('.').map { it.toInt() }
        if (p.any { it !in 0..255 }) return false
        if (p.joinToString(".") != value) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }
    fun signature(key: ByteArray, body: ByteArray): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal("streak-v1\n".toByteArray(Charsets.UTF_8) + body).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

object StreakSync {
    private val executor = Executors.newSingleThreadExecutor()
    // Called from main-thread state transitions: capture a coherent snapshot before enqueueing IO.
    fun send(context: Context) {
        val app = context.applicationContext
        val prefs = AlarmState(app).prefs
        val address = prefs.getString("pcAddress", "")!!
        if (!SyncProtocol.isLocalAddress(address)) return
        val key = runCatching { TimePin.decode(AlarmState(app).secret) }.getOrNull() ?: return
        val progress = HabitStore(app).progress()
        val revision = maxOf(System.currentTimeMillis(), prefs.getLong("syncRevision", 0) + 1)
        prefs.edit().putLong("syncRevision", revision).putString("syncStatus", "Envio pendente…").apply()
        val count = progress.visibleCount(LocalDate.now().toEpochDay())
        executor.execute {
            val body = JSONObject().put("streak", count).put("best", progress.best).put("revision", revision)
                .put("sentAt", System.currentTimeMillis() / 1000).toString().toByteArray(Charsets.UTF_8)
            val result = runCatching {
                val connection = URL("http://$address:8080/streak").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.connectTimeout = 2000; connection.readTimeout = 2000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("X-Alarm-Signature", SyncProtocol.signature(key, body))
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                    check(connection.responseCode == 200) { "PC recusou o envio (${connection.responseCode}). Confira a chave e os relógios." }
                } finally { connection.disconnect() }
            }
            prefs.edit().putString("syncStatus", if (result.isSuccess) "Ofensiva sincronizada com $address."
                else "Sem sincronização: confira IP, rede, firewall e programa do PC. O alarme continua funcionando.").apply()
        }
    }
}
