package `in`.aladin.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

class VoiceTypingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 72)
        } else {
            launchRecognition()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 72 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) launchRecognition()
        else { Toast.makeText(this, "Voice typing needs microphone permission", Toast.LENGTH_LONG).show(); finish() }
    }

    private fun launchRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, when (getSharedPreferences("aladin_local_brain", MODE_PRIVATE).getString("keyboard_language", "English")) {
                    "हिन्दी" -> "hi-IN"
                    "한국어" -> "ko-KR"
                    "Español" -> "es-ES"
                    "Français" -> "fr-FR"
                    "العربية" -> "ar-SA"
                    else -> "en-IN"
                })
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to type with Aladin")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            startActivityForResult(intent, 73)
        } catch (_: Exception) {
            Toast.makeText(this, "Speech recognition is not available on this device", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 73 && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                sendBroadcast(Intent("in.aladin.keyboard.VOICE_RESULT").setPackage(packageName).putExtra("recognized_text", text))
            }
        }
        finish()
    }
}
