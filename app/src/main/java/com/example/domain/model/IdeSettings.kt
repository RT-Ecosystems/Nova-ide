package com.example.domain.model

data class IdeSettings(
    val useDefaultFreeAi: Boolean = true,
    val customBaseUrl: String = "",
    val customApiKey: String = "",
    val customModelName: String = "",
    val githubPat: String = "",
    val editorFontSizeSp: Float = 14f,
    val selectedVoiceId: String = EdgeVoicePreset.EMMA_MULTILINGUAL.voiceId,
    val ttsSpeed: Float = 1.0f,
    val autoSpeakEnabled: Boolean = false
) {
    val hasGithubPat: Boolean
        get() = githubPat.isNotBlank()

    val hasCustomByokConfig: Boolean
        get() = customBaseUrl.isNotBlank() && customApiKey.isNotBlank() && customModelName.isNotBlank()

    val activeVoicePreset: EdgeVoicePreset
        get() = EdgeVoicePreset.fromVoiceId(selectedVoiceId)

    val activeAiSummary: String
        get() = if (useDefaultFreeAi) {
            "Pollinations.ai (Free Default)"
        } else if (customModelName.isNotBlank()) {
            "BYOK • $customModelName"
        } else {
            "Custom BYOK (Configure in Tools)"
        }
}
