package com.naang360.missminutes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread

/** Elliot's buttonless native shell. All controls are conversational or keyboard-driven. */
class ElliotActivity : Activity() {
    private lateinit var presence: ElliotPresenceView
    private lateinit var transcript: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var input: EditText
    private lateinit var brain: FrameworkBridge
    private lateinit var memory: NativeChronalMemory
    private lateinit var voice: VoiceBackend
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0L
    @Volatile private var generationJob: String? = null
    private var voiceEnabled = true

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(5, 9, 17)
        window.navigationBarColor = Color.rgb(5, 9, 17)
        window.decorView.systemUiVisibility = 0
        brain = FrameworkBridge(filesDir.resolve("framework"), this)
        memory = NativeChronalMemory(this)
        voice = VoiceBackend(this)
        buildInterface()
        addMessage("ELLIOT", "Good evening. I'm online, and your local memory is intact.")
        requestPermissionsIfNeeded()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 12, 22, 10)
            setBackgroundColor(Color.rgb(5, 9, 17))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        header.addView(TextView(this).apply {
            text = "ELLIOT"
            textSize = 13f
            letterSpacing = .34f
            setTextColor(Color.rgb(218, 232, 249))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 32))
        header.addView(TextView(this).apply {
            text = "PERSONAL INTELLIGENCE  /  ON-DEVICE"
            textSize = 9f
            letterSpacing = .16f
            setTextColor(Color.rgb(102, 133, 165))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 22))
        root.addView(header)

        presence = ElliotPresenceView(this)
        root.addView(presence, LinearLayout.LayoutParams(-1, 0, 1.12f))

        scroller = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            setPadding(2, 4, 2, 8)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(95, 13, 23, 39))
                cornerRadius = 22f
                setStroke(1, Color.argb(35, 135, 184, 225))
            }
        }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 10, 14, 10) }
        scroller.addView(transcript)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, .72f))

        input = EditText(this).apply {
            hint = "Talk to Elliot…"
            setHintTextColor(Color.rgb(94, 119, 146))
            setTextColor(Color.rgb(231, 241, 252))
            textSize = 15f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(17, 14, 17, 14)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(12, 21, 35))
                cornerRadius = 24f
                setStroke(1, Color.rgb(46, 78, 111))
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_DONE) { send(); true } else false
            }
            setOnLongClickListener { launchSpeechRecognition(); true }
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12; bottomMargin = 5 })
        root.addView(TextView(this).apply {
            text = "ENTER TO SEND  ·  HOLD INPUT TO SPEAK"
            gravity = Gravity.CENTER
            textSize = 8f
            letterSpacing = .12f
            setTextColor(Color.rgb(72, 96, 122))
            setPadding(0, 5, 0, 2)
        })
        setContentView(root)
    }

    private fun launchSpeechRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Elliot")
            }
            startActivityForResult(intent, 9201)
        } catch (_: Throwable) { setActivity("VOICE INPUT UNAVAILABLE", ElliotPresenceView.State.ERROR) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9201 && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) { input.setText(spoken); send() }
        } else if (requestCode == 9202 && resultCode == RESULT_OK && data?.data != null) {
            importLocalModel(data.data!!)
        }
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        input.text.clear()
        generationJob?.let { NativeBrain.cancel(it) }; generationJob = null
        generation++
        val token = generation
        voice.stop()
        presence.state = ElliotPresenceView.State.THINKING
        setActivity("UNDERSTANDING REQUEST", ElliotPresenceView.State.THINKING)
        addMessage("YOU", text)
        memory.remember(text, "conversation", .38, type = "episode")
        val q = text.lowercase(Locale.ROOT)

        when {
            q.startsWith("remember ") || q.startsWith("remember:") -> {
                val fact = text.replace(Regex("(?i)^remember\\s*:?\\s*"), "").trim()
                memory.remember(fact, "explicit", .99, type = "fact")
                reply("I'll remember that. It's stored locally on this device.")
            }
            q.contains("what do you remember") -> {
                setActivity("RETRIEVING PERSONAL MEMORY", ElliotPresenceView.State.EXECUTING)
                val traces = memory.recall("", 18)
                reply(if (traces.isEmpty()) "I don't have a relevant memory recorded yet." else traces.joinToString("\n• ", "Here's what I found in your memory:\n• "))
            }
            q == "install brain" || q == "download model" -> installBrain()
            q == "import model" || q == "use my model" -> chooseLocalModel()
            q.contains("memory status") || q == "memory" -> reply("Your private on-device memory contains ${memory.count()} traces and ${memory.mutationCount()} recorded mutations.")
            q.contains("voice off") -> { voiceEnabled = false; voice.stop(); reply("Voice output is off.") }
            q.contains("voice on") -> { voiceEnabled = true; reply("Voice output is on.") }
            q.startsWith("open https://") || q.startsWith("open http://") -> {
                val url = text.substringAfter(" ").trim()
                brain.openUrl(url); reply("I've handed that address to your browser.")
            }
            q.startsWith("search ") || q.startsWith("look up ") || q.startsWith("look online ") -> search(extractSearchQuery(text))
            q == "wind up" || q == "rollback" -> reply("The native checkpoint restore interface is not yet connected to this shell.")
            q.contains("chess") -> startActivity(Intent(this, ChessModeActivity::class.java))
            q == "time" || q.contains("what time") -> reply("It's " + java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date()) + ".")
            else -> generate(text, token)
        }
    }

    private fun generate(userText: String, token: Long) {
        val modelPath = brain.modelPath()
        if (modelPath.isBlank()) {
            setActivity("INSTALLING LOCAL LANGUAGE MODEL", ElliotPresenceView.State.EXECUTING)
            reply("My local language model isn't installed yet. Say 'install brain' and I'll download the verified starter model.")
            return
        }
        setActivity("RETRIEVING RELEVANT MEMORY", ElliotPresenceView.State.THINKING)
        thread {
            val recalled = memory.recall(userText, 6)
            val prompt = buildString {
                append("SYSTEM INSTRUCTIONS\n")
                append("You are Elliot, a capable, personable personal AI assistant. Your identity is ELLIOT: Evolving Learning Logical Intelligence for Operations and Thinking. Be sophisticated, warm, concise, witty when appropriate, technically capable, honest about uncertainty, and proactive without being pushy. You run locally on an Android phone. Never claim consciousness, hidden access, or completed actions you did not perform. Do not reveal private chain-of-thought; provide concise conclusions and brief explanations instead. Financial transactions, purchases, subscriptions, destructive actions, and consequential external commitments require explicit user authorization.\n")
                append("LONG-TERM MEMORY (relevant retrieved traces):\n")
                append(recalled.joinToString("\n") { "- " + it.take(450) })
                append("\nRECENT CONTEXT: This conversation is stored in the local chronal archive.\n")
                append("\nUSER REQUEST\n")
                append(userText.take(1400))
                append("\nASSISTANT RESPONSE\n")
            }
            handler.post { if (token == generation) setActivity("RUNNING LOCAL INFERENCE", ElliotPresenceView.State.THINKING) }
            val job = NativeBrain.start(modelPath, prompt)
            generationJob = job
            var result = ""
            while (token == generation) {
                val packet = NativeBrain.poll(job)
                val split = packet.indexOf('\n')
                val status = if (split < 0) packet else packet.substring(0, split)
                val partial = if (split < 0) "" else packet.substring(split + 1)
                if (status == "RUNNING") {
                    handler.post {
                        if (token == generation) setActivity("GENERATING RESPONSE · ${partial.length} CHARS", ElliotPresenceView.State.THINKING)
                    }
                    Thread.sleep(110)
                } else {
                    result = if (status == "DONE" || status == "ERROR") partial else packet
                    break
                }
            }
            if (token == generation) {
                generationJob = null
                handler.post {
                    if (token == generation) {
                        memory.remember(result, "assistant-response", .22, type = "episode")
                        reply(result)
                    }
                }
            }
        }
    }

    private fun extractSearchQuery(text: String): String = when {
        text.startsWith("search ", true) -> text.substringAfter(" ").trim()
        text.startsWith("look up ", true) -> text.substringAfter("look up ", "").trim()
        text.startsWith("look online ", true) -> text.substringAfter("look online ", "").trim()
        else -> text
    }.take(240)

    private fun search(query: String) {
        setActivity("SEARCHING THE WEB", ElliotPresenceView.State.SEARCHING)
        thread {
            val results = brain.search(query)
            handler.post { reply(if (results.isBlank()) "The search returned no useful results." else "Here's what I found:\n" + results) }
        }
    }


    private fun chooseLocalModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-gguf"))
        }
        try { startActivityForResult(intent, 9202) }
        catch (_: Throwable) { reply("Android couldn't open the model picker.") }
    }

    private fun importLocalModel(uri: Uri) {
        setActivity("IMPORTING LOCAL GGUF MODEL", ElliotPresenceView.State.EXECUTING)
        thread {
            try {
                val directory = File(filesDir, "framework/models").apply { mkdirs() }
                val name = (uri.lastPathSegment ?: "elliot-model.gguf").substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
                require(name.endsWith(".gguf", true)) { "Choose a GGUF model file." }
                val target = File(directory, name)
                val temporary = File(directory, "$name.importing")
                contentResolver.openInputStream(uri)?.use { source ->
                    FileOutputStream(temporary).use { sink -> source.copyTo(sink, 1024 * 1024) }
                } ?: error("Android could not read that file.")
                require(temporary.length() > 50L * 1024 * 1024) { "That file is too small to be a language model." }
                val header = ByteArray(4)
                temporary.inputStream().use { require(it.read(header) == 4 && String(header, Charsets.US_ASCII) == "GGUF") { "The file does not have a valid GGUF header." } }
                if (target.exists()) target.delete()
                check(temporary.renameTo(target)) { "Could not move the imported model into private storage." }
                File(filesDir, "framework/active-model.txt").writeText(name)
                handler.post {
                    setActivity("LOCAL MODEL READY", ElliotPresenceView.State.IDLE)
                    reply("The GGUF model is imported and selected. I'll use it locally. Performance depends on its size and your phone's available memory.")
                }
            } catch (t: Throwable) {
                handler.post {
                    setActivity("MODEL IMPORT FAILED", ElliotPresenceView.State.ERROR)
                    reply("I couldn't import that model: " + (t.message ?: "unknown error"))
                }
            }
        }
    }

    private fun installBrain() {
        if (brain.modelPath().isNotBlank()) { reply("The local model is already installed. I won't replace it without your approval."); return }
        setActivity("DOWNLOADING VERIFIED MODEL", ElliotPresenceView.State.EXECUTING)
        reply("I'm downloading the verified starter model. This may take a little while.")
        thread {
            brain.installBaseModel { status -> handler.post { setActivity(status.uppercase(), ElliotPresenceView.State.EXECUTING) } }
            handler.post { setActivity(if (brain.modelPath().isNotBlank()) "LOCAL MODEL READY" else "MODEL INSTALL FAILED", if (brain.modelPath().isNotBlank()) ElliotPresenceView.State.IDLE else ElliotPresenceView.State.ERROR) }
        }
    }

    private fun reply(raw: String) {
        handler.post {
            val text = clean(raw)
            presence.state = ElliotPresenceView.State.SPEAKING
            setActivity("FORMULATING RESPONSE", ElliotPresenceView.State.SPEAKING)
            addMessage("ELLIOT", text)
            memory.remember(text, "assistant-response", .2, type = "episode")
            if (voiceEnabled) voice.speak(text.take(700))
            handler.postDelayed({
                if (presence.state == ElliotPresenceView.State.SPEAKING) setActivity("LOCAL INTELLIGENCE · STANDBY", ElliotPresenceView.State.IDLE)
            }, maxOf(1200L, text.length * 34L))
        }
    }

    private fun clean(raw: String): String {
        var value = raw.trim()
        try {
            val start = value.indexOf('{'); val end = value.lastIndexOf('}')
            if (start >= 0 && end > start) value = org.json.JSONObject(value.substring(start, end + 1)).optString("text", value)
        } catch (_: Throwable) {}
        value = value.replace(Regex("(?im)^User:\\s*.*$"), "").replace(Regex("(?im)^Assistant:\\s*"), "").trim()
        return value.ifBlank { "I couldn't produce a response. Please try again." }.take(1800)
    }

    private fun addMessage(who: String, message: String) {
        val isElliot = who == "ELLIOT"
        val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 7, 0, 9) }
        block.addView(TextView(this).apply {
            text = who
            textSize = 9f
            letterSpacing = .18f
            setTextColor(if (isElliot) Color.rgb(112, 211, 255) else Color.rgb(155, 169, 190))
        })
        block.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.rgb(220, 231, 244))
            setLineSpacing(3f, 1.02f)
        })
        transcript.addView(block)
        scroller.post { scroller.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun setActivity(label: String, state: ElliotPresenceView.State) {
        presence.detail = label
        presence.state = state
    }

    private fun requestPermissionsIfNeeded() {
        val missing = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) missing += Manifest.permission.RECORD_AUDIO
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 9001)
    }

    override fun onDestroy() {
        voice.shutdown(); brain.shutdown()
        super.onDestroy()
    }
}
