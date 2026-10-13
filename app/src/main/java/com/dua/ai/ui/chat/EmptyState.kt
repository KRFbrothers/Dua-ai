package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.ui.screens.AnimatedVoiceCube

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    shimmerPreview: Float? = null,
    suggestions: List<PromptSuggestion> = emptyList(),
    onSuggestion: (String) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // --- CENTER HERO: 3D DUA LOGO & TAGLINE ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Metallic 3D Animated Cube / Logo with listening = false
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVoiceCube(listening = false)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // — DUA — with Cubes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(7.dp).background(Color(0xFF374151), shape = RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.width(22.dp).height(1.5.dp).background(Color(0xFF9CA3AF)))
                Text(
                    text = "DUA",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = Color(0xFF1F2937)
                )
                Box(modifier = Modifier.width(22.dp).height(1.5.dp).background(Color(0xFF9CA3AF)))
                Box(modifier = Modifier.size(7.dp).background(Color(0xFF374151), shape = RoundedCornerShape(1.dp)))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tagline
            Text(
                text = "Dua always with you ✨",
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF111827)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- 3 ACTION BUTTONS (Above Composer) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionButton(
                icon = Icons.Outlined.Image,
                modifier = Modifier.weight(1f),
                onClick = { onSuggestion("Create an image") }
            )
            QuickActionButton(
                icon = Icons.Outlined.Lightbulb,
                modifier = Modifier.weight(1f),
                onClick = { onSuggestion("Brainstorm ideas") }
            )
            QuickActionButton(
                icon = Icons.Outlined.Public,
                modifier = Modifier.weight(1f),
                onClick = { onSuggestion("Search the web") }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF3F4F6))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF1F2937),
            modifier = Modifier.size(22.dp)
        )
    }
}
