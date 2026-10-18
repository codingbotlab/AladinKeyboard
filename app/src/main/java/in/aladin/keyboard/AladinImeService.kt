package `in`.aladin.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.HapticFeedbackConstants
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import java.text.Normalizer
import java.util.Locale

class AladinImeService : InputMethodService() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var learningDb: AladinLearningDb
    private val bg: Int get() = when (prefs.getString("keyboard_theme", "midnight")) {
        "oasis" -> Color.rgb(18, 48, 43)
        "royal" -> Color.rgb(37, 25, 64)
        "paper" -> Color.rgb(239, 242, 248)
        "korean" -> Color.rgb(255, 241, 246)
        else -> Color.rgb(22, 29, 44)
    }
    private val key: Int get() = when (prefs.getString("keyboard_theme", "midnight")) {
        "oasis" -> Color.rgb(35, 79, 68)
        "royal" -> Color.rgb(67, 48, 96)
        "paper" -> Color.rgb(220, 226, 238)
        "korean" -> Color.rgb(255, 255, 255)
        else -> Color.rgb(43, 52, 72)
    }
    private val accent: Int get() = when (prefs.getString("keyboard_theme", "midnight")) {
        "oasis" -> Color.rgb(44, 145, 111)
        "royal" -> Color.rgb(124, 83, 183)
        "paper" -> Color.rgb(176, 194, 235)
        "korean" -> Color.rgb(255, 143, 177)
        else -> Color.rgb(113, 139, 255)
    }
    private lateinit var root: LinearLayout
    private lateinit var suggestions: LinearLayout
    private var symbols=false
    private var mediaMode = ""
    private var secureField=false
    private var composing=""
    private val syncHandler = Handler(Looper.getMainLooper())
    private val syncRunnable = Runnable { if (::prefs.isInitialized && prefs.getBoolean("learning_enabled", false) && prefs.getBoolean("pc_sync_enabled", true)) PcLearningSync.sync(this) }
    private var lastWord=""
    private val learned=linkedMapOf<String,Int>()
    private val pairs=linkedMapOf<String,Int>()
    private val neuralPredictor = LocalNeuralPredictor()
    private val selfAttention = LocalSelfAttention()
    private val recentContext = mutableListOf<String>()
    private val languages = listOf("English", "हिन्दी", "한국어", "Español", "Français", "العربية", "日本語", "中文")
    private var activeLanguage = "English"
    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val spoken = intent?.getStringExtra("recognized_text")?.trim().orEmpty()
            if (spoken.isNotEmpty() && !secureField) {
                currentInputConnection?.finishComposingText()
                currentInputConnection?.commitText(spoken + " ", 1)
                composing = ""
                lastWord = norm(spoken.substringAfterLast(" "))
                recentContext.add(lastWord)
                if (recentContext.size > 8) recentContext.removeAt(0)
                renderSuggestions()
            }
        }
    }
    private val builtInWords = listOf(
        "hello","hi","how","are","you","bhai","kya","hal","hai","main","mai","kaise","ho","theek","accha","achha",
        "I","am","is","are","the","a","an","and","to","for","of","in","on","with","my","your","we","they","this","that",
        "what","when","where","why","please","thank","thanks","good","morning","today","tomorrow","help","love","friend",
        "नमस्ते","हैलो","कैसे","हो","हैं","हूँ","भाई","क्या","हाल","अच्छा","ठीक","धन्यवाद","आप","मैं","मेरा","आपका","मदद","करो",
        "안녕","안녕하세요","감사합니다","사랑","친구","좋아요","네","아니요","오늘","내일","어떻게","무엇",
        "bro","brother","yaar","yar","mera","meri","mere","tum","tumhara","tumhari","aap","apka","apni","hum","ham",
        "kya","kyu","kyun","kaise","kaisa","kaisi","hai","hain","hu","ho","tha","thi","the","kar","karo","karna",
        "batao","bolo","suno","dekho","chalo","acha","achha","accha","thik","theek","nahi","nahin","haan","han",
        "mujhe","mujhko","tumhe","tumko","aaj","kal","abhi","baad","mein","me","se","ko","par","pe","ke","ki","ka",
        "pyaar","pyar","dost","zindagi","kaam","ghar","paani","khana","chai","mast","badhiya","shukriya",
        "hola","gracias","por","favor","amigo","bonjour","merci","salut","oui","non",
        "こんにちは","ありがとう","はい","いいえ","你好","谢谢","是","不是","مرحبا","شكرا","نعم","لا"
    )
    private val hindiTransliteration = mapOf(
        "namaste" to "नमस्ते", "kaise" to "कैसे", "bhai" to "भाई", "kya" to "क्या",
        "hai" to "है", "hain" to "हैं", "achha" to "अच्छा", "theek" to "ठीक",
        "dhanyavaad" to "धन्यवाद", "aap" to "आप", "main" to "मैं", "madad" to "मदद",
        "karo" to "करो", "mera" to "मेरा", "aapka" to "आपका"
    )
    private val nextWordSeeds = mapOf(
        "hello" to listOf("there","bhai","everyone"), "hi" to listOf("there","bhai"),
        "how" to listOf("are","is"), "are" to listOf("you","we","they"),
        "you" to listOf("are","can","have"), "kya" to listOf("hal","hai","kar"),
        "bhai" to listOf("kya","kaise","hello"), "kaise" to listOf("ho","hai"),
        "good" to listOf("morning","day","to"), "thank" to listOf("you"),
        "नमस्ते" to listOf("आप","कैसे"), "कैसे" to listOf("हो","हैं"),
        "안녕" to listOf("하세요"), "안녕하세요" to listOf("반가워요","친구"),
        "감사합니다" to listOf("정말","친구"),
        "hello" to listOf("bhai","there","kaise"), "kya" to listOf("haal","hal","kar","hua"),
        "kya haal" to listOf("hai","hain"), "kaise" to listOf("ho","hain"), "aap" to listOf("kaise","ho"),
        "tum" to listOf("kaise","kya","ho"), "mujhe" to listOf("help","batao","chahiye"),
        "mai" to listOf("theek","hoon","kar","ja","raha"), "main" to listOf("theek","hoon","kar","raha"),
        "good" to listOf("morning","night","hai"), "how" to listOf("are","is"),
        "hola" to listOf("amigo","como"), "bonjour" to listOf("tout","merci"),
        "こんにちは" to listOf("ありがとう","元気"), "你好" to listOf("谢谢","你好")
    )
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun norm(s:String)=Normalizer.normalize(s.lowercase(Locale.ROOT),Normalizer.Form.NFC).trim()
    private fun switchLanguage() {
        val index = languages.indexOf(activeLanguage).coerceAtLeast(0)
        activeLanguage = languages[(index + 1) % languages.size]
        prefs.edit().putString("keyboard_language", activeLanguage).apply()
        if (::root.isInitialized) setInputView(onCreateInputView())
    }

    override fun onCreate(){
        super.onCreate()
        prefs=getSharedPreferences("aladin_local_brain",Context.MODE_PRIVATE)
        activeLanguage = prefs.getString("keyboard_language", "English") ?: "English"
        learningDb=AladinLearningDb(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(voiceReceiver, IntentFilter("in.aladin.keyboard.VOICE_RESULT"), Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(voiceReceiver, IntentFilter("in.aladin.keyboard.VOICE_RESULT"))
        }
        loadBrain()
        // Rebuild the lightweight model from saved local learning after service restart.
        neuralPredictor.train(learned.entries.toList().takeLast(256).associate { it.toPair() }, pairs, epochs = 6)
        if (prefs.getBoolean("learning_enabled", false) && prefs.getBoolean("pc_sync_enabled", true)) syncHandler.postDelayed(syncRunnable, 2500)
    }
    override fun onDestroy() {
        syncHandler.removeCallbacks(syncRunnable)
        try { unregisterReceiver(voiceReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
    override fun onStartInput(attribute:EditorInfo?,restarting:Boolean){
        super.onStartInput(attribute,restarting)
        val input=attribute?.inputType?:0
        val cls=input and android.text.InputType.TYPE_MASK_CLASS
        val variation=input and android.text.InputType.TYPE_MASK_VARIATION
        secureField=(cls==android.text.InputType.TYPE_CLASS_TEXT && variation in listOf(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) || (cls==android.text.InputType.TYPE_CLASS_NUMBER && variation==android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        composing=""
        if (secureField) lastWord = ""
    }
    override fun onCreateInputView():View{
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(3),dp(2),dp(3),dp(3));setBackgroundColor(bg)}
        val toolbar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        listOf("☺ Emoji" to "emoji", "GIF" to "gif", "✦ Stickers" to "stickers", "🎙" to "voice").forEach { (label,mode) ->
            val item=TextView(this).apply { text=label; textSize=12f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); background=GradientDrawable().apply { setColor(if(mediaMode==mode) accent else key); cornerRadius=dp(10).toFloat() }; setOnClickListener { if(mode=="voice") startActivity(Intent(this@AladinImeService,VoiceTypingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else { mediaMode=if(mediaMode==mode) "" else mode; setInputView(onCreateInputView()) } } }
            toolbar.addView(item,LinearLayout.LayoutParams(0,dp(30),1f).apply { setMargins(dp(2),0,dp(2),0) })
        }
        root.addView(toolbar,LinearLayout.LayoutParams(-1,dp(32)))
        if(mediaMode.isNotEmpty()) { when(mediaMode) { "emoji" -> renderEmojiPanel(); "stickers" -> renderStickerPanel(); "gif" -> renderGifPanel() }; return root }
        suggestions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        root.addView(suggestions,LinearLayout.LayoutParams(-1,dp(35)))
        renderSuggestions()
        if(!symbols){
            row("1 2 3 4 5 6 7 8 9 0".split(" "))
            if (activeLanguage == "한국어") {
                row("ㅂ ㅈ ㄷ ㄱ ㅅ ㅛ ㅕ ㅑ ㅐ ㅔ".split(" "))
                row("ㅁ ㄴ ㅇ ㄹ ㅎ ㅗ ㅓ ㅏ ㅣ".split(" "))
                row(listOf("⇧","ㅋ","ㅌ","ㅊ","ㅍ","ㅠ","ㅜ","ㅡ","⌫"))
            } else {
                row("q w e r t y u i o p".split(" "))
                row("a s d f g h j k l".split(" "))
                row(listOf("⇧","z","x","c","v","b","n","m","⌫"))
            }
            row(listOf("?123",",","🌐","☺","space",".","↵"))
        }else{
            row("1 2 3 4 5 6 7 8 9 0".split(" "))
            row(listOf("@","#","$","%","&","-","+","(",")","/"))
            row(listOf("*","\"","'",":",";","!","?","⌫"))
            row(listOf("ABC",",","🌐","☺","space",".","↵"))
        }
        return root
    }
    private fun renderEmojiPanel() {
        val tabs=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val groups=listOf(
            "Smileys" to "😀 😃 😄 😁 😆 😅 😂 🤣 🥹 😊 😇 🙂 🙃 😉 😍 🥰 😘 😋 😎 🤓 🤔 🫡 😭 😤 😴 🥳 🤯 😱 🥺 🤗".split(" "),
            "Hands" to "👋 🤚 🖐️ ✋ 👌 🤌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 👍 👎 ✊ 👊 👏 🙌 👐 🤲 🙏 💪".split(" "),
            "Hearts" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟".split(" "),
            "Nature" to "🐶 🐱 🐼 🦊 🐻 🐸 🐵 🦋 🌸 🌹 🌻 🌈 ☀️ 🌙 ⭐ 🔥 🌊 🍀".split(" "),
            "Food" to "🍎 🍕 🍔 🍟 🌮 🍜 🍰 🍩 ☕ 🧋 🍉 🥭 🍓 🍿".split(" ")
        )
        groups.forEach { (name,items) ->
            val tab=TextView(this).apply { text=name; textSize=10f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setPadding(dp(3),dp(5),dp(3),dp(5)); setOnClickListener { renderEmojiGrid(items) } }
            tabs.addView(tab,LinearLayout.LayoutParams(0,dp(28),1f))
        }
        root.addView(tabs)
        renderEmojiGrid(groups[0].second)
    }
    private fun renderEmojiGrid(items:List<String>) {
        if (root.childCount > 2) root.removeViews(2,root.childCount-2)
        val scroll=android.widget.ScrollView(this)
        val grid=android.widget.GridLayout(this).apply { columnCount=8 }
        items.forEach { emoji ->
            val v=TextView(this).apply { text=emoji; textSize=24f; gravity=Gravity.CENTER; setOnClickListener { currentInputConnection?.commitText(emoji,1) } }
            grid.addView(v,android.widget.GridLayout.LayoutParams().apply { width=0; height=dp(42); columnSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f) })
        }
        scroll.addView(grid)
        root.addView(scroll,LinearLayout.LayoutParams(-1,dp(205)))
    }
    private fun renderStickerPanel() {
        val packs=listOf("🥹👉👈","༼ つ ◕_◕ ༽つ","(づ｡◕‿‿◕｡)づ","ʕ•ᴥ•ʔ","(╥﹏╥)","(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧","✨💖✨","🔥💯🔥","🙏❤️🙏","😂🤣😂","🌸😊🌸","🫶🫶🫶")
        val note=TextView(this).apply { text="ALADIN STICKERS · Tap to send"; textSize=12f; setTextColor(Color.LTGRAY); gravity=Gravity.CENTER; setPadding(0,dp(5),0,dp(5)) }
        root.addView(note)
        val grid=android.widget.GridLayout(this).apply { columnCount=3 }
        packs.forEach { sticker -> val v=TextView(this).apply { text=sticker; textSize=18f; gravity=Gravity.CENTER; setPadding(dp(3),dp(8),dp(3),dp(8)); setOnClickListener { currentInputConnection?.commitText(sticker,1) } }; grid.addView(v,android.widget.GridLayout.LayoutParams().apply { width=0; height=dp(48); columnSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f) }) }
        root.addView(grid,LinearLayout.LayoutParams(-1,dp(185)))
    }
    private fun renderGifPanel() {
        val info=TextView(this).apply { text="GIF search requires an active provider. Tenor stopped accepting new API clients and retired its API in June 2026."; textSize=12f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        root.addView(info,LinearLayout.LayoutParams(-1,dp(80)))
        val open=TextView(this).apply { text="Search GIFs online ↗"; textSize=14f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); background=GradientDrawable().apply { setColor(accent); cornerRadius=dp(12).toFloat() }; setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://giphy.com/search/reaction-gifs")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_:Exception) {} } }
        root.addView(open,LinearLayout.LayoutParams(-1,dp(42)).apply { setMargins(dp(28),dp(8),dp(28),0) })
    }

    private fun row(keys:List<String>){
        val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        keys.forEach{ label->
            val special=label in listOf("↵","space","⇧","⌫","?123","ABC","🌐","😊","☺","🎙")
            val b=TextView(this).apply{
                text=label;textSize=if(label.length>1)13f else 19f;setTextColor(if (prefs.getString("keyboard_theme", "midnight") in listOf("paper","korean")) Color.rgb(55,35,45) else Color.WHITE);gravity=Gravity.CENTER
                background=GradientDrawable().apply{setColor(if(special)accent else key);cornerRadius=dp(if (prefs.getString("keyboard_theme", "midnight") == "korean") 18 else 7).toFloat()}
                setPadding(dp(1),dp(3),dp(1),dp(3));isClickable=true;isFocusable=true
                setOnClickListener{perform(label);performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)}
            }
            val weight=if(label=="space")4f else if(special)1.35f else 1f
            line.addView(b,LinearLayout.LayoutParams(0,dp(40),weight).apply{setMargins(dp(2),dp(3),dp(2),dp(3))})
        }
        root.addView(line,LinearLayout.LayoutParams(-1,dp(45)))
    }
    private fun perform(label:String){
        val ic=currentInputConnection?:return
        when(label){
            "⌫"->{if(composing.isNotEmpty()){composing=composing.dropLast(1);ic.setComposingText(composing,1)}else ic.deleteSurroundingText(1,0);renderSuggestions()}
            "⇧"->{}
            "?123"->{symbols=true;setInputView(onCreateInputView())}
            "ABC"->{symbols=false;setInputView(onCreateInputView())}
            "🌐"->{switchLanguage()}
            "☺"->{mediaMode="emoji";setInputView(onCreateInputView())}
            "🎙"->{startActivity(Intent(this, VoiceTypingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
            "space"->{ic.finishComposingText();commitCurrentWord();ic.commitText(" ",1);composing="";renderSuggestions()}
            "↵"->{commitCurrentWord();val action=currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)?:EditorInfo.IME_ACTION_NONE
                if(action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED)ic.performEditorAction(action) else ic.commitText("\n",1);composing="";renderSuggestions()}
            "😊"->ic.commitText("😊",1)
            ",","."->{commitCurrentWord();ic.commitText(label,1);composing="";renderSuggestions()}
            else->{composing+=label;ic.setComposingText(composing,1);renderSuggestions()}
        }
    }
    private fun commitCurrentWord(){
        if (secureField) return
        val word=norm(composing)
        if(word.length<1)return
        // Context tracking works even when optional local learning is switched off.
        if(lastWord.isNotEmpty()&&lastWord!=word){
            val p="$lastWord|$word"
            if (prefs.getBoolean("learning_enabled", false)) {
                pairs[p]=(pairs[p]?:0)+1
                learningDb.incrementPair(lastWord, word)
            }
        }
        if (prefs.getBoolean("learning_enabled", false) && word.length >= 2) {
            learned[word]=(learned[word]?:0)+1
            learningDb.incrementWord(word)
            neuralPredictor.train(learned.entries.toList().takeLast(256).associate { it.toPair() }, pairs, epochs = 2)
            saveBrain()
        }
        lastWord=word
        if (prefs.getBoolean("learning_enabled", false) && prefs.getBoolean("pc_sync_enabled", true)) {
            syncHandler.removeCallbacks(syncRunnable)
            syncHandler.postDelayed(syncRunnable, 4000)
        }
        recentContext.add(word)
        if (recentContext.size > 8) recentContext.removeAt(0)
    }
    private fun renderSuggestions(){
        if(!::suggestions.isInitialized)return
        suggestions.removeAllViews()
        if (secureField) return
        val prefix=norm(composing)
        val maxCount = (learned.values.maxOrNull() ?: 1).coerceAtLeast(1)
        val vocabulary = (builtInWords + learned.keys).distinct()
        val candidates = if (prefix.isBlank()) {
            val observed = pairs.entries.filter { it.key.substringBefore("|") == norm(lastWord) }
                .sortedByDescending { it.value }.map { it.key.substringAfter("|") }
            val seeded = nextWordSeeds[norm(lastWord)].orEmpty()
            (observed + seeded + listOf("hello","नमस्ते","안녕하세요")).distinct().take(3)
        } else {
            val matching = if (activeLanguage == "हिन्दी") {
                hindiTransliteration.filterKeys { it.startsWith(prefix) }.values.toList() +
                    vocabulary.filter { norm(it).startsWith(prefix) && norm(it) != prefix }
            } else {
                vocabulary.filter { norm(it).startsWith(prefix) && norm(it) != prefix }
            }
            matching.distinct().sortedByDescending { candidate ->
                val count = learned[candidate] ?: 0
                val pairBoost = if (lastWord.isBlank()) 0 else (pairs["${norm(lastWord)}|${norm(candidate)}"] ?: 0)
                neuralPredictor.score(candidate, count, maxCount, prefix.length, pairBoost) +
                    selfAttention.contextBoost(norm(candidate), recentContext, pairs)
            }.take(3)
        }
        (candidates+listOf("✓")).distinct().take(4).forEach{word->
            val t=TextView(this).apply{
                text=word;textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setPadding(dp(8),dp(5),dp(8),dp(5))
                setOnClickListener{
                    if(word=="🌐"){ switchLanguage() }
                    else if(word!="✓" && word!="⋯" && word!="Aladin"){
                        // A prediction completes the current token, or inserts a next-word suggestion.
                        currentInputConnection?.commitText(word + " ",1)
                        lastWord = norm(word)
                        recentContext.add(lastWord)
                        if (recentContext.size > 8) recentContext.removeAt(0)
                        composing=""
                        renderSuggestions()
                    }
                }
            }
            suggestions.addView(t,LinearLayout.LayoutParams(0,-1,1f))
        }
    }
    private fun loadBrain(){
        (prefs.getString("words","")?:"").split("\n").forEach{line->val p=line.split("\t");if(p.size==2&&p[0].isNotBlank())learned[p[0]]=p[1].toIntOrNull()?:1}
        (prefs.getString("pairs","")?:"").split("\n").forEach{line->val p=line.split("\t");if(p.size==2&&p[0].isNotBlank())pairs[p[0]]=p[1].toIntOrNull()?:1}
    }
    private fun saveBrain(){
        prefs.edit()
            .putString("words",learned.entries.toList().takeLast(5000).joinToString("\n"){"${it.key}\t${it.value}"})
            .putString("pairs",pairs.entries.toList().takeLast(5000).joinToString("\n"){"${it.key}\t${it.value}"})
            .apply()
    }
}
