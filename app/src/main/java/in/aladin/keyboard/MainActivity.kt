package `in`.aladin.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("aladin_local_brain", Context.MODE_PRIVATE) }
    private val learningDb by lazy { AladinLearningDb(this) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(s: String, size: Float, color: Int = Color.WHITE) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            setLineSpacing(dp(2).toFloat(), 1.04f)
            setPadding(0, dp(4), 0, dp(6))
        }
    private fun button(s: String, action: () -> Unit) =
        Button(this).apply {
            text = s
            isAllCaps = false
            textSize = 14.5f
            minHeight = dp(48)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextColor(if (prefs.getString("keyboard_theme", "midnight") in listOf("paper", "korean")) Color.rgb(48, 40, 65) else Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(themeSurface())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), themeAccent())
            }
            setOnClickListener { action() }
        }
    private lateinit var learningCheck: CheckBox
    private val createExport = 4102
    private val syncToken = "AladinLocalSync-v1-change-this"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        migrateLearningToDatabase()
        if (!prefs.getBoolean("welcome_seen", false)) {
            showWelcomeScreen()
        } else if (!prefs.getBoolean("consent_screen_seen", false)) {
            showConsentScreen()
        } else {
            showMainScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        // When cloud sync has been explicitly enabled, sync automatically on app open/resume.
        if (prefs.getBoolean(SupabaseLearningSync.PREF_ENABLED, false)) {
            SupabaseLearningSync(this, learningDb).sync()
        }
    }

    private fun setPage(page: LinearLayout) {
        // Keep forms readable on tablets and landscape screens while using the full
        // available width on compact phones.
        val contentWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(260)).coerceAtMost(dp(720))
        page.layoutParams = android.widget.FrameLayout.LayoutParams(contentWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.CENTER
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(12), 0, dp(18))
            setBackgroundColor(themeBackground())
            addView(page)
        }
        window.statusBarColor = themeBackground()
        window.navigationBarColor = themeBackground()
        window.decorView.systemUiVisibility =
            if (prefs.getString("keyboard_theme", "midnight") == "paper") android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        setContentView(scroll)
    }

    private fun themeSurface(): Int = when (prefs.getString("keyboard_theme", "midnight")) {
        "oasis" -> Color.rgb(25, 62, 55)
        "royal" -> Color.rgb(47, 34, 75)
        "paper" -> Color.rgb(255, 255, 255)
        "korean" -> Color.rgb(255, 249, 251)
        else -> Color.rgb(21, 29, 47)
    }

    private fun basePage(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(22))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(themeSurface())
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), if (prefs.getString("keyboard_theme", "midnight") == "paper") Color.rgb(224, 229, 239) else Color.rgb(55, 67, 95))
        }
    }

    private fun themeBackground(): Int = when (prefs.getString("keyboard_theme", "midnight")) {
        "oasis" -> Color.rgb(18, 48, 43)
        "royal" -> Color.rgb(37, 25, 64)
        "paper" -> Color.rgb(239, 242, 248)
        "korean" -> Color.rgb(255, 241, 246)
        else -> Color.rgb(12, 18, 32)
    }

    private fun showWelcomeScreen() {
        val page = basePage()
        page.addView(text("✦ ALADIN WORLD ✦", 28f, themeAccent()))
        page.addView(text("A little magic for every word.", 18f, if (prefs.getString("keyboard_theme", "midnight") == "paper") Color.DKGRAY else Color.LTGRAY))
        page.addView(text(
            "\nEk din aapko ek naya keyboard mila…\n\n" +
            "Yeh sirf keys ka board nahi. Aladin aapke roz ke words ko aasaan banane, " +
            "aapki language mein type karne aur dheere-dheere aapki pasand ke suggestions dene ke liye bana hai.\n\n" +
            "Aap iski duniya ko apni theme se saja sakte hain. Aapki privacy aur aapke faisle sabse pehle hain.",
            17f, if (prefs.getString("keyboard_theme", "midnight") == "paper") Color.DKGRAY else Color.WHITE
        ))
        page.addView(text("Choose your world", 18f, themeAccent()))
        page.addView(button("🌌 Midnight Magic") { saveTheme("midnight"); showWelcomeScreen() })
        page.addView(button("🌿 Oasis Green") { saveTheme("oasis"); showWelcomeScreen() })
        page.addView(button("👑 Royal Violet") { saveTheme("royal"); showWelcomeScreen() })
        page.addView(button("☀️ Paper Light") { saveTheme("paper"); showWelcomeScreen() })
        page.addView(button("🌸 Korean Cafe") { saveTheme("korean"); showWelcomeScreen() })
        page.addView(button("Begin the journey →") {
            prefs.edit().putBoolean("welcome_seen", true).apply()
            if (!prefs.getBoolean("consent_screen_seen", false)) showConsentScreen() else showMainScreen()
        })
        setPage(page)
    }

    private fun themeAccent(): Int = when (prefs.getString("keyboard_theme", "midnight")) {
        "oasis" -> Color.rgb(88, 220, 168)
        "royal" -> Color.rgb(190, 148, 255)
        "paper" -> Color.rgb(52, 80, 160)
        "korean" -> Color.rgb(222, 74, 127)
        else -> Color.rgb(117, 143, 255)
    }

    private fun saveTheme(theme: String) {
        prefs.edit().putString("keyboard_theme", theme).apply()
    }

    private fun consentCheck(label: String, key: String, defaultValue: Boolean = false) =
        CheckBox(this).apply {
            text = label
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(key, defaultValue)
        }

    private fun showConsentScreen() {
        val page = basePage()
        page.addView(text("ALADIN 1.0", 27f, Color.rgb(117, 143, 255)))
        page.addView(text("Smart Keyboard · Responsible AI", 15f, Color.LTGRAY))
        page.addView(text(
            "Choose what you allow. Each choice is optional. The basic keyboard remains available if you say no.",
            16f, Color.WHITE
        ))
        val ai = consentCheck("AI local learning: learn word frequency and simple word pairs on this device", "learning_enabled")
        val analytics = consentCheck("Share anonymous usage counts with Aladin admin (device ID and activity times only; never typed text)", "analytics_consent")
        val cloudLearning = consentCheck("Sync learned word and word-pair COUNTS to Aladin cloud (Supabase). This is optional; no typed sentences or raw keystrokes are uploaded.", SupabaseLearningSync.PREF_ENABLED)
        val ads = consentCheck("Allow advertising to support this project (ad delivery is not active in this build)", "ads_consent")
        val impact = consentCheck("Receive information about environmental and social-impact initiatives", "impact_consent")
        page.addView(ai)
        page.addView(analytics)
        page.addView(cloudLearning)
        page.addView(ads)
        page.addView(impact)
        page.addView(text(
            "Privacy promise: no passwords or raw keystrokes are sent. Anonymous usage reporting is not connected yet in this version. You can change these choices later in settings.",
            13f, Color.LTGRAY
        ))
        page.addView(button("Save choices and continue") {
            prefs.edit()
                .putBoolean("learning_enabled", ai.isChecked)
                .putBoolean("analytics_consent", analytics.isChecked)
                .putBoolean(SupabaseLearningSync.PREF_ENABLED, cloudLearning.isChecked)
                .putBoolean("ads_consent", ads.isChecked)
                .putBoolean("impact_consent", impact.isChecked)
                .putBoolean("consent_screen_seen", true)
                .apply()
            showMainScreen()
        })
        page.addView(button("Reject optional choices and continue") {
            prefs.edit()
                .putBoolean("learning_enabled", false)
                .putBoolean("analytics_consent", false)
                .putBoolean(SupabaseLearningSync.PREF_ENABLED, false)
                .putBoolean("ads_consent", false)
                .putBoolean("impact_consent", false)
                .putBoolean("consent_screen_seen", true)
                .apply()
            showMainScreen()
        })
        setPage(page)
    }

    private fun showMainScreen() {
        val page = basePage()
        page.addView(text("ALADIN KEYBOARD", 25f, themeAccent()))
        page.addView(text("Local-first keyboard and learning controls", 15f, if (prefs.getString("keyboard_theme", "midnight") == "paper") Color.DKGRAY else Color.LTGRAY))
        page.addView(text("KEYBOARD THEMES", 18f, themeAccent()))
        page.addView(button("🌌 Midnight Magic") { saveTheme("midnight"); showMainScreen(); Toast.makeText(this, "Theme applied", Toast.LENGTH_SHORT).show() })
        page.addView(button("🌿 Oasis Green") { saveTheme("oasis"); showMainScreen(); Toast.makeText(this, "Theme applied", Toast.LENGTH_SHORT).show() })
        page.addView(button("👑 Royal Violet") { saveTheme("royal"); showMainScreen(); Toast.makeText(this, "Theme applied", Toast.LENGTH_SHORT).show() })
        page.addView(button("☀️ Paper Light") { saveTheme("paper"); showMainScreen(); Toast.makeText(this, "Theme applied", Toast.LENGTH_SHORT).show() })
        page.addView(button("🌸 Korean Cafe") { saveTheme("korean"); showMainScreen(); Toast.makeText(this, "Korean style applied", Toast.LENGTH_SHORT).show() })
        page.addView(text("LANGUAGE", 18f, themeAccent()))
        page.addView(text("Current keyboard language: ${prefs.getString("keyboard_language", "English")}", 14f, Color.LTGRAY))
        page.addView(button("🌐 Set English") { prefs.edit().putString("keyboard_language", "English").apply(); showMainScreen() })
        page.addView(button("🌐 Set Hindi (हिन्दी)") { prefs.edit().putString("keyboard_language", "हिन्दी").apply(); showMainScreen() })
        page.addView(button("🌐 Set Korean (한국어)") { prefs.edit().putString("keyboard_language", "한국어").apply(); showMainScreen() })
        page.addView(button("🌐 Set Spanish (Español)") { prefs.edit().putString("keyboard_language", "Español").apply(); showMainScreen() })
        page.addView(button("🌐 Set French (Français)") { prefs.edit().putString("keyboard_language", "Français").apply(); showMainScreen() })
        page.addView(button("🌐 Set Arabic (العربية)") { prefs.edit().putString("keyboard_language", "العربية").apply(); showMainScreen() })
        page.addView(button("🌐 Set Japanese (日本語)") { prefs.edit().putString("keyboard_language", "日本語").apply(); showMainScreen() })
        page.addView(button("🌐 Set Chinese (中文)") { prefs.edit().putString("keyboard_language", "中文").apply(); showMainScreen() })
        page.addView(text("Voice typing: tap the 🎙 key; your device speech-recognition service handles audio. Availability and offline support depend on Android speech services.", 13f, Color.LTGRAY))
        page.addView(button("1. Enable Aladin Keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        page.addView(button("2. Choose Aladin Keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showInputMethodPicker()
        })
        page.addView(text("YOUR DATA, YOUR CHOICE", 18f, Color.rgb(108, 224, 192)))
        learningCheck = CheckBox(this).apply {
            text = "Allow local learning from committed words and word pairs"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("learning_enabled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("learning_enabled", checked).apply()
                Toast.makeText(this@MainActivity,
                    if (checked) "Local learning enabled" else "Local learning paused",
                    Toast.LENGTH_SHORT).show()
            }
        }
        page.addView(learningCheck)
        page.addView(text(
            "Local learning uses word frequency and simple word pairs. No raw keystroke stream is uploaded. Password fields are excluded.",
            13f, Color.LTGRAY
        ))
        page.addView(button("Change privacy and consent choices") { showConsentScreen() })
        page.addView(text("ALADIN CLOUD SYNC (internet)", 18f, themeAccent()))
        page.addView(text("Optional HTTPS sync with your Aladin Supabase account. Only learned word counts and word-pair counts sync; raw keystrokes, typed sentences, and passwords are not uploaded.", 13f, Color.LTGRAY))
        val cloudSync = CheckBox(this).apply {
            text = "Automatically sync learning counts with Aladin cloud"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(SupabaseLearningSync.PREF_ENABLED, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(SupabaseLearningSync.PREF_ENABLED, checked).apply()
                if (checked) SupabaseLearningSync(this@MainActivity, learningDb).sync(true) { message -> runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() } }
                else Toast.makeText(this@MainActivity, "Aladin cloud sync paused", Toast.LENGTH_SHORT).show()
            }
        }
        page.addView(cloudSync)
        page.addView(button("Sync to/from Aladin cloud now") {
            if (!prefs.getBoolean(SupabaseLearningSync.PREF_ENABLED, false)) {
                Toast.makeText(this, "First enable cloud sync above; this is optional", Toast.LENGTH_LONG).show()
            } else SupabaseLearningSync(this, learningDb).sync(true) { message -> runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() } }
        })
        page.addView(text("Cloud sync status: ${prefs.getString(SupabaseLearningSync.PREF_STATUS, "Not synced yet")}", 12f, Color.LTGRAY))
        page.addView(text("PC DATABASE SYNC (same Wi-Fi)", 18f, themeAccent()))
        page.addView(text("Only learned word counts and word-pair counts sync. Raw keystrokes, typed sentences and passwords are never sent. PC sync works when both devices are on the same Wi-Fi and the PC receiver is running.", 13f, Color.LTGRAY))
        val autoSync = CheckBox(this).apply {
            text = "Automatically sync learning counts to my PC"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("pc_sync_enabled", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("pc_sync_enabled", checked).apply(); Toast.makeText(this@MainActivity, if (checked) "Automatic PC sync enabled" else "Automatic PC sync paused", Toast.LENGTH_SHORT).show() }
        }
        page.addView(autoSync)
        page.addView(text("Last sync: ${prefs.getString("pc_sync_last_message", "Not synced yet")}", 12f, Color.LTGRAY))
        val pcAddress = EditText(this).apply {
            hint = "PC local IP, e.g. 192.168.1.20"
            setSingleLine(true)
            setText(prefs.getString("sync_pc_address", "192.168.29.145") ?: "192.168.29.145")
            setTextColor(if (prefs.getString("keyboard_theme", "midnight") == "paper") Color.DKGRAY else Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        page.addView(pcAddress, LinearLayout.LayoutParams(-1, dp(52)))
        page.addView(button("Sync learning database to PC") {
            val host = pcAddress.text.toString().trim().removePrefix("http://").removePrefix("https://").substringBefore("/").substringBefore(":8768")
            if (host.isBlank()) {
                Toast.makeText(this, "PC ka local IP address enter karo", Toast.LENGTH_LONG).show()
            } else {
                prefs.edit().putString("sync_pc_address", host).apply()
                syncLearningToPc(host)
            }
        })
        page.addView(button("Export learning data (JSON)") { exportLearningData() })
        page.addView(button("Clear all local learning data") { clearLearningData() })
        page.addView(text("Try typing", 18f))
        page.addView(EditText(this).apply {
            hint = "Test the keyboard here"
            textSize = 18f
            minLines = 2
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }, LinearLayout.LayoutParams(-1, dp(90)))
        page.addView(text(
            "This early version learns word frequency and simple word pairs. It is not yet a full language model.",
            13f, Color.LTGRAY
        ))
        page.addView(button("Keyboard settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        setPage(page)
    }

    private fun migrateLearningToDatabase() {
        if (prefs.getBoolean("learning_db_migrated_v1", false)) return
        (prefs.getString("words", "") ?: "").lineSequence().forEach { line ->
            val p = line.split('\t')
            if (p.size == 2 && p[0].isNotBlank()) learningDb.setWordCount(p[0], p[1].toIntOrNull() ?: 1)
        }
        (prefs.getString("pairs", "") ?: "").lineSequence().forEach { line ->
            val p = line.split('\t')
            if (p.size == 2 && p[0].isNotBlank()) {
                val pair = p[0].split('|')
                if (pair.size == 2) learningDb.setPairCount(pair[0], pair[1], p[1].toIntOrNull() ?: 1)
            }
        }
        prefs.edit().putBoolean("learning_db_migrated_v1", true).apply()
    }

    private fun syncLearningToPc(host: String) {
        val payload = learningDb.toSyncJson()
        payload.put("token", syncToken)
        payload.put("device_id", prefs.getString("sync_device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("sync_device_id", it).apply()
        })
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("http://$host:8768/api/keyboard-sync").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 12000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
                val result = JSONObject(response)
                runOnUiThread {
                    if (code in 200..299 && result.optBoolean("ok")) {
                        Toast.makeText(this, "Sync complete: ${result.optInt("words_received")} words, ${result.optInt("pairs_received")} pairs sent to PC database", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Sync failed ($code): ${result.optString("error", "Check PC server/IP and Wi-Fi")}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Sync failed: ${e.message ?: "PC not reachable"}. Same Wi-Fi aur PC firewall check karo.", Toast.LENGTH_LONG).show() }
            } finally { connection?.disconnect() }
        }.start()
    }

    private fun exportLearningData() {
        val json = JSONObject()
        json.put("format", "aladin-keyboard-learning-v1")
        json.put("exported_at_utc", java.time.Instant.now().toString())
        json.put("learning_enabled", prefs.getBoolean("learning_enabled", false))
        val words = JSONArray()
        (prefs.getString("words", "") ?: "").lineSequence().forEach { line ->
            val p = line.split("\\t")
            if (p.size == 2 && p[0].isNotBlank()) {
                words.put(JSONObject().put("word", p[0]).put("count", p[1].toIntOrNull() ?: 1))
            }
        }
        val pairs = JSONArray()
        (prefs.getString("pairs", "") ?: "").lineSequence().forEach { line ->
            val p = line.split("\\t")
            if (p.size == 2 && p[0].isNotBlank()) {
                val pair = p[0].split("|")
                if (pair.size == 2) pairs.put(JSONObject()
                    .put("previous_word", pair[0]).put("next_word", pair[1])
                    .put("count", p[1].toIntOrNull() ?: 1))
            }
        }
        json.put("word_counts", words)
        json.put("word_pairs", pairs)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "aladin-learning-data.json")
        }
        pendingExport = json.toString(2)
        startActivityForResult(intent, createExport)
    }

    private var pendingExport: String = ""

    @Deprecated("Uses document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == createExport && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(pendingExport)
                    }
                    Toast.makeText(this, "Learning data exported to chosen location", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearLearningData() {
        prefs.edit().remove("words").remove("pairs").putBoolean("learning_db_migrated_v1", true).apply()
        learningDb.clearLearning()
        Toast.makeText(this, "Local learning database cleared", Toast.LENGTH_LONG).show()
    }
}
