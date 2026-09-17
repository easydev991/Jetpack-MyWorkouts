package com.myworkouts.ui.screens.exercises

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.domain.model.DeleteExerciseError
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExercisesUiState
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
 * `archivedCount > 0`).
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
    onEditExercise: (Long) -> Unit = {},
    onArchivedExercisesClick: () -> Unit,
    analyticsProvider: AnalyticsProvider,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var contextMenu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Локализованный текст ошибки для текущего `Success.deleteError`.
    // Резолвится на уровне композиции (stringResource — @Composable),
    // чтобы LaunchedEffect мог его использовать без повторного
    // резолва во время suspend-блока. Пересоздаётся при изменении
    // `deleteError` вместе с recomposition обёртки. Для Loading —
    // всегда `null` (поле `deleteError` существует только в Success).
    val errorMessage: String? =
        when (val s = uiState) {
            ExercisesUiState.Loading -> null
            is ExercisesUiState.Success -> s.deleteError?.let { deleteErrorText(it) }
        }

    // One-shot snackbar для ошибки удаления (design Decision 6):
    // ключ LaunchedEffect — текущий `uiState`, при изменении блок
    // перезапускается, текущий снекбар закрывается, новый
    // показывается. `onErrorShown()` сбрасывает флаг после показа —
    // без этого LaunchedEffect сработал бы повторно при следующем
    // composition с тем же `null`-после-сброса.
    LaunchedEffect(uiState) {
        val currentSuccess = uiState as? ExercisesUiState.Success ?: return@LaunchedEffect
        if (currentSuccess.deleteError == null) return@LaunchedEffect
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }

    ExercisesScreenContent(
        state =
            ExercisesScreenState(
                uiState = uiState,
                contextMenu = contextMenu,
                onAddExerciseClick = {
                    // FAB-событие в точке нажатия (design D10)
                    analyticsProvider.log(
                        AnalyticsEvent.UserAction(UserActionType.OPEN_EXERCISE_CREATE)
                    )
                    onAddExerciseClick()
                },
                onContextMenuItemChange = { contextMenu = it },
                onRequestEdit = { item ->
                    // Паттерн MainScreen.kt:90–95 — обёртка закрывает
                    // меню и делегирует без побочных эффектов
                    contextMenu = null
                    onEditExercise(item.id)
                },
                onRequestArchive = { item ->
                    contextMenu = null
                    viewModel.requestArchive(item)
                },
                onRequestDelete = { item ->
                    contextMenu = null
                    viewModel.requestDelete(item)
                },
                onConfirmDelete = viewModel::confirmDelete,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmArchive = viewModel::confirmArchive,
                onCancelArchive = viewModel::cancelArchive,
                onArchivedExercisesClick = onArchivedExercisesClick
            ),
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
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
 * Режим long-press меню: [Active] — пункты по типу упражнения
 * (Изменить/Архивировать/Удалить для кастомных, Архивировать для
 * стандартных); [Unarchive] — единственный пункт «Вернуть» для
 * архивных строк (exercises-archive Decision 4). Разделяемое
 * упражнениями и архивом — режим выбирает сам меню, вызывающему
 * достаточно передать нужные колбэки с дефолтом `{}`.
 */
internal enum class ExerciseMenuMode { Active, Unarchive }

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
    val onAddExerciseClick: () -> Unit = {},
    val onContextMenuItemChange: (ExerciseContextMenuState?) -> Unit = {},
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
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
                    // Кнопка архива — реактивно по `archivedCount > 0`
                    // (exercises-archive spec «Кнопка архива в TopAppBar»).
                    // Видимость через текущий `Success` — при `Loading`
                    // или в превью без VM поле `archivedCount` отсутствует,
                    // иконка не показывается.
                    val archivedCount =
                        (state.uiState as? ExercisesUiState.Success)?.archivedCount ?: 0
                    if (archivedCount > 0) {
                        IconButton(onClick = state.onArchivedExercisesClick) {
                            Icon(
                                imageVector = Icons.Filled.Archive,
                                contentDescription =
                                    stringResource(R.string.exercises_archive_button)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = state.onAddExerciseClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.exercises_add_exercise)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            when (val s = state.uiState) {
                ExercisesUiState.Loading -> Unit
                is ExercisesUiState.Success -> SuccessBody(state = state, success = s)
            }
        }
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
 * Список упражнений + контекстное меню (edit-delete-exercise
 * §4.6.b–d): long-press на кастомной строке открывает меню у
 * точки нажатия, алерт удаления управляется VM через
 * `pendingDeleteId` (рендерится в [SuccessBody], не здесь).
 */
@Composable
private fun ExercisesList(
    state: ExercisesScreenState,
    success: ExercisesUiState.Success
) {
    // Якорь для DropdownMenu (образец MainScreen.kt:261–271):
    // позиция списка в корне + высота нужны, чтобы сместить меню
    // к точке long-press с поправкой на верхнюю границу контейнера.
    var anchorOrigin by remember { mutableStateOf(IntOffset.Zero) }
    var anchorHeight by remember { mutableStateOf(0) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    anchorOrigin = IntOffset(pos.x.toInt(), pos.y.toInt())
                    anchorHeight = coords.size.height
                }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = success.exercises,
                key = { it.id },
                contentType = { "row" }
            ) { exercise ->
                var positionInRoot by remember { mutableStateOf(Offset.Zero) }
                ExerciseListItemRow(
                    exercise = exercise,
                    isSelected = state.contextMenu?.item?.id == exercise.id,
                    modifier =
                        Modifier
                            .onGloballyPositioned { coords ->
                                positionInRoot = coords.positionInRoot()
                            }.applyLongPress(
                                item = exercise,
                                positionInRootProvider = { positionInRoot },
                                onItemLongPress = state.onContextMenuItemChange
                            )
                )
            }
        }
        state.contextMenu?.let { menu ->
            ExerciseContextMenu(
                offset = contextMenuDpOffset(menu.offsetInRoot, anchorOrigin, anchorHeight),
                mode = ExerciseMenuMode.Active,
                isCustom = menu.item.isCustom,
                onMenuDismiss = { state.onContextMenuItemChange(null) },
                onEditClick = { state.onRequestEdit(menu.item) },
                onArchiveClick = { state.onRequestArchive(menu.item) },
                onDeleteClick = { state.onRequestDelete(menu.item) }
            )
        }
    }
}

/**
 * Строка каталога: название + лейбл типа измерения, комментарий
 * ниже при наличии (spec «Строка списка ExerciseListItemRow»;
 * образец — `EntryListItemRow` главного экрана). Фон анимируется
 * в `surfaceVariant`, пока для строки открыто контекстное меню
 * (edit-delete-exercise §4.6.b).
 *
 * Семантика `Selected` — стандартный канал accessibility Compose;
 * читается androidTest-ом через `SemanticsProperties.Selected`.
 * `mergeDescendants = true` нужен, чтобы `onNodeWithText` находил
 * строку, а не отдельный `Text`, иначе свойство Selected останется
 * только у родителя (образец — `EntryListItemRow.kt:88–95`).
 */
@Composable
internal fun ExerciseListItemRow(
    exercise: Exercise,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val backgroundColor =
        animateColorAsState(
            targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
            label = "rowBackground"
        ).value

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = backgroundColor)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {
                    selected = isSelected
                },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // Локализованный лейбл типа измерения. Тотальный `when` без
            // `else`: при добавлении значения enum компилятор заставит
            // дописать ветку (design decision 5).
            Text(
                text =
                    when (exercise.measuredBy) {
                        ExerciseMeasuredBy.REPS -> stringResource(R.string.exercise_measured_by_reps)
                        ExerciseMeasuredBy.TIME -> stringResource(R.string.exercise_measured_by_time)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
        exercise.comment?.let { comment ->
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Меню `DropdownMenu` в точке long-press: режим [ExerciseMenuMode.Active]
 * — для кастомного упражнения три пункта (Изменить / Архивировать /
 * Удалить), для стандартного один пункт «Архивировать»; режим
 * [ExerciseMenuMode.Unarchive] — единственный пункт «Вернуть» для
 * архивных строк (exercises-archive spec «Контекстное меню по долгому
 * нажатию — с учётом типа упражнения»; «Long-press меню «Вернуть» для
 * архивных строк»). У всех `leadingIcon` с `contentDescription = null` —
 * текст пункта сам читается службами доступности (entry-edit-mode,
 * design Decision 6; образец — `MainScreen.EntryContextMenu`).
 *
 * Колбэки лишних пунктов для конкретного режима не нужны и идут с
 * дефолтом `{}` (`onUnarchiveClick` — для Active, остальные — для
 * Unarchive). Это позволяет одному вызову обслуживать оба экрана
 * (`ExercisesScreen`, `ArchivedExercisesScreen`).
 */
@Suppress("LongParameterList")
@Composable
internal fun ExerciseContextMenu(
    offset: DpOffset,
    mode: ExerciseMenuMode,
    isCustom: Boolean,
    onMenuDismiss: () -> Unit,
    onEditClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onUnarchiveClick: () -> Unit = {}
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onMenuDismiss,
        offset = offset
    ) {
        when (mode) {
            ExerciseMenuMode.Active -> {
                if (isCustom) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.exercise_list_menu_edit)) },
                        onClick = onEditClick,
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exercise_list_menu_archive)) },
                    onClick = onArchiveClick,
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Archive, contentDescription = null)
                    }
                )
                if (isCustom) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.exercise_list_menu_delete)) },
                        onClick = onDeleteClick,
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                        }
                    )
                }
            }
            ExerciseMenuMode.Unarchive ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exercise_list_menu_unarchive)) },
                    onClick = onUnarchiveClick,
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Unarchive, contentDescription = null)
                    }
                )
        }
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
 */
@Composable
internal fun ArchiveSuppressSlot(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
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
        DeleteExerciseError.Other ->
            stringResource(R.string.exercise_delete_error_generic)
    }
