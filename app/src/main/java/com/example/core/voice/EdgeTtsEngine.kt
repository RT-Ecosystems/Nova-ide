package com.example.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.domain.model.EdgeVoicePreset
import com.example.domain.model.TtsPlaybackState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Zero-API-key Microsoft Edge-TTS neural speech synthesizer using OkHttp WebSocket -> temp MP3 -> MediaPlayer,
 * paired with an automatic Android TextToSpeech fallback so audio playback never fails even offline.
 */
class EdgeTtsEngine(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _playbackState = MutableStateFlow(TtsPlaybackState())
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeWebSocket: WebSocket? = null

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var currentAudioFile: File? = null

    @Volatile
    private var androidTts: TextToSpeech? = null

    @Volatile
    private var androidTtsReady: Boolean = false

    init {
        initAndroidTtsFallback()
    }

    private fun initAndroidTtsFallback() {
        runCatching {
            androidTts = TextToSpeech(appContext) { status ->
                androidTtsReady = (status == TextToSpeech.SUCCESS)
                if (androidTtsReady) {
                    androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _playbackState.value = _playbackState.value.copy(
                                isSpeaking = true,
                                isPreparing = false
                            )
                        }

                        override fun onDone(utteranceId: String?) {
                            if (_playbackState.value.activeMessageId == utteranceId) {
                                _playbackState.value = TtsPlaybackState()
                            }
                        }

                        @Deprecated("Deprecated in Android API")
                        override fun onError(utteranceId: String?) {
                            if (_playbackState.value.activeMessageId == utteranceId) {
                                _playbackState.value = TtsPlaybackState()
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (_playbackState.value.activeMessageId == utteranceId) {
                                _playbackState.value = TtsPlaybackState()
                            }
                        }
                    })
                }
            }
        }
    }

    fun speak(
        messageId: String,
        rawText: String,
        voiceId: String,
        speed: Float
    ) {
        val cleanText = prepareTextForSpeech(rawText)
        if (cleanText.isBlank()) {
            stop()
            return
        }

        stopInternal()

        val preset = EdgeVoicePreset.fromVoiceId(voiceId)
        _playbackState.value = TtsPlaybackState(
            isSpeaking = false,
            isPreparing = true,
            activeMessageId = messageId
        )

        activeJob = scope.launch {
            val mp3File = synthesizeWithEdgeWebSocket(
                text = cleanText,
                preset = preset,
                speed = speed
            )

            if (_playbackState.value.activeMessageId != messageId) {
                mp3File?.delete()
                return@launch
            }

            if (mp3File != null && mp3File.exists() && mp3File.length() > 128L) {
                playMp3File(messageId = messageId, audioFile = mp3File)
            } else {
                mp3File?.delete()
                speakWithAndroidTtsFallback(
                    messageId = messageId,
                    text = cleanText,
                    preset = preset,
                    speed = speed
                )
            }
        }
    }

    fun stop() {
        stopInternal()
        _playbackState.value = TtsPlaybackState()
    }

    fun release() {
        stopInternal()
        _playbackState.value = TtsPlaybackState()
        runCatching {
            androidTts?.stop()
            androidTts?.shutdown()
        }
        androidTts = null
        androidTtsReady = false
        scope.cancel()
    }

    private fun stopInternal() {
        activeJob?.cancel()
        activeJob = null

        runCatching { activeWebSocket?.cancel() }
        activeWebSocket = null

        runCatching {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        }
        mediaPlayer = null

        runCatching { currentAudioFile?.delete() }
        currentAudioFile = null

        runCatching { androidTts?.stop() }
    }

    private suspend fun synthesizeWithEdgeWebSocket(
        text: String,
        preset: EdgeVoicePreset,
        speed: Float
    ): File? = withContext(Dispatchers.IO) {
        val outputFile = File(appContext.cacheDir, "edge_tts_${UUID.randomUUID()}.mp3")
        val completion = CompletableDeferred<Boolean>()
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val secMsGec = generateSecMsGec()

        val wsUrl = buildString {
            append(EDGE_TTS_BASE_WSS)
            append("?TrustedClientToken=").append(TRUSTED_CLIENT_TOKEN)
            append("&ConnectionId=").append(connectionId)
            append("&Sec-MS-GEC=").append(secMsGec)
            append("&Sec-MS-GEC-Version=").append(SEC_MS_GEC_VERSION)
        }

        val request = Request.Builder()
            .url(wsUrl)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
            .header("User-Agent", EDGE_USER_AGENT)
            .build()

        var fileOutputStream: FileOutputStream? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runCatching {
                    fileOutputStream = FileOutputStream(outputFile)
                    val timestamp = formatEdgeTimestamp()
                    val configPayload = buildString {
                        append("X-Timestamp:").append(timestamp).append("\r\n")
                        append("Content-Type:application/json; charset=utf-8\r\n")
                        append("Path:speech.config\r\n\r\n")
                        append(
                            """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""
                        )
                    }
                    webSocket.send(configPayload)

                    val ratePercent = speedToProsodyRate(speed)
                    val escapedText = escapeXml(text.take(MAX_EDGE_SPEECH_CHARS))
                    val ssml = buildString {
                        append("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='")
                        append(preset.localeTag)
                        append("'><voice name='")
                        append(preset.voiceId)
                        append("'><prosody pitch='+0Hz' rate='")
                        append(ratePercent)
                        append("' volume='+0%'>")
                        append(escapedText)
                        append("</prosody></voice></speak>")
                    }

                    val ssmlPayload = buildString {
                        append("X-RequestId:").append(requestId).append("\r\n")
                        append("Content-Type:application/ssml+xml\r\n")
                        append("X-Timestamp:").append(timestamp).append("Z\r\n")
                        append("Path:ssml\r\n\r\n")
                        append(ssml)
                    }
                    webSocket.send(ssmlPayload)
                }.onFailure {
                    completion.complete(false)
                }
            }

            override fun onMessage(webSocket: WebSocket, textMessage: String) {
                if (textMessage.contains("Path:turn.end")) {
                    runCatching {
                        fileOutputStream?.flush()
                        fileOutputStream?.close()
                        fileOutputStream = null
                    }
                    webSocket.close(1000, "Done")
                    completion.complete(true)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                runCatching {
                    val array = bytes.toByteArray()
                    if (array.size > 2) {
                        val headerLen = ((array[0].toInt() and 0xFF) shl 8) or (array[1].toInt() and 0xFF)
                        val dataStart = 2 + headerLen
                        if (dataStart < array.size) {
                            val headerText = String(array, 2, headerLen, StandardCharsets.UTF_8)
                            if (headerText.contains("Path:audio")) {
                                fileOutputStream?.write(array, dataStart, array.size - dataStart)
                            }
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runCatching {
                    fileOutputStream?.close()
                    fileOutputStream = null
                }
                completion.complete(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runCatching {
                    fileOutputStream?.close()
                    fileOutputStream = null
                }
                if (!completion.isCompleted) {
                    completion.complete(outputFile.exists() && outputFile.length() > 128L)
                }
            }
        }

        val ws = okHttpClient.newWebSocket(request, listener)
        activeWebSocket = ws

        val success = withTimeoutOrNull(EDGE_SYNTH_TIMEOUT_MS) {
            completion.await()
        } ?: false

        runCatching {
            fileOutputStream?.close()
            ws.cancel()
        }
        activeWebSocket = null

        if (success && outputFile.exists() && outputFile.length() > 128L) {
            outputFile
        } else {
            runCatching { outputFile.delete() }
            null
        }
    }

    private suspend fun playMp3File(messageId: String, audioFile: File) = withContext(Dispatchers.Main) {
        if (_playbackState.value.activeMessageId != messageId) {
            audioFile.delete()
            return@withContext
        }

        currentAudioFile = audioFile
        runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener { mp ->
                    runCatching {
                        mp.reset()
                        mp.release()
                    }
                    if (mediaPlayer === mp) mediaPlayer = null
                    runCatching { audioFile.delete() }
                    if (_playbackState.value.activeMessageId == messageId) {
                        _playbackState.value = TtsPlaybackState()
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    runCatching {
                        mp.reset()
                        mp.release()
                    }
                    if (mediaPlayer === mp) mediaPlayer = null
                    runCatching { audioFile.delete() }
                    if (_playbackState.value.activeMessageId == messageId) {
                        _playbackState.value = TtsPlaybackState()
                    }
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _playbackState.value = TtsPlaybackState(
                isSpeaking = true,
                isPreparing = false,
                activeMessageId = messageId
            )
        }.onFailure {
            runCatching { audioFile.delete() }
            _playbackState.value = TtsPlaybackState()
        }
    }

    private suspend fun speakWithAndroidTtsFallback(
        messageId: String,
        text: String,
        preset: EdgeVoicePreset,
        speed: Float
    ) = withContext(Dispatchers.Main) {
        val tts = androidTts
        if (tts == null || !androidTtsReady) {
            _playbackState.value = TtsPlaybackState()
            return@withContext
        }

        runCatching {
            val targetLocale = Locale.forLanguageTag(preset.localeTag)
            val langResult = tts.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
            tts.setSpeechRate(speed.coerceIn(0.75f, 2.0f))

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, messageId)
            }
            val speakResult = tts.speak(
                text.take(MAX_EDGE_SPEECH_CHARS),
                TextToSpeech.QUEUE_FLUSH,
                params,
                messageId
            )
            if (speakResult == TextToSpeech.SUCCESS) {
                _playbackState.value = TtsPlaybackState(
                    isSpeaking = true,
                    isPreparing = false,
                    activeMessageId = messageId
                )
            } else {
                _playbackState.value = TtsPlaybackState()
            }
        }.onFailure {
            _playbackState.value = TtsPlaybackState()
        }
    }

    /**
     * Strips markdown syntax, URLs, and raw fenced code blocks so spoken AI responses sound crisp and natural.
     */
    fun prepareTextForSpeech(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
        // Replace fenced code blocks with a brief natural cue
        text = text.replace(Regex("```[\\s\\S]*?```"), " Code block provided on screen. ")
        // Remove inline backticks while keeping their word
        text = text.replace(Regex("`([^`]*)`"), "$1")
        // Convert markdown links [label](url) -> label
        text = text.replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
        // Remove raw URLs
        text = text.replace(Regex("https?://\\S+"), "")
        // Strip markdown headings, bold/italic markers, bullet prefixes
        text = text.replace(Regex("(?m)^#{1,6}\\s*"), "")
        text = text.replace(Regex("[*_~]{1,3}"), "")
        text = text.replace(Regex("(?m)^\\s*[-*•]\\s+"), "")
        // Collapse excessive whitespace
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    private fun speedToProsodyRate(speed: Float): String {
        val deltaPercent = ((speed - 1.0f) * 100f).roundToInt().coerceIn(-50, 100)
        return if (deltaPercent >= 0) "+${deltaPercent}%" else "${deltaPercent}%"
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatEdgeTimestamp(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /**
     * Generates the Edge ReadAloud Sec-MS-GEC token (SHA-256 of 5-min rounded Windows FileTime ticks + client token).
     */
    private fun generateSecMsGec(): String {
        val unixSeconds = System.currentTimeMillis() / 1000L
        val winEpochSeconds = unixSeconds + 11644473600L
        val roundedSeconds = winEpochSeconds - (winEpochSeconds % 300L)
        val ticks = roundedSeconds * 10_000_000L
        val payload = "$ticks$TRUSTED_CLIENT_TOKEN"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    companion object {
        private const val EDGE_TTS_BASE_WSS =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val SEC_MS_GEC_VERSION = "1-130.0.2849.68"
        private const val EDGE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
        private const val EDGE_SYNTH_TIMEOUT_MS = 12_000L
        private const val MAX_EDGE_SPEECH_CHARS = 2_400
    }
}
