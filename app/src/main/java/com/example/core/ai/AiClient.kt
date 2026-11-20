package com.example.core.ai

import com.example.domain.model.IdeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class AiChatTurn(
    val role: String,
    val content: String
)

/**
 * OpenAI-compatible HTTP client powered by OkHttp3 on Dispatchers.IO.
 * Supports Pollinations.ai (default free, zero API key) and user-configured Custom BYOK providers.
 */
class AiClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun chatCompletion(
        settings: IdeSettings,
        messages: List<AiChatTurn>,
        temperature: Double = 0.4
    ): Result<String> = withContext(Dispatchers.IO) {
        val (endpointUrl, modelName, apiKey) = resolveProviderConfig(settings)

        if (endpointUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Custom BYOK Base URL is empty. Configure it in Model Settings or switch to Pollinations.ai.")
            )
        }

        val payload = JSONObject().apply {
            put("model", modelName)
            put("temperature", temperature)
            val msgArray = JSONArray()
            messages.forEach { turn ->
                msgArray.put(
                    JSONObject().apply {
                        put("role", turn.role)
                        put("content", turn.content)
                    }
                )
            }
            put("messages", msgArray)
        }

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "NovaIDE-Android/1.0")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(formatHttpError(response.code, rawBody, settings.useDefaultFreeAi))
                    )
                }

                val parsed = extractAssistantText(rawBody)
                if (parsed.isBlank()) {
                    Result.failure(IllegalStateException("AI returned an empty response. Please try again."))
                } else {
                    Result.success(parsed)
                }
            }
        } catch (_: UnknownHostException) {
            Result.failure(
                IOException("You appear to be offline or the AI host could not be resolved. Check your internet connection.")
            )
        } catch (_: SocketTimeoutException) {
            Result.failure(
                IOException("AI request timed out after 90 seconds. Try a shorter prompt or retry.")
            )
        } catch (e: Exception) {
            Result.failure(
                IOException("AI request failed: ${e.localizedMessage ?: "Unexpected network error."}")
            )
        }
    }

    private fun resolveProviderConfig(settings: IdeSettings): Triple<String, String, String?> {
        if (settings.useDefaultFreeAi) {
            return Triple(POLLINATIONS_OPENAI_URL, POLLINATIONS_DEFAULT_MODEL, null)
        }

        val rawBase = settings.customBaseUrl.trim().trimEnd('/')
        val resolvedUrl = when {
            rawBase.isBlank() -> ""
            rawBase.endsWith("/chat/completions") || rawBase.endsWith("/openai") -> rawBase
            else -> "$rawBase/chat/completions"
        }
        val model = settings.customModelName.trim().ifBlank { "gpt-4o-mini" }
        val key = settings.customApiKey.trim().takeIf { it.isNotEmpty() }
        return Triple(resolvedUrl, model, key)
    }

    private fun extractAssistantText(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return ""
        return runCatching {
            val json = JSONObject(trimmed)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val first = choices.getJSONObject(0)
                val message = first.optJSONObject("message")
                val content = message?.optString("content").orEmpty()
                if (content.isNotBlank()) return@runCatching content.trim()
                val text = first.optString("text").orEmpty()
                if (text.isNotBlank()) return@runCatching text.trim()
            }
            // Fallback for alternative JSON fields
            json.optString("content").ifBlank {
                json.optString("response").ifBlank { trimmed }
            }.trim()
        }.getOrElse {
            // Some endpoints return raw markdown/plain text directly
            trimmed
        }
    }

    private fun formatHttpError(code: Int, rawBody: String, isPollinations: Boolean): String {
        val detail = runCatching {
            val json = JSONObject(rawBody)
            json.optJSONObject("error")?.optString("message").orEmpty()
                .ifBlank { json.optString("message") }
        }.getOrDefault("")

        return when (code) {
            401, 403 -> if (isPollinations) {
                "Pollinations.ai rejected the request (HTTP $code). Please retry shortly."
            } else {
                "Authentication failed (HTTP $code). Check your BYOK API Key in Model Settings."
            }
            404 -> "Model or endpoint not found (HTTP 404). Verify your Base URL and Model Name."
            429 -> "Rate limit reached (HTTP 429). Please wait a few seconds or switch AI providers."
            in 500..599 -> "AI provider is temporarily busy (HTTP $code). Please retry in a moment."
            else -> if (detail.isNotBlank()) "AI error (HTTP $code): $detail" else "AI request failed with HTTP $code."
        }
    }

    companion object {
        const val POLLINATIONS_OPENAI_URL = "https://text.pollinations.ai/openai"
        const val POLLINATIONS_DEFAULT_MODEL = "openai"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
