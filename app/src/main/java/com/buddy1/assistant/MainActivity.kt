
package com.buddy1.assistant

import android.Manifest
import android.app.Activity
import android.app.AlarmClock
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private var apiKey = ""
    private var language = "en-US"

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) listen()
        }

    private val speech =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val words = it.data?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )?.firstOrNull()

                if (!words.isNullOrBlank()) {
                    input.setText(words)
                    sendMessage(words)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiKey = getPreferences(MODE_PRIVATE)
            .getString("api_key", "") ?: ""

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 20)
            setBackgroundColor(Color.rgb(5, 10, 25))
        }

        val title = TextView(this).apply {
            text = "B U D D Y 1"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.CYAN)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "YOUR PERSONAL AI ASSISTANT"
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }

        output = TextView(this).apply {
            text = "BUDDY1: Hello! I am ready to help you."
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(12, 15, 12, 15)
        }

        val scroll = ScrollView(this).apply {
            addView(output)
        }

        input = EditText(this).apply {
            hint = "Talk to BUDDY1..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(12, 12, 12, 12)
        }

        val send = Button(this).apply {
            text = "SEND"
            setOnClickListener {
                sendMessage(input.text.toString())
            }
        }

        val mic = Button(this).apply {
            text = "🎙 SPEAK"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                    listen()
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        val lang = Button(this).apply {
            text = "ENGLISH / हिंदी"
            setOnClickListener {
                language = if (language == "en-US") "hi-IN" else "en-US"
                Toast.makeText(
                    this@MainActivity,
                    "Language changed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val key = Button(this).apply {
            text = "ADD GEMINI API KEY"
            setOnClickListener { showKeyDialog() }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(input)
        layout.addView(send)
        layout.addView(mic)
        layout.addView(lang)
        layout.addView(key)

        setContentView(layout)
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        }

        try {
            speech.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage(message: String) {
        val text = message.trim()
        if (text.isEmpty()) return

        input.setText("")
        output.append("\n\nYOU: $text")

        when {
            text.contains("open youtube", true) -> {
                startActivity(Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://youtube.com")
                ))
                reply("Opening YouTube.")
                return
            }

            text.contains("open settings", true) -> {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                reply("Opening phone settings.")
                return
            }

            text.contains("set alarm", true) -> {
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM))
                reply("Please choose your alarm time.")
                return
            }
        }

        if (apiKey.isBlank()) {
            reply("Please add your Gemini API key first.")
            return
        }

        output.append("\n\nBUDDY1: Thinking...")

        thread {
            try {
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type", "application/json"
                )

                val body = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "text",
                                        "You are BUDDY1, a helpful AI assistant. Reply in the user's language, Hindi or English. Be friendly and truthful. User says: $text"
                                    )
                                )
                            )
                        )
                    )
                }

                connection.outputStream.use {
                    it.write(body.toString().toByteArray())
                }

                val code = connection.responseCode
                val stream = if (code in 200..299)
                    connection.inputStream else connection.errorStream

                val response = stream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (code !in 200..299) {
                    throw Exception("API error $code")
                }

                val answer = JSONObject(response)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                runOnUiThread { reply(answer) }

            } catch (e: Exception) {
                runOnUiThread {
                    reply("Connection error. Please check internet and API key.")
                }
            }
        }
    }

    private fun reply(message: String) {
        output.append("\n\nBUDDY1: $message")
        tts.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "buddy1"
        )
    }

    private fun showKeyDialog() {
        val field = EditText(this).apply {
            hint = "Paste Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(apiKey)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("BUDDY1 API KEY")
            .setView(field)
            .setPositiveButton("SAVE") { _, _ ->
                apiKey = field.text.toString().trim()
                getPreferences(MODE_PRIVATE).edit()
                    .putString("api_key", apiKey)
                    .apply()
                Toast.makeText(
                    this,
                    "API key saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setPitch(0.80f)
            tts.setSpeechRate(0.90f)
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}

