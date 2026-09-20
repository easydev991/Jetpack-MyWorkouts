package com.myworkouts.ui.screens.exercises

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.state.ExerciseCreateUiState
import com.myworkouts.ui.viewmodel.ExerciseCreateViewModel

/**
 * Экран создания упражнения: VM-обёртка — подписка на состояние
 * и колбэки, вся верстка — в [ExerciseCreateScreenContent].
 *
 * @param viewModel ViewModel экрана
 * @param onBackClick Навигация назад (закрытие экрана)
 * @param modifier внешний модификатор
 */
@Composable
fun ExerciseCreateScreen(
    viewModel: ExerciseCreateViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    ExerciseCreateScreenContent(
        state =
            ExerciseCreateScreenState(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onCategoryChange = viewModel::onCategoryChange,
                onMeasuredByChange = viewModel::onMeasuredByChange,
                onCommentChange = viewModel::onCommentChange,
                onIconNumberChange = viewModel::onIconNumberChange,
                onSaveClick = { viewModel.save(onSaved = onBackClick) },
                onBackClick = onBackClick
            ),
        modifier = modifier
    )
}

/**
 * Test/Preview-state для [ExerciseCreateScreenContent]: поля
 * [ExerciseCreateUiState] плюс колбэки. UI-тесты и превью передают
 * готовый state без ViewModel.
 */
internal data class ExerciseCreateScreenState(
    val uiState: ExerciseCreateUiState,
    val onNameChange: (String) -> Unit,
    val onCategoryChange: (ExerciseCategory) -> Unit,
    val onMeasuredByChange: (ExerciseMeasuredBy) -> Unit,
    val onCommentChange: (String) -> Unit,
    val onIconNumberChange: (Int?) -> Unit,
    val onSaveClick: () -> Unit,
    val onBackClick: () -> Unit
)

/**
 * Контент экрана создания упражнения: TopAppBar со стрелкой «назад»,
 * форма из 4 полей и кнопка «Сохранить» в `Scaffold.bottomBar` с
 * блоком ошибки под ней. Системный back и стрелка идут через единый
 * `attemptClose` — при несохранённых изменениях показывается диалог
 * подтверждения (spec-требование «BackHandler + hasUnsavedChanges —
 * подтверждение закрытия»).
 *
 * Используется для UI-тестов в изоляции без ViewModel (образец —
 * `EntryCreateScreenContent`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseCreateScreenContent(
    state: ExerciseCreateScreenState,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val attemptClose = {
        if (state.uiState.hasUnsavedChanges) showConfirmDialog = true else state.onBackClick()
    }

    // Системный «назад» идёт через то же подтверждение, что и стрелка
    // TopAppBar; без несохранённых изменений обработчик отключён
    BackHandler(enabled = state.uiState.hasUnsavedChanges) { attemptClose() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // Режим редактирования — «Изменить упражнение», создание —
                // «Новое упражнение» (edit-delete-exercise, §8.4)
                title = {
                    Text(
                        stringResource(
                            if (state.uiState.isEditMode) {
                                R.string.exercise_edit_title
                            } else {
                                R.string.exercise_create_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = attemptClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = { ExerciseCreateSaveBottomBar(state = state) }
    ) { paddingValues ->
        ExerciseCreateForm(state = state, paddingValues = paddingValues)
    }

    if (showConfirmDialog) {
        ExerciseCreateCloseConfirmDialog(
            onConfirm = {
                showConfirmDialog = false
                state.onBackClick()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}

/**
 * Алерт подтверждения закрытия экрана с несохранёнными изменениями:
 * заголовок «Закрыть?», кнопка подтверждения в `colorScheme.error`,
 * отмена — существующая строка `cancel` (паттерн-близнец
 * `EntryCloseConfirmDialog`; строки переиспользуются без новых ключей —
 * текст универсален для любой формы).
 *
 * @param onConfirm Обработчик «Закрыть» — закрыть экран без сохранения
 * @param onDismiss Обработчик «Отмена»/тапа мимо — скрыть диалог,
 *   экран остаётся открытым
 */
@Composable
internal fun ExerciseCreateCloseConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_confirm_close_dialog_title)) },
        text = { Text(stringResource(R.string.entry_confirm_close_dialog_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
            ) {
                Text(stringResource(R.string.entry_confirm_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Скроллящаяся форма создания упражнения: название, категория,
 * тип измерения и комментарий.
 */
@Composable
internal fun ExerciseCreateForm(
    state: ExerciseCreateScreenState,
    paddingValues: PaddingValues
) {
    val uiState = state.uiState
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = uiState.name.orEmpty(),
            onValueChange = state.onNameChange,
            label = { Text(stringResource(R.string.exercise_field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        ExerciseIconPickerSection(
            selectedIconNumber = state.uiState.iconNumber,
            onIconSelect = state.onIconNumberChange
        )

        CategoryDropdownField(
            selectedCategory = uiState.category,
            onCategoryChange = state.onCategoryChange
        )

        // Два сегмента — те же строки, что в каталоге (один домен — одна
        // локализация); общий маппер не нужен, ресурсы и есть источник истины
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = uiState.measuredBy == ExerciseMeasuredBy.REPS,
                onClick = { state.onMeasuredByChange(ExerciseMeasuredBy.REPS) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text(stringResource(R.string.exercise_measured_by_reps)) }
            )
            SegmentedButton(
                selected = uiState.measuredBy == ExerciseMeasuredBy.TIME,
                onClick = { state.onMeasuredByChange(ExerciseMeasuredBy.TIME) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text(stringResource(R.string.exercise_measured_by_time)) }
            )
        }

        OutlinedTextField(
            value = uiState.comment.orEmpty(),
            onValueChange = state.onCommentChange,
            label = { Text(stringResource(R.string.exercise_field_comment)) },
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Выпадающий список категорий (`ExposedDropdownMenuBox`) —
 * паттерн-близнец `DurationDropdownField`. Пока категория не выбрана,
 * показывается placeholder; сами категории — локализованные лейблы
 * через [labelResId], в БД сохраняется enum.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryDropdownField(
    selectedCategory: ExerciseCategory?,
    onCategoryChange: (ExerciseCategory) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value =
                selectedCategory
                    ?.let { stringResource(it.labelResId()) }
                    .orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.exercise_field_category)) },
            placeholder = { Text(stringResource(R.string.exercise_field_category_select)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ExerciseCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.labelResId())) },
                    onClick = {
                        onCategoryChange(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Нижняя панель экрана: единственная кнопка «Сохранить» поверх
 * клавиатуры (`navigationBarsPadding` + `imePadding` — максимум
 * инсетов, не сумма) и блок ошибки под ней при `isSaveError = true`
 * (паттерн — `EntryCreateScreen.SaveBottomBar`, без секции подсказок).
 */
@Composable
internal fun ExerciseCreateSaveBottomBar(state: ExerciseCreateScreenState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = state.onSaveClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.uiState.canSave
        ) {
            Text(stringResource(R.string.entry_save))
        }

        // Decision 5 (edit-delete-exercise): упражнение не найдено — кнопка
        // недостижимо неактивна (guard `!isExerciseMissing` в canSave),
        // подсказка объясняет почему (паттерн `isEntryMissing`)
        if (state.uiState.isExerciseMissing) {
            Text(
                text = stringResource(R.string.exercise_hint_exercise_missing),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.uiState.isSaveError) {
            Text(
                text = stringResource(R.string.exercise_save_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Локализованный лейбл категории для UI (в БД — сам enum, не строка).
 * Единственное место маппинга `ExerciseCategory` → строковый ресурс.
 */
@StringRes
private fun ExerciseCategory.labelResId(): Int =
    when (this) {
        ExerciseCategory.PUSH -> R.string.exercise_category_push
        ExerciseCategory.PULL -> R.string.exercise_category_pull
        ExerciseCategory.LEGS -> R.string.exercise_category_legs
        ExerciseCategory.CORE -> R.string.exercise_category_core
        ExerciseCategory.CARDIO -> R.string.exercise_category_cardio
    }

/**
 * Экран создания/редактирования доступен только для пользовательских
 * упражнений (`is_custom = true`) — стандартные не редактируются, поэтому
 * отдельного условия показа секции в форме не нужно: она видна всегда.
 * Одна и та же секция работает и при создании, и при изменении уже
 * выбранной иконки (edit-режим делит эту же форму).
 *
 * Сетка — [FlowRow], а не `LazyVerticalGrid`: форма скроллится через
 * `verticalScroll`, вкладывать lazy-скролл в lazy-скролл ради 20 ячеек
 * незачем. Колонки и размер ячеек считаются из фактической ширины
 * ([BoxWithConstraints]): минимум 56dp на ячейку, ряд всегда заполнен
 * целиком — хвост ширины распределяется по ячейкам, а не остаётся
 * справа «под ещё одну иконку».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseIconPickerSection(
    selectedIconNumber: Int?,
    onIconSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Минимум 56dp на ячейку + 8dp между ними; Dp/Dp даёт Float, toInt усекает вниз
        val columns = ((maxWidth + 8.dp) / (56.dp + 8.dp)).toInt().coerceAtLeast(1)
        // Ячейки добирают недостающее до полного ряда (максимум +8dp на ячейку).
        // Усечение до целого пикселя обязательно: Modifier.size измеряет ячейку
        // roundToPx, и округление каждой ячейки вверх переполняет ряд на 1–2px —
        // FlowRow переносит последнюю ячейку каждого ряда, справа остаётся
        // пустой «столбец под ещё одну иконку» (Samsung S25, density 540)
        val cellSize =
            with(LocalDensity.current) {
                ((maxWidth - 8.dp * (columns - 1)) / columns).toPx().toInt().toDp()
            }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.exercise_field_icon),
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseIconPickerCell(
                    cellSize = cellSize,
                    selected = selectedIconNumber == null,
                    onClick = { onIconSelect(null) },
                    testTagSuffix = EXERCISE_ICON_PICKER_CELL_NO_ICON_TAG
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = stringResource(R.string.exercise_icon_picker_no_icon)
                    )
                }
                ExerciseIcons.byNumber.entries.forEach { (number, ref) ->
                    ExerciseIconPickerCell(
                        cellSize = cellSize,
                        selected = selectedIconNumber == number,
                        onClick = { onIconSelect(number) },
                        testTagSuffix = number.toString()
                    ) {
                        // Вариант ресурса по теме — как в [ExerciseIcon]
                        val resId = if (isSystemInDarkTheme()) ref.dark else ref.light
                        Image(
                            painter = painterResource(resId),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Тег ячейки сетки иконок в Compose UI-тестах (exercise-icon-selection,
 * design Decision 5): префикс + суффикс — номер иконки (`1`..`19`) или
 * `"none"` для ячейки «Без иконки». Полный тег: `exercise_icon_picker_cell_<суффикс>`.
 */
internal const val EXERCISE_ICON_PICKER_CELL_TEST_TAG = "exercise_icon_picker_cell_"

/** Суффикс тега ячейки «Без иконки». */
internal const val EXERCISE_ICON_PICKER_CELL_NO_ICON_TAG = "none"

/**
 * Ячейка сетки иконок: квадрат [cellSize], не выбрана — едва заметная
 * обводка `outlineVariant` 0.5dp (штатный M3-цвет для тонких границ),
 * выбрана — primary-обводка 2dp. Обводка layout не сдвигает — скакания
 * ячеек при выборе нет.
 *
 * Внутри `Modifier.selectableGroup` (родительский `FlowRow`) ячейка
 * работает как `Role.RadioButton` — `Modifier.selectable` ставит
 * `Selected` semantic на узел, его ловят UI-тесты через
 * `assertIsSelected`/`assertIsNotSelected` в unmerged-дереве; для
 * TalkBack ячейки — это radio-группа «выбрать иконку упражнения».
 * Сама иконка каталога остаётся `contentDescription = null` —
 * именованные лейблы для 19 иконок не вводятся (спека «Секция выбора
 * иконки»); ячейка «Без иконки» несёт `stringResource(
 * R.string.exercise_icon_picker_no_icon)` как `contentDescription`,
 * которое добавляет семантический лейбл именно той ячейке, у которой
 * нет визуального ярлыка.
 *
 * [testTagSuffix] — суффикс для `Modifier.testTag` (номер иконки или
 * `"none"` для ячейки «Без иконки»), задаётся на callsite секции.
 */
@Composable
private fun ExerciseIconPickerCell(
    cellSize: Dp,
    selected: Boolean,
    onClick: () -> Unit,
    testTagSuffix: String,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val tagModifier = Modifier.testTag(EXERCISE_ICON_PICKER_CELL_TEST_TAG + testTagSuffix)
    Box(
        modifier =
            Modifier
                .size(cellSize)
                .border(
                    width = if (selected) 2.dp else 0.5.dp,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    shape = shape
                ).clip(shape)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton
                ).then(tagModifier),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
