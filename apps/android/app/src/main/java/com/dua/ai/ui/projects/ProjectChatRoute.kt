package com.dua.ai.ui.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import com.dua.ai.ui.theme.ProjectTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.dua.ai.ui.chat.ChatFeature
import com.dua.ai.ui.chat.ChatRoute
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.voice.ReplyVoice
import com.dua.ai.viewmodel.ChatViewModel

@Composable internal fun ProjectChatRoute(viewModel: ChatViewModel, voice: ReplyVoice, onBack: () -> Unit,
    onDetail: (String) -> Unit, onSettings: () -> Unit, onDestination: (DuaDestination) -> Unit,
    onMic: () -> Unit, onSetup: () -> Unit, onVoice: () -> Unit) {
    val settings by viewModel.localSettings.collectAsState()
    val projectId by viewModel.projectId.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val busy by viewModel.dataBusy.collectAsState()
    val project = settings.projects.find { it.id == projectId }
    if (project == null) {
        Column(Modifier.fillMaxSize().background(ProjectTheme.colors.background)) {
            ProjectHeader("Projects", onBack) {}
            ProjectNotice("Project settings are missing from this cache. Restore cloud history to reopen the project.")
        }
        return
    }
    val context = LocalContext.current
    var notice by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val back = { if (!busy) { viewModel.startNewChat(); viewModel.selectAuto(); onBack() } }
    if (instructions) {
        ProjectInstructionsEditor(project.instructions, {
        if (viewModel.updateProjectPresentation(project.id, project.name, it, project.details?.icon ?: "folder", project.details?.color ?: "purple")) instructions = false
    }, { instructions = false })
        return
    }
    ChatRoute(viewModel, voice, onSettings, onMic, onSetup, onOpenDestination = onDestination,
        onOpenVoiceMode = onVoice, feature = ChatFeature.Projects, onExitFeature = back,
        topBar = {
            Column {
                ProjectDetailHeader(project, "Chat", onMode = { if (it == "Work") onDetail("work") }, onBack = back,
                    onAction = { action ->
                        if (typing || busy) notice = "Finish the current response before changing the project."
                        else when (action) {
                            ProjectAction.NewChat -> viewModel.startNewChat()
                            ProjectAction.Pin -> viewModel.pinProject(project.id)
                            ProjectAction.Edit -> onDetail("edit")
                            ProjectAction.Instructions -> instructions = true
                            ProjectAction.AddHome -> notice = ProjectShortcuts.pin(context, project)
                            ProjectAction.Delete -> deleting = true
                        }
                    })
                ProjectNotice(notice)
            }
        })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete project?") },
        text = { Text("Remove “${project.name}” from Projects? Its chats and stored files stay available in History and Data controls.") },
        confirmButton = { TextButton({ if (viewModel.deleteProject(project.id)) { deleting = false; onDetail("list") } }, enabled = !typing && !busy) { Text("Delete project") } },
        dismissButton = { TextButton({ deleting = false }) { Text("Cancel") } })
}
