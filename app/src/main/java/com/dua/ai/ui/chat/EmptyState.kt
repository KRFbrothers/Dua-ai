package com.dua.ai.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.R
import com.dua.ai.ui.icons.DuaIcons
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.MontserratFontFamily
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.ui.theme.rememberMotionAllowed

/** Ease-out curve for the entrance (fast start, gentle stop). */
private val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Home (new chat): the Dua logo and tagline, optically centred a little above the middle. The logo shrinks
 * on short screens and hides when the keyboard leaves little room. On open the group fades in with a slight
 * rise and scale (500 ms, ease-out), skipped when the system removes animations. A soft light sweep crosses
 * the logo every 9 s (see [rememberLogoShimmer]). [shimmerPreview] pins the sweep at one point (screenshots).
 */
@Composable
fun EmptyState(modifier: Modifier = Modifier, shimmerPreview: Float? = null,
    suggestions: List<PromptSuggestion> = emptyList(), onSuggestion: (String) -> Unit = {}) {
    val motion = rememberMotionAllowed()
    val shimmer = rememberLogoShimmer(enabled = motion && shimmerPreview == null)
    val entrance = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { if (motion) entrance.animateTo(1f, tween(durationMillis = 500, easing = EaseOut)) }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // A smaller original logo leaves room for prompts; hide it below 220dp (keyboard/landscape).
        val logoHeight = (maxHeight * 0.30f).coerceIn(80.dp, 160.dp)
        val showLogo = maxHeight >= 220.dp
        val logoGap = Spacing.lg
        val minHeight = maxHeight
        val taglineSize = if (maxWidth < 360.dp) 18.sp else 20.sp
        val showSuggestions = maxHeight >= 420.dp && LocalDensity.current.fontScale <= 1.4f && suggestions.isNotEmpty()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = Spacing.xxl, vertical = Spacing.lg)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // A bit above centre reads as centred (the composer below carries visual weight).
                    modifier = Modifier.align(BiasAlignment(0f, -0.08f)).graphicsLayer {
                        val p = entrance.value
                        alpha = p
                        translationY = (1f - p) * rise
                        val scale = 0.96f + 0.04f * p
                        scaleX = scale; scaleY = scale
                    },
                ) {
                    if (showLogo) {
                        // Light theme: darker chrome with a soft shadow, so "DUA" stays readable on white. The light
                        // asset has a 4% shadow margin, so it is drawn 4% taller to keep the same visible size.
                        val dark = DuaTheme.colors.isDark
                        val sweep = Modifier.logoShimmer(progress = { shimmerPreview ?: shimmer.floatValue }, strength = if (dark) .38f else .42f)
                        if (dark) Image(painterResource(R.drawable.dua_logo_home), contentDescription = "Dua logo", modifier = Modifier.height(logoHeight).then(sweep))
                        else Image(painterResource(R.drawable.dua_logo_home_light), contentDescription = "Dua logo", modifier = Modifier.height(logoHeight * 1.04f).then(sweep))
                        Spacer(Modifier.height(logoGap))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                        Text("Dua always with you", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
                            style = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium,
                                fontSize = taglineSize, lineHeight = 28.sp), modifier = Modifier.weight(1f, fill = false))
                        Icon(DuaIcons.Sparkle, contentDescription = null, tint = DuaTheme.colors.sparkle,
                            modifier = Modifier.padding(start = Spacing.xxs).size(Sizes.iconSmall + 2.dp))
                    }
                    if (showSuggestions) {
                        Spacer(Modifier.height(Spacing.xxl))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            suggestions.take(2).forEach { suggestion ->
                                Surface(onClick = { onSuggestion(suggestion.prompt) }, shape = DuaShape.card,
                                    color = MaterialTheme.colorScheme.background,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.weight(1f)) {
                                    Column(Modifier.heightIn(min = 72.dp).padding(Spacing.md), verticalArrangement = Arrangement.Center) {
                                        Text(suggestion.title, style = MaterialTheme.typography.labelLarge)
                                        Text(suggestion.category, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Seconds between logo light sweeps, and how long one sweep takes. */
private const val SHIMMER_INTERVAL_MS = 9_000L
private const val SHIMMER_DURATION_MS = 1_200

/**
 * Progress of the logo's light sweep: 0..1 while a sweep runs, -1 between sweeps. Runs only while this screen's
 * lifecycle is RESUMED (so not with the screen off, the app in the background or another destination on top)
 * and only when [enabled] (animations allowed). Draw-phase only: no recomposition per frame.
 */
@Composable
private fun rememberLogoShimmer(enabled: Boolean): MutableFloatState {
    val progress = remember { mutableFloatStateOf(-1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled, lifecycle) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    delay(SHIMMER_INTERVAL_MS)
                    animate(0f, 1f, animationSpec = tween(SHIMMER_DURATION_MS, easing = FastOutSlowInEasing)) { value, _ -> progress.floatValue = value }
                    progress.floatValue = -1f
                }
            } finally {
                progress.floatValue = -1f
            }
        }
    }
    return progress
}

/** A diagonal band of light drawn over the image and clipped to its opaque pixels (SrcAtop on an offscreen layer). */
private fun Modifier.logoShimmer(progress: () -> Float, strength: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val p = progress()
        if (p < 0f) return@drawWithContent
        val diagonal = size.width + size.height
        val band = diagonal * .12f
        // Band centre travels along x + y from just before the top-left corner to just past the bottom-right.
        val centre = -band + p * (diagonal + 2 * band)
        val start = Offset((centre - band) / 2f, (centre - band) / 2f)
        val end = Offset((centre + band) / 2f, (centre + band) / 2f)
        val light = Color.White
        drawRect(
            brush = Brush.linearGradient(listOf(light.copy(alpha = 0f), light.copy(alpha = strength), light.copy(alpha = 0f)), start = start, end = end),
            blendMode = BlendMode.SrcAtop,
        )
    }
