package com.dua.ai.ui.chat
package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. Missing ModelChipRow reference solve karne ke liye
@Composable
fun ModelChipRow(
    modifier: Modifier = Modifier,
    selectedModel: String = "Auto",
    onModelClick: () -> Unit = {}
) {
    // Header model indicator if called by ChatScreen
}

// 2. Full-compatible Composer Component
@Composable
fun Composer(
    modifier: Modifier = Modifier,
    state: Any? = null,
    focusRequester: FocusRequester? = null,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    onDraftChange: (String) -> Unit = onValueChange,
    onSend: (String) -> Unit = {},
    onAttach: () -> Unit = {},
    onClearAttachments: () -> Unit = {},
    onMic: () -> Unit = {},
    onVoiceClick: () -> Unit = onMic,
    onVoiceMode: () -> Unit = onVoiceClick,
    onModelClick: () -> Unit = {},
    onTextFieldFocus: ((Boolean) -> Unit)? = null,
    onTextFieldBounds: ((Rect) -> Unit)? = null,
    currentModel: String = "Auto"
) {
    var localText by remember { mutableStateOf(value) }
    val activeText = if (value.isNotEmpty()) value else localText

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // --- Inset Rounded Card Container ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFF9FAFB))
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(28.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Text Input ("Ask anything")
            val textFieldModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }

            BasicTextField(
                value = activeText,
                onValueChange = {
                    localText = it
                    onValueChange(it)
                    onDraftChange(it)
                },
                modifier = textFieldModifier,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(Color(0xFF111827)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (activeText.isNotBlank()) {
                            onSend(activeText)
                            localText = ""
                            onValueChange("")
                            onDraftChange("")
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    if (activeText.isEmpty()) {
                        Text(
                            text = "Ask anything",
                            fontSize = 16.sp,
                            color = Color(0xFF9CA3AF),
                            fontWeight = FontWeight.Normal
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Controls: (+) and [ ✨ Auto ⌵ ]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5E7EB))
                            .clickable { onAttach() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFE5E7EB))
                            .clickable { onModelClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = currentModel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F2937)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Right Controls: (Mic) and [ Black Voice Waveform Button / Send Button ]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onMic() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Dictate",
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (activeText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .clickable {
                                    onSend(activeText)
                                    localText = ""
                                    onValueChange("")
                                    onDraftChange("")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .clickable {
                                    onVoiceMode()
                                    onVoiceClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Live Voice",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
