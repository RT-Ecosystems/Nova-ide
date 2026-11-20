package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.security.SecretCrypto
import com.example.domain.model.EdgeVoicePreset
import com.example.domain.model.IdeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.ideSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nova_ide_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val USE_DEFAULT_FREE_AI = booleanPreferencesKey("use_default_free_ai")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val CUSTOM_API_KEY_ENC = stringPreferencesKey("custom_api_key_enc")
        val CUSTOM_MODEL_NAME = stringPreferencesKey("custom_model_name")
        val GITHUB_PAT_ENC = stringPreferencesKey("github_pat_enc")
        val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val AUTO_SPEAK_ENABLED = booleanPreferencesKey("auto_speak_enabled")
    }

    val settingsFlow: Flow<IdeSettings> = context.ideSettingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            IdeSettings(
                useDefaultFreeAi = prefs[Keys.USE_DEFAULT_FREE_AI] ?: true,
                customBaseUrl = prefs[Keys.CUSTOM_BASE_URL] ?: "",
                customApiKey = SecretCrypto.decrypt(prefs[Keys.CUSTOM_API_KEY_ENC] ?: ""),
                customModelName = prefs[Keys.CUSTOM_MODEL_NAME] ?: "",
                githubPat = SecretCrypto.decrypt(prefs[Keys.GITHUB_PAT_ENC] ?: ""),
                editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 14f,
                selectedVoiceId = prefs[Keys.SELECTED_VOICE_ID] ?: EdgeVoicePreset.EMMA_MULTILINGUAL.voiceId,
                ttsSpeed = prefs[Keys.TTS_SPEED] ?: 1.0f,
                autoSpeakEnabled = prefs[Keys.AUTO_SPEAK_ENABLED] ?: false
            )
        }

    suspend fun setUseDefaultFreeAi(enabled: Boolean) {
        context.ideSettingsDataStore.edit { prefs ->
            prefs[Keys.USE_DEFAULT_FREE_AI] = enabled
        }
    }

    suspend fun saveByokConfig(baseUrl: String, apiKey: String, modelName: String) {
        context.ideSettingsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_BASE_URL] = baseUrl.trim()
            prefs[Keys.CUSTOM_API_KEY_ENC] = SecretCrypto.encrypt(apiKey.trim())
            prefs[Keys.CUSTOM_MODEL_NAME] = modelName.trim()
        }
    }

    suspend fun clearByokConfig() {
        context.ideSettingsDataStore.edit { prefs ->
            prefs.remove(Keys.CUSTOM_BASE_URL)
            prefs.remove(Keys.CUSTOM_API_KEY_ENC)
            prefs.remove(Keys.CUSTOM_MODEL_NAME)
            prefs[Keys.USE_DEFAULT_FREE_AI] = true
        }
    }

    suspend fun saveGithubPat(token: String) {
        context.ideSettingsDataStore.edit { prefs ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.GITHUB_PAT_ENC)
            } else {
                prefs[Keys.GITHUB_PAT_ENC] = SecretCrypto.encrypt(trimmed)
            }
        }
    }

    suspend fun clearGithubPat() {
        context.ideSettingsDataStore.edit { prefs ->
            prefs.remove(Keys.GITHUB_PAT_ENC)
        }
    }

    suspend fun setEditorFontSize(fontSizeSp: Float) {
        context.ideSettingsDataStore.edit { prefs ->
            prefs[Keys.EDITOR_FONT_SIZE] = fontSizeSp.coerceIn(11f, 22f)
        }
    }

    suspend fun setSelectedVoiceId(voiceId: String) {
        context.ideSettingsDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE_ID] = voiceId
        }
    }

    suspend fun setTtsSpeed(speed: Float) {
        context.ideSettingsDataStore.edit { prefs ->
            prefs[Keys.TTS_SPEED] = speed.coerceIn(0.8f, 2.0f)
        }
    }

    suspend fun setAutoSpeakEnabled(enabled: Boolean) {
        context.ideSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_SPEAK_ENABLED] = enabled
        }
    }
}
