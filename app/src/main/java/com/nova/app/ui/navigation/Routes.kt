package com.nova.app.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val FORGOT = "forgot"

    const val HOME = "home"
    const val TASKS = "tasks"
    const val TASK_EDIT = "task_edit"
    const val NOTES = "notes"
    const val NOTE_DETAIL = "note_detail"
    const val HABITS = "habits"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"

    fun taskEdit(id: String? = null): String =
        if (id == null) "$TASK_EDIT?id=" else "$TASK_EDIT?id=$id"

    fun noteDetail(id: String): String = "$NOTE_DETAIL/$id"
}

val BottomTabRoutes = listOf(Routes.HOME, Routes.TASKS, Routes.NOTES, Routes.PROFILE)
