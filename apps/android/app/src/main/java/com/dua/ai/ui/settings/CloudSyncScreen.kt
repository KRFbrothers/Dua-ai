package com.dua.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dua.ai.account.AccountState
import com.dua.ai.setup.AutomaticSyncState
import com.dua.ai.ui.chat.SheetFilledButton
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.chat.SheetTonalButton
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.components.PageBody
import com.dua.ai.ui.components.PageCardTitle
import com.dua.ai.ui.components.PageNote
import com.dua.ai.ui.components.PagePaddedCard
import com.dua.ai.ui.components.PageStatus
import com.dua.ai.ui.components.SettingsGroup
import com.dua.ai.ui.components.SettingsNavRow
import com.dua.ai.ui.components.SettingsRowDivider
import com.dua.ai.ui.components.SettingsToggleRow
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.viewmodel.ChatViewModel

/** Every Cloud sync action, so the page can be driven without a view model (tests, previews). */
class CloudSyncActions(
    val onProfile: () -> Unit = {}, val onEnableAuto: () -> Unit = {}, val onDisableAuto: () -> Unit = {}, val onResetAuto: () -> Unit = {},
    val onResumeAuto: () -> Unit = {}, val onRetryAuto: () -> Unit = {}, val onReview: (String) -> Unit = {}, val onConfirm: () -> Unit = {},
    val onCancelReview: () -> Unit = {},
)

/** Cloud sync: automatic foreground sync and reviewed manual snapshot transfers. Sign-in and Log out live in Profile. */
@Composable
fun CloudSyncScreen(viewModel: ChatViewModel, onBack: () -> Unit, onProfile: () -> Unit) {
    val state by viewModel.account.state.collectAsState()
    val preview by viewModel.cloudPreview.collectAsState()
    val sync by viewModel.automaticSyncState.collectAsState()
    val dataBusy by viewModel.dataBusy.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadAccount() }
    CloudSyncContent(state, preview, sync, dataBusy, onBack, CloudSyncActions(
        onProfile = onProfile, onEnableAuto = { viewModel.enableAutomaticSync() }, onDisableAuto = { viewModel.disableAutomaticSync() },
        onResetAuto = { viewModel.resetAutomaticSync() }, onResumeAuto = { viewModel.resumeAutomaticSyncAfterReview() }, onRetryAuto = { viewModel.retryAutomaticSync() },
        onReview = { viewModel.reviewCloud(it) }, onConfirm = { viewModel.confirmCloud() }, onCancelReview = { viewModel.cancelCloudReview() }))
}

@Composable
fun CloudSyncContent(state: AccountState, preview: ChatViewModel.CloudPreview?, sync: AutomaticSyncState, dataBusy: Boolean, onBack: () -> Unit, actions: CloudSyncActions) {
    DuaPageScaffold(title = "Cloud sync", onBack = onBack) {
        PageNote(state.user?.email ?: "Not signed in")
        if (state.user == null) SheetTextButton("Sign in from Profile", onClick = actions.onProfile)
        SettingsGroup(title = "Automatic sync") {
            SettingsToggleRow(icon = null, title = "Automatic sync on this device", checked = sync.enabled,
                enabled = sync.phase != "checking" && !state.busy && !dataBusy && (sync.enabled || (state.configured && state.user != null && preview == null)),
                onCheckedChange = { enabled -> if (enabled) actions.onEnableAuto() else actions.onDisableAuto() })
            SettingsRowDivider()
            Column(Modifier.fillMaxWidth().padding(PageSizes.cardPadding), verticalArrangement = Arrangement.spacedBy(PageSizes.itemGap)) {
                PageNote("When enabled, projects, instructions, tasks, saved chats, drafts, memory and chat metadata sync while this app is open and online. Raw file/audio bytes stay in private Storage. Sync pauses during replies and manual reviews.")
                PageStatus(sync.message)
                if (sync.phase == "checking") {
                    SheetTextButton("Cancel sync setup", onClick = actions.onDisableAuto)
                } else if (sync.phase == "storage-error") {
                    SheetTextButton("Reset automatic sync settings", onClick = actions.onResetAuto, enabled = !state.busy && !dataBusy)
                    PageNote("Reset clears sync consent and guards on this device. Cached chats and memories stay until the next server sync. Review both copies before enabling again.")
                } else if (sync.reviewRequired) {
                    PageNote("Use the manual actions below to inspect the server snapshot and this phone’s cache. Resume only after checking the changes; remaining local data can be uploaded again.")
                    SheetTonalButton("Resume after review", onClick = actions.onResumeAuto, enabled = state.user != null && !state.busy && !dataBusy && preview == null)
                } else if (sync.enabled) {
                    SheetTextButton("Check sync / Retry", onClick = actions.onRetryAuto, enabled = state.user != null && !state.busy && !dataBusy && preview == null)
                }
            }
        }
        val manualEnabled = state.configured && state.user != null && !state.busy
        SettingsGroup(title = "Manual snapshot") {
            SettingsNavRow(Icons.Outlined.CloudUpload, "Review upload", enabled = manualEnabled, onClick = { actions.onReview("upload") })
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.CloudDownload, "Review download", enabled = manualEnabled, onClick = { actions.onReview("download") })
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.CloudOff, "Clear cloud snapshot", titleColor = PageTheme.colors.danger, enabled = manualEnabled, onClick = { actions.onReview("delete") })
        }
        PageNote("Download restores server chats at their original IDs, including the open chat. Different phone versions stay as unsynced copies, and phone-only chats stay cached. Server memory, project instructions and tasks replace cached settings; phone-only projects are kept. Upload replaces the server snapshot after review. Includes drafts and chat metadata; no file/audio bytes.")
        preview?.let { reviewed ->
            PagePaddedCard {
                PageCardTitle("Confirm ${reviewed.kind} · cloud revision ${reviewed.review.revision}")
                if (reviewed.kind != "delete") {
                    reviewed.review.snapshot.projects.forEach { project ->
                        PageBody("Project: ${project.name}${if (project.archived) " (archived)" else ""}\nInstructions: ${project.instructions.ifBlank { "None" }}")
                        if (project.id != com.dua.ai.projects.ProjectCodec.PERSONAL) PageBody("Memory: ${if (project.memory.enabled) "On" else "Off"}\n" + project.memory.notes.joinToString("\n"))
                        project.tasks.forEach { task -> PageBody("${if (task.completed) "✓" else "○"} ${task.title}${task.dueAt?.let { "\nDue: ${com.dua.ai.tasks.TaskDates.display(it)} · ${java.time.ZoneId.systemDefault().id}" }.orEmpty()}") }
                    }
                    reviewed.review.snapshot.chats.forEach { chat -> PageBody("${chat.title}: ${chat.messages.size} messages\nDraft: ${chat.draft}") }
                    PageBody("Memories:\n" + reviewed.review.snapshot.memory.joinToString("\n"))
                } else PageBody("Server chats, memory, project settings and tasks are cleared. This phone's cache stays saved.")
                PageNote("Check the account shown above. Automatic uploads require consent on this device. Personal memory keeps its phone on/off setting. Other projects restore their server memory switch and notes.")
                SheetFilledButton("Confirm", onClick = actions.onConfirm, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                SheetTextButton("Cancel", onClick = actions.onCancelReview, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
        }
        if (!state.configured) PageNote("Supabase account setup is not configured yet. Local data is unchanged.")
        if (state.notice.isNotBlank()) PageStatus(state.notice)
    }
}
