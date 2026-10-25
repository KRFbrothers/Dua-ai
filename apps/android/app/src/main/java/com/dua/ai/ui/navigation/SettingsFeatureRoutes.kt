package com.dua.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** The Settings hub's functional sub-pages cannot fall through to Coming soon. */
internal fun NavGraphBuilder.settingsFeaturePages(memory: @Composable () -> Unit, general: @Composable () -> Unit,
    notifications: @Composable () -> Unit, usage: @Composable () -> Unit, safety: @Composable () -> Unit, security: @Composable () -> Unit) {
    composable(DuaDestination.Memory.route) { memory() }
    composable(DuaDestination.General.route) { general() }
    composable(DuaDestination.Notifications.route) { notifications() }
    composable(DuaDestination.UsageLimits.route) { usage() }
    composable(DuaDestination.Safety.route) { safety() }
    composable(DuaDestination.SecurityLogin.route) { security() }
}
