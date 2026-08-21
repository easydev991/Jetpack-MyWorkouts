package com.myworkouts.ui.screens.root

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.screens.main.MainScreen
import com.myworkouts.ui.screens.more.MoreScreen
import com.myworkouts.ui.viewmodel.RootScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    modifier: Modifier = Modifier,
    viewModel: RootScreenViewModel = viewModel()
) {
    val navController = rememberNavController()
    val currentTab by viewModel.currentTab.collectAsState()
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBarContent(
                tabs = Screen.AllTabs,
                currentTab = currentTab,
                onTabSelected = viewModel::switchTab,
                navController = navController
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Main.route) {
                MainScreen(
                    onFabClick = {
                        Log.d("MainScreen", "FAB нажата — добавить тренировку")
                    }
                )
            }
            composable(Screen.More.route) {
                MoreScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationBarContent(
    tabs: List<Screen>,
    currentTab: Screen,
    onTabSelected: (Screen) -> Unit,
    navController: NavController
) {
    NavigationBar {
        tabs.forEach { screen ->
            val title = stringResource(screen.titleResId)
            NavigationBarItem(
                selected = currentTab == screen,
                onClick = {
                    onTabSelected(screen)
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(screen.icon, contentDescription = title) },
                label = { Text(title) }
            )
        }
    }
}
