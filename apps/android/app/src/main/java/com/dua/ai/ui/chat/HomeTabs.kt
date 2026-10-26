package com.dua.ai.ui.chat

import com.dua.ai.R

/** Home destinations shared by navigation and both chat panes. */
enum class HomeTab(val label: String) { Chat("Chat"), Work("Work") }

/** Task starter labels (strings.xml) paired with their icons. */
internal val HomeTaskPromptIds = listOf(R.string.home_task_prompt_backup, R.string.home_task_prompt_react, R.string.home_task_prompt_compare)
