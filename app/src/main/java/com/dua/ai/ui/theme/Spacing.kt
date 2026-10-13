package com.dua.ai.ui.theme

import androidx.compose.ui.unit.dp

/** 4-dp spacing scale. Use these instead of literal dp values for padding and gaps. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 48.dp
}

/** Component sizes shared across screens. */
object Sizes {
    /** Minimum touch target (Material and Android accessibility guidance). */
    val touchTarget = 48.dp
    val icon = 24.dp
    val iconSmall = 20.dp
    val iconTiny = 16.dp
    /** Readable line length on tablets and foldables. */
    val contentMaxWidth = 760.dp
    val drawerWidth = 320.dp
    /** Round buttons in the chat top bar (menu, new chat). */
    val topBarButton = 44.dp
    val topBarHeight = 64.dp
    /** Mode chips above the composer (Run code, Reasoning, Search). */
    val modeChipHeight = 44.dp
    /** Round buttons and the Auto pill inside the composer. */
    val composerButton = 36.dp
    val sendButton = 38.dp
    /** Home logo height on a roomy screen; it shrinks on short screens and with the keyboard open. */
    val homeLogoHeight = 196.dp
    /** Animated DUA logo on the empty Home screen (Dabba). */
    val homeLogo = 130.dp
    /** Composer round buttons: +, mic, voice/send/stop. */
    val composerRound = 48.dp
    /** Rule either side of the DUA wordmark. */
    val wordmarkRule = 22.dp
    val wordmarkRuleThickness = 1.5.dp
    val wordmarkSquare = 7.dp
}
