package com.myworkouts.ui.screens.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.domain.crash.CrashReporter
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.screens.appdata.AppDataScreen
import com.myworkouts.ui.screens.appearance.AppearanceScreen
import com.myworkouts.ui.screens.main.MainScreen
import com.myworkouts.ui.screens.more.MoreScreen
import com.myworkouts.ui.screens.overview.OverviewScreen
import com.myworkouts.ui.screens.overview.OverviewSettingsScreen
import com.myworkouts.ui.viewmodel.AppDataViewModel
import com.myworkouts.ui.viewmodel.AppearanceViewModel
import com.myworkouts.ui.viewmodel.MainViewModel
import com.myworkouts.ui.viewmodel.OverviewSettingsViewModel
import com.myworkouts.ui.viewmodel.OverviewViewModel
import kotlinx.coroutines.launch

/*
 * Часть destination-компоузаблов `AppNavHost` — вынесена из `RootScreen.kt`
 * ради detekt `LongMethod`/`TooManyFunctions` (тот же паттерн, что у
 * оставшихся destination: фабрика VM + навигационные колбэки, логики нет).
 */

/**
 * `composable(Screen.Main.route)` — список тренировок. Настройку тоггла
 * MoreScreen получает параметром (единая подписка в [RootScreen]); её
 * запись (`scope.launch`) остаётся рядом с NavHost.
 */
@Composable
internal fun MainNavDestination(
    repository: WorkoutDataRepository,
    analyticsProvider: AnalyticsProvider,
    crashReporter: CrashReporter,
    navController: NavHostController
) {
    MainScreen(
        viewModel =
            viewModel(
                factory = MainViewModel.factory(repository, analyticsProvider, crashReporter)
            ),
        onAddEntryClick = { navController.navigate(Screen.EntryCreate.createRoute()) },
        onEditEntryClick = { entryId ->
            navController.navigate(Screen.EntryCreate.createRoute(entryId))
        },
        onOverviewClick = { navController.navigate(Screen.Overview.route) },
        analyticsProvider = analyticsProvider
    )
}

/** `composable(Screen.More.route)` — дополнительные настройки; запись тоггла — здесь. */
@Composable
internal fun MoreNavDestination(
    dataStore: AppSettingsDataStore,
    showExercisesTab: Boolean,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    val scope = rememberCoroutineScope()
    MoreScreen(
        navController = navController,
        showExercisesTab = showExercisesTab,
        onShowExercisesTabChange = { show ->
            scope.launch { dataStore.setShowExercisesTab(show) }
        },
        analyticsProvider = analyticsProvider
    )
}

/** `composable(Screen.Appearance.route)` — оформление (тема, динамические цвета). */
@Composable
internal fun AppearanceNavDestination(
    dataStore: AppSettingsDataStore,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    AppearanceScreen(
        viewModel = viewModel(factory = AppearanceViewModel.factory(dataStore, analyticsProvider)),
        onBackClick = { navController.popBackStack() }
    )
}

/** `composable(Screen.AppData.route)` — экспорт, восстановление, удаление данных. */
@Composable
internal fun AppDataNavDestination(
    repository: WorkoutDataRepository,
    dataStore: AppSettingsDataStore,
    analyticsProvider: AnalyticsProvider,
    crashReporter: CrashReporter,
    navController: NavHostController
) {
    val context = LocalContext.current
    AppDataScreen(
        viewModel =
            viewModel(
                factory =
                    AppDataViewModel.factory(repository, dataStore, context, analyticsProvider, crashReporter)
            ),
        onBackClick = { navController.popBackStack() }
    )
}

/**
 * `composable(Screen.Overview.route)` — экран обзора (overview-screen):
 * тап кнопки на главном ведёт сюда; нижний бар скрыт контрактом
 * `shouldShowBottomBar`, отдельной правки не требует. Возврат — системный
 * Back (non-tab-screen-navigation).
 */
@Composable
internal fun OverviewNavDestination(
    repository: WorkoutDataRepository,
    dataStore: AppSettingsDataStore,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    OverviewScreen(
        viewModel =
            viewModel(
                factory = OverviewViewModel.factory(repository, dataStore, analyticsProvider)
            ),
        onOpenSettings = { navController.navigate(Screen.OverviewSettings.route) },
        onBack = { navController.popBackStack() }
    )
}

/** `composable(Screen.OverviewSettings.route)` — настройки видимости вкладок обзора. */
@Composable
internal fun OverviewSettingsNavDestination(
    dataStore: AppSettingsDataStore,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    OverviewSettingsScreen(
        viewModel =
            viewModel(
                factory = OverviewSettingsViewModel.factory(dataStore, analyticsProvider)
            ),
        onBackClick = { navController.popBackStack() }
    )
}
