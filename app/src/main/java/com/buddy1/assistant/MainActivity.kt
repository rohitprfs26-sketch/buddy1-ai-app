
package com.buddy1.assistant

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var apiKey: EditText
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null

    private val prefs by lazy {
        getSharedPreferences("Buddy1Settings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val scroll = ScrollView(this)
        scroll.addView(layout)

        val heading = TextView(this).apply {
            text = "BUDDY1"
            textSize = 30f
            setPadding(0, 10, 0, 20)
        }
        layout.addView(heading)

        apiKey = EditText(this).apply {
            hint = "Enter Gemini API Key"
            setText(prefs.getString("api_key", ""))
        }
        layout.addView(apiKey)

        addButton(layout, "Save API Key") {
            prefs.edit().putString("api_key", apiKey.text.toString().trim()).apply()
            Toast.makeText(this, "API Key Saved", Toast.LENGTH_SHORT).show()
        }

        output = TextView(this).apply {
            text = "Hello! I am Buddy1. How can I help you?"
            textSize = 18f
            setPadding(0, 25, 0, 25)
        }
        layout.addView(output)

        input = EditText(this).apply {
            hint = "Type your message..."
            minLines = 2
        }
        layout.addView(input)

        addButton(layout, "Send Message") {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) {
                askGemini(message)
            }
        }

        addButton(layout, "Speak to Buddy1") {
            startVoiceRecognition()
        }

        addButton(layout, "Open YouTube") {
            openApp("com.google.android.youtube")
        }

        addButton(layout, "Open Phone Settings") {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }

        addButton(layout, "Set Alarm") {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Alarm set by Buddy1")
                    }
                    startActivity(alarmIntent)
                },
                7,
                0,
                false
            ).show()
        }

        setContentView(scroll)
    }

    private fun addButton(
        layout: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        layout.addView(
            button,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun askGemini(message: String) {
        val key = apiKey.text.toString().trim()

        if (key.isEmpty()) {
            output.text = "Please enter and save your Gemini API key first."
            return
        }

        output.text = "Buddy1 is thinking..."

        Thread {
            try {
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-goog-api-key", key)

                val body = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "text",
                                        "You are Buddy1, a helpful Android AI assistant. Reply naturally in Hindi or English according to the user's language.\n\n$message"
                                    )
                                )
                            )
                        )
                    )
                }

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (responseCode !in 200..299) {
                    throw Exception("Gemini API error $responseCode: $responseText")
                }

                val json = JSONObject(responseText)
                val answer = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                runOnUiThread {
                    output.text = answer
                    speak(answer)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    output.text = "Error: ${e.message}"
                }
            }
        }.start()
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                this,
                "Speech recognition is not available",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                output.text = "Listening..."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                output.text = "Voice recognition error. Please try again."
            }

            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val message = words?.firstOrNull()

                if (!message.isNullOrBlank()) {
                    input.setText(message)
                    askGemini(message)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceRecognition()
        } else if (requestCode == 101) {
            Toast.makeText(
                this,
                "Microphone permission is needed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "buddy1_speech")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN")
        }
    }

    private fun openApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
