package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DuaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Named shapes for chat surfaces. */
object DuaShape {
    val userBubble = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    val composer = RoundedCornerShape(28.dp)
    val codeBlock = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(50)
    val card = RoundedCornerShape(16.dp)
    val drawer = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    val pill = RoundedCornerShape(24.dp)
    val homeAction = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}
