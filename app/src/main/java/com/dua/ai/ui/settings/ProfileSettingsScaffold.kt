package com.dua.ai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Approved profile hub. Back, search and info remain reachable while its sections scroll. */
@Composable
internal fun ProfileSettingsScaffold(name: String, onBack: () -> Unit, onEdit: () -> Unit,
    onSearch: () -> Unit, onAbout: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = 8.dp)) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp).size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Box(Modifier.align(Alignment.Center).size(80.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(72.dp).align(Alignment.Center)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PersonOutline, null, Modifier.size(36.dp)) }
                }
                IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.BottomEnd).size(48.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Icon(Icons.Outlined.Edit, "Edit profile photo", Modifier.padding(4.dp).size(16.dp))
                    }
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(top = 12.dp)) {
                IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Search, "Search settings") }
                IconButton(onClick = onAbout, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Info, "About Dua AI") }
            }
        }
        Text(name, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f).widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
    }
}
