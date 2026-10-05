// Требование detekt TooManyFunctions: Content + два списка + табы +
// меню-хелперы пакета живут рядом; дробить файл хуже подавления.
@file:Suppress("TooManyFunctions")

package com.myworkouts.ui.screens.exercises

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.ui.state.ArchivedExercisesUiState
import com.myworkouts.ui.state.ArchivedTab
import com.myworkouts.ui.viewmodel.ArchivedExercisesViewModel

/**
 * Экран «Архив» — реактивные списки архивных упражнений и архивных
 * категорий с таб-баром «Упражнения» / «Категории» (exercises-archive +
 * custom-categories, capability `archived-exercises-screen`).
 *
 * Не участвует в нижней навигации (`Screen.ArchivedExercises` НЕ в
 * `BottomTabs`) — паттерн `Screen.Appearance`
 * (non-tab-screen-navigation/spec.md).
 *
 * VM-обёртка: подписка на [ArchivedExercisesViewModel.uiState], локальные
 * состояния long-press меню (упражнения и категории). Вся верстка — в
 * [ArchivedExercisesScreenContent].
 *
 * @param viewModel ViewModel с реактивными списками архива
 * @param onBackClick Тап стрелки «назад» в TopAppBar → навигация
 *   обратно на [com.myworkouts.navigation.Screen.Exercises]
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedExercisesScreen(
    viewModel: ArchivedExercisesViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var exerciseContextMenu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
    var categoryContextMenu by remember { mutableStateOf<CategoryContextMenuState?>(null) }

    ArchivedExercisesScreenContent(
        state =
            ArchivedExercisesState(
                uiState = uiState,
                contextMenu = exerciseContextMenu,
                categoryContextMenu = categoryContextMenu,
                onContextMenuItemChange = { exerciseContextMenu = it },
                onCategoryContextMenuItemChange = { categoryContextMenu = it },
                onUnarchiveExercise = { id ->
                    // Паттерн ExercisesScreen.onRequestArchive — обёртка
                    // закрывает меню и делегирует без побочных эффектов
                    exerciseContextMenu = null
                    viewModel.unarchive(id)
                },
                onUnarchiveCategory = { id ->
                    categoryContextMenu = null
                    viewModel.unarchiveCategory(id)
                },
                onTabSelect = viewModel::selectTab,
                onBackClick = onBackClick
            ),
        modifier = modifier
    )
}

/**
 * Test/Preview-state для [ArchivedExercisesScreenContent]: данные
 * [ArchivedExercisesUiState] плюс колбэки. UI-тесты и превью передают
 * готовый state без ViewModel (образец — `ExercisesScreenState`).
 *
 * `contextMenu` / `categoryContextMenu` хранят транзитные состояния
 * long-press меню (общие типы с `ExercisesScreen`) — структура
 * идентична, отдельные типы не нужны.
 *
 * @property contextMenu Транзитное состояние long-press меню
 *   упражнения (`null` — закрыто)
 * @property categoryContextMenu Транзитное состояние long-press меню
 *   категории (`null` — закрыто; custom-categories, tasks 7.3)
 */
internal data class ArchivedExercisesState(
    val uiState: ArchivedExercisesUiState,
    val contextMenu: ExerciseContextMenuState? = null,
    val categoryContextMenu: CategoryContextMenuState? = null,
    val onContextMenuItemChange: (ExerciseContextMenuState?) -> Unit = {},
    val onCategoryContextMenuItemChange: (CategoryContextMenuState?) -> Unit = {},
    val onUnarchiveExercise: (Long) -> Unit = {},
    val onUnarchiveCategory: (Long) -> Unit = {},
    /** Переключение вкладки архива (custom-categories, tasks 7.3). */
    val onTabSelect: (ArchivedTab) -> Unit = {},
    val onBackClick: () -> Unit = {}
)

/**
 * Контент экрана «Архив»: TopAppBar со стрелкой «назад» + тело
 * (таб-бар при двух непустых коллекциях, списки по вкладке или
 * заглушка пустого архива) + long-press меню «Вернуть».
 *
 * Используется для UI-тестов и превью без ViewModel (design Decision 3,
 * change `entry-create-screen`).
 *
 * @param state Данные экрана и колбэки (см. [ArchivedExercisesState])
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchivedExercisesScreenContent(
    state: ArchivedExercisesState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        // System nav bar inset уже учтён NavigationBar в RootScreen;
        // здесь оставляем только горизонтальные insets (displayCutout
        // и т.п.) — паттерн ExercisesScreen/AppearanceScreen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                // Общий заголовок архива (custom-categories, spec
                // «TopAppBar со стрелкой назад»): экран объединяет архивы
                // упражнений и категорий; contentDescription кнопки
                // архива ExercisesScreen — тот же ключ.
                title = { Text(stringResource(R.string.archive_title)) },
                navigationIcon = {
                    IconButton(onClick = state.onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            // Один cast на контент: таб-бар и выбранная вкладка — из Success
            // (до первой эмиссии (Loading) — вкладка «Упражнения»).
            val success = state.uiState as? ArchivedExercisesUiState.Success
            val selectedTab = success?.selectedTab ?: ArchivedTab.EXERCISES
            // Таб-бар только при двух непустых коллекциях (spec «Табы
            // „Упражнения“ / „Категории“ и видимость таб-бара») —
            // паттерн «таб-бар при ≥ 2 видимых вкладках» OverviewScreen.
            if (success != null && success.exercises.isNotEmpty() && success.categories.isNotEmpty()) {
                CatalogTabs(
                    selectedIndex = selectedTab.ordinal,
                    onSelect = { state.onTabSelect(ArchivedTab.entries[it]) }
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state.uiState) {
                    ArchivedExercisesUiState.Loading -> Unit
                    is ArchivedExercisesUiState.Success ->
                        when {
                            // Обе коллекции пусты — заглушка по центру
                            // (spec «Пустое состояние списка»), экран
                            // не закрывается.
                            s.exercises.isEmpty() && s.categories.isEmpty() -> ArchivedEmptyStub()
                            // Один непустой тип (таб-бар скрыт) — просто
                            // список этого типа (spec «Только упражнения»
                            // / «Только категории»), вкладка не важна.
                            s.categories.isEmpty() -> ArchivedExercisesList(state = state, success = s)
                            s.exercises.isEmpty() -> ArchivedCategoriesList(state = state, success = s)
                            // Оба типа — контент по выбранной вкладке;
                            // пустая вкладка = просто пустой список.
                            s.selectedTab == ArchivedTab.EXERCISES ->
                                ArchivedExercisesList(state = state, success = s)
                            else -> ArchivedCategoriesList(state = state, success = s)
                        }
                }
            }
        }
    }
}

/**
 * Заглушка пустого архива — «В архиве пусто» по центру (паттерн
 * `exercises_list_empty`). Показывается только когда пусты ОБЕ
 * коллекции (spec «Пустое состояние списка»); экран не закрывается.
 */
@Composable
private fun ArchivedEmptyStub() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.archived_exercises_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
        )
    }
}

/**
 * Список архивных упражнений + контекстное меню «Вернуть»:
 * long-press открывает меню у точки нажатия (паттерн
 * `ExercisesList` в `ExercisesScreen.kt`).
 */
@Composable
private fun ArchivedExercisesList(
    state: ArchivedExercisesState,
    success: ArchivedExercisesUiState.Success
) {
    ContextMenuList(
        items = success.exercises,
        keyOf = { it.id },
        menuOffsetInRoot = state.contextMenu?.offsetInRoot,
        onLongPress = { item, offset ->
            state.onContextMenuItemChange(ExerciseContextMenuState(item, offset))
        },
        rowContent = { exercise, modifier ->
            ExerciseListItemRow(
                exercise = exercise,
                isSelected = state.contextMenu?.item?.id == exercise.id,
                modifier = modifier
            )
        },
        menu = { offset ->
            state.contextMenu?.let { menu ->
                UnarchiveContextMenu(
                    offset = offset,
                    onMenuDismiss = { state.onContextMenuItemChange(null) },
                    onUnarchiveClick = { state.onUnarchiveExercise(menu.item.id) }
                )
            }
        }
    )
}

/**
 * Список архивных категорий + контекстное меню «Вернуть»
 * (custom-categories, spec «Вкладка „Категории“ в архиве»): тот же
 * [CategoryListItemRow], что на вкладке категорий экрана упражнений;
 * меню — единственный пункт «Вернуть» для стандартных и кастомных
 * ([UnarchiveContextMenu] — по образцу архивных упражнений).
 */
@Composable
private fun ArchivedCategoriesList(
    state: ArchivedExercisesState,
    success: ArchivedExercisesUiState.Success
) {
    ContextMenuList(
        items = success.categories,
        keyOf = { it.id },
        menuOffsetInRoot = state.categoryContextMenu?.offsetInRoot,
        onLongPress = { item, offset ->
            state.onCategoryContextMenuItemChange(CategoryContextMenuState(item, offset))
        },
        rowContent = { category, modifier ->
            CategoryListItemRow(
                category = category,
                isSelected = state.categoryContextMenu?.item?.id == category.id,
                modifier = modifier
            )
        },
        menu = { offset ->
            state.categoryContextMenu?.let { menu ->
                UnarchiveContextMenu(
                    offset = offset,
                    onMenuDismiss = { state.onCategoryContextMenuItemChange(null) },
                    onUnarchiveClick = { state.onUnarchiveCategory(menu.item.id) }
                )
            }
        }
    )
}
