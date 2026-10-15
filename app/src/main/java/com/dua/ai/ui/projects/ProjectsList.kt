package com.dua.ai.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.history.SavedChat
import com.dua.ai.projects.ChatProject
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.ui.theme.*
import java.text.DateFormat
import java.util.Date

internal fun projectDate(project: ChatProject, chats: List<SavedChat>): Long? =
    (listOfNotNull(project.details?.updatedAt) + chats.filter { it.projectId == project.id }.map { it.updatedAt }).maxOrNull()?.takeIf { it > 0 }

@Composable internal fun ProjectsList(projects: List<ChatProject>, chats: List<SavedChat>, busy: Boolean, notice: String,
    onBack: () -> Unit, onNew: () -> Unit, onOpen: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    val c = ProjectTheme.colors
    val actual = remember(projects, chats) { ProjectCodec.userProjects(projects, chats) }
    val visible = remember(actual, filter, query, chats) {
        if (filter == "Shared with you") emptyList() else actual.filter { it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<ChatProject> { it.details?.pinned == true }.thenByDescending { projectDate(it, chats) ?: 0 })
    }
    Column(Modifier.fillMaxSize().testTag("projects-list")) {
        ProjectHeader("Projects", onBack) { ProjectCircle("New Project", ProjectIcons.Plus, !busy && ProjectCodec.hasCapacity(projects), onNew) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf("All", "Created by you", "Shared with you").forEach { label -> ProjectChip(label, filter == label, { filter = label }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (visible.isEmpty()) Column(Modifier.align(Alignment.Center).padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (filter == "Shared with you") "No shared projects" else if (query.isNotBlank()) "No projects found" else "No projects yet",
                    style = MaterialTheme.typography.titleMedium.merge(ProjectEmptyTitleStyle), color = c.text)
                Text(if (filter == "Shared with you") "Project sharing is not available yet." else if (query.isNotBlank()) "Try another search." else "Create your first project with +.",
                    Modifier.padding(top = Spacing.sm), style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = c.subText)
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                items(visible, key = { it.id }) { project -> ProjectListRow(project, chats, !busy) { onOpen(project.id) } }
            }
        }
        ProjectNotice(notice)
        Surface(Modifier.fillMaxWidth().padding(horizontal = ProjectSizes.searchPadding, vertical = Spacing.md).navigationBarsPadding(),
            shape = ProjectShapes.search, color = c.surface, shadowElevation = ProjectSizes.searchElevation) {
            Row(Modifier.fillMaxWidth().heightIn(min = ProjectSizes.searchHeight).padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(ProjectIcons.Search, null, Modifier.size(ProjectSizes.icon), tint = c.subText)
                BasicTextField(query, { query = it }, Modifier.weight(1f).heightIn(min = Sizes.touchTarget).wrapContentHeight()
                    .semantics { contentDescription = "Search projects" }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(ProjectChatRowStyle).copy(color = c.text), decorationBox = { input ->
                        Box(contentAlignment = Alignment.CenterStart) { if (query.isEmpty()) Text("Search", color = c.subText, style = MaterialTheme.typography.bodyLarge.merge(ProjectChatRowStyle)); input() }
                    })
            }
        }
    }
}
@Composable private fun ProjectListRow(project: ChatProject, chats: List<SavedChat>, enabled: Boolean, onClick: () -> Unit) {
    val c = ProjectTheme.colors
    val badge = projectBadgeColors(project.details?.color ?: "purple")
    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(Modifier.size(ProjectSizes.badge), shape = ProjectShapes.badge, color = badge.first) {
            Box(contentAlignment = Alignment.Center) { Icon(projectIcon(project.details?.icon ?: "folder"), null, Modifier.size(ProjectSizes.icon), tint = badge.second) }
        }
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.bodyLarge.merge(ProjectRowStyle), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            projectDate(project, chats)?.let { date -> Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(date)),
                style = MaterialTheme.typography.bodyMedium.merge(ProjectBodyStyle), color = c.date) }
        }
        if (project.details?.pinned == true) Icon(ProjectIcons.Pin, "Pinned project", Modifier.size(ProjectSizes.icon), tint = c.subText)
    }
}
