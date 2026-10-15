package com.dua.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatFeature
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.chat.ProjectsContent
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.theme.DuaPageTheme
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.viewmodel.ChatViewModel

/** Missing destinations stay honest; no task is simulated by the disabled new-session button. */
@Composable
fun FeatureComingSoonScreen(title: String, onBack: () -> Unit) = DuaPageTheme {
    Column(Modifier.fillMaxSize().background(PageTheme.colors.background).navigationBarsPadding()) {
        FeatureTopBar(title, onBack, onNewSession = {}, newSessionEnabled = false)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Coming soon", style = MaterialTheme.typography.bodyLarge, color = PageTheme.colors.subText)
        }
    }
}

@Composable
fun ProjectsScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpenProject: () -> Unit) {
    val settings by viewModel.localSettings.collectAsState()
    val selected by viewModel.projectId.collectAsState()
    val notice by viewModel.projectNotice.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val busy by viewModel.dataBusy.collectAsState()
    ProjectsFeaturePage(ChatUiState(projects = settings.projects, projectId = selected, projectNotice = notice,
        typing = typing || busy, projectTasksEnabled = integrations.projectTasksEnabled,
        taskDueDatesEnabled = integrations.taskDueDatesEnabled, projectMemoryEnabled = integrations.projectMemoryEnabled),
        ChatActions(onSelectProject = { viewModel.selectProject(it); if (viewModel.projectId.value == it && viewModel.projectNotice.value.isBlank()) onOpenProject() }, onSaveProject = viewModel::saveProject,
            onSaveProjectMemory = viewModel::saveProjectMemory, onSaveTask = viewModel::saveTask,
            onToggleTask = viewModel::toggleTask, onDeleteTask = viewModel::deleteTask), onBack)
}

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
