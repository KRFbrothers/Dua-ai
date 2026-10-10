package com.dua.ai.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Show current text immediately; colour it off the UI thread after streaming briefly settles. */
@Composable
internal fun rememberHighlightedCode(code: String, language: String?, dark: Boolean): AnnotatedString {
    var text by remember(code, language, dark) { mutableStateOf(AnnotatedString(code)) }
    LaunchedEffect(code, language, dark) {
        // Avoid starting expensive work for every token in an actively streaming fence.
        delay(120)
        text = withContext(Dispatchers.Default) { highlightCode(code, language, dark) }
    }
    return text
}
