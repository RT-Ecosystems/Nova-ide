package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Lifecycle-safe wrapper around Android SpeechRecognizer supporting Hindi & English live streaming
 * directly into the Chat Composer input box.
 */
class SpeechInputHelper(
    private val context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageTag: String? = null) {
        if (isListening) return
        if (!isAvailable()) {
            onErrorMessage("Speech recognition service is not available on this device.")
            return
        }

        runCatching {
            destroyRecognizer()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListeningChanged(true)
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    isListening = false
                    onListeningChanged(false)
                }

                override fun onError(error: Int) {
                    isListening = false
                    onListeningChanged(false)
                    val friendly = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                        SpeechRecognizer.ERROR_CLIENT -> null
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Network issue during speech recognition."
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap the mic and try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again in a moment."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                        else -> "Voice input stopped (code $error)."
                    }
                    if (friendly != null) {
                        onErrorMessage(friendly)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onListeningChanged(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    if (spoken.isNotEmpty()) {
                        onFinalText(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        onPartialText(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val preferredLang = languageTag?.ifBlank { null } ?: Locale.getDefault().toLanguageTag()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLang)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            isListening = true
            onListeningChanged(true)
            recognizer.startListening(intent)
        }.onFailure { err ->
            isListening = false
            onListeningChanged(false)
            onErrorMessage(err.message ?: "Unable to start microphone input.")
        }
    }

    fun stopListening() {
        isListening = false
        onListeningChanged(false)
        runCatching { speechRecognizer?.stopListening() }
    }

    fun destroy() {
        isListening = false
        onListeningChanged(false)
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }
}
