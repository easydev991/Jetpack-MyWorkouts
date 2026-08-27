package com.myworkouts.ui.screens.root

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.navigation.BottomTabs
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.screens.appearance.AppearanceScreen
import com.myworkouts.ui.screens.main.MainScreen
import com.myworkouts.ui.screens.more.MoreScreen
import com.myworkouts.ui.viewmodel.AppearanceViewModel

/**
 * Корневой экран с нижней навигацией и вложенными маршрутами.
 *
 * `AppSettingsDataStore` создаётся один раз в `MainActivity.onCreate` и
 * передаётся сюда параметром — повторное создание внутри `RootScreen`
 * приведёт к `There are multiple DataStores active for the same file`.
 *
 * @param dataStore единый экземпляр [AppSettingsDataStore] для всего приложения
 * @param modifier внешний модификатор для [Scaffold]
 */
@Composable
fun RootScreen(
    dataStore: AppSettingsDataStore,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // Нижняя навигация только на табах; на не-tab экранах (например,
    // Appearance) её прячем, иначе лишний NavigationBarItem "More" висит
    // над TopAppBar и выглядит как регрессия.
    val shouldShowBottomBar = currentRoute in BottomTabs.map { it.route }
    Scaffold(
        modifier = modifier,
        // NavigationBar сама учитывает system nav bar inset (NavigationBarDefaults.windowInsets),
        // поэтому обнуляем contentWindowInsets, иначе вложенный MainScreen вычтет его дважды
        // и FAB уедет вверх на ~2× высоту system nav bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (shouldShowBottomBar) {
                NavigationBar {
                    BottomTabs.forEach { tab ->
                        val title = stringResource(tab.titleResId)
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(tab.icon, contentDescription = title)
                            },
                            label = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Main.route) { MainScreen() }
            composable(Screen.More.route) { MoreScreen(navController = navController) }
            composable(Screen.Appearance.route) {
                AppearanceScreen(
                    viewModel =
                        viewModel(
                            factory = AppearanceViewModel.factory(dataStore)
                        ),
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
