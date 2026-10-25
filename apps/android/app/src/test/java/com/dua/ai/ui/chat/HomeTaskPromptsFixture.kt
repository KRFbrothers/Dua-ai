package com.dua.ai.ui.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider

/** Resolved Task starter labels from strings.xml, so tests match exactly what Home renders. */
internal val HomeTaskPrompts: List<String>
    get() = ApplicationProvider.getApplicationContext<Context>().let { c -> HomeTaskPromptIds.map(c::getString) }
