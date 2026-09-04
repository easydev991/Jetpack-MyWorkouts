package com.myworkouts.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
    val onDateChange: (LocalDate) -> Unit,
    val onTimeChange: (hour: Int, minute: Int) -> Unit,
    val onCommentChange: (String) -> Unit,
    val onSaveClick: () -> Unit,
    val onBackClick: () -> Unit
)

/**
 * Контент экрана создания записи: выбор упражнения, подходы,
 * типозависимое поле (повторы или длительность), дата и время,
 * комментарий, кнопка сохранения с подсказками.
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
                title = { Text(stringResource(R.string.entry_create_title)) },
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
        EntryCreateForm(state = state, paddingValues = paddingValues)
    }
}

/**
 * Скроллящаяся форма ввода записи: выбор упражнения, подходы,
 * типозависимое поле, дата, время, комментарий, сохранение.
 * `imePadding` поднимает контент над клавиатурой, `verticalScroll`
 * прокручивает сфокусированное поле в видимую зону
 * (design.md Decision 7).
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExerciseDropdownField(
            exercises = uiState.exercises,
            selectedExercise = uiState.selectedExercise,
            onExerciseChange = state.onExerciseChange
        )

        NumberEntryField(
            label = stringResource(R.string.entry_field_sets),
            value = uiState.setsText,
            onValueChange = state.onSetsChange
        )

        MeasuredByField(state = state)

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

        SaveSection(state = state)

        if (uiState.isSaveError) {
            Text(
                text = stringResource(R.string.entry_save_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
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
 * Кнопка сохранения `enabled = canSave` и подсказка обязательности при
 * `canSave = false` — spec: «кнопка неактивна И подсказка».
 */
@Composable
internal fun SaveSection(state: EntryCreateScreenState) {
    val uiState = state.uiState
    Button(
        onClick = state.onSaveClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = uiState.canSave
    ) {
        Text(stringResource(R.string.entry_save))
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
