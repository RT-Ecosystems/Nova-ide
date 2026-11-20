package com.example.core.files

import android.content.Context
import com.example.domain.model.BreadcrumbSegment
import com.example.domain.model.JavaProjectOption
import com.example.domain.model.ProjectTemplate
import com.example.domain.model.WorkspaceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

class WorkspaceFileManager(context: Context) {

    val projectsRoot: File = File(context.filesDir, PROJECTS_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private val canonicalRootPath: String
        get() = projectsRoot.canonicalPath

    fun ensureWorkspaceRoot(): File {
        if (!projectsRoot.exists()) {
            projectsRoot.mkdirs()
        }
        return projectsRoot
    }

    fun resolveSafe(relativePath: String): File {
        val root = ensureWorkspaceRoot()
        val normalized = relativePath.trim().trimStart('/').replace("\\", "/")
        val candidate = if (normalized.isEmpty()) root else File(root, normalized)
        val canonicalCandidate = candidate.canonicalPath
        val rootPrefix = if (canonicalRootPath.endsWith(File.separator)) {
            canonicalRootPath
        } else {
            canonicalRootPath + File.separator
        }
        require(canonicalCandidate == canonicalRootPath || canonicalCandidate.startsWith(rootPrefix)) {
            "Access outside /projects/ workspace is not allowed."
        }
        return candidate
    }

    fun relativePathOf(file: File): String {
        val rootPath = ensureWorkspaceRoot().canonicalPath
        val targetPath = file.canonicalPath
        if (targetPath == rootPath) return ""
        return targetPath.removePrefix(rootPath).trimStart(File.separatorChar).replace(File.separatorChar, '/')
    }

    fun buildBreadcrumbs(currentRelativePath: String): List<BreadcrumbSegment> {
        val segments = mutableListOf(BreadcrumbSegment(label = PROJECTS_DIR_NAME, relativePath = ""))
        val clean = currentRelativePath.trim().trim('/')
        if (clean.isEmpty()) return segments

        var accumulated = ""
        clean.split('/').filter { it.isNotBlank() }.forEach { part ->
            accumulated = if (accumulated.isEmpty()) part else "$accumulated/$part"
            segments.add(BreadcrumbSegment(label = part, relativePath = accumulated))
        }
        return segments
    }

    suspend fun listItems(relativePath: String): Result<List<WorkspaceItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = resolveSafe(relativePath)
            if (!dir.exists()) dir.mkdirs()
            val files = dir.listFiles().orEmpty()
                .filter { it.name != ".git" }
                .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })

            files.map { file ->
                val isDir = file.isDirectory
                val childCount = if (isDir) {
                    file.listFiles()?.count { it.name != ".git" } ?: 0
                } else 0
                val isGitRepo = isDir && File(file, ".git").exists()
                WorkspaceItem(
                    name = file.name,
                    absolutePath = file.canonicalPath,
                    relativePath = relativePathOf(file),
                    isDirectory = isDir,
                    sizeBytes = if (isDir) 0L else file.length(),
                    lastModifiedMillis = file.lastModified(),
                    extension = if (isDir) "" else file.extension.lowercase(),
                    isGitRepo = isGitRepo,
                    childCount = childCount
                )
            }
        }
    }

    suspend fun createProject(
        projectName: String,
        template: ProjectTemplate
    ): Result<WorkspaceItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = sanitizeEntryName(projectName)
            val projectDir = resolveSafe(cleanName)
            require(!projectDir.exists()) { "A project named \"$cleanName\" already exists." }
            check(projectDir.mkdirs()) { "Unable to create project directory." }

            when (template) {
                ProjectTemplate.WEB_STARTER -> writeWebStarterFiles(projectDir, cleanName)
                ProjectTemplate.ANDROID_JAVA_APP -> writeAndroidJavaStarterFiles(projectDir, cleanName)
                ProjectTemplate.KOTLIN_STARTER -> writeKotlinStarterFiles(projectDir, cleanName)
                ProjectTemplate.PYTHON_STARTER -> writePythonStarterFiles(projectDir, cleanName)
                ProjectTemplate.EMPTY -> Unit
            }

            WorkspaceItem(
                name = projectDir.name,
                absolutePath = projectDir.canonicalPath,
                relativePath = relativePathOf(projectDir),
                isDirectory = true,
                sizeBytes = 0L,
                lastModifiedMillis = projectDir.lastModified(),
                extension = "",
                isGitRepo = false,
                childCount = projectDir.listFiles()?.size ?: 0
            )
        }
    }

    suspend fun createEntry(
        parentRelativePath: String,
        name: String,
        isDirectory: Boolean
    ): Result<WorkspaceItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = sanitizeEntryName(name)
            val parent = resolveSafe(parentRelativePath)
            if (!parent.exists()) parent.mkdirs()
            val target = resolveSafe(
                if (parentRelativePath.isBlank()) cleanName else "$parentRelativePath/$cleanName"
            )
            require(!target.exists()) { "\"$cleanName\" already exists here." }

            if (isDirectory) {
                check(target.mkdirs()) { "Failed to create folder \"$cleanName\"." }
            } else {
                check(target.createNewFile()) { "Failed to create file \"$cleanName\"." }
                val starter = defaultContentForExtension(target.extension.lowercase(), target.nameWithoutExtension)
                if (starter.isNotEmpty()) {
                    target.writeText(starter, StandardCharsets.UTF_8)
                }
            }

            WorkspaceItem(
                name = target.name,
                absolutePath = target.canonicalPath,
                relativePath = relativePathOf(target),
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) 0L else target.length(),
                lastModifiedMillis = target.lastModified(),
                extension = if (isDirectory) "" else target.extension.lowercase(),
                isGitRepo = false,
                childCount = 0
            )
        }
    }

    suspend fun renameEntry(relativePath: String, newName: String): Result<WorkspaceItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanNewName = sanitizeEntryName(newName)
                val source = resolveSafe(relativePath)
                require(source.exists()) { "Item no longer exists." }
                val parent = source.parentFile ?: ensureWorkspaceRoot()
                val destination = File(parent, cleanNewName)
                require(destination.canonicalPath.startsWith(canonicalRootPath)) {
                    "Invalid target path."
                }
                require(!destination.exists()) { "\"$cleanNewName\" already exists." }
                check(source.renameTo(destination)) { "Could not rename \"${source.name}\"." }

                WorkspaceItem(
                    name = destination.name,
                    absolutePath = destination.canonicalPath,
                    relativePath = relativePathOf(destination),
                    isDirectory = destination.isDirectory,
                    sizeBytes = if (destination.isDirectory) 0L else destination.length(),
                    lastModifiedMillis = destination.lastModified(),
                    extension = if (destination.isDirectory) "" else destination.extension.lowercase(),
                    isGitRepo = destination.isDirectory && File(destination, ".git").exists(),
                    childCount = if (destination.isDirectory) destination.listFiles()?.count { it.name != ".git" } ?: 0 else 0
                )
            }
        }

    suspend fun deleteEntry(relativePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = resolveSafe(relativePath)
            require(target.canonicalPath != canonicalRootPath) { "Cannot delete workspace root." }
            if (target.exists()) {
                check(target.deleteRecursively()) { "Failed to delete \"${target.name}\"." }
            }
        }
    }

    suspend fun readTextFile(absolutePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(absolutePath)
            require(file.canonicalPath.startsWith(canonicalRootPath)) { "File outside workspace." }
            require(file.exists() && file.isFile) { "File not found." }
            require(file.length() <= MAX_EDITABLE_BYTES) {
                "File exceeds 2 MB editor limit."
            }
            file.readText(StandardCharsets.UTF_8)
        }
    }

    suspend fun writeTextFile(absolutePath: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(absolutePath)
            require(file.canonicalPath.startsWith(canonicalRootPath)) { "File outside workspace." }
            file.parentFile?.mkdirs()
            file.writeText(content, StandardCharsets.UTF_8)
        }
    }

    suspend fun findPreviewableProjects(): List<com.example.domain.model.PreviewProjectOption> =
        withContext(Dispatchers.IO) {
            val root = ensureWorkspaceRoot()
            root.listFiles().orEmpty()
                .filter { it.isDirectory && it.name != ".git" }
                .mapNotNull { projectDir ->
                    val htmlFiles = projectDir.walkTopDown()
                        .maxDepth(4)
                        .onEnter { it.name != ".git" && it.name != "node_modules" }
                        .filter {
                            it.isFile && (it.extension.equals("html", true) || it.extension.equals("htm", true))
                        }
                        .toList()
                    if (htmlFiles.isEmpty()) return@mapNotNull null

                    val entryFile = htmlFiles.firstOrNull {
                        it.parentFile?.canonicalPath == projectDir.canonicalPath &&
                            (it.name.equals("index.html", true) || it.name.equals("index.htm", true))
                    } ?: htmlFiles.firstOrNull {
                        it.name.equals("index.html", true)
                    } ?: htmlFiles.minByOrNull { it.canonicalPath.length }!!

                    val relEntry = entryFile.canonicalPath
                        .removePrefix(projectDir.canonicalPath)
                        .trimStart(File.separatorChar)
                        .replace(File.separatorChar, '/')

                    com.example.domain.model.PreviewProjectOption(
                        projectName = projectDir.name,
                        projectPath = projectDir.canonicalPath,
                        entryHtmlRelativePath = relEntry,
                        htmlCount = htmlFiles.size
                    )
                }
                .sortedBy { it.projectName.lowercase() }
        }

    suspend fun findJavaProjects(): List<JavaProjectOption> = withContext(Dispatchers.IO) {
        val root = ensureWorkspaceRoot()
        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != ".git" }
            .mapNotNull { projectDir ->
                val javaCount = projectDir.walkTopDown()
                    .maxDepth(8)
                    .onEnter { it.name != ".git" && it.name != "build" && it.name != "node_modules" }
                    .count { it.isFile && it.extension.equals("java", ignoreCase = true) }
                if (javaCount == 0) return@mapNotNull null

                JavaProjectOption(
                    projectName = projectDir.name,
                    projectPath = projectDir.canonicalPath,
                    javaFileCount = javaCount,
                    hasManifest = File(projectDir, "AndroidManifest.xml").exists()
                )
            }
            .sortedBy { it.projectName.lowercase() }
    }

    fun resolveTopLevelProjectDir(fileAbsolutePath: String): File? {
        val root = ensureWorkspaceRoot()
        val rootCanonical = root.canonicalPath
        val targetCanonical = runCatching { File(fileAbsolutePath).canonicalPath }.getOrNull() ?: return null
        val prefix = if (rootCanonical.endsWith(File.separator)) rootCanonical else rootCanonical + File.separator
        if (!targetCanonical.startsWith(prefix)) return null

        val relative = targetCanonical.removePrefix(prefix)
        val firstSegment = relative.substringBefore(File.separatorChar)
        if (firstSegment.isBlank()) return null

        val candidate = File(root, firstSegment)
        return if (candidate.exists() && candidate.isDirectory) candidate else null
    }

    suspend fun readProjectContextSnapshot(
        projectDir: File,
        maxFiles: Int = 6,
        maxCharsPerFile: Int = 6000
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (!projectDir.exists() || !projectDir.isDirectory) return@withContext emptyMap()
        val projectCanonical = projectDir.canonicalPath
        val result = LinkedHashMap<String, String>()

        val candidates = projectDir.walkTopDown()
            .maxDepth(4)
            .onEnter { it.name != ".git" && it.name != "node_modules" && it.name != "build" }
            .filter { file ->
                file.isFile &&
                    file.length() in 1L..200_000L &&
                    file.extension.lowercase() in WorkspaceItem.SUPPORTED_CODE_EXTENSIONS
            }
            .sortedWith(
                compareBy<File>(
                    { !it.name.equals("index.html", true) },
                    { it.extension.lowercase() !in setOf("html", "css", "js", "ts", "kt", "java", "py", "xml") },
                    { it.canonicalPath.length }
                )
            )
            .take(maxFiles)

        for (file in candidates) {
            val rel = file.canonicalPath
                .removePrefix(projectCanonical)
                .trimStart(File.separatorChar)
                .replace(File.separatorChar, '/')
            val text = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull() ?: continue
            result[rel] = if (text.length > maxCharsPerFile) {
                text.take(maxCharsPerFile) + "\n// ...truncated..."
            } else text
        }
        result
    }

    suspend fun listEditableFiles(projectName: String? = null): List<WorkspaceItem> =
        withContext(Dispatchers.IO) {
            val root = ensureWorkspaceRoot()
            val searchBase = if (!projectName.isNullOrBlank()) {
                val proj = File(root, projectName)
                if (proj.exists() && proj.isDirectory) proj else root
            } else root

            searchBase.walkTopDown()
                .maxDepth(4)
                .onEnter { it.name != ".git" && it.name != "node_modules" && it.name != "build" }
                .filter { it.isFile && it.extension.lowercase() in WorkspaceItem.SUPPORTED_CODE_EXTENSIONS }
                .map { file ->
                    WorkspaceItem(
                        name = file.name,
                        absolutePath = file.canonicalPath,
                        relativePath = relativePathOf(file),
                        isDirectory = false,
                        sizeBytes = file.length(),
                        lastModifiedMillis = file.lastModified(),
                        extension = file.extension.lowercase()
                    )
                }
                .sortedBy { it.relativePath.lowercase() }
                .take(40)
                .toList()
        }

    suspend fun listRootProjectNames(): List<String> = withContext(Dispatchers.IO) {
        ensureWorkspaceRoot()
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name != ".git" }
            .map { it.name }
            .sortedBy { it.lowercase() }
    }

    suspend fun ensureTargetProjectDir(preferredProjectName: String?): File = withContext(Dispatchers.IO) {
        val root = ensureWorkspaceRoot()
        if (!preferredProjectName.isNullOrBlank()) {
            val preferred = runCatching { resolveSafe(preferredProjectName) }.getOrNull()
            if (preferred != null && preferred.exists() && preferred.isDirectory) {
                return@withContext preferred
            }
        }
        val firstExisting = root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != ".git" }
            .minByOrNull { it.name.lowercase() }
        if (firstExisting != null) return@withContext firstExisting

        val autoProject = File(root, "nova-web-app").apply { mkdirs() }
        autoProject
    }

    suspend fun revertChangeSet(changeSet: com.example.domain.model.AppliedChangeSet): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rootPrefix = if (canonicalRootPath.endsWith(File.separator)) {
                    canonicalRootPath
                } else {
                    canonicalRootPath + File.separator
                }
                for (entry in changeSet.files) {
                    val file = File(entry.absolutePath)
                    if (!file.canonicalPath.startsWith(rootPrefix)) continue
                    if (entry.existedBefore && entry.previousContent != null) {
                        file.parentFile?.mkdirs()
                        file.writeText(entry.previousContent, StandardCharsets.UTF_8)
                    } else if (!entry.existedBefore && file.exists()) {
                        file.delete()
                    }
                }
            }
        }

    suspend fun findHtmlEntryPoints(): List<WorkspaceItem> = withContext(Dispatchers.IO) {
        val root = ensureWorkspaceRoot()
        root.walkTopDown()
            .maxDepth(4)
            .onEnter { it.name != ".git" && it.name != "node_modules" }
            .filter { it.isFile && (it.extension.equals("html", ignoreCase = true) || it.extension.equals("htm", ignoreCase = true)) }
            .map { file ->
                WorkspaceItem(
                    name = file.name,
                    absolutePath = file.canonicalPath,
                    relativePath = relativePathOf(file),
                    isDirectory = false,
                    sizeBytes = file.length(),
                    lastModifiedMillis = file.lastModified(),
                    extension = file.extension.lowercase()
                )
            }
            .sortedBy { it.relativePath.lowercase() }
            .toList()
    }

    fun sanitizeEntryName(raw: String): String {
        val cleaned = raw.trim()
        require(cleaned.isNotEmpty()) { "Name cannot be empty." }
        require(!cleaned.contains('/') && !cleaned.contains('\\')) { "Name cannot contain path separators." }
        require(cleaned != "." && cleaned != "..") { "Invalid name." }
        return cleaned
    }

    private fun writeWebStarterFiles(projectDir: File, projectName: String) {
        File(projectDir, "index.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>$projectName</title>
              <link rel="stylesheet" href="styles.css" />
            </head>
            <body>
              <main class="card">
                <span class="badge">Nova IDE Workspace</span>
                <h1>$projectName</h1>
                <p>Edit <code>index.html</code>, <code>styles.css</code>, and <code>app.js</code> right inside Sora Editor.</p>
                <button id="counterBtn">Clicked 0 times</button>
              </main>
              <script src="app.js"></script>
            </body>
            </html>
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
        File(projectDir, "styles.css").writeText(
            """
            :root {
              --bg: #0d1117;
              --surface: #161b22;
              --border: #30363d;
              --accent: #22d3ee;
              --text: #f0f6fc;
            }

            * { box-sizing: border-box; }

            body {
              margin: 0;
              min-height: 100vh;
              display: grid;
              place-items: center;
              background: var(--bg);
              color: var(--text);
              font-family: system-ui, -apple-system, sans-serif;
            }

            .card {
              padding: 2rem;
              max-width: 420px;
              background: var(--surface);
              border: 1px solid var(--border);
              border-radius: 16px;
            }

            .badge {
              color: var(--accent);
              font-size: 0.75rem;
              letter-spacing: 0.08em;
              text-transform: uppercase;
            }

            button {
              margin-top: 1rem;
              padding: 0.75rem 1.25rem;
              border: none;
              border-radius: 10px;
              background: var(--accent);
              color: #041418;
              font-weight: 700;
              cursor: pointer;
            }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
        File(projectDir, "app.js").writeText(
            """
            const button = document.getElementById("counterBtn");
            let count = 0;

            button?.addEventListener("click", () => {
              count += 1;
              button.textContent = `Clicked ${'$'}{count} ${'$'}{count === 1 ? "time" : "times"}`;
            });
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
    }

    private fun writeKotlinStarterFiles(projectDir: File, projectName: String) {
        val srcDir = File(projectDir, "src").apply { mkdirs() }
        File(srcDir, "Main.kt").writeText(
            """
            package app

            data class BuildConfig(val name: String, val version: String)

            fun main() {
                val config = BuildConfig(name = "$projectName", version = "1.0.0")
                println("Running ${'$'}{config.name} v${'$'}{config.version}")
            }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
        File(projectDir, "README.md").writeText(
            """
            # $projectName

            Created inside **Nova IDE** (`/projects/$projectName`).
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
    }

    private fun writePythonStarterFiles(projectDir: File, projectName: String) {
        File(projectDir, "main.py").writeText(
            """
            def greet(project_name: str) -> str:
                return f"Hello from {project_name}!"


            if __name__ == "__main__":
                print(greet("$projectName"))
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
        File(projectDir, "requirements.txt").writeText("# Add dependencies here\n", StandardCharsets.UTF_8)
    }

    private fun writeAndroidJavaStarterFiles(projectDir: File, projectName: String) {
        val safeXmlName = projectName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        File(projectDir, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.nova.sample">

                <application
                    android:allowBackup="true"
                    android:label="@string/app_name">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        val javaPkgDir = File(projectDir, "src/com/nova/sample").apply { mkdirs() }
        File(javaPkgDir, "MainActivity.java").writeText(
            """
            package com.nova.sample;

            import android.app.Activity;
            import android.graphics.Color;
            import android.os.Bundle;
            import android.view.Gravity;
            import android.widget.LinearLayout;
            import android.widget.TextView;

            public class MainActivity extends Activity {

                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);

                    LinearLayout root = new LinearLayout(this);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setGravity(Gravity.CENTER);
                    root.setBackgroundColor(Color.parseColor("#F8FAFC"));
                    root.setPadding(48, 48, 48, 48);

                    TextView title = new TextView(this);
                    title.setText("$projectName");
                    title.setTextSize(26f);
                    title.setTextColor(Color.parseColor("#0F172A"));
                    title.setGravity(Gravity.CENTER);

                    TextView subtitle = new TextView(this);
                    subtitle.setText("Compiled on-device with Nova IDE + Eclipse ECJ");
                    subtitle.setTextSize(15f);
                    subtitle.setTextColor(Color.parseColor("#2563EB"));
                    subtitle.setGravity(Gravity.CENTER);
                    subtitle.setPadding(0, 20, 0, 0);

                    root.addView(title);
                    root.addView(subtitle);
                    setContentView(root);
                }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        val valuesDir = File(projectDir, "res/values").apply { mkdirs() }
        File(valuesDir, "strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$safeXmlName</string>
            </resources>
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
    }

    private fun defaultContentForExtension(ext: String, baseName: String): String = when (ext) {
        "html", "htm" -> "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\" />\n  <title>$baseName</title>\n</head>\n<body>\n  \n</body>\n</html>\n"
        "kt" -> "fun main() {\n    println(\"Hello from $baseName\")\n}\n"
        "java" -> "public class $baseName {\n    public static void main(String[] args) {\n        System.out.println(\"Hello from $baseName\");\n    }\n}\n"
        "json" -> "{\n  \"name\": \"$baseName\"\n}\n"
        "md" -> "# $baseName\n\n"
        else -> ""
    }

    companion object {
        const val PROJECTS_DIR_NAME = "projects"
        private const val MAX_EDITABLE_BYTES = 2 * 1024 * 1024L
    }
}
