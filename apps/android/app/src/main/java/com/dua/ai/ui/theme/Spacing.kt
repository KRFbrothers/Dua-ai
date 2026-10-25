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

/** Bounded inline HTML/SVG/diagram canvas inside a chat code block. */
object CodePreviewSizes { val height = 320.dp }

/** Dynamic composer visuals; controls still occupy Sizes.touchTarget slots. */
object ComposerSizes {
    val actionButton = 44.dp
    val attachment = 60.dp
    val fileChipMaxWidth = 220.dp
    val tabShape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val expandedShape = DuaShape.composer
    val attachmentShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
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
    val topBarHeight = 64.dp
}
