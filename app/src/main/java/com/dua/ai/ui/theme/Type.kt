package com.dua.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dua.ai.R

// Söhne Font Family (Jo aapne res/font/ mein upload kiya)
val SoehneFamily = FontFamily(
    Font(R.font.soehne_kraeftig, FontWeight.Normal),
    Font(R.font.soehne_kraeftig, FontWeight.Medium),
    Font(R.font.soehne_kraeftig, FontWeight.SemiBold),
    Font(R.font.soehne_kraeftig, FontWeight.Bold)
)

// Menlo Code Font (Code blocks ke liye)
val MenloFamily = FontFamily(
    Font(R.font.menlo_regular, FontWeight.Normal)
)

// App Typography
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SoehneFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SoehneFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SoehneFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SoehneFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SoehneFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SoehneFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
   
    )
)
