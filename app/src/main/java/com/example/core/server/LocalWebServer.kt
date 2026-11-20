package com.example.core.server

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Lightweight embedded NanoHTTPD static file server serving a workspace project directory
 * with accurate MIME types, index.html resolution, path-traversal safety, and no-cache headers.
 */
class LocalWebServer(
    private val projectRootDir: File,
    port: Int
) : NanoHTTPD(BIND_HOST, port) {

    private val canonicalRootPath: String = projectRootDir.canonicalPath

    override fun serve(session: IHTTPSession): Response {
        val rawUri = session.uri ?: "/"
        val decodedUri = runCatching {
            URLDecoder.decode(rawUri, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawUri)

        val cleanPath = decodedUri
            .substringBefore('?')
            .trimStart('/')
            .replace('\\', '/')

        val candidate = if (cleanPath.isEmpty()) {
            resolveDirectoryIndex(projectRootDir)
        } else {
            val resolved = File(projectRootDir, cleanPath)
            if (resolved.isDirectory) resolveDirectoryIndex(resolved) else resolved
        }

        if (candidate == null) {
            return notFoundResponse(decodedUri)
        }

        val canonicalTarget = runCatching { candidate.canonicalPath }.getOrNull()
            ?: return forbiddenResponse()

        val rootPrefix = if (canonicalRootPath.endsWith(File.separator)) {
            canonicalRootPath
        } else {
            canonicalRootPath + File.separator
        }

        if (canonicalTarget != canonicalRootPath && !canonicalTarget.startsWith(rootPrefix)) {
            return forbiddenResponse()
        }

        if (!candidate.exists() || !candidate.isFile) {
            return notFoundResponse(decodedUri)
        }

        val mimeType = resolveMimeType(candidate.extension.lowercase())
        return try {
            val stream = FileInputStream(candidate)
            newFixedLengthResponse(Response.Status.OK, mimeType, stream, candidate.length()).apply {
                addNoCacheHeaders(this)
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain; charset=utf-8",
                "Failed to read ${candidate.name}: ${e.localizedMessage ?: "I/O error"}"
            ).apply {
                addNoCacheHeaders(this)
            }
        }
    }

    private fun resolveDirectoryIndex(dir: File): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        val indexHtml = File(dir, "index.html")
        if (indexHtml.exists() && indexHtml.isFile) return indexHtml
        val indexHtm = File(dir, "index.htm")
        if (indexHtm.exists() && indexHtm.isFile) return indexHtm
        return dir.listFiles()
            ?.firstOrNull { it.isFile && (it.extension.equals("html", true) || it.extension.equals("htm", true)) }
    }

    private fun notFoundResponse(requestedUri: String): Response {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>404 Not Found</title>
              <style>
                body { font-family: system-ui, sans-serif; background: #f8fafc; color: #0f172a; display: grid; place-items: center; min-height: 100vh; margin: 0; padding: 24px; }
                .box { background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; padding: 24px; max-width: 420px; width: 100%; }
                code { background: #f1f5f9; padding: 2px 6px; border-radius: 6px; color: #2563eb; }
              </style>
            </head>
            <body>
              <div class="box">
                <h2>404 • File Not Found</h2>
                <p>No file matched <code>${escapeHtml(requestedUri)}</code> inside <code>${escapeHtml(projectRootDir.name)}</code>.</p>
                <p>Create an <code>index.html</code> file in this project and tap Reload.</p>
              </div>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html; charset=utf-8", html).apply {
            addNoCacheHeaders(this)
        }
    }

    private fun forbiddenResponse(): Response {
        return newFixedLengthResponse(
            Response.Status.FORBIDDEN,
            "text/plain; charset=utf-8",
            "403 Forbidden: Path outside served workspace project."
        ).apply {
            addNoCacheHeaders(this)
        }
    }

    private fun addNoCacheHeaders(response: Response) {
        response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        response.addHeader("Pragma", "no-cache")
        response.addHeader("Expires", "0")
        response.addHeader("Access-Control-Allow-Origin", "*")
    }

    private fun resolveMimeType(extension: String): String = when (extension) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs", "cjs" -> "application/javascript; charset=utf-8"
        "json", "map" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "xml" -> "application/xml; charset=utf-8"
        "txt", "md" -> "text/plain; charset=utf-8"
        "wasm" -> "application/wasm"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        else -> "application/octet-stream"
    }

    private fun escapeHtml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        const val BIND_HOST = "0.0.0.0"
    }
}
