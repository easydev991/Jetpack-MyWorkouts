package com.myworkouts.ui.screens.entry

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.EntryCreateUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import java.time.LocalDate

/**
 * Preview-компонентов экрана создания записи (конвенция проекта: светлая +
 * тёмная темы, `showBackground`, `locale = "ru"`, обёртка в тему приложения).
 */
private val previewRepsExercise =
    Exercise(
        id = 1L,
        name = "Подтягивания",
        category = ExerciseCategory.PULL,
        isCustom = false,
        comment = null,
        createdAt = 0L,
        measuredBy = ExerciseMeasuredBy.REPS
    )

private val previewTimeExercise =
    Exercise(
        id = 2L,
        name = "Планка",
        category = ExerciseCategory.CORE,
        isCustom = false,
        comment = null,
        createdAt = 0L,
        measuredBy = ExerciseMeasuredBy.TIME
    )

private val previewCatalog = listOf(previewRepsExercise, previewTimeExercise)

private fun previewScreenState(uiState: EntryCreateUiState): EntryCreateScreenState =
    EntryCreateScreenState(
        uiState = uiState,
        onExerciseChange = {},
        onSetsChange = {},
        onRepsChange = {},
        onDurationChange = {},
        onWeightChange = {},
        onDateChange = {},
        onTimeChange = { _, _ -> },
        onCommentChange = {},
        onSaveClick = {},
        onBackClick = {}
    )

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun EntryCreateScreenContentPreview() {
    JetpackMyWorkoutsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EntryCreateScreenContent(
                // Упражнение не выбрано — поля выполнения скрыты
                state =
                    previewScreenState(
                        EntryCreateUiState(exercises = previewCatalog)
                    )
            )
            EntryCreateScreenContent(
                // REPS без повторов: canSave = false, видна подсказка
                state =
                    previewScreenState(
                        EntryCreateUiState(
                            exercises = previewCatalog,
                            selectedExercise = previewRepsExercise
                        )
                    )
            )
            EntryCreateScreenContent(
                // REPS заполненное с введённым весом: canSave = true
                state =
                    previewScreenState(
                        EntryCreateUiState(
                            exercises = previewCatalog,
                            selectedExercise = previewRepsExercise,
                            repsText = "10",
                            setsText = "3",
                            weightText = "12.5"
                        )
                    )
            )
            EntryCreateScreenContent(
                // TIME с длительностью и введённым весом
                state =
                    previewScreenState(
                        EntryCreateUiState(
                            exercises = previewCatalog,
                            selectedExercise = previewTimeExercise,
                            durationSec = 60,
                            weightText = "7.5"
                        )
                    )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseDropdownFieldPreview() {
    JetpackMyWorkoutsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Не выбрано — плейсхолдер
            ExerciseDropdownField(
                exercises = previewCatalog,
                selectedExercise = null,
                onExerciseChange = {}
            )
            // Выбрано REPS-упражнение
            ExerciseDropdownField(
                exercises = previewCatalog,
                selectedExercise = previewRepsExercise,
                onExerciseChange = {}
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun NumberEntryFieldPreview() {
    JetpackMyWorkoutsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberEntryField(label = "Подходы", value = "1", onValueChange = {}) // префилл
            NumberEntryField(label = "Повторы (обязательно)", value = "10", onValueChange = {}) // заполнено
            NumberEntryField( // вес — Decimal-клавиатура, дефолт «0»
                label = "Доп. вес (кг)",
                value = "0",
                onValueChange = {},
                keyboardType = KeyboardType.Decimal
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun DurationDropdownFieldPreview() {
    JetpackMyWorkoutsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DurationDropdownField(durationSec = null, onDurationChange = {}) // не выбрана
            DurationDropdownField(durationSec = 60, onDurationChange = {}) // «1 мин»
            DurationDropdownField(durationSec = 3600, onDurationChange = {}) // «1 час»
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun DateFieldPreview() {
    JetpackMyWorkoutsTheme {
        DateField(date = LocalDate.of(2024, 7, 15), onDateChange = {})
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun TimeFieldPreview() {
    JetpackMyWorkoutsTheme {
        TimeField(hour = 14, minute = 30, onTimeChange = { _, _ -> })
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun SaveSectionPreview() {
    JetpackMyWorkoutsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Упражнение не выбрано — подсказка выбрать
            SaveSection(
                state =
                    previewScreenState(
                        EntryCreateUiState(exercises = previewCatalog)
                    )
            )
            // REPS без повторов — подсказка заполнить
            SaveSection(
                state =
                    previewScreenState(
                        EntryCreateUiState(
                            exercises = previewCatalog,
                            selectedExercise = previewRepsExercise
                        )
                    )
            )
            // Всё заполнено — без подсказки
            SaveSection(
                state =
                    previewScreenState(
                        EntryCreateUiState(
                            exercises = previewCatalog,
                            selectedExercise = previewRepsExercise,
                            setsText = "3",
                            repsText = "10"
                        )
                    )
            )
        }
    }
}
