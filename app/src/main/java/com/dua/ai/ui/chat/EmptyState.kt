package com.dua.ai.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dua.ai.R
import com.dua.ai.ui.icons.DuaIcons
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Home (new chat): the Dua logo and tagline, centred. The logo shrinks on short screens and hides when the keyboard leaves little room. */
@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Roughly a third of the free height, between 96 dp and the full size; hidden below 220 dp (keyboard open, landscape).
        val logo = (maxHeight * 0.44f).coerceIn(96.dp, Sizes.homeLogoHeight)
        val showLogo = maxHeight >= 220.dp
        val logoGap = if (maxHeight > 480.dp) Spacing.huge else Spacing.xxl
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xxl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (showLogo) {
                Image(painterResource(R.drawable.dua_logo_home), contentDescription = "Dua logo", modifier = Modifier.height(logo))
                Spacer(Modifier.height(logoGap))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text("Dua always with you", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.06.em))
                Icon(DuaIcons.Sparkle, contentDescription = null, tint = DuaTheme.colors.sparkle,
                    modifier = Modifier.padding(start = Spacing.sm).size(Sizes.iconSmall))
            }
        }
    }
}
