package com.dua.ai.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.dua.ai.ui.components.DuaPageScaffold

/** Registers a titled "Coming soon" page for every destination that has no dedicated screen yet. */
internal fun NavGraphBuilder.comingSoonPages(onBack: () -> Unit, guard: @Composable () -> Unit = {}) {
    DuaDestination.entries.forEach { destination ->
        val title = destination.comingSoonTitle ?: return@forEach
        composable(destination.route) {
            guard()
            ComingSoonPage(title, onBack)
        }
    }
}

@Composable
internal fun ComingSoonPage(title: String, onBack: () -> Unit) {
    DuaPageScaffold(title, onBack) {
        Text("Coming soon", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
