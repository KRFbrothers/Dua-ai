package com.dua.ai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.dua.ai.R
import com.dua.ai.ui.theme.DuaTheme

/** Original calligraphy paths and fixed gradients, independent of the selected accent. */
@Composable
fun DuaCalligraphyLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(
            if (DuaTheme.colors.isDark) R.drawable.ic_dua_logo_option1_dark
            else R.drawable.ic_dua_logo_option1_light,
        ),
        contentDescription = "Dua AI",
        modifier = modifier.aspectRatio(610f / 1024f),
    )
}
