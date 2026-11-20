package com.example.core.ai

import com.example.domain.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Zero-API-key live web search powered by DuckDuckGo HTML endpoint + Jsoup parsing.
 */
class WebSearchTool {

    suspend fun search(query: String, maxResults: Int = 5): Result<List<WebSearchResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())

            runCatching {
                val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
                val url = "https://html.duckduckgo.com/html/?q=$encoded"

                val document = Jsoup.connect(url)
                    .userAgent(BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(12_000)
                    .followRedirects(true)
                    .get()

                val results = mutableListOf<WebSearchResult>()
                val nodes = document.select(".result, .results_links")

                for (node in nodes) {
                    if (results.size >= maxResults) break
                    val anchor = node.selectFirst("a.result__a") ?: continue
                    val title = anchor.text().trim()
                    if (title.isBlank()) continue

                    val rawHref = anchor.attr("href").trim()
                    val resolvedUrl = resolveDuckDuckGoUrl(rawHref)
                    if (resolvedUrl.isBlank()) continue

                    val snippet = node.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
                    results.add(
                        WebSearchResult(
                            title = title,
                            snippet = snippet,
                            url = resolvedUrl
                        )
                    )
                }
                results
            }
        }

    fun shouldAutoTriggerSearch(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return AUTO_SEARCH_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    fun formatResultsForSystemPrompt(query: String, results: List<WebSearchResult>): String {
        if (results.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("LIVE WEB SEARCH RESULTS for \"$query\" (use these up-to-date facts and cite source URLs inline):")
        results.forEachIndexed { index, item ->
            sb.appendLine("[${index + 1}] ${item.title}")
            sb.appendLine("URL: ${item.url}")
            if (item.snippet.isNotBlank()) {
                sb.appendLine("Snippet: ${item.snippet}")
            }
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    private fun resolveDuckDuckGoUrl(rawHref: String): String {
        if (rawHref.isBlank()) return ""
        val uddgMatch = Regex("[?&]uddg=([^&]+)").find(rawHref)
        if (uddgMatch != null) {
            return runCatching {
                URLDecoder.decode(uddgMatch.groupValues[1], StandardCharsets.UTF_8.name())
            }.getOrDefault(rawHref)
        }
        return when {
            rawHref.startsWith("//") -> "https:$rawHref"
            rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
            else -> ""
        }
    }

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val AUTO_SEARCH_KEYWORDS = listOf(
            "latest", "today", "news", "current", "currently", "recent",
            "2025", "2026", "weather", "price", "stock", "release notes",
            "what's new", "newest version", "search the web", "look up"
        )
    }
}
