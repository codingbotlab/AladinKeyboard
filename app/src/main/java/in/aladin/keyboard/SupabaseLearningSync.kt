package `in`.aladin.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Opt-in cloud sync for aggregate word-frequency and word-pair counts only.
 * Never receives the raw keyboard event stream or typed sentences.
 */
class SupabaseLearningSync(private val context: Context, private val db: AladinLearningDb) {
    companion object {
        const val PREF_ENABLED = "supabase_learning_sync_enabled"
        const val PREF_STATUS = "supabase_learning_sync_status"
        const val PREF_LAST_AT = "supabase_learning_sync_last_at"
        private const val BASE = "https://yzqtavvsyjdxmovrgpyl.supabase.co"
        private const val API_KEY = "sb_publishable_QOywgkhHJH5wn56ZdGEGUQ_F4ygfJA3"
        private const val PREFS = "aladin_local_brain"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun sync(force: Boolean = false, finished: ((String) -> Unit)? = null) {
        if (!prefs.getBoolean(PREF_ENABLED, false)) {
            finished?.invoke("Cloud sync is off")
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(PREF_LAST_AT, 0L) < 15 * 60 * 1000L) {
            finished?.invoke(prefs.getString(PREF_STATUS, "Sync recently checked") ?: "Sync recently checked")
            return
        }
        Thread {
            val message = try {
                performSync()
            } catch (e: Exception) {
                "Cloud sync pending: ${e.message ?: "network or authentication error"}"
            }
            prefs.edit().putString(PREF_STATUS, message).putLong(PREF_LAST_AT, System.currentTimeMillis()).apply()
            finished?.invoke(message)
        }.start()
    }

    private fun performSync(): String {
        var token = prefs.getString("sb_access_token", null)
        val expires = prefs.getLong("sb_token_expires_at", 0L)
        if (token.isNullOrBlank() || expires < System.currentTimeMillis() + 60_000L) {
            token = refreshOrCreateSession()
        }
        val userId = prefs.getString("sb_user_id", null) ?: error("No cloud account; retry sync")
        val headers = mapOf("Authorization" to "Bearer $token")

        // Explicitly record opt-in before writing learning data. RLS rejects writes without this row.
        val consent = JSONObject()
            .put("user_id", userId)
            .put("opted_in", true)
            .put("consent_version", "aladin-keyboard-cloud-learning-v1")
        request("POST", "/rest/v1/keyboard_sync_consent?on_conflict=user_id", consent.toString(),
            headers + mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))

        val remote = request("GET",
            "/rest/v1/keyboard_learning_items?select=item_type,item_value,language_code,occurrence_count&order=updated_at.desc",
            null, headers)
        val remoteRows = JSONArray(remote)
        val merged = LinkedHashMap<String, JSONObject>()
        for (i in 0 until remoteRows.length()) {
            val row = remoteRows.getJSONObject(i)
            val key = key(row.optString("item_type"), row.optString("item_value"), row.optString("language_code", "und"))
            merged[key] = JSONObject(row.toString())
        }

        val local = db.toSyncJson()
        val words = local.optJSONArray("word_counts") ?: JSONArray()
        for (i in 0 until words.length()) {
            val item = words.getJSONObject(i)
            val value = item.optString("word")
            if (value.isBlank() || value.length > 500) continue
            merge(merged, "word", value, "und", item.optInt("count", 1))
        }
        val pairs = local.optJSONArray("word_pairs") ?: JSONArray()
        for (i in 0 until pairs.length()) {
            val item = pairs.getJSONObject(i)
            val previous = item.optString("previous_word")
            val next = item.optString("next_word")
            if (previous.isBlank() || next.isBlank()) continue
            val value = JSONObject().put("previous_word", previous).put("next_word", next).toString()
            merge(merged, "pair", value, "und", item.optInt("count", 1))
        }

        // Upsert merged maxima (not sums), so repeated syncs do not inflate counts.
        val upload = JSONArray()
        merged.values.forEach { row ->
            upload.put(JSONObject()
                .put("user_id", userId)
                .put("item_type", row.getString("item_type"))
                .put("item_value", row.getString("item_value"))
                .put("language_code", row.optString("language_code", "und"))
                .put("occurrence_count", row.optInt("occurrence_count", 1).coerceIn(1, 1_000_000)))
        }
        if (upload.length() > 0) {
            request("POST",
                "/rest/v1/keyboard_learning_items?on_conflict=user_id,item_type,item_value,language_code",
                upload.toString(), headers + mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
        }

        // Restore cloud counts to the local learning DB.
        merged.values.forEach { row ->
            val count = row.optInt("occurrence_count", 1).coerceAtLeast(1)
            when (row.optString("item_type")) {
                "word" -> db.setWordCount(row.optString("item_value"), count)
                "pair" -> try {
                    val pair = JSONObject(row.optString("item_value"))
                    db.setPairCount(pair.optString("previous_word"), pair.optString("next_word"), count)
                } catch (_: Exception) { /* Ignore malformed legacy pair rows. */ }
            }
        }
        return "Cloud sync complete · ${words.length()} local words / ${pairs.length()} local pairs checked · ${merged.size} cloud items"
    }

    private fun merge(target: MutableMap<String, JSONObject>, type: String, value: String, language: String, count: Int) {
        val k = key(type, value, language)
        val old = target[k]
        if (old == null) {
            target[k] = JSONObject()
                .put("item_type", type).put("item_value", value)
                .put("language_code", language).put("occurrence_count", count.coerceIn(1, 1_000_000))
        } else {
            old.put("occurrence_count", maxOf(old.optInt("occurrence_count", 1), count).coerceIn(1, 1_000_000))
        }
    }

    private fun key(type: String, value: String, language: String) = "$type\u0000$language\u0000$value"

    private fun refreshOrCreateSession(): String {
        val refresh = prefs.getString("sb_refresh_token", null)
        val response = if (!refresh.isNullOrBlank()) {
            try {
                request("POST", "/auth/v1/token?grant_type=refresh_token",
                    JSONObject().put("refresh_token", refresh).toString(), emptyMap())
            } catch (_: Exception) { createAnonymousSession() }
        } else createAnonymousSession()
        val json = JSONObject(response)
        val access = json.optString("access_token")
        if (access.isBlank()) error("Supabase sign-in failed. Check Anonymous Sign-Ins is enabled in Supabase Auth settings.")
        val user = json.optJSONObject("user") ?: error("Supabase returned no user")
        val id = user.optString("id")
        if (id.isBlank()) error("Supabase returned no user ID")
        prefs.edit()
            .putString("sb_access_token", access)
            .putString("sb_refresh_token", json.optString("refresh_token"))
            .putString("sb_user_id", id)
            .putLong("sb_token_expires_at", System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
            .apply()
        return access
    }

    private fun createAnonymousSession(): String {
        return request("POST", "/auth/v1/signup", JSONObject().put("data", JSONObject().put("app", "aladin-keyboard")).toString(), emptyMap())
    }

    private fun request(method: String, path: String, body: String?, extraHeaders: Map<String, String>): String {
        val connection = (URL(BASE + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", API_KEY)
            connection.setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val safeMessage = try { JSONObject(response).optString("msg").ifBlank { JSONObject(response).optString("message") } } catch (_: Exception) { "" }
                error("HTTP $code ${safeMessage.ifBlank { "Supabase request rejected" }}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
