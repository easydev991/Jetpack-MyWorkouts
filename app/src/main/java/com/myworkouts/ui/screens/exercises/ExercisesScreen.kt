package com.myworkouts.ui.screens.exercises

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.DeleteExerciseError
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExercisesUiState
import com.myworkouts.ui.viewmodel.ExercisesViewModel

/**
 * Экран «Упражнения» — вкладка каталога: реактивный список
 * стандартных и пользовательских упражнений, пустое состояние, FAB
 * навигации на создание упражнения, long-press меню для кастомных
 * упражнений с edit/delete (exercises-screen, спека + спека
 * edit-delete-exercise «Контекстное меню по долгому нажатию»,
 * «Алерт подтверждения удаления упражнения», «Ошибка удаления —
 * типизированные причины в снекбаре»).
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
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    onAddExerciseClick: () -> Unit,
    onEditExercise: (Long) -> Unit = {},
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
            is ExercisesUiState.Success ->
                s.deleteError?.let { deleteErrorMessageResource(it) }
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
                onAddExerciseClick = onAddExerciseClick,
                onContextMenuItemChange = { contextMenu = it },
                onRequestEdit = { item ->
                    // Паттерн MainScreen.kt:90–95 — обёртка закрывает
                    // меню и делегирует без побочных эффектов
                    contextMenu = null
                    onEditExercise(item.id)
                },
                onRequestDelete = { item ->
                    contextMenu = null
                    viewModel.requestDelete(item)
                },
                onConfirmDelete = viewModel::confirmDelete,
                onCancelDelete = viewModel::cancelDelete
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
    val onRequestDelete: (Exercise) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {}
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
            TopAppBar(title = { Text(stringResource(R.string.exercises_title)) })
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
 * непустом `pendingDeleteId` (edit-delete-exercise §4.6.d).
 */
@Composable
private fun SuccessBody(
    state: ExercisesScreenState,
    success: ExercisesUiState.Success
) {
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

        val pendingExercise =
            success.exercises.firstOrNull { it.id == success.pendingDeleteId }
        if (pendingExercise != null) {
            ExerciseDeleteDialog(
                itemName = pendingExercise.name,
                onConfirm = state.onConfirmDelete,
                onCancel = state.onCancelDelete
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
                                isCustom = exercise.isCustom,
                                item = exercise,
                                positionInRootProvider = { positionInRoot },
                                onItemLongPress = state.onContextMenuItemChange
                            )
                )
            }
        }
        if (state.contextMenu != null) {
            val density = LocalDensity.current
            val menuOffset =
                DpOffset(
                    x =
                        with(density) {
                            (state.contextMenu.offsetInRoot.x - anchorOrigin.x).toDp()
                        },
                    y =
                        with(density) {
                            (state.contextMenu.offsetInRoot.y - anchorOrigin.y - anchorHeight)
                                .toDp()
                        }
                )
            ExerciseContextMenu(
                offset = menuOffset,
                onMenuDismiss = { state.onContextMenuItemChange(null) },
                onEditClick = { state.onRequestEdit(state.contextMenu.item) },
                onDeleteClick = { state.onRequestDelete(state.contextMenu.item) }
            )
        }
    }
}

/**
 * Подключает `detectTapGestures(onLongPress)` только для кастомных
 * упражнений (design Decision 4: стандартные — тишина, не дискриминация).
 * `positionInRootProvider` — лямбда, возвращающая актуальную позицию
 * строки в корне: closure ссылается на `mutableStateOf<Offset>`, чтобы
 * избежать пересоздания `pointerInput` при каждом изменении позиции
 * (иначе long-press отваливался бы на скролле).
 */
private fun Modifier.applyLongPress(
    isCustom: Boolean,
    item: Exercise,
    positionInRootProvider: () -> Offset,
    onItemLongPress: (ExerciseContextMenuState) -> Unit
): Modifier =
    if (isCustom) {
        this.pointerInput(item.id) {
            detectTapGestures(
                onLongPress = { localOffset ->
                    onItemLongPress(
                        ExerciseContextMenuState(item, positionInRootProvider() + localOffset)
                    )
                }
            )
        }
    } else {
        this
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
            Text(
                text = measuredByLabel(exercise.measuredBy),
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
 * Меню `DropdownMenu` в точке long-press: «Изменить» (карандаш)
 * выше «Удалить» (корзина), у обоих `leadingIcon` с
 * `contentDescription = null` — текст пункта сам читается
 * службами доступности (entry-edit-mode, design Decision 6;
 * образец — `MainScreen.EntryContextMenu`).
 */
@Composable
internal fun ExerciseContextMenu(
    offset: DpOffset,
    onMenuDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onMenuDismiss,
        offset = offset
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.exercise_list_menu_edit)) },
            onClick = onEditClick,
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.exercise_list_menu_delete)) },
            onClick = onDeleteClick,
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
            }
        )
    }
}

/**
 * Алерт подтверждения удаления упражнения: заголовок
 * «Удалить упражнение?», текст с названием упражнения,
 * confirm в `colorScheme.error` (edit-delete-exercise §4.6.c;
 * образец — `MainScreen.EntryDeleteDialog`).
 *
 * @param itemName Название упражнения (для текста алерта)
 * @param onConfirm Обработчик «Удалить» — VM вызывает `confirmDelete()`
 * @param onCancel Обработчик «Отмена» — VM вызывает `cancelDelete()`,
 *   также срабатывает на тап вне алерта / системный back
 *   (`onDismissRequest = onCancel`).
 */
@Composable
internal fun ExerciseDeleteDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.exercise_delete_dialog_title)) },
        text = {
            Text(stringResource(R.string.exercise_delete_dialog_text, itemName))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
            ) {
                Text(stringResource(R.string.exercise_delete_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.exercise_delete_dialog_cancel))
            }
        }
    )
}

/**
 * Локализованный лейбл типа измерения. Тотальный `when` без `else`:
 * при добавлении значения enum компилятор заставит дописать ветку
 * (design Decision 5).
 */
@Composable
private fun measuredByLabel(measuredBy: ExerciseMeasuredBy): String =
    when (measuredBy) {
        ExerciseMeasuredBy.REPS -> stringResource(R.string.exercise_measured_by_reps)
        ExerciseMeasuredBy.TIME -> stringResource(R.string.exercise_measured_by_time)
    }

/**
 * Маппинг типизированной ошибки удаления в локализованное сообщение
 * снекбара (edit-delete-exercise §4.6.d, design Decision 3 + 6).
 * Канон маппинга — норматив в спек-е `exercises-screen`, requirement
 * «Ошибка удаления — типизированные причины в снекбаре»: VM не
 * парсит текст исключения, UI не парсит — решение о типе ошибки
 * (`DeleteExerciseError`) принимается в VM, локализация — здесь.
 */
@Composable
private fun deleteErrorMessageResource(error: DeleteExerciseError): String =
    when (error) {
        DeleteExerciseError.HasLinkedEntries ->
            stringResource(R.string.exercise_delete_error_used_in_workouts)
        DeleteExerciseError.StandardExercise ->
            stringResource(R.string.exercise_delete_error_standard)
        DeleteExerciseError.Other ->
            stringResource(R.string.exercise_delete_error_generic)
    }
