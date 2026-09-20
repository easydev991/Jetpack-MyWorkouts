// TooManyFunctions подавлен осознанно: 9 preview-функций — это требование
// конвенции проекта (светлая + тёмная тема для каждого ключевого состояния,
// AGENTS.md), а не раздувание логики; дробить превью на файлы хуже подавления.
@file:Suppress("TooManyFunctions")

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
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.state.EntryCreateUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import java.time.LocalDate
import java.time.ZoneId

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
        measuredBy = ExerciseMeasuredBy.REPS,
        iconNumber = ExerciseIcons.PULLUPS
    )

private val previewTimeExercise =
    Exercise(
        id = 2L,
        name = "Планка",
        category = ExerciseCategory.CORE,
        isCustom = false,
        comment = null,
        createdAt = 0L,
        measuredBy = ExerciseMeasuredBy.TIME,
        iconNumber = ExerciseIcons.PUSHUPS
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
        onOpenSettings = {},
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
            EntryCreateScreenContent(
                // Режим редактирования: предзаполненная форма, заголовок
                // «Изменить запись», кнопка неактивна без изменений
                state =
                    previewScreenState(
                        EntryCreateUiState(
                            exercises = previewCatalog,
                            selectedExercise = previewRepsExercise,
                            setsText = "3",
                            repsText = "10",
                            weightText = "12.5",
                            comment = "разминочный подход легче",
                            isEditMode = true
                        )
                    )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun EntryCreateScreenLongFormPreview() {
    JetpackMyWorkoutsTheme {
        EntryCreateScreenContent(
            // Длинная форма: кнопка видна без скролла — закреплена в
            // bottomBar (spec «Кнопка видна без прокрутки формы»;
            // тот же сценарий автоматизирован в EntryCreateScreenTest)
            state =
                previewScreenState(
                    EntryCreateUiState(
                        exercises = previewCatalog,
                        selectedExercise = previewRepsExercise,
                        setsText = "3",
                        repsText = "10",
                        weightText = "12.5",
                        date = LocalDate.of(2024, 7, 15),
                        hour = 14,
                        minute = 30,
                        comment =
                            List(30) { line ->
                                "Строка $line: достаточно длинный текст комментария"
                            }.joinToString(separator = "\n")
                    )
                )
        )
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
        // Момент открытия — тот же день, что и дата в поле (день открытия
        // экрана): более поздние дни в пикере превью будут недоступны
        val openMomentMillis =
            LocalDate
                .of(2024, 7, 15)
                .atTime(18, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        DateField(
            date = LocalDate.of(2024, 7, 15),
            openMomentMillis = openMomentMillis,
            onDateChange = {}
        )
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

@Composable
private fun PreviewSaveBottomBar(uiState: EntryCreateUiState) {
    SaveBottomBar(state = previewScreenState(uiState))
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun SaveBottomBarPreview() {
    JetpackMyWorkoutsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Всё заполнено — кнопка активна, подсказок нет
            PreviewSaveBottomBar(
                EntryCreateUiState(
                    exercises = previewCatalog,
                    selectedExercise = previewRepsExercise,
                    setsText = "3",
                    repsText = "10"
                )
            )
            // Упражнение не выбрано — кнопка неактивна, подсказка выбрать
            PreviewSaveBottomBar(EntryCreateUiState(exercises = previewCatalog))
            // REPS без повторов — подсказка заполнить
            PreviewSaveBottomBar(
                EntryCreateUiState(
                    exercises = previewCatalog,
                    selectedExercise = previewRepsExercise
                )
            )
            // Подходы очищены — подсказка заполнить подходы
            PreviewSaveBottomBar(
                EntryCreateUiState(
                    exercises = previewCatalog,
                    selectedExercise = previewRepsExercise,
                    repsText = "10",
                    setsText = ""
                )
            )
            // Запись не найдена в режиме редактирования — подсказка
            // «Запись была удалена» (design Decision 8)
            PreviewSaveBottomBar(EntryCreateUiState(isEditMode = true, isEntryMissing = true))
            // Ошибка сохранения — сообщение под кнопкой
            PreviewSaveBottomBar(
                EntryCreateUiState(
                    exercises = previewCatalog,
                    selectedExercise = previewRepsExercise,
                    setsText = "3",
                    repsText = "10",
                    isSaveError = true
                )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun EntryCloseConfirmDialogPreview() {
    JetpackMyWorkoutsTheme {
        EntryCloseConfirmDialog(onConfirm = {}, onDismiss = {})
    }
}
