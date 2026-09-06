package com.myworkouts.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.EntryCreateUiState
import com.myworkouts.ui.viewmodel.EntryCreateViewModel
import java.time.LocalDate

/**
 * Экран создания записи о выполнении: VM-обёртка — подписка на состояние
 * и колбэки, вся верстка — в [EntryCreateScreenContent].
 *
 * @param viewModel ViewModel экрана
 * @param onBackClick Навигация назад (закрытие экрана)
 */
@Composable
fun EntryCreateScreen(
    viewModel: EntryCreateViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    EntryCreateScreenContent(
        state =
            EntryCreateScreenState(
                uiState = uiState,
                onExerciseChange = viewModel::onExerciseChange,
                onSetsChange = viewModel::onSetsChange,
                onRepsChange = viewModel::onRepsChange,
                onDurationChange = viewModel::onDurationChange,
                onWeightChange = viewModel::onWeightChange,
                onDateChange = viewModel::onDateChange,
                onTimeChange = viewModel::onTimeChange,
                onCommentChange = viewModel::onCommentChange,
                onSaveClick = { viewModel.save(onSaved = onBackClick) },
                onBackClick = onBackClick
            )
    )
}

/**
 * Test/Preview-state для [EntryCreateScreenContent]: поля [EntryCreateUiState]
 * плюс колбэки. UI-тесты и превью передают готовый state без ViewModel.
 */
internal data class EntryCreateScreenState(
    val uiState: EntryCreateUiState,
    val onExerciseChange: (Exercise) -> Unit,
    val onSetsChange: (String) -> Unit,
    val onRepsChange: (String) -> Unit,
    val onDurationChange: (Int?) -> Unit,
    val onWeightChange: (String) -> Unit,
    val onDateChange: (LocalDate) -> Unit,
    val onTimeChange: (hour: Int, minute: Int) -> Unit,
    val onCommentChange: (String) -> Unit,
    val onSaveClick: () -> Unit,
    val onBackClick: () -> Unit
)

/**
 * Контент экрана создания записи: выбор упражнения, подходы,
 * типозависимое поле (повторы или длительность), дата и время,
 * комментарий; кнопка сохранения закреплена в `Scaffold.bottomBar`,
 * подсказки и ошибка сохранения — под кнопкой ([SaveBottomBar]).
 *
 * Используется для UI-тестов в изоляции без ViewModel (образец —
 * `AppearanceScreenContent`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryCreateScreenContent(state: EntryCreateScreenState) {
    Scaffold(
        topBar = {
            TopAppBar(
                // Режим редактирования — «Изменить запись», создание — «Новая
                // запись» (entry-edit-mode, design Decision 7)
                title = {
                    Text(
                        stringResource(
                            if (state.uiState.isEditMode) {
                                R.string.entry_edit_title
                            } else {
                                R.string.entry_create_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = state.onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = { SaveBottomBar(state = state) }
    ) { paddingValues ->
        EntryCreateForm(state = state, paddingValues = paddingValues)
    }
}

/**
 * Скроллящаяся форма ввода записи: выбор упражнения, подходы,
 * типозависимое поле, дата, время и комментарий.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCreateForm(
    state: EntryCreateScreenState,
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
        ExerciseDropdownField(
            exercises = uiState.exercises,
            selectedExercise = uiState.selectedExercise,
            onExerciseChange = state.onExerciseChange
        )

        // Поля выполнения — только после выбора упражнения (design
        // Decision 4): подходы, типозависимое поле, доп. вес
        if (uiState.selectedExercise != null) {
            NumberEntryField(
                label = stringResource(R.string.entry_field_sets),
                value = uiState.setsText,
                onValueChange = state.onSetsChange
            )

            MeasuredByField(state = state)

            NumberEntryField(
                label = stringResource(R.string.entry_field_weight),
                value = uiState.weightText,
                onValueChange = state.onWeightChange,
                keyboardType = KeyboardType.Decimal
            )
        }

        DateField(
            date = uiState.date,
            onDateChange = state.onDateChange
        )

        TimeField(
            hour = uiState.hour,
            minute = uiState.minute,
            onTimeChange = state.onTimeChange
        )

        OutlinedTextField(
            value = uiState.comment,
            onValueChange = state.onCommentChange,
            label = { Text(stringResource(R.string.entry_field_comment)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Типозависимое поле (design.md Decision 2 / spec-сценарий «Смена
 * упражнения переключает поле»): REPS → «Повторы», TIME → «Длительность»,
 * упражнение не выбрано — поля нет.
 */
@Composable
private fun MeasuredByField(state: EntryCreateScreenState) {
    when (state.uiState.selectedExercise?.measuredBy) {
        ExerciseMeasuredBy.REPS ->
            NumberEntryField(
                label = stringResource(R.string.entry_field_reps),
                value = state.uiState.repsText,
                onValueChange = state.onRepsChange
            )

        ExerciseMeasuredBy.TIME ->
            DurationDropdownField(
                durationSec = state.uiState.durationSec,
                onDurationChange = state.onDurationChange
            )

        null -> Unit
    }
}

/**
 * Нижняя панель экрана (design.md Decision 1/3): кнопка «Сохранить»
 * в `Scaffold.bottomBar`, под ней подсказки `entry_hint_*` (живут
 * в [SaveHintsSection]) и ошибка сохранения — подсказка объясняет
 * неактивность кнопки, ошибка — неудачу сохранения. Контейнер с
 * `navigationBarsPadding` + `imePadding` (максимум инсетов, не сумма).
 */
@Composable
internal fun SaveBottomBar(state: EntryCreateScreenState) {
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

        SaveHintsSection(state = state)

        if (state.uiState.isSaveError) {
            Text(
                text = stringResource(R.string.entry_save_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Блок подсказок `entry_hint_*` без кнопки (design.md Decision 3):
 * живёт в [SaveBottomBar] под неактивной кнопкой. Инвариант spec
 * «кнопка неактивна И подсказка»: подсказка рендерится только при
 * `canSave = false` (см. [EntryCreateUiState.canSave]).
 */
@Composable
private fun SaveHintsSection(state: EntryCreateScreenState) {
    val uiState = state.uiState

    // Decision 8 (entry-edit-mode): запись не найдена — кнопка недостижимо
    // неактивна, подсказка объясняет почему
    if (uiState.isEntryMissing) {
        Text(
            text = stringResource(R.string.entry_hint_entry_missing),
            style = MaterialTheme.typography.bodySmall
        )
    }

    val hintResId =
        when {
            uiState.selectedExercise == null -> R.string.entry_hint_select_exercise
            uiState.selectedExercise.measuredBy == ExerciseMeasuredBy.REPS &&
                uiState.repsText.toIntOrNull() == null -> R.string.entry_hint_fill_reps

            uiState.selectedExercise.measuredBy == ExerciseMeasuredBy.TIME &&
                uiState.durationSec == null -> R.string.entry_hint_select_duration

            (uiState.setsText.toIntOrNull() ?: 0) < 1 -> R.string.entry_hint_fill_sets
            else -> null
        }
    if (hintResId != null && !uiState.canSave) {
        Text(
            text = stringResource(hintResId),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
