package com.dua.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.theme.DuaPageTheme

/** Compatibility entry for the old Projects sheet: same empty-by-default reference list and editor. */
@Composable
fun ProjectsFeaturePage(state: ChatUiState, actions: ChatActions, onBack: () -> Unit) = DuaPageTheme {
    var creating by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(com.dua.ai.ui.theme.ProjectTheme.colors.background)) {
        if (creating) com.dua.ai.ui.projects.NewProjectScreen(notice = state.projectNotice, busy = state.typing,
            onBack = { creating = false }, onSave = { name, instructions, icon, color ->
                actions.onCreateProject(name, instructions, icon, color).also { if (it) creating = false }
            })
        else com.dua.ai.ui.projects.ProjectsList(state.projects, state.savedChats, state.typing, state.projectNotice, onBack,
            onNew = { creating = true }, onOpen = { id -> actions.onSelectProject(id); actions.onOpenDestination(com.dua.ai.ui.navigation.DuaDestination.ProjectChat) })
    }
}
