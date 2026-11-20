package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.builder.WebToApkBuilder
import com.example.domain.model.KeystoreInfo
import com.example.domain.model.ToolchainStatus
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentAmberContainer
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentEmeraldBorder
import com.example.ui.theme.AccentEmeraldContainer
import com.example.ui.theme.AccentRose
import com.example.ui.theme.AccentRoseContainer
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BrandIndigo
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
fun ToolsToolchainCard(
    status: ToolchainStatus,
    keystoreInfo: KeystoreInfo,
    onImportAndroidJar: (Uri) -> Unit,
    onDownloadAndroidJar: (String) -> Unit,
    onDeleteAndroidJar: () -> Unit,
    onCreateCustomKeystore: (alias: String, commonName: String, organization: String, password: String) -> Unit,
    onResetToDebugKeystore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var downloadUrl by remember { mutableStateOf("") }
    var showCreateKeystoreDialog by remember { mutableStateOf(false) }

    val openJarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportAndroidJar(uri)
        }
    }

    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BorderLight),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .testTag("tools_toolchain_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (status.isAndroidJarReady) AccentEmeraldContainer else AccentAmberContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (status.isAndroidJarReady) AccentEmeraldBorder else AccentAmber.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = if (status.isAndroidJarReady) AccentEmerald else AccentAmber,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.toolchain_manager_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "On-device ECJ compile SDK (android.jar)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    color = if (status.isAndroidJarReady) AccentEmeraldContainer else AccentAmberContainer,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        1.dp,
                        if (status.isAndroidJarReady) AccentEmeraldBorder else AccentAmber.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (status.isAndroidJarReady) "Ready" else "Missing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (status.isAndroidJarReady) AccentEmerald else AccentAmber,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            // Status Info Panel
            Surface(
                color = if (status.isAndroidJarReady) AccentEmeraldContainer.copy(alpha = 0.55f) else AccentAmberContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (status.isAndroidJarReady) AccentEmeraldBorder else AccentAmber.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (status.isAndroidJarReady) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (status.isAndroidJarReady) AccentEmerald else AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (status.isAndroidJarReady) {
                                "android.jar Ready (${WebToApkBuilder.formatSize(status.fileSizeBytes)})"
                            } else {
                                "Missing — Import/Download Required"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (status.isAndroidJarReady) AccentEmerald else TextPrimary
                        )
                        Text(
                            text = if (status.isAndroidJarReady) {
                                "Path: ${status.filePath}"
                            } else {
                                "Import any Android platform android.jar (API 26–35) to compile native Java apps."
                            },
                            fontFamily = if (status.isAndroidJarReady) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // aapt2 Resource Linker Availability Row
            Surface(
                color = if (status.isAapt2Ready) AccentEmeraldContainer.copy(alpha = 0.45f) else SurfaceBackground,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (status.isAapt2Ready) AccentEmeraldBorder else BorderLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toolchain_aapt2_status_row")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "aapt2 Resource Linker",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (status.isAapt2Ready) {
                                "Binary detected: ${status.aapt2Path.orEmpty()}"
                            } else {
                                "Not bundled — APK packager uses ZipOutputStream fallback"
                            },
                            fontFamily = if (status.isAapt2Ready) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (status.isAapt2Ready) AccentEmeraldContainer else AccentAmberContainer,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            1.dp,
                            if (status.isAapt2Ready) AccentEmeraldBorder else AccentAmber.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (status.isAapt2Ready) "Ready" else "Optional / Fallback",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (status.isAapt2Ready) AccentEmerald else AccentAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Action Row: Import from Device + Delete/Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        openJarLauncher.launch(
                            arrayOf("application/java-archive", "application/octet-stream", "*/*")
                        )
                    },
                    enabled = !status.isWorking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandIndigo,
                        contentColor = OnBrandLight,
                        disabledContainerColor = SurfaceElevated,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toolchain_import_jar_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (status.isAndroidJarReady) "Replace android.jar" else "Import android.jar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                if (status.isAndroidJarReady) {
                    OutlinedButton(
                        onClick = onDeleteAndroidJar,
                        enabled = !status.isWorking,
                        border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                        modifier = Modifier.testTag("toolchain_delete_jar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete android.jar",
                            tint = AccentRose,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", color = AccentRose, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Download android.jar from Custom URL
            OutlinedTextField(
                value = downloadUrl,
                onValueChange = { downloadUrl = it },
                label = { Text("Direct android.jar URL (optional)") },
                placeholder = { Text("https://.../android.jar") },
                singleLine = true,
                enabled = !status.isWorking,
                colors = ideTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toolchain_download_url_input")
            )

            OutlinedButton(
                onClick = { onDownloadAndroidJar(downloadUrl) },
                enabled = downloadUrl.isNotBlank() && !status.isWorking,
                border = BorderStroke(
                    1.dp,
                    if (downloadUrl.isNotBlank() && !status.isWorking) BrandIndigo else BorderLight
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 11.dp, horizontal = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toolchain_download_jar_button")
            ) {
                if (status.isWorking) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = BrandIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = if (downloadUrl.isNotBlank()) BrandIndigo else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (status.isWorking) "Downloading / Importing..." else "Download android.jar from URL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (downloadUrl.isNotBlank() && !status.isWorking) BrandIndigo else TextMuted
                )
            }

            // Live Progress Bar & Status Message
            AnimatedVisibility(visible = status.isWorking || !status.statusMessage.isNullOrBlank()) {
                Surface(
                    color = if (status.isError) AccentRoseContainer else SurfaceBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (status.isError) AccentRose.copy(alpha = 0.4f) else BorderLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (status.isWorking) {
                            val prog = status.downloadProgress
                            if (prog != null) {
                                LinearProgressIndicator(
                                    progress = { prog },
                                    color = BrandIndigo,
                                    trackColor = BrandIndigoContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = BrandIndigo,
                                    trackColor = BrandIndigoContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
                        }
                        status.statusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status.isError) AccentRose else TextSecondary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = BorderLight)

            // Phase 8: APK Signing Keystore Section
            Surface(
                color = SurfaceBackground,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_keystore_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = if (keystoreInfo.isCustom) BrandIndigo else AccentEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "APK Signing Keystore (v1 + v2)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Alias: ${keystoreInfo.alias} • CN=${keystoreInfo.commonName}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = if (keystoreInfo.isCustom) BrandIndigoContainer else AccentEmeraldContainer,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                1.dp,
                                if (keystoreInfo.isCustom) BrandIndigo.copy(alpha = 0.35f) else AccentEmeraldBorder
                            )
                        ) {
                            Text(
                                text = if (keystoreInfo.isCustom) "Custom PKCS12" else "Auto-Debug",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (keystoreInfo.isCustom) BrandIndigo else AccentEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "${keystoreInfo.filePath} • ${if (keystoreInfo.exists) "RSA-2048 Ready" else "Auto-generates on first build"}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCreateKeystoreDialog = true },
                            enabled = !keystoreInfo.isWorking,
                            border = BorderStroke(1.dp, BrandIndigo),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tools_create_custom_keystore_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = BrandIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (keystoreInfo.isCustom) "New Release Key" else "Create Custom Keystore",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandIndigo,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (keystoreInfo.isCustom) {
                            OutlinedButton(
                                onClick = onResetToDebugKeystore,
                                enabled = !keystoreInfo.isWorking,
                                border = BorderStroke(1.dp, BorderLight),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                                modifier = Modifier.testTag("tools_reset_debug_keystore_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reset to Debug Keystore",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Debug",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    if (!keystoreInfo.statusMessage.isNullOrBlank()) {
                        Text(
                            text = keystoreInfo.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (keystoreInfo.isError) AccentRose else AccentEmerald
                        )
                    }
                }
            }
        }
    }

    if (showCreateKeystoreDialog) {
        CreateCustomKeystoreDialog(
            onDismiss = { showCreateKeystoreDialog = false },
            onCreate = { alias, cn, org, password ->
                showCreateKeystoreDialog = false
                onCreateCustomKeystore(alias, cn, org, password)
            }
        )
    }
}

@Composable
private fun CreateCustomKeystoreDialog(
    onDismiss: () -> Unit,
    onCreate: (alias: String, commonName: String, organization: String, password: String) -> Unit
) {
    var alias by remember { mutableStateOf("releasekey") }
    var commonName by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val isValid = alias.trim().length >= 2 && commonName.trim().isNotEmpty() && password.trim().length >= 6

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Create Release Keystore",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Generates an RSA-2048 X.509 certificate (25-year validity) saved as a PKCS12 (.p12) keystore in /toolchain/keystores/.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it.replace(" ", "") },
                    label = { Text("Key Alias") },
                    placeholder = { Text("releasekey") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keystore_alias_input")
                )
                OutlinedTextField(
                    value = commonName,
                    onValueChange = { commonName = it },
                    label = { Text("Common Name (CN)") },
                    placeholder = { Text("Your Name or App Studio") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keystore_cn_input")
                )
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it },
                    label = { Text("Organization (O) — optional") },
                    placeholder = { Text("Nova Studio") },
                    singleLine = true,
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keystore_org_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Keystore Password (min 6 chars)") },
                    placeholder = { Text("••••••") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = ideTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keystore_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(alias, commonName, organization, password) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandIndigo,
                    contentColor = OnBrandLight
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("keystore_create_confirm_button")
            ) {
                Text("Generate Keystore", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
