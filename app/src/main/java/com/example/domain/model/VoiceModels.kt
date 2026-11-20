package com.example.domain.model

enum class EdgeVoicePreset(
    val displayName: String,
    val voiceId: String,
    val localeTag: String,
    val shortBadge: String
) {
    SWARA_HI(
        displayName = "Swara (Hindi)",
        voiceId = "hi-IN-SwaraNeural",
        localeTag = "hi-IN",
        shortBadge = "HI"
    ),
    MADHUR_HI(
        displayName = "Madhur (Hindi)",
        voiceId = "hi-IN-MadhurNeural",
        localeTag = "hi-IN",
        shortBadge = "HI"
    ),
    EMMA_MULTILINGUAL(
        displayName = "Emma (Multilingual)",
        voiceId = "en-US-EmmaMultilingualNeural",
        localeTag = "en-US",
        shortBadge = "MULTI"
    ),
    NEERJA_EN_IN(
        displayName = "Neerja (English India)",
        voiceId = "en-IN-NeerjaNeural",
        localeTag = "en-IN",
        shortBadge = "EN-IN"
    ),
    PRABHAT_EN_IN(
        displayName = "Prabhat (English India)",
        voiceId = "en-IN-PrabhatNeural",
        localeTag = "en-IN",
        shortBadge = "EN-IN"
    );

    companion object {
        val SPEED_PRESETS: List<Float> = listOf(1.0f, 1.2f, 1.3f, 1.5f)

        fun fromVoiceId(voiceId: String?): EdgeVoicePreset {
            return entries.firstOrNull { it.voiceId == voiceId } ?: EMMA_MULTILINGUAL
        }

        fun formatSpeed(speed: Float): String {
            return String.format(java.util.Locale.US, "%.1fx", speed)
        }
    }
}

data class TtsPlaybackState(
    val isSpeaking: Boolean = false,
    val isPreparing: Boolean = false,
    val activeMessageId: String? = null
) {
    fun isMessageActive(messageId: String): Boolean {
        return activeMessageId == messageId && (isSpeaking || isPreparing)
    }
}
