package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R

sealed class IdeDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    data object Chat : IdeDestination(
        route = ROUTE_CHAT,
        labelRes = R.string.nav_chat,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_tab_chat"
    )

    data object Files : IdeDestination(
        route = ROUTE_FILES,
        labelRes = R.string.nav_files,
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        testTag = "nav_tab_files"
    )

    data object Editor : IdeDestination(
        route = ROUTE_EDITOR,
        labelRes = R.string.nav_editor,
        selectedIcon = Icons.Filled.Code,
        unselectedIcon = Icons.Outlined.Code,
        testTag = "nav_tab_editor"
    )

    data object Preview : IdeDestination(
        route = ROUTE_PREVIEW,
        labelRes = R.string.nav_preview,
        selectedIcon = Icons.Filled.PlayArrow,
        unselectedIcon = Icons.Outlined.PlayArrow,
        testTag = "nav_tab_preview"
    )

    data object Web2Apk : IdeDestination(
        route = ROUTE_WEB2APK,
        labelRes = R.string.nav_web2apk,
        selectedIcon = Icons.Filled.InstallMobile,
        unselectedIcon = Icons.Outlined.InstallMobile,
        testTag = "nav_tab_web2apk"
    )

    data object Tools : IdeDestination(
        route = ROUTE_TOOLS,
        labelRes = R.string.nav_tools,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_tab_tools"
    )

    companion object {
        const val ROUTE_CHAT = "chat"
        const val ROUTE_FILES = "files"
        const val ROUTE_EDITOR = "editor"
        const val ROUTE_PREVIEW = "preview"
        const val ROUTE_WEB2APK = "web2apk"
        const val ROUTE_TOOLS = "tools"

        val allTabs: List<IdeDestination> by lazy {
            listOf(Chat, Files, Editor, Preview, Web2Apk, Tools)
        }
    }
}
