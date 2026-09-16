package com.myworkouts.ui.screens.root

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.AppScreen
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.navigation.BottomTab
import com.myworkouts.navigation.Screen
import com.myworkouts.navigation.visibleTabs
import com.myworkouts.ui.screens.appdata.AppDataScreen
import com.myworkouts.ui.screens.appearance.AppearanceScreen
import com.myworkouts.ui.screens.entry.EntryCreateScreen
import com.myworkouts.ui.screens.exercises.ArchivedExercisesScreen
import com.myworkouts.ui.screens.exercises.ExerciseCreateScreen
import com.myworkouts.ui.screens.exercises.ExercisesScreen
import com.myworkouts.ui.screens.main.MainScreen
import com.myworkouts.ui.screens.more.MoreScreen
import com.myworkouts.ui.viewmodel.AppDataViewModel
import com.myworkouts.ui.viewmodel.AppearanceViewModel
import com.myworkouts.ui.viewmodel.ArchivedExercisesViewModel
import com.myworkouts.ui.viewmodel.EntryCreateViewModel
import com.myworkouts.ui.viewmodel.ExerciseCreateViewModel
import com.myworkouts.ui.viewmodel.ExercisesViewModel
import com.myworkouts.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Корневой экран с нижней навигацией и вложенными маршрутами.
 *
 * `AppSettingsDataStore` создаётся один раз в `MyWorkoutsApplication` и
 * передаётся сюда параметром — повторное создание внутри `RootScreen`
 * приведёт к `There are multiple DataStores active for the same file`.
 *
 * @param dataStore единый экземпляр [AppSettingsDataStore] для всего приложения
 * @param repository репозиторий данных тренировок (создан в `MyWorkoutsApplication`)
 * @param modifier внешний модификатор для [Scaffold]
 */
@Composable
fun RootScreen(
    dataStore: AppSettingsDataStore,
    repository: WorkoutDataRepository,
    analyticsProvider: AnalyticsProvider,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // Breadcrumbs навигации (design D3): один слушатель на
    // navController эмитит ScreenView на каждое изменение destination
    // (старт, переход, back). Маппинг route-template → AppScreen;
    // для экранов с аргументами destination.route возвращает шаблон
    // с placeholder ({entryId}, {exerciseId}), поэтому сравниваем с
    // Screen.*.route как есть. Снятие в onDispose — иначе при
    // рекомпозиции RootScreen регистрация дублируется.
    DisposableEffect(navController) {
        val listener =
            NavController.OnDestinationChangedListener { _, destination, _ ->
                val screen =
                    when (destination.route) {
                        Screen.Main.route -> AppScreen.MainScreen
                        Screen.Exercises.route -> AppScreen.ExercisesScreen
                        Screen.More.route -> AppScreen.MoreScreen
                        Screen.Appearance.route -> AppScreen.AppearanceScreen
                        Screen.AppData.route -> AppScreen.AppDataScreen
                        Screen.ArchivedExercises.route -> AppScreen.ArchivedExercisesScreen
                        Screen.EntryCreate.route -> AppScreen.EntryCreateScreen
                        Screen.ExerciseCreate.route -> AppScreen.ExerciseCreateScreen
                        else -> null
                    }
                if (screen != null) {
                    analyticsProvider.log(AnalyticsEvent.ScreenView(screen))
                }
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    val currentRoute = navBackStackEntry?.destination?.route
    // Настройка видимости вкладки «Упражнения» (exercises-tab, design
    // Decision 8): единая подписка здесь — значение уходит и в бар, и
    // параметром в AppNavHost (MoreScreen); запись через suspend-setter.
    val showExercisesTab by dataStore.showExercisesTab.collectAsState(initial = false)
    // Видимые вкладки = BottomTabs минус Exercises при выключенной
    // настройке (design Decision 7). Нижняя навигация только на табах;
    // на не-tab экранах (например, Appearance) её прячем, иначе лишний
    // NavigationBarItem "More" висит над TopAppBar и выглядит как регрессия.
    val tabs = visibleTabs(showExercisesTab)
    val shouldShowBottomBar = currentRoute in tabs.map { it.route }
    Scaffold(
        modifier = modifier,
        // NavigationBar сама учитывает system nav bar inset (NavigationBarDefaults.windowInsets),
        // поэтому обнуляем contentWindowInsets, иначе вложенный MainScreen вычтет его дважды
        // и FAB уедет вверх на ~2× высоту system nav bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (shouldShowBottomBar) {
                AppBottomBar(
                    tabs = tabs,
                    currentRoute = currentRoute,
                    onTabClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        AppNavHost(
            navController = navController,
            dataStore = dataStore,
            showExercisesTab = showExercisesTab,
            repository = repository,
            analyticsProvider = analyticsProvider,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Граф навигации всех destination. Настройку тоггла получает параметром
 * (единая подписка в [RootScreen]); её запись (`scope.launch`) живёт
 * здесь — рядом с местом передачи.
 *
 * Destination exercises регистрируется ВСЕГДА (exercises-tab, design
 * Decision 7): скрытие настройкой убирает только `NavigationBarItem`,
 * включение таба мгновенное и без перестройки графа.
 */
@Composable
private fun AppNavHost(
    navController: NavHostController,
    dataStore: AppSettingsDataStore,
    showExercisesTab: Boolean,
    repository: WorkoutDataRepository,
    analyticsProvider: AnalyticsProvider,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        modifier = modifier
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                viewModel = viewModel(factory = MainViewModel.factory(repository, analyticsProvider)),
                onAddEntryClick = { navController.navigate(Screen.EntryCreate.createRoute()) },
                onEditEntryClick = { entryId ->
                    navController.navigate(Screen.EntryCreate.createRoute(entryId))
                },
                analyticsProvider = analyticsProvider
            )
        }
        composable(Screen.Exercises.route) {
            ExercisesNavDestination(repository, dataStore, analyticsProvider, navController)
        }
        composable(Screen.More.route) {
            MoreScreen(
                navController = navController,
                showExercisesTab = showExercisesTab,
                onShowExercisesTabChange = { show ->
                    scope.launch { dataStore.setShowExercisesTab(show) }
                },
                analyticsProvider = analyticsProvider
            )
        }
        composable(Screen.Appearance.route) {
            AppearanceScreen(
                viewModel = viewModel(factory = AppearanceViewModel.factory(dataStore, analyticsProvider)),
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.AppData.route) {
            val context = LocalContext.current
            AppDataScreen(
                viewModel = viewModel(factory = AppDataViewModel.factory(repository, context, analyticsProvider)),
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.EntryCreate.route, arguments = Screen.EntryCreate.arguments) { backStackEntry ->
            val entryId =
                backStackEntry.arguments?.getLong(Screen.EntryCreate.ARG_ENTRY_ID)
                    ?: Screen.EntryCreate.NO_ENTRY_ID
            EntryCreateScreen(
                viewModel = viewModel(factory = EntryCreateViewModel.factory(repository, entryId, analyticsProvider)),
                onBackClick = { navController.popBackStack() }
            )
        }
        // ExerciseCreate: фабрика строится в composable-блоке; query-аргумент
        // exerciseId выбирает режим create/edit (edit-delete-exercise,
        // design Decision 1), FAB «Упражнений» ведёт на createRoute().
        composable(Screen.ExerciseCreate.route, arguments = Screen.ExerciseCreate.arguments) { backStackEntry ->
            val exerciseId =
                backStackEntry.arguments?.getLong(
                    Screen.ExerciseCreate.ARG_EXERCISE_ID,
                    Screen.ExerciseCreate.NO_EXERCISE_ID
                )
                    ?: Screen.ExerciseCreate.NO_EXERCISE_ID
            ExerciseCreateScreen(
                viewModel = viewModel(factory = ExerciseCreateViewModel.factory(repository, exerciseId, analyticsProvider)),
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.ArchivedExercises.route) {
            ArchivedExercisesNavDestination(
                repository = repository,
                analyticsProvider = analyticsProvider,
                navController = navController
            )
        }
    }
}

/**
 * `composable(Screen.Exercises.route) { ... }` — вынесено из
 * `AppNavHost` ради соблюдения detekt `LongMethod`. Колбэк архива —
 * навигация на `Screen.ArchivedExercises.route` (exercises-archive,
 * tasks §6.7.a).
 */
@Composable
private fun ExercisesNavDestination(
    repository: WorkoutDataRepository,
    dataStore: AppSettingsDataStore,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    ExercisesScreen(
        viewModel =
            viewModel(
                factory = ExercisesViewModel.factory(repository, dataStore, analyticsProvider)
            ),
        onAddExerciseClick = { navController.navigate(Screen.ExerciseCreate.createRoute()) },
        onEditExercise = { exerciseId ->
            navController.navigate(Screen.ExerciseCreate.createRoute(exerciseId))
        },
        onArchivedExercisesClick = {
            navController.navigate(Screen.ArchivedExercises.route)
        },
        analyticsProvider = analyticsProvider
    )
}

/**
 * `composable(Screen.ArchivedExercises.route) { ... }` — вынесено из
 * `AppNavHost` ради соблюдения detekt `LongMethod`.
 * Не-tab экран (non-tab-screen-navigation/spec.md).
 */
@Composable
private fun ArchivedExercisesNavDestination(
    repository: WorkoutDataRepository,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    ArchivedExercisesScreen(
        viewModel =
            viewModel(
                factory = ArchivedExercisesViewModel.factory(repository, analyticsProvider)
            ),
        onBackClick = { navController.popBackStack() }
    )
}

/**
 * Нижняя навигация табов — по списку видимых вкладок [visibleTabs]
 * (exercises-tab, design Decision 7). На не-tab экранах не вызывается
 * вовсе — `Scaffold.bottomBar` вычисляется по `shouldShowBottomBar` в
 * [RootScreen].
 */
@Composable
private fun AppBottomBar(
    tabs: List<BottomTab>,
    currentRoute: String?,
    onTabClick: (route: String) -> Unit
) {
    NavigationBar {
        tabs.forEach { tab ->
            val title = stringResource(tab.titleResId)
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onTabClick(tab.route) },
                icon = {
                    Icon(tab.icon, contentDescription = title)
                },
                label = { Text(title) }
            )
        }
    }
}
