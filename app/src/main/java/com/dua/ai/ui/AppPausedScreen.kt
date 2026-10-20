/*
 * Adapted from Androidify's AppInactiveScreen.kt (https://github.com/android/androidify, read through
 * the fork https://github.com/KRFbrothers/androidify), Copyright 2025 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md. Changes: Dua's own
 * calligraphy logo (no Android robot art), server-provided message, Try again button, no top bar.
 */
package com.dua.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.components.DuaCalligraphyLogo
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Full-screen "App paused" page shown while the server reports maintenance (DUA_MAINTENANCE=1). */
@Composable
fun AppPausedScreen(message: String, checking: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
            .padding(horizontal = Spacing.xl).testTag("app-paused"),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DuaCalligraphyLogo(Modifier.height(96.dp))
        Spacer(Modifier.height(Spacing.lg))
        Text("App paused", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(Spacing.xs))
        Text("Your chats are safe on this phone.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onRetry, enabled = !checking, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
            if (checking) CircularProgressIndicator(Modifier.height(18.dp).widthIn(max = 18.dp), strokeWidth = 2.dp) else Text("Try again")
        }
    }
}
