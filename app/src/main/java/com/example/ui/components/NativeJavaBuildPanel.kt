package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.builder.WebToApkBuilder
import com.example.domain.model.BuildStageStatus
import com.example.domain.model.CompilerLogEntry
import com.example.domain.model.CompilerLogLevel
import com.example.domain.model.JavaCompileState
import com.example.domain.model.JavaProjectOption
import com.example.domain.model.KeystoreInfo
import com.example.domain.model.NativeBuildArtifact
import com.example.domain.model.ToolchainStatus
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentAmberContainer
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentRoseContainer
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.AccentVioletContainer
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandIndigoBorder
import com.example.ui.theme.BrandIndigoContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OnBrandLight
import com.example.ui.theme.SurfaceBackground
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun NativeJavaBuildPanel(
    toolchainStatus: ToolchainStatus,
    keystoreInfo: KeystoreInfo,
    javaProjects: List<JavaProjectOption>,
    compileState: JavaCompileState,
    onImportAndroidJar: (Uri) -> Unit,
    onOpenToolchainSettings: () -> Unit,
    onCreateSampleJavaProject: () -> Unit,
    onOpenWorkspaceFiles: () -> Unit,
    onCompileJavaProject: (projectPath: String) -> Unit,
    onBuildSignedApk: (projectPath: String) -> Unit,
    onShareSignedApk: (artifact: NativeBuildArtifact) -> Unit,
    onAutoFixJavaErrors: (projectPath: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedProjectPath by remember { mutableStateOf<String?>(compileState.selectedProjectPath) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(javaProjects) {
        if (selectedProjectPath == null || javaProjects.none { it.projectPath == selectedProjectPath }) {
            selectedProjectPath = javaProjects.firstOrNull()?.projectPath
        }
    }

    val selectedProject = remember(javaProjects, selectedProjectPath) {
        javaProjects.firstOrNull { it.projectPath == selectedProjectPath }
    }

    val quickImportJarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportAndroidJar(uri)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("native_java_build_panel"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. SDK Toolchain Readiness Card (android.jar + aapt2)
        item(key = "toolchain_status_banner") {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderLight),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Memory,
                                contentDescription = null,
                                tint = if (toolchainStatus.isAndroidJarReady) AccentEmerald else AccentAmber,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = "Android SDK Toolchain",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Surface(
                            color = if (toolchainStatus.isAndroidJarReady) AccentEmeraldContainer else AccentAmberContainer,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                1.dp,
                                if (toolchainStatus.isAndroidJarReady) AccentEmeraldBorder else AccentAmber.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("native_build_toolchain_pill")
                        ) {
                            Text(
                                text = if (toolchainStatus.isAndroidJarReady) {
                                    "android.jar: Ready (${WebToApkBuilder.formatSize(toolchainStatus.fileSizeBytes)})"
                                } else {
                                    "android.jar: Missing"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (toolchainStatus.isAndroidJarReady) AccentEmerald else AccentAmber,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // aapt2 Status Pill Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "aapt2 Resource Linker",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Surface(
                            color = if (toolchainStatus.isAapt2Ready) AccentEmeraldContainer else SurfaceElevated,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                1.dp,
                                if (toolchainStatus.isAapt2Ready) AccentEmeraldBorder else BorderLight
                            ),
                            modifier = Modifier.testTag("native_build_aapt2_pill")
                        ) {
                            Text(
                                text = if (toolchainStatus.isAapt2Ready) "aapt2: Ready" else "aapt2: Optional / Fallback",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (toolchainStatus.isAapt2Ready) AccentEmerald else TextSecondary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Signing Keystore Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "APK Signer (v1 + v2)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Surface(
                            color = if (keystoreInfo.isCustom) BrandIndigoContainer else AccentEmeraldContainer,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                1.dp,
                                if (keystoreInfo.isCustom) BrandIndigoBorder else AccentEmeraldBorder
                            ),
                            modifier = Modifier.testTag("native_build_keystore_pill")
                        ) {
                            Text(
                                text = if (keystoreInfo.isCustom) {
                                    "Keystore: ${keystoreInfo.alias} (Custom)"
                                } else {
                                    "Keystore: Auto-Debug Ready"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (keystoreInfo.isCustom) BrandIndigo else AccentEmerald,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (!toolchainStatus.isAndroidJarReady) {
                        Text(
                            text = "Eclipse ECJ compiles Java 8 sources against /toolchain/android.jar. Import it from device storage or download via URL in Tools.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                quickImportJarLauncher.launch(
                                    arrayOf("application/java-archive", "application/octet-stream", "*/*")
                                )
                            },
                            enabled = !toolchainStatus.isWorking,
                            border = BorderStroke(1.dp, BrandIndigo),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("native_build_quick_import_jar")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = BrandIndigo,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (toolchainStatus.isAndroidJarReady) "Replace JAR" else "Import android.jar",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandIndigo,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenToolchainSettings,
                            border = BorderStroke(1.dp, BorderLight),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("native_build_open_toolchain_tools")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Toolchain URL",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 2. Java Project Selector + 3-Step Build Stepper + Action Buttons
        item(key = "java_project_selector_card") {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderLight),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Native Java Build Pipeline",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Compile .java → .class (ECJ) → classes.dex → Package → Sign (v1+v2)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    // 4-Step Visual Stepper
                    BuildPipelineStepper(
                        ecjStatus = compileState.ecjStatus,
                        dexStatus = compileState.dexStatus,
                        packageStatus = compileState.packageStatus,
                        signStatus = compileState.signStatus
                    )

                    if (javaProjects.isEmpty()) {
                        Surface(
                            color = BrandIndigoContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BrandIndigoBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No Java projects found yet",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Create an \"Android Java App\" project with AndroidManifest.xml, MainActivity.java, and strings.xml.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onCreateSampleJavaProject,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BrandIndigo,
                                            contentColor = OnBrandLight
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                                        modifier = Modifier.testTag("native_build_create_sample_java")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Sample Java App", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = onOpenWorkspaceFiles,
                                        border = BorderStroke(1.dp, BorderLight),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Text("Files", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                color = SurfaceBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BorderLight),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { projectDropdownExpanded = true }
                                    .testTag("native_build_project_dropdown")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Code,
                                            contentDescription = null,
                                            tint = BrandIndigo,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = selectedProject?.projectName ?: "Select Java project",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = selectedProject?.let {
                                                    "${it.javaFileCount} .java file(s)${if (it.hasManifest) " • AndroidManifest.xml" else ""}"
                                                } ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Choose Java project",
                                        tint = TextSecondary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = projectDropdownExpanded,
                                onDismissRequest = { projectDropdownExpanded = false },
                                containerColor = SurfaceCard
                            ) {
                                javaProjects.forEach { proj ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = proj.projectName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (proj.projectPath == selectedProjectPath) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (proj.projectPath == selectedProjectPath) BrandIndigo else TextPrimary
                                                )
                                                Text(
                                                    text = "${proj.javaFileCount} .java file(s)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted
                                                )
                                            }
                                        },
                                        onClick = {
                                            projectDropdownExpanded = false
                                            selectedProjectPath = proj.projectPath
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Primary 1-Tap Full 4-Stage Pipeline CTA: Build Signed APK
                    Button(
                        onClick = {
                            selectedProject?.let { onBuildSignedApk(it.projectPath) }
                        },
                        enabled = selectedProject != null && !compileState.isBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandIndigo,
                            contentColor = OnBrandLight,
                            disabledContainerColor = SurfaceElevated,
                            disabledContentColor = TextMuted
                        ),
                        contentPadding = PaddingValues(vertical = 13.dp, horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("native_build_signed_apk_button")
                    ) {
                        if (compileState.isBuildingApk) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = OnBrandLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    compileState.signStatus == BuildStageStatus.RUNNING -> "Signing APK (v1 + v2)..."
                                    compileState.packageStatus == BuildStageStatus.RUNNING -> "Packaging Unsigned APK..."
                                    compileState.dexStatus == BuildStageStatus.RUNNING -> "Converting .class → classes.dex..."
                                    else -> "Compiling Java with ECJ..."
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Android,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Build Signed APK",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Secondary Quick Option: Compile Java Only (ECJ)
                    OutlinedButton(
                        onClick = {
                            selectedProject?.let { onCompileJavaProject(it.projectPath) }
                        },
                        enabled = selectedProject != null && !compileState.isBusy,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selectedProject != null && !compileState.isBusy) BrandIndigo else BorderLight
                        ),
                        contentPadding = PaddingValues(vertical = 11.dp, horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("native_build_compile_ecj_button")
                    ) {
                        if (compileState.isCompiling && !compileState.isBuildingApk) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = BrandIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Compiling Java (ECJ)...",
                                style = MaterialTheme.typography.labelLarge,
                                color = BrandIndigo
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = if (selectedProject != null && !compileState.isBusy) BrandIndigo else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Compile Java Only (ECJ)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedProject != null && !compileState.isBusy) BrandIndigo else TextMuted
                            )
                        }
                    }

                    // Build Outcome Summary Banner
                    if (compileState.success != null && !compileState.isCompiling) {
                        val passed = compileState.success == true
                        Surface(
                            color = if (passed) AccentEmeraldContainer else AccentRoseContainer,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (passed) AccentEmeraldBorder else AccentRose.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("native_build_result_summary")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = if (passed) AccentEmerald else AccentRose,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    text = if (passed) {
                                        when {
                                            compileState.signedApkArtifact != null ->
                                                "Built & signed ${compileState.signedApkArtifact.fileName} in ${compileState.durationMs ?: 0}ms"
                                            compileState.unsignedApkArtifact != null ->
                                                "Built ${compileState.unsignedApkArtifact.fileName} in ${compileState.durationMs ?: 0}ms"
                                            else ->
                                                "Compiled ${compileState.compiledClassFiles.size} .class file(s) in ${compileState.durationMs ?: 0}ms"
                                        }
                                    } else {
                                        "Failed: ${compileState.errorCount} error(s), ${compileState.warningCount} warning(s) (${compileState.durationMs ?: 0}ms)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (passed) AccentEmerald else AccentRose
                                )
                            }
                        }

                        // AI Auto-Fix Hook when ECJ fails with Java compiler errors
                        if (!passed &&
                            compileState.ecjStatus == BuildStageStatus.FAILED &&
                            selectedProject != null &&
                            toolchainStatus.isAndroidJarReady
                        ) {
                            Button(
                                onClick = { onAutoFixJavaErrors(selectedProject.projectPath) },
                                enabled = !compileState.isAutoFixingJava,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentViolet,
                                    contentColor = OnBrandLight
                                ),
                                contentPadding = PaddingValues(vertical = 11.dp, horizontal = 14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("native_build_autofix_ai_button")
                            ) {
                                if (compileState.isAutoFixingJava) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = OnBrandLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AI Fixing Java Errors...", style = MaterialTheme.typography.labelLarge)
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "✨ Auto-Fix Java Errors with AI",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Artifacts Output Card (signed.apk + unsigned.apk + classes.dex)
        if (compileState.signedApkArtifact != null || compileState.dexArtifact != null || compileState.unsignedApkArtifact != null) {
            item(key = "artifacts_output_card") {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, AccentEmeraldBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("native_build_artifacts_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (compileState.signedApkArtifact != null) Icons.Filled.VerifiedUser else Icons.Filled.Archive,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(19.dp)
                                )
                                Text(
                                    text = "Build Artifacts",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Surface(
                                color = if (compileState.signedApkArtifact != null) AccentEmeraldContainer else BrandIndigoContainer,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (compileState.signedApkArtifact != null) AccentEmeraldBorder else BrandIndigoBorder
                                )
                            ) {
                                Text(
                                    text = if (compileState.signedApkArtifact != null) "Signed • v1 + v2" else "Unsigned",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (compileState.signedApkArtifact != null) AccentEmerald else BrandIndigo,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }

                        compileState.signedApkArtifact?.let { signed ->
                            ArtifactItemRow(
                                artifact = signed,
                                badge = "SIGNED",
                                isPrimary = true
                            )

                            Button(
                                onClick = { onShareSignedApk(signed) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentEmerald,
                                    contentColor = OnBrandLight
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("native_build_share_signed_apk_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Share / Save Signed APK",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        compileState.unsignedApkArtifact?.let { apk ->
                            ArtifactItemRow(
                                artifact = apk,
                                badge = "APK",
                                isPrimary = false
                            )
                        }

                        compileState.dexArtifact?.let { dex ->
                            ArtifactItemRow(
                                artifact = dex,
                                badge = "DEX",
                                isPrimary = false
                            )
                        }
                    }
                }
            }
        }

        // 4. Live Compiler & Packager Console Card
        item(key = "compiler_console_card") {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderLight),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("native_build_compiler_console")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = BrandIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Build Pipeline Console",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    if (compileState.logs.isEmpty()) {
                        Surface(
                            color = SurfaceBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Tap \"Build Signed APK\" to run ECJ → DEX → Package → Sign (v1+v2), or \"Compile Java Only (ECJ)\" for fast syntax checks.",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.5.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = SurfaceBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                compileState.logs.forEach { entry ->
                                    CompilerLogRow(entry = entry)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Generated .class Bytecode Output Card
        if (compileState.compiledClassFiles.isNotEmpty()) {
            item(key = "compiled_classes_card") {
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("native_build_classes_list")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Compiled Java Bytecode (${compileState.compiledClassFiles.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Intermediate .class files inside build/classes/",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        compileState.compiledClassFiles.forEach { classPath ->
                            Surface(
                                color = AccentVioletContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "build/classes/$classPath",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.5.sp,
                                    color = AccentViolet,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "native_bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BuildPipelineStepper(
    ecjStatus: BuildStageStatus,
    dexStatus: BuildStageStatus,
    packageStatus: BuildStageStatus,
    signStatus: BuildStageStatus
) {
    Surface(
        color = SurfaceBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("native_build_pipeline_stepper")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PipelineStepNode(
                index = "1",
                title = "ECJ",
                subtitle = ".class",
                status = ecjStatus,
                modifier = Modifier.weight(1f)
            )
            StepperConnector(active = ecjStatus == BuildStageStatus.SUCCESS)
            PipelineStepNode(
                index = "2",
                title = "DEX",
                subtitle = "dex",
                status = dexStatus,
                modifier = Modifier.weight(1f)
            )
            StepperConnector(active = dexStatus == BuildStageStatus.SUCCESS)
            PipelineStepNode(
                index = "3",
                title = "Package",
                subtitle = "unsigned",
                status = packageStatus,
                modifier = Modifier.weight(1f)
            )
            StepperConnector(active = packageStatus == BuildStageStatus.SUCCESS)
            PipelineStepNode(
                index = "4",
                title = "Sign",
                subtitle = "v1+v2",
                status = signStatus,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PipelineStepNode(
    index: String,
    title: String,
    subtitle: String,
    status: BuildStageStatus,
    modifier: Modifier = Modifier
) {
    val circleBg = when (status) {
        BuildStageStatus.SUCCESS -> AccentEmerald
        BuildStageStatus.RUNNING -> BrandIndigo
        BuildStageStatus.FAILED -> AccentRose
        BuildStageStatus.IDLE, BuildStageStatus.SKIPPED -> SurfaceElevated
    }
    val borderColor = when (status) {
        BuildStageStatus.SUCCESS -> AccentEmerald
        BuildStageStatus.RUNNING -> BrandIndigo
        BuildStageStatus.FAILED -> AccentRose
        BuildStageStatus.IDLE, BuildStageStatus.SKIPPED -> BorderLight
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            color = circleBg,
            shape = CircleShape,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (status) {
                    BuildStageStatus.RUNNING -> {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = OnBrandLight,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    BuildStageStatus.SUCCESS -> {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "$title complete",
                            tint = OnBrandLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    BuildStageStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "$title failed",
                            tint = OnBrandLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    BuildStageStatus.IDLE, BuildStageStatus.SKIPPED -> {
                        Text(
                            text = index,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = when (status) {
                BuildStageStatus.SUCCESS -> AccentEmerald
                BuildStageStatus.RUNNING -> BrandIndigo
                BuildStageStatus.FAILED -> AccentRose
                else -> TextPrimary
            },
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepperConnector(active: Boolean) {
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) AccentEmerald else BorderLight)
    )
}

@Composable
private fun ArtifactItemRow(
    artifact: NativeBuildArtifact,
    badge: String,
    isPrimary: Boolean
) {
    Surface(
        color = if (isPrimary) AccentEmeraldContainer.copy(alpha = 0.55f) else SurfaceBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isPrimary) AccentEmeraldBorder else BorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = if (isPrimary) AccentEmerald else BrandIndigoContainer,
                shape = RoundedCornerShape(8.dp),
                border = if (isPrimary) null else BorderStroke(1.dp, BrandIndigoBorder)
            ) {
                Text(
                    text = badge,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrimary) OnBrandLight else BrandIndigo,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = artifact.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artifact.relativePath,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = WebToApkBuilder.formatSize(artifact.sizeBytes),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isPrimary) AccentEmerald else BrandIndigo
            )
        }
    }
}

@Composable
private fun CompilerLogRow(entry: CompilerLogEntry) {
    val badgeBg = when (entry.level) {
        CompilerLogLevel.INFO -> BrandIndigoContainer
        CompilerLogLevel.WARNING -> AccentAmberContainer
        CompilerLogLevel.ERROR -> AccentRoseContainer
    }
    val badgeFg = when (entry.level) {
        CompilerLogLevel.INFO -> BrandIndigo
        CompilerLogLevel.WARNING -> AccentAmber
        CompilerLogLevel.ERROR -> AccentRose
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = entry.level.badge,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeFg,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (!entry.filePath.isNullOrBlank()) {
                Text(
                    text = buildString {
                        append(entry.filePath)
                        entry.lineNumber?.let { append(":$it") }
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = entry.message,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = when (entry.level) {
                CompilerLogLevel.ERROR -> AccentRose
                CompilerLogLevel.WARNING -> TextPrimary
                CompilerLogLevel.INFO -> TextSecondary
            }
        )
    }
}
