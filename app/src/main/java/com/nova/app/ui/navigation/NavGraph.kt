package com.nova.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nova.app.ui.screens.ForgotPasswordScreen
import com.nova.app.ui.screens.HabitsScreen
import com.nova.app.ui.screens.HomeScreen
import com.nova.app.ui.screens.LoginScreen
import com.nova.app.ui.screens.NoteDetailScreen
import com.nova.app.ui.screens.NotesListScreen
import com.nova.app.ui.screens.OnboardingScreen
import com.nova.app.ui.screens.ProfileScreen
import com.nova.app.ui.screens.SettingsScreen
import com.nova.app.ui.screens.SignupScreen
import com.nova.app.ui.screens.SplashScreen
import com.nova.app.ui.screens.TaskEditScreen
import com.nova.app.ui.screens.TaskListScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.TASKS, "Tasks", Icons.Outlined.CheckCircle),
    Tab(Routes.NOTES, "Notes", Icons.Outlined.StickyNote2),
    Tab(Routes.PROFILE, "Profile", Icons.Outlined.Person)
)

@Composable
fun NovaApp() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = {
            fadeIn(tween(220)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
        },
        exitTransition = {
            fadeOut(tween(180))
        },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = {
            fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180))
        }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen {
                nav.navigate(Routes.ONBOARDING) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen {
                nav.navigate(Routes.LOGIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLogin = { nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onSignup = { nav.navigate(Routes.SIGNUP) },
                onForgot = { nav.navigate(Routes.FORGOT) }
            )
        }
        composable(Routes.SIGNUP) {
            SignupScreen(
                onSignup = { nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onLogin = { nav.popBackStack() }
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(onSent = {}, onBack = { nav.popBackStack() })
        }
        composable(Routes.HOME) { MainScaffold(nav) { HomeBody(nav) } }
        composable(Routes.TASKS) { MainScaffold(nav) { TasksBody(nav) } }
        composable(Routes.NOTES) { MainScaffold(nav) { NotesBody(nav) } }
        composable(Routes.PROFILE) { MainScaffold(nav) { ProfileBody(nav) } }
        composable(
            route = "${Routes.TASK_EDIT}?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            TaskEditScreen(
                taskId = entry.arguments?.getString("id")?.ifBlank { null },
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() }
            )
        }
        composable(
            route = "${Routes.NOTE_DETAIL}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            NoteDetailScreen(
                noteId = entry.arguments?.getString("id") ?: "new",
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.HABITS) { HabitsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainScaffold(nav: NavHostController, content: @Composable () -> Unit) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun HomeBody(nav: NavHostController) {
    HomeScreen(
        onOpenTasks = { nav.navigate(Routes.TASKS) },
        onOpenHabits = { nav.navigate(Routes.HABITS) },
        onOpenNotes = { nav.navigate(Routes.NOTES) },
        onOpenProfile = { nav.navigate(Routes.PROFILE) }
    )
}

@Composable
private fun TasksBody(nav: NavHostController) {
    TaskListScreen(
        onAdd = { nav.navigate(Routes.taskEdit()) },
        onEdit = { id -> nav.navigate(Routes.taskEdit(id)) }
    )
}

@Composable
private fun NotesBody(nav: NavHostController) {
    NotesListScreen(
        onOpen = { id -> nav.navigate(Routes.noteDetail(id)) },
        onAdd = { nav.navigate(Routes.noteDetail("new")) }
    )
}

@Composable
private fun ProfileBody(nav: NavHostController) {
    ProfileScreen(
        onSettings = { nav.navigate(Routes.SETTINGS) },
        onSignOut = {
            nav.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    )
}
