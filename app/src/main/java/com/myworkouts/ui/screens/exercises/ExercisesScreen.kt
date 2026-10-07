// TooManyFunctions подавлен осознанно: таб-переключатель и вкладка
// «Категории» (custom-categories 5.4) добавили top-level функции, а
// конвенция превью (светлая + тёмная тема) и переиспользование алертов
// требуют держать их рядом с упражнениями; дробить файл хуже подавления.
@file:Suppress("TooManyFunctions")

package com.myworkouts.ui.screens.exercises

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.DeleteCategoryError
import com.myworkouts.domain.model.DeleteExerciseError
import com.myworkouts.domain.model.Exercise
import com.myworkouts.ui.state.ExercisesTab
import com.myworkouts.ui.state.ExercisesUiState
import com.myworkouts.ui.util.categoryLabel
import com.myworkouts.ui.viewmodel.ExercisesViewModel

/**
 * Экран «Упражнения» — вкладка каталога: реактивный список
 * стандартных и пользовательских упражнений, пустое состояние, FAB
 * навигации на создание упражнения, long-press меню для кастомных
 * упражнений с edit/delete/archive (exercises-screen, спека + спека
 * edit-delete-exercise «Контекстное меню по долгому нажатию»,
 * «Алерт подтверждения удаления упражнения», «Ошибка удаления —
 * типизированные причины в снекбаре»; exercises-archive: меню
 * «Архивировать» для кастомных и стандартных, алерт подтверждения
 * архивации, `IconButton` архива в `TopAppBar` при
 * `hasArchived`).
 *
 * VM-обёртка: подписка на [ExercisesViewModel.uiState], локальное
 * состояние long-press меню, `SnackbarHostState` для one-shot
 * ошибок удаления. Вся верстка — в [ExercisesScreenContent].
 *
 * @param viewModel ViewModel с реактивным списком каталога
 * @param onAddExerciseClick Тап по FAB — навигация на
 *   [com.myworkouts.navigation.Screen.ExerciseCreate] (прокидывается из
 *   `RootScreen`, как `MainScreen → EntryCreateScreen`)
 * @param onEditExercise Тап «Изменить» в long-press меню кастомного
 *   упражнения → навигация на
 *   `Screen.ExerciseCreate.createRoute(item.id)` (edit-delete-exercise
 *   §4.6.a — маршрут прокидывается из `RootScreen`).
 * @param onArchivedExercisesClick Тап `IconButton` архива в
 *   `TopAppBar` → навигация на
 *   [com.myworkouts.navigation.Screen.ArchivedExercises]
 *   (exercises-archive spec «Кнопка архива в TopAppBar»).
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    onAddExerciseClick: () -> Unit,
    onAddCategoryClick: () -> Unit,
    onArchivedExercisesClick: () -> Unit,
    analyticsProvider: AnalyticsProvider,
    onEditExercise: (Long) -> Unit = {},
    onEditCategory: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var contextMenu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
    var categoryContextMenu by remember { mutableStateOf<CategoryContextMenuState?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    ExercisesErrorSnackbar(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onErrorShown = viewModel::onErrorShown,
        onCategoryErrorShown = viewModel::onCategoryErrorShown
    )

    ExercisesScreenContent(
        state =
            ExercisesScreenState(
                uiState = uiState,
                contextMenu = contextMenu,
                categoryContextMenu = categoryContextMenu,
                // FAB-события в точке нажатия (design D10)
                onAddExerciseClick = {
                    analyticsProvider.log(
                        AnalyticsEvent.UserAction(UserActionType.OPEN_EXERCISE_CREATE)
                    )
                    onAddExerciseClick()
                },
                onAddCategoryClick = {
                    analyticsProvider.log(
                        AnalyticsEvent.UserAction(UserActionType.OPEN_CATEGORY_CREATE)
                    )
                    onAddCategoryClick()
                },
                // Обёртки long-press меню: «закрыть меню и делегировать»
                // (паттерн MainScreen.kt:90–95)
                onTabSelect = viewModel::selectTab,
                onContextMenuItemChange = { contextMenu = it },
                onCategoryContextMenuItemChange = { categoryContextMenu = it },
                onRequestEdit = {
                    contextMenu = null
                    onEditExercise(it.id)
                },
                onRequestArchive = {
                    contextMenu = null
                    viewModel.requestArchive(it)
                },
                onRequestDelete = {
                    contextMenu = null
                    viewModel.requestDelete(it)
                },
                onConfirmDelete = viewModel::confirmDelete,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmArchive = viewModel::confirmArchive,
                onCancelArchive = viewModel::cancelArchive,
                onRequestCategoryEdit = {
                    categoryContextMenu = null
                    onEditCategory(it.id)
                },
                onRequestCategoryArchive = {
                    categoryContextMenu = null
                    viewModel.requestCategoryArchive(it.id)
                },
                onRequestCategoryDelete = {
                    categoryContextMenu = null
                    viewModel.requestCategoryDelete(it)
                },
                onConfirmCategoryDelete = viewModel::confirmCategoryDelete,
                onCancelCategoryDelete = viewModel::cancelCategoryDelete,
                onConfirmCategoryArchive = viewModel::confirmCategoryArchive,
                onCancelCategoryArchive = viewModel::cancelCategoryArchive,
                onArchivedExercisesClick = onArchivedExercisesClick
            ),
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * One-shot снекбар ошибок каталога (design Decision 6;
 * prevent-from-archiving-all — показывает и ошибки архивации, поэтому
 * переименован из `ExercisesDeleteErrorSnackbar`): локализованный
 * текст резолвится на уровне композиции (`stringResource` —
 * `@Composable`), чтобы `LaunchedEffect` использовал его без повторного
 * резолва в suspend-блоке (для `Loading` — всегда `null`). Ключ эффекта —
 * текущий [uiState]: при изменении блок перезапускается, текущий снекбар
 * закрывается, показывается новый; сбрасывается пара каналов той
 * сущности, чья ошибка была показана (упражнения приоритетнее
 * категорий — паттерн прежний).
 */
@Composable
private fun ExercisesErrorSnackbar(
    uiState: ExercisesUiState,
    snackbarHostState: SnackbarHostState,
    onErrorShown: () -> Unit,
    onCategoryErrorShown: () -> Unit
) {
    val errorMessage: String? =
        when (val s = uiState) {
            ExercisesUiState.Loading -> null
            is ExercisesUiState.Success ->
                s.deleteError?.let { deleteErrorText(it) }
                    ?: s.archiveError
                        .takeIf { it }
                        ?.let { stringResource(R.string.exercise_last_active_archive_error) }
                    ?: s.categoryDeleteError?.let { categoryDeleteErrorText(it) }
                    ?: s.categoryArchiveError
                        .takeIf { it }
                        ?.let { stringResource(R.string.category_last_active_archive_error) }
        }
    LaunchedEffect(uiState) {
        val currentSuccess = uiState as? ExercisesUiState.Success ?: return@LaunchedEffect
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
        if (currentSuccess.deleteError != null || currentSuccess.archiveError) {
            onErrorShown()
        } else {
            onCategoryErrorShown()
        }
    }
}

/**
 * Транзитное состояние long-press меню: какое упражнение + где на
 * экране (edit-delete-exercise §4.6.a — образец
 * `MainScreen.ContextMenuState`).
 */
internal data class ExerciseContextMenuState(
    val item: Exercise,
    val offsetInRoot: Offset
)

/**
 * Транзитное состояние long-press меню категории (custom-categories
 * 5.4): какая категория + где на экране — копия
 * [ExerciseContextMenuState].
 */
internal data class CategoryContextMenuState(
    val item: Category,
    val offsetInRoot: Offset
)

/**
 * Test/Preview-state для [ExercisesScreenContent]: данные
 * [ExercisesUiState] плюс колбэки. UI-тесты и превью передают
 * готовый state без ViewModel (образец — `MainScreenState`).
 *
 * @property contextMenu Транзитное состояние long-press меню
 *   (`null` — закрыто). Прокидывается из VM-обёртки
 *   [ExercisesScreen], которая хранит `remember` `mutableStateOf`.
 */
internal data class ExercisesScreenState(
    val uiState: ExercisesUiState,
    val contextMenu: ExerciseContextMenuState? = null,
    /** Транзитное состояние long-press меню категории (custom-categories). */
    val categoryContextMenu: CategoryContextMenuState? = null,
    val onAddExerciseClick: () -> Unit = {},
    /** FAB на вкладке «Категории» → `Screen.CategoryCreate.createRoute()`. */
    val onAddCategoryClick: () -> Unit = {},
    /** Переключение вкладки (custom-categories, design Decision 6). */
    val onTabSelect: (ExercisesTab) -> Unit = {},
    val onContextMenuItemChange: (ExerciseContextMenuState?) -> Unit = {},
    val onCategoryContextMenuItemChange: (CategoryContextMenuState?) -> Unit = {},
    val onRequestEdit: (Exercise) -> Unit = {},
    val onRequestArchive: (Exercise) -> Unit = {},
    val onRequestDelete: (Exercise) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    /**
     * Подтверждение архивации. Сигнатура `(Boolean) -> Unit`: аргумент —
     * значение чекбокса «Больше не показывать» (true = записать pref
     * «больше не показывать диалог», false = не записывать;
     * archive-alert-hide, tasks §4.3, design.md Decision 4).
     * `ExerciseConfirmDialog.onConfirm` остаётся `() -> Unit` — лямбда
     * `{ state.onConfirmArchive(suppress) }` в [SuccessBody] склеивает
     * захваченный `suppress` и диалог.
     */
    val onConfirmArchive: (Boolean) -> Unit = {},
    val onCancelArchive: () -> Unit = {},
    /** Тап «Изменить» в меню категории → edit-маршрут (`category.id`). */
    val onRequestCategoryEdit: (Category) -> Unit = {},
    /** Тап «Архивировать» в меню категории (VM читает suppress-флаг). */
    val onRequestCategoryArchive: (Category) -> Unit = {},
    /** Тап «Удалить» в меню категории (пре-чек внутри VM). */
    val onRequestCategoryDelete: (Category) -> Unit = {},
    val onConfirmCategoryDelete: () -> Unit = {},
    val onCancelCategoryDelete: () -> Unit = {},
    /**
     * Подтверждение архивации категории — сигнатура с suppress,
     * симметрично [onConfirmArchive] (custom-categories, spec «Алерт
     * архивации категории с чекбоксом подавления»).
     */
    val onConfirmCategoryArchive: (Boolean) -> Unit = {},
    val onCancelCategoryArchive: () -> Unit = {},
    val onArchivedExercisesClick: () -> Unit = {}
)

/**
 * Контент экрана «Упражнения»: TopAppBar, тело (список или
 * заглушка), FAB, long-press меню и алерт подтверждения удаления.
 * Используется для UI-тестов и превью без ViewModel
 * (design Decision 3, change `entry-create-screen`).
 *
 * `snackbarHostState` — параметр, чтобы один `Scaffold` мог
 * разделять один `SnackbarHostState` между VM-обёрткой
 * [ExercisesScreen] и контентом (всплывающий снекбар управляется
 * `LaunchedEffect` на уровне обёртки).
 *
 * @param state Данные экрана и колбэки (см. [ExercisesScreenState])
 * @param snackbarHostState Состояние Material3 `SnackbarHost`,
 *   общее между обёрткой и Content. По умолчанию — новый
 *   `remember { SnackbarHostState() }` (для превью и unit-вызовов).
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisesScreenContent(
    state: ExercisesScreenState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    // Один cast на контент: выбранная вкладка — из Success (до первой
    // эмиссии (Loading) и в превью — «Упражнения», таб-бар и FAB отрисованы
    // сразу, без прыжка после загрузки), видимость кнопки архива — тоже.
    val success = state.uiState as? ExercisesUiState.Success
    val selectedTab = success?.selectedTab ?: ExercisesTab.EXERCISES
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // System nav bar inset уже учтён NavigationBar в RootScreen;
        // здесь оставляем только горизонтальные insets (displayCutout
        // и т.п.).
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises_title)) },
                actions = {
                    // Кнопка архива — реактивно по наличию архивного
                    // объекта любого типа (custom-categories, spec
                    // «Кнопка архива в TopAppBar»). Видимость через
                    // текущий `Success` — при `Loading` иконка не
                    // показывается.
                    ExercisesTopBarArchiveAction(
                        visible = success?.hasArchived == true,
                        onClick = state.onArchivedExercisesClick
                    )
                }
            )
        },
        floatingActionButton = {
            ExercisesFab(selectedTab = selectedTab, state = state)
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            CatalogTabs(
                selectedIndex = selectedTab.ordinal,
                onSelect = { state.onTabSelect(ExercisesTab.entries[it]) }
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state.uiState) {
                    ExercisesUiState.Loading -> Unit
                    is ExercisesUiState.Success ->
                        when (s.selectedTab) {
                            ExercisesTab.EXERCISES -> SuccessBody(state = state, success = s)
                            ExercisesTab.CATEGORIES -> CategoriesBody(state = state, success = s)
                        }
                }
            }
        }
    }
}

/**
 * Кнопка архива в `TopAppBar` (exercises-archive + custom-categories,
 * spec «Кнопка архива в TopAppBar»): contentDescription = archive_title —
 * общий ключ с заголовком экрана архива (паттерн «кнопка → экран» для
 * TalkBack; exercises_archive_button удалён).
 */
@Composable
private fun ExercisesTopBarArchiveAction(
    visible: Boolean,
    onClick: () -> Unit
) {
    if (visible) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = stringResource(R.string.archive_title)
            )
        }
    }
}

/**
 * FAB экрана — действует по активной вкладке (spec «FAB открывает
 * ExerciseCreateScreen» + custom-categories: на вкладке категорий —
 * CategoryCreate); contentDescription по вкладке.
 */
@Composable
private fun ExercisesFab(
    selectedTab: ExercisesTab,
    state: ExercisesScreenState
) {
    val isCategoriesTab = selectedTab == ExercisesTab.CATEGORIES
    FloatingActionButton(
        onClick = {
            if (isCategoriesTab) state.onAddCategoryClick() else state.onAddExerciseClick()
        }
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription =
                stringResource(
                    if (isCategoriesTab) {
                        R.string.categories_add_category
                    } else {
                        R.string.exercises_add_exercise
                    }
                )
        )
    }
}

/**
 * Общий таб-бар «Упражнения / Категории» каталога и архива
 * (custom-categories, spec «Табы „Упражнения“ / „Категории“»):
 * индексная сигнатура — один рендер для [ExercisesTab] и [ArchivedTab]
 * (паттерн OverviewTabs, [SecondaryTabRow]); call-site маппит свой
 * enum в индекс.
 */
@Composable
internal fun CatalogTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    SecondaryTabRow(selectedTabIndex = selectedIndex) {
        Tab(
            selected = selectedIndex == 0,
            onClick = { onSelect(0) },
            text = { Text(stringResource(R.string.exercises_tab_exercises)) }
        )
        Tab(
            selected = selectedIndex == 1,
            onClick = { onSelect(1) },
            text = { Text(stringResource(R.string.exercises_tab_categories)) }
        )
    }
}

/**
 * Тело экрана для `ExercisesUiState.Success`: каталог или заглушка
 * пустого состояния, long-press меню у якоря, алерт удаления при
 * непустом `pendingDeleteId` (edit-delete-exercise §4.6.d),
 * алерт архивации при `pendingArchiveId` (exercises-archive spec
 * «Алерт подтверждения архивации»).
 */
@Composable
private fun SuccessBody(
    state: ExercisesScreenState,
    success: ExercisesUiState.Success
) {
    // Состояние чекбокса «Больше не показывать» в диалоге архивации
    // (archive-alert-hide, tasks §4.3, design.md Decision 4): лежит в
    // scope `SuccessBody` (вне composable-лямбды слота), чтобы и
    // suppressContent-слот, и onConfirm-лямбда захватывали одну и ту же
    // `MutableState`-переменную через замыкание. `remember` — состояние
    // переживает recomposition, но не переживает закрытие диалога
    // (новый `pendingArchiveId` → новый вызов SuccessBody →
    // `remember` сбрасывается на `false`, как и требуется — галочка не
    // переносится между разными диалогами).
    var suppress by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (success.exercises.isEmpty()) {
            // Заглушка пустого состояния (spec «Пустое состояние
            // списка»): один текст без подзаголовка.
            Text(
                text = stringResource(R.string.exercises_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
            )
        } else {
            ExercisesList(state = state, success = success)
        }

        val pendingDelete =
            success.exercises.firstOrNull { it.id == success.pendingDeleteId }
        if (pendingDelete != null) {
            ExerciseConfirmDialog(
                itemName = pendingDelete.name,
                titleRes = R.string.exercise_delete_dialog_title,
                textRes = R.string.exercise_delete_dialog_text,
                confirmRes = R.string.exercise_delete_dialog_confirm,
                cancelRes = R.string.exercise_delete_dialog_cancel,
                confirmInError = true,
                onConfirm = state.onConfirmDelete,
                onCancel = state.onCancelDelete
            )
        }

        val pendingArchive =
            success.exercises.firstOrNull { it.id == success.pendingArchiveId }
        if (pendingArchive != null) {
            ExerciseConfirmDialog(
                itemName = pendingArchive.name,
                titleRes = R.string.exercise_archive_dialog_title,
                textRes = R.string.exercise_archive_dialog_text,
                confirmRes = R.string.exercise_archive_dialog_confirm,
                cancelRes = R.string.exercise_archive_dialog_cancel,
                confirmInError = false,
                // Слот для чекбокса «Больше не показывать». Захватывает
                // ту же `suppress`, что и onConfirm-лямбда ниже.
                suppressContent = {
                    ArchiveSuppressSlot(
                        checked = suppress,
                        onCheckedChange = { suppress = it }
                    )
                },
                // `() -> Unit` для ExerciseConfirmDialog.onConfirm —
                // совместимая сигнатура. Внутри читает захваченную
                // `suppress` и передаёт в стейт-холдер, который вызовет
                // `confirmArchive(suppress)` (design.md Decision 4:
                // диалог не знает про suppress).
                onConfirm = { state.onConfirmArchive(suppress) },
                onCancel = state.onCancelArchive
            )
        }
    }
}

/**
 * «Последний активный» элемент каталога — guard видимости пунктов
 * long-press меню (prevent-from-archiving-all, design Decision 7):
 * вычисляется из уже загруженных списков `ExercisesUiState.Success`
 * (активные `exercises` / `categories`), новых запросов в VM нет.
 * Обобщён: параметр [idOf] — извлечение id (у [Exercise] и [Category]
 * совпадает с `keyOf` списка).
 */
private fun <T> isLastActive(
    item: T,
    active: List<T>,
    idOf: (T) -> Long
): Boolean = active.size == 1 && idOf(active.single()) == idOf(item)

/**
 * Список упражнений + контекстное меню (edit-delete-exercise
 * §4.6.b–d): long-press на кастомной строке открывает меню у
 * точки нажатия, алерт удаления управляется VM через
 * `pendingDeleteId` (рендерится в [SuccessBody], не здесь).
 * У последнего активного упражнения «Архивировать»/«Удалить» скрыты;
 * у стандартного последнего активного меню пустое — long-press и
 * accessibility-действие его не открывают (prevent-from-archiving-all,
 * design Decision 7).
 */
@Composable
private fun ExercisesList(
    state: ExercisesScreenState,
    success: ExercisesUiState.Success
) {
    ContextMenuList(
        items = success.exercises,
        keyOf = { it.id },
        menuOffsetInRoot = state.contextMenu?.offsetInRoot,
        onLongPress = { item, offset ->
            // Early return до смены state: не открываем пустое меню
            // (стандартное + последний активный) и не включаем подсветку.
            // Паттерн закрывает и a11y-канал — custom action идёт через
            // ту же лямбду.
            if (item.isCustom || !isLastActive(item, success.exercises) { it.id }) {
                state.onContextMenuItemChange(ExerciseContextMenuState(item, offset))
            }
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
                val lastActive =
                    isLastActive(menu.item, success.exercises) { it.id }
                ActiveContextMenu(
                    offset = offset,
                    isCustom = menu.item.isCustom,
                    showArchive = !lastActive,
                    showDelete = !lastActive,
                    onMenuDismiss = { state.onContextMenuItemChange(null) },
                    onEditClick = { state.onRequestEdit(menu.item) },
                    onArchiveClick = { state.onRequestArchive(menu.item) },
                    onDeleteClick = { state.onRequestDelete(menu.item) }
                )
            }
        }
    )
}

/**
 * Тело вкладки «Категории» (custom-categories, spec «Вкладка
 * „Категории“ — список и пустое состояние»): список активных
 * категорий или заглушка, алерт удаления при
 * `pendingCategoryDeleteId`, алерт архивации с чекбоксом подавления
 * при `pendingCategoryArchiveId` (переиспользование
 * [ExerciseConfirmDialog] + [ArchiveSuppressSlot], тексты категорий).
 */
@Composable
private fun CategoriesBody(
    state: ExercisesScreenState,
    success: ExercisesUiState.Success
) {
    // Состояние чекбокса «Больше не показывать» — отдельная копия
    // от диалога архивации упражнений (см. [SuccessBody]; custom-categories,
    // spec «Алерт архивации категории с чекбоксом подавления»).
    var suppress by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (success.categories.isEmpty()) {
            // Заглушка пустого состояния: «Нет категорий» по центру
            // (паттерн exercises_list_empty).
            Text(
                text = stringResource(R.string.categories_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
            )
        } else {
            CategoriesList(state = state, success = success)
        }

        val pendingCategoryDelete =
            success.categories.firstOrNull { it.id == success.pendingCategoryDeleteId }
        if (pendingCategoryDelete != null) {
            ExerciseConfirmDialog(
                itemName = categoryLabel(pendingCategoryDelete.name),
                titleRes = R.string.category_delete_dialog_title,
                textRes = R.string.category_delete_dialog_text,
                confirmRes = R.string.delete,
                cancelRes = R.string.cancel,
                confirmInError = true,
                onConfirm = state.onConfirmCategoryDelete,
                onCancel = state.onCancelCategoryDelete
            )
        }

        val pendingCategoryArchive =
            success.categories.firstOrNull { it.id == success.pendingCategoryArchiveId }
        if (pendingCategoryArchive != null) {
            ExerciseConfirmDialog(
                itemName = categoryLabel(pendingCategoryArchive.name),
                titleRes = R.string.category_archive_dialog_title,
                textRes = R.string.category_archive_dialog_text,
                confirmRes = R.string.category_archive_dialog_confirm,
                cancelRes = R.string.cancel,
                confirmInError = false,
                // Чекбокс подавления — тот же слот, что у упражнений;
                // флаг в DataStore — отдельный (design Decision 8).
                suppressContent = {
                    ArchiveSuppressSlot(
                        checked = suppress,
                        onCheckedChange = { suppress = it }
                    )
                },
                onConfirm = { state.onConfirmCategoryArchive(suppress) },
                onCancel = state.onCancelCategoryArchive
            )
        }
    }
}

/**
 * Список категорий + контекстное меню (custom-categories, spec
 * «Контекстное меню категории по типу»): long-press открывает меню
 * у точки нажатия — для кастомной категории три пункта
 * (Изменить/Архивировать/Удалить), для стандартной один
 * («Архивировать»). У последней активной категории
 * «Архивировать»/«Удалить» скрыты; стандартная последняя активная
 * не открывает пустое меню (симметрично [ExercisesList];
 * prevent-from-archiving-all, design Decision 7). Алерты рендерятся
 * в [CategoriesBody].
 */
@Composable
private fun CategoriesList(
    state: ExercisesScreenState,
    success: ExercisesUiState.Success
) {
    ContextMenuList(
        items = success.categories,
        keyOf = { it.id },
        menuOffsetInRoot = state.categoryContextMenu?.offsetInRoot,
        onLongPress = { item, offset ->
            if (item.isCustom || !isLastActive(item, success.categories) { it.id }) {
                state.onCategoryContextMenuItemChange(CategoryContextMenuState(item, offset))
            }
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
                val lastActive =
                    isLastActive(menu.item, success.categories) { it.id }
                ActiveContextMenu(
                    offset = offset,
                    isCustom = menu.item.isCustom,
                    showArchive = !lastActive,
                    showDelete = !lastActive,
                    onMenuDismiss = { state.onCategoryContextMenuItemChange(null) },
                    onEditClick = { state.onRequestCategoryEdit(menu.item) },
                    onArchiveClick = { state.onRequestCategoryArchive(menu.item) },
                    onDeleteClick = { state.onRequestCategoryDelete(menu.item) },
                    editRes = R.string.category_list_menu_edit,
                    archiveRes = R.string.category_list_menu_archive,
                    deleteRes = R.string.category_list_menu_delete
                )
            }
        }
    )
}

/**
 * Активное меню `DropdownMenu` в точке long-press — для кастомного
 * элемента три пункта (Изменить / Архивировать / Удалить), для
 * стандартного один «Архивировать» (edit-delete-exercise §4.6.a;
 * exercises-archive Decision 4; custom-categories — то же для
 * категорий). Общее для упражнений и категорий каталога: тексты —
 * параметрами-ресурсами (дефолт — строки упражнений, категориям
 * передаются `category_list_menu_*`). У всех `leadingIcon` с
 * `contentDescription = null` — текст пункта сам читается службами
 * доступности (entry-edit-mode, design Decision 6; образец —
 * `MainScreen.EntryContextMenu`).
 *
 * Видимость «Архивировать»/«Удалить» выключается call-сайтами для
 * последнего активного элемента каталога (prevent-from-archiving-all,
 * design Decision 7); пустое меню при этом не открывается —
 * long-press подавляется до смены state.
 *
 * @param showArchive Показывать пункт «Архивировать»
 * @param showDelete Показывать пункт «Удалить» (для кастомных; через
 *   [isCustom] и так скрыт у стандартных)
 */
@Suppress("LongParameterList")
@Composable
internal fun ActiveContextMenu(
    offset: DpOffset,
    isCustom: Boolean,
    showArchive: Boolean = true,
    showDelete: Boolean = true,
    onMenuDismiss: () -> Unit,
    onEditClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    @StringRes editRes: Int = R.string.exercise_list_menu_edit,
    @StringRes archiveRes: Int = R.string.exercise_list_menu_archive,
    @StringRes deleteRes: Int = R.string.exercise_list_menu_delete
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onMenuDismiss,
        offset = offset
    ) {
        if (isCustom) {
            DropdownMenuItem(
                text = { Text(stringResource(editRes)) },
                onClick = onEditClick,
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                }
            )
        }
        if (showArchive) {
            DropdownMenuItem(
                text = { Text(stringResource(archiveRes)) },
                onClick = onArchiveClick,
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Archive, contentDescription = null)
                }
            )
        }
        if (isCustom && showDelete) {
            DropdownMenuItem(
                text = { Text(stringResource(deleteRes)) },
                onClick = onDeleteClick,
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                }
            )
        }
    }
}

/**
 * Меню `DropdownMenu` архивной строки — единственный пункт «Вернуть»
 * (exercises-archive spec «Long-press меню «Вернуть» для архивных
 * строк»); общее для упражнений и категорий архива. `leadingIcon` —
 * как в [ActiveContextMenu].
 */
@Composable
internal fun UnarchiveContextMenu(
    offset: DpOffset,
    onMenuDismiss: () -> Unit,
    onUnarchiveClick: () -> Unit = {}
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onMenuDismiss,
        offset = offset
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.exercise_list_menu_unarchive)) },
            onClick = onUnarchiveClick,
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Unarchive, contentDescription = null)
            }
        )
    }
}

/**
 * Алерт подтверждения для удаления/архивации упражнения: одна функция
 * обслуживает оба сценария — различаются только локализованные строки и
 * цвет confirm (edit-delete-exercise §4.6.c; exercises-archive design
 * Decision 5). Используется и в `ExercisesScreen` (удаление,
 * архивация), и в превью-файле. Структура `AlertDialog` (title + text +
 * confirm + dismiss) одинакова для обоих сценариев; раньше это были две
 * почти-одинаковые функции, отличавшиеся тремя ID ресурсов и цветом
 * confirm (`colorScheme.error` для удаления, дефолтный для архивации —
 * операция обратима).
 *
 * @param itemName Название упражнения (для текста алерта — `%1$s`)
 * @param titleRes Заголовок алерта
 * @param textRes Тело алерта (с подстановкой `%1$s`)
 * @param confirmRes Текст кнопки подтверждения
 * @param cancelRes Текст кнопки отмены
 * @param confirmInError `true` — confirm в `colorScheme.error`
 *   (необратимое действие); `false` — дефолтный цвет (обратимое)
 * @param suppressContent Опциональный слот для дополнительного
 *   контента между основным текстом и кнопками (archive-alert-hide,
 *   tasks §4.1). `null` — рендеринг байт-в-байт прежний. Используется
 *   для чекбокса «Больше не показывать» в диалоге архивации; диалог
 *   удаления и обратная совместимость call-сайтов не передают слот.
 * @param onConfirm Подтверждение — VM вызывает `confirmDelete()` /
 *   `confirmArchive()`. Сигнатура `() -> Unit` неизменна: диалог не
 *   знает про `suppress`, состояние чекбокса захватывается замыканием
 *   в `SuccessBody` (design.md Decision 4).
 * @param onCancel Отмена — VM вызывает `cancelDelete()` /
 *   `cancelArchive()`; также срабатывает на тап вне алерта / системный
 *   back (`onDismissRequest = onCancel`).
 */
@Suppress("LongParameterList")
@Composable
internal fun ExerciseConfirmDialog(
    itemName: String,
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    @StringRes confirmRes: Int,
    @StringRes cancelRes: Int,
    confirmInError: Boolean,
    suppressContent: (@Composable () -> Unit)? = null,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val confirmColors = exerciseConfirmButtonColors(confirmInError)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(titleRes)) },
        text = {
            // suppressContent (если есть) отрисовывается между текстом
            // и кнопками (внутри слота `text` AlertDialog). `null` —
            // рендеринг прежний, одиночный Text.
            Column {
                Text(stringResource(textRes, itemName))
                if (suppressContent != null) {
                    suppressContent()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = confirmColors) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(cancelRes))
            }
        }
    )
}

/**
 * Слот `suppressContent` алерта архивации: чекбокс «Больше не
 * показывать» + локализованный лейбл (archive-alert-hide). Состояние
 * поднимается на call-сайте: [SuccessBody] захватывает его в
 * `onConfirm`-лямбде, превью держат собственную копию через `remember`
 * (design.md Decision 4 — диалог не знает про `suppress`).
 *
 * Accessibility (accessibility-semantics-fixes, design D7): `Row`
 * обёрнут в `toggleable` c `Role.Checkbox`, у чекбокса `onClick = null`
 * — лейбл и чекбокс образуют единую кликабельную область (по образцу
 * радио-строк `AppearanceScreen`, где вместо `toggleable` — `selectable`).
 */
@Composable
internal fun ArchiveSuppressSlot(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                // Padding ДО toggleable: зона нажатия обнимает контент,
                // иначе подсветка long-press включает пустой отступ и
                // контент выглядит не отцентрированным в зоне.
                .padding(top = 8.dp)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange
                )
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            // `onCheckedChange = null` отключает у M3 Checkbox слот
            // `minimumInteractiveComponentSize` — чекбокс рендерится
            // «голым» боксом вплотную к лейблу. Явный 48dp-слот
            // воспроизводит геометрию интерактивного чекбокса: бокс
            // 20dp центрируется в слоте (зазор и выравнивание как до
            // accessibility-semantics-fixes).
            modifier = Modifier.size(48.dp)
        )
        Text(stringResource(R.string.exercise_archive_dont_show_again))
    }
}

/**
 * Цвета confirm-кнопки алерта подтверждения: error-цвет для удаления
 * (`confirmInError = true`), обычные — для архивации. Общие для
 * [ExerciseConfirmDialog] и windowless-превью его контента
 * (`ExerciseConfirmDialogSample` в `ExercisesScreenPreview.kt`).
 */
@Composable
internal fun exerciseConfirmButtonColors(confirmInError: Boolean): ButtonColors =
    if (confirmInError) {
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.textButtonColors()
    }

/**
 * Локализованный текст типизированной ошибки удаления для снекбара
 * (спека «Ошибка удаления — типизированные причины в снекбаре»).
 * Резолвится на уровне композиции: `stringResource` — `@Composable`.
 * Вынесено из VM-обёртки [ExercisesScreen] ради лимита detekt
 * `LongMethod: 60` (место инлайна возвращено после выноса общих
 * long-press хелперов в `ExerciseMenuHelpers.kt`).
 */
@Composable
private fun deleteErrorText(error: DeleteExerciseError): String =
    when (error) {
        DeleteExerciseError.HasLinkedEntries ->
            stringResource(R.string.exercise_delete_error_used_in_workouts)
        DeleteExerciseError.StandardExercise ->
            stringResource(R.string.exercise_delete_error_standard)
        DeleteExerciseError.LastActive ->
            stringResource(R.string.exercise_last_active_delete_error)
        DeleteExerciseError.Other ->
            stringResource(R.string.exercise_delete_error_generic)
    }

/**
 * Локализованный текст типизированной ошибки удаления категории для
 * снекбара (custom-categories, spec «Ошибки удаления категории —
 * типизированные причины в снекбаре»). Паттерн — [deleteErrorText].
 */
@Composable
private fun categoryDeleteErrorText(error: DeleteCategoryError): String =
    when (error) {
        DeleteCategoryError.UsedByExercises ->
            stringResource(R.string.category_delete_error_used_by_exercises)
        DeleteCategoryError.StandardCategory ->
            stringResource(R.string.category_delete_error_standard)
        DeleteCategoryError.LastActive ->
            stringResource(R.string.category_last_active_delete_error)
        DeleteCategoryError.Other ->
            stringResource(R.string.category_delete_error_generic)
    }
