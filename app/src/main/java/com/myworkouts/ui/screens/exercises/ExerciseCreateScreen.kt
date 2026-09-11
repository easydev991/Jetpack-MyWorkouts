package com.myworkouts.ui.screens.exercises

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
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
                title = { Text(stringResource(R.string.exercise_create_title)) },
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
                    .menuAnchor()
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
