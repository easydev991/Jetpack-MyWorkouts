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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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
import com.myworkouts.ui.screens.entry.EntryCreateScreen
import com.myworkouts.ui.screens.entry.EntrySettingsScreen
import com.myworkouts.ui.screens.exercises.ArchivedExercisesScreen
import com.myworkouts.ui.screens.exercises.CategoryCreateScreen
import com.myworkouts.ui.screens.exercises.ExerciseCreateScreen
import com.myworkouts.ui.screens.exercises.ExercisesScreen
import com.myworkouts.ui.viewmodel.ArchivedExercisesViewModel
import com.myworkouts.ui.viewmodel.CategoryCreateViewModel
import com.myworkouts.ui.viewmodel.EntryCreateViewModel
import com.myworkouts.ui.viewmodel.EntrySettingsViewModel
import com.myworkouts.ui.viewmodel.ExerciseCreateViewModel
import com.myworkouts.ui.viewmodel.ExercisesViewModel

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
                // Маппинг route-template → AppScreen; для экранов с
                // аргументами destination.route возвращает шаблон
                // с placeholder ({entryId}, {exerciseId}), поэтому
                // сравниваем с Screen.*.route как есть.
                val screen = screenForRoute(destination.route)
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
 * Граф навигации всех destination. Запись тоггла «Упражнения»
 * (`scope.launch`) живёт в [MoreNavDestination] — рядом с местом передачи
 * (файл `NavDestinations.kt`).
 *
 * Destination exercises регистрируется ВСЕГДА (exercises-tab, design
 * Decision 7): скрытие настройкой убирает только `NavigationBarItem`,
 * включение таба мгновенное и без перестройки графа.
 */
@Suppress("LongParameterList")
@Composable
private fun AppNavHost(
    navController: NavHostController,
    dataStore: AppSettingsDataStore,
    showExercisesTab: Boolean,
    repository: WorkoutDataRepository,
    analyticsProvider: AnalyticsProvider,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        modifier = modifier
    ) {
        composable(Screen.Main.route) {
            MainNavDestination(repository, analyticsProvider, navController)
        }
        composable(Screen.Exercises.route) {
            ExercisesNavDestination(repository, dataStore, analyticsProvider, navController)
        }
        composable(Screen.More.route) {
            MoreNavDestination(dataStore, showExercisesTab, analyticsProvider, navController)
        }
        composable(Screen.Appearance.route) {
            AppearanceNavDestination(dataStore, analyticsProvider, navController)
        }
        composable(Screen.AppData.route) {
            AppDataNavDestination(repository, dataStore, analyticsProvider, navController)
        }
        composable(Screen.Overview.route) {
            OverviewNavDestination(repository, dataStore, analyticsProvider, navController)
        }
        composable(Screen.OverviewSettings.route) {
            OverviewSettingsNavDestination(dataStore, analyticsProvider, navController)
        }
        composable(Screen.EntrySettings.route) {
            EntrySettingsNavDestination(navController, dataStore, analyticsProvider)
        }
        composable(Screen.EntryCreate.route, arguments = Screen.EntryCreate.arguments) { backStackEntry ->
            EntryCreateNavDestination(
                backStackEntry = backStackEntry,
                repository = repository,
                dataStore = dataStore,
                analyticsProvider = analyticsProvider,
                navController = navController
            )
        }
        composable(Screen.ExerciseCreate.route, arguments = Screen.ExerciseCreate.arguments) { backStackEntry ->
            ExerciseCreateNavDestination(
                backStackEntry = backStackEntry,
                repository = repository,
                analyticsProvider = analyticsProvider,
                navController = navController
            )
        }
        composable(Screen.ArchivedExercises.route) {
            ArchivedExercisesNavDestination(
                repository = repository,
                analyticsProvider = analyticsProvider,
                navController = navController
            )
        }
        composable(Screen.CategoryCreate.route, arguments = Screen.CategoryCreate.arguments) { backStackEntry ->
            CategoryCreateNavDestination(
                backStackEntry = backStackEntry,
                repository = repository,
                analyticsProvider = analyticsProvider,
                navController = navController
            )
        }
    }
}

/**
 * Маппинг route-шаблона destination в элемент аналитики [AppScreen]
 * (design D3). Экраны с аргументами имеют в route placeholder
 * ({entryId}, {exerciseId}) — сравниваем с `Screen.*.route` как есть.
 * `null` — route вне таблицы (например, системные destination).
 */
private fun screenForRoute(route: String?): AppScreen? =
    when (route) {
        Screen.Main.route -> AppScreen.MainScreen
        Screen.Exercises.route -> AppScreen.ExercisesScreen
        Screen.More.route -> AppScreen.MoreScreen
        Screen.Appearance.route -> AppScreen.AppearanceScreen
        Screen.AppData.route -> AppScreen.AppDataScreen
        Screen.ArchivedExercises.route -> AppScreen.ArchivedExercisesScreen
        Screen.EntryCreate.route -> AppScreen.EntryCreateScreen
        Screen.EntrySettings.route -> AppScreen.EntrySettingsScreen
        Screen.ExerciseCreate.route -> AppScreen.ExerciseCreateScreen
        Screen.CategoryCreate.route -> AppScreen.CategoryCreateScreen
        Screen.Overview.route -> AppScreen.OverviewScreen
        Screen.OverviewSettings.route -> AppScreen.OverviewSettingsScreen
        else -> null
    }

/**
 * `composable(Screen.EntrySettings.route) { ... }` — вынесено из
 * `AppNavHost` ради соблюдения detekt `LongMethod`. Экран настроек
 * видимости полей записи (entry-settings-screen): нижний бар скрыт
 * контрактом `shouldShowBottomBar`, отдельной правки не требует.
 */
@Composable
private fun EntrySettingsNavDestination(
    navController: NavHostController,
    dataStore: AppSettingsDataStore,
    analyticsProvider: AnalyticsProvider
) {
    EntrySettingsScreen(
        viewModel =
            viewModel(
                factory = EntrySettingsViewModel.factory(dataStore, analyticsProvider)
            ),
        onBackClick = { navController.popBackStack() }
    )
}

/**
 * `composable(Screen.EntryCreate.route) { ... }` — вынесено из
 * `AppNavHost` ради соблюдения detekt `LongMethod`. Аргумент entryId
 * выбирает режим create/edit (edit-entry): `NO_ENTRY_ID` — создание.
 */
@Composable
private fun EntryCreateNavDestination(
    backStackEntry: NavBackStackEntry,
    repository: WorkoutDataRepository,
    dataStore: AppSettingsDataStore,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    val entryId =
        backStackEntry.arguments?.getLong(Screen.EntryCreate.ARG_ENTRY_ID)
            ?: Screen.EntryCreate.NO_ENTRY_ID
    EntryCreateScreen(
        viewModel =
            viewModel(
                factory =
                    EntryCreateViewModel.factory(repository, entryId, dataStore, analyticsProvider)
            ),
        onBackClick = { navController.popBackStack() },
        onOpenSettings = { navController.navigate(Screen.EntrySettings.route) }
    )
}

/**
 * `composable(Screen.ExerciseCreate.route) { ... }` — вынесено из
 * `AppNavHost` ради соблюдения detekt `LongMethod`. Query-аргумент
 * exerciseId выбирает режим create/edit (edit-delete-exercise,
 * design Decision 1), FAB «Упражнений» ведёт на createRoute().
 */
@Composable
private fun ExerciseCreateNavDestination(
    backStackEntry: NavBackStackEntry,
    repository: WorkoutDataRepository,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    val exerciseId =
        backStackEntry.arguments?.getLong(
            Screen.ExerciseCreate.ARG_EXERCISE_ID,
            Screen.ExerciseCreate.NO_EXERCISE_ID
        )
            ?: Screen.ExerciseCreate.NO_EXERCISE_ID
    ExerciseCreateScreen(
        viewModel =
            viewModel(
                factory =
                    ExerciseCreateViewModel.factory(
                        repository,
                        exerciseId,
                        analyticsProvider
                    )
            ),
        onBackClick = { navController.popBackStack() }
    )
}

/**
 * `composable(Screen.CategoryCreate.route) { ... }` — вынесено из
 * `AppNavHost` ради соблюдения detekt `LongMethod`. Query-аргумент
 * categoryId выбирает режим create/edit (custom-categories, design
 * Decision 7), FAB вкладки «Категории» ведёт на createRoute().
 */
@Composable
private fun CategoryCreateNavDestination(
    backStackEntry: NavBackStackEntry,
    repository: WorkoutDataRepository,
    analyticsProvider: AnalyticsProvider,
    navController: NavHostController
) {
    val categoryId =
        backStackEntry.arguments?.getLong(
            Screen.CategoryCreate.ARG_CATEGORY_ID,
            Screen.CategoryCreate.NO_CATEGORY_ID
        ) ?: Screen.CategoryCreate.NO_CATEGORY_ID
    CategoryCreateScreen(
        viewModel =
            viewModel(
                factory = CategoryCreateViewModel.factory(repository, categoryId, analyticsProvider)
            ),
        onBackClick = { navController.popBackStack() }
    )
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
        onAddCategoryClick = { navController.navigate(Screen.CategoryCreate.createRoute()) },
        onEditExercise = { exerciseId ->
            navController.navigate(Screen.ExerciseCreate.createRoute(exerciseId))
        },
        onEditCategory = { categoryId ->
            navController.navigate(Screen.CategoryCreate.createRoute(categoryId))
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
                    // Accessibility (accessibility-semantics-fixes, design
                    // D6): null вместо дублирующего title — лейбл вкладки
                    // и так смержен с элементом, иначе TalkBack читает
                    // название дважды.
                    Icon(tab.icon, contentDescription = null)
                },
                label = { Text(title) }
            )
        }
    }
}
