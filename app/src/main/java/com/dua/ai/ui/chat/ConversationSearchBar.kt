package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Search stays above the message list; controls wrap independently of the query field. */
@Composable
internal fun ConversationSearchBar(query: String, onQuery: (String) -> Unit, searching: Boolean,
    matches: List<ConversationMatch>, currentIndex: Int, onPrevious: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun leaveInput(action: () -> Unit) { focusManager.clearFocus(); keyboard?.hide(); action() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, onValueChange = { onQuery(it.take(ConversationSearch.MAX_QUERY_CHARS)) },
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Search within chat" },
                singleLine = true, label = { Text("Find in chat") })
            IconButton(onClick = { leaveInput(onClose) }, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Outlined.Close, "Close chat search")
            }
        }
        val status = when {
            query.isBlank() -> "Search messages in this chat"
            searching -> "Searching messages…"
            matches.isEmpty() -> "No matching messages"
            else -> "${currentIndex + 1} of ${matches.size} matching messages"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            IconButton(onClick = { leaveInput(onPrevious) }, enabled = matches.size > 1,
                modifier = Modifier.size(Sizes.touchTarget)) { Icon(Icons.Outlined.ArrowUpward, "Previous matching message") }
            IconButton(onClick = { leaveInput(onNext) }, enabled = matches.size > 1,
                modifier = Modifier.size(Sizes.touchTarget)) { Icon(Icons.Outlined.ArrowDownward, "Next matching message") }
        }
        matches.getOrNull(currentIndex)?.snippet?.let { snippet ->
            val highlighted = buildAnnotatedString {
                append(snippet.text)
                addStyle(SpanStyle(background = MaterialTheme.colorScheme.secondaryContainer,
                    color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold),
                    snippet.matchStart, snippet.matchEnd)
            }
            Text(highlighted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
        }
    }
}
