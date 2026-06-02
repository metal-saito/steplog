package com.cellomsai.steplog.ui.navigation

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.cellomsai.steplog.R
import com.cellomsai.steplog.ui.screens.calendar.CalendarScreen
import com.cellomsai.steplog.ui.screens.detail.DetailScreen
import com.cellomsai.steplog.ui.screens.graph.GraphScreen
import com.cellomsai.steplog.ui.screens.home.HomeScreen
import com.cellomsai.steplog.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Calendar : Screen("calendar")
    object Graph : Screen("graph")
    object Settings : Screen("settings")
    object Detail : Screen("detail/{date}") {
        fun createRoute(date: String) = "detail/$date"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val labelRes: Int,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, R.string.nav_today, Icons.Outlined.Today),
    BottomNavItem(Screen.Calendar, R.string.nav_calendar, Icons.Outlined.CalendarMonth),
    BottomNavItem(Screen.Graph, R.string.nav_graph, Icons.Outlined.BarChart),
    BottomNavItem(Screen.Settings, R.string.nav_settings, Icons.Outlined.Settings),
)

private fun tabIndex(route: String?) =
    bottomNavItems.indexOfFirst { it.screen.route == route }

private const val TAB_ANIM_MS = 300
private const val DETAIL_ANIM_MS = 340

@Composable
fun StepLogNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        // Directional horizontal slide between tabs (left/right based on tab order)
        enterTransition = {
            val from = tabIndex(initialState.destination.route)
            val to = tabIndex(targetState.destination.route)
            if (from >= 0 && to >= 0) {
                val dir = if (to > from) 1 else -1
                slideInHorizontally(
                    initialOffsetX = { dir * it / 3 },
                    animationSpec = tween(TAB_ANIM_MS, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(TAB_ANIM_MS))
            } else {
                fadeIn(tween(260))
            }
        },
        exitTransition = {
            val from = tabIndex(initialState.destination.route)
            val to = tabIndex(targetState.destination.route)
            if (from >= 0 && to >= 0) {
                val dir = if (to > from) -1 else 1
                slideOutHorizontally(
                    targetOffsetX = { dir * it / 3 },
                    animationSpec = tween(TAB_ANIM_MS - 30, easing = FastOutLinearInEasing),
                ) + fadeOut(tween(TAB_ANIM_MS - 30))
            } else {
                fadeOut(tween(220))
            }
        },
        popEnterTransition = {
            val from = tabIndex(initialState.destination.route)
            val to = tabIndex(targetState.destination.route)
            if (from >= 0 && to >= 0) {
                val dir = if (to > from) 1 else -1
                slideInHorizontally(
                    initialOffsetX = { dir * it / 3 },
                    animationSpec = tween(TAB_ANIM_MS, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(TAB_ANIM_MS))
            } else {
                fadeIn(tween(260))
            }
        },
        popExitTransition = {
            val from = tabIndex(initialState.destination.route)
            val to = tabIndex(targetState.destination.route)
            if (from >= 0 && to >= 0) {
                val dir = if (to > from) -1 else 1
                slideOutHorizontally(
                    targetOffsetX = { dir * it / 3 },
                    animationSpec = tween(TAB_ANIM_MS - 30, easing = FastOutLinearInEasing),
                ) + fadeOut(tween(TAB_ANIM_MS - 30))
            } else {
                fadeOut(tween(220))
            }
        },
    ) {
        composable(Screen.Home.route) { HomeScreen() }
        composable(Screen.Calendar.route) {
            CalendarScreen(onDayClick = { date ->
                navController.navigate(Screen.Detail.createRoute(date))
            })
        }
        composable(Screen.Graph.route) { GraphScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
        composable(
            route = Screen.Detail.route,
            // Detail slides up from the bottom like a modal sheet
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(DETAIL_ANIM_MS, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(DETAIL_ANIM_MS))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it / 3 },
                    animationSpec = tween(DETAIL_ANIM_MS - 60, easing = FastOutLinearInEasing),
                ) + fadeOut(tween(DETAIL_ANIM_MS - 60))
            },
            popEnterTransition = {
                fadeIn(tween(260))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it / 3 },
                    animationSpec = tween(DETAIL_ANIM_MS - 60, easing = FastOutLinearInEasing),
                ) + fadeOut(tween(DETAIL_ANIM_MS - 60))
            },
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { backStack ->
            val date = backStack.arguments?.getString("date") ?: return@composable
            DetailScreen(date = date, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun StepLogBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}
