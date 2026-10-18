package `in`.aladin.keyboard

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object PcLearningSync {
    private const val TOKEN = "AladinLocalSync-v1-change-this"
    private const val PORT = 8767

    fun sync(context: Context, callback: ((Boolean, String) -> Unit)? = null) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("aladin_local_brain", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pc_sync_enabled", true)) {
            callback?.invoke(false, "PC sync paused")
            return
        }
        val host = (prefs.getString("sync_pc_address", "192.168.29.145") ?: "192.168.29.145")
            .trim().removePrefix("http://").removePrefix("https://")
            .substringBefore("/").substringBefore(":")
        if (host.isBlank()) {
            callback?.invoke(false, "Set PC local IP in Aladin settings")
            return
        }
        val deviceId = prefs.getString("sync_device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("sync_device_id", it).apply()
        }
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val payload = AladinLearningDb(app).toSyncJson()
                    .put("token", TOKEN).put("device_id", deviceId)
                conn = (URL("http://$host:$PORT/api/keyboard-sync").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 2500
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
                val result = JSONObject(response)
                val ok = code in 200..299 && result.optBoolean("ok")
                prefs.edit()
                    .putLong("pc_sync_last_attempt", System.currentTimeMillis())
                    .putBoolean("pc_sync_last_ok", ok)
                    .putString("pc_sync_last_message", if (ok) "Synced " + result.optInt("words_received") + " words / " + result.optInt("pairs_received") + " pairs" else result.optString("error", "HTTP $code"))
                    .apply()
                callback?.invoke(ok, prefs.getString("pc_sync_last_message", "") ?: "")
            } catch (e: Exception) {
                prefs.edit().putLong("pc_sync_last_attempt", System.currentTimeMillis())
                    .putBoolean("pc_sync_last_ok", false)
                    .putString("pc_sync_last_message", e.message ?: "PC offline")
                    .apply()
                callback?.invoke(false, e.message ?: "PC offline")
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
}
