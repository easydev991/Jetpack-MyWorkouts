package com.myworkouts.ui.screens.exercises

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExerciseCreateUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview-компонентов экрана создания упражнения — по паттерну соседних
 * `ExercisesScreenPreview` / `EntryCreateScreenPreview`: одна функция на
 * состояние, светлая + тёмная темы (`UI_MODE_NIGHT_YES`), `showBackground`,
 * `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`. Четыре состояния
 * формы (spec «Превью в обеих темах») × 2 темы = 8 `@Preview`: пустая
 * форма, name + category (Save enabled), с комментарием, measuredBy = TIME.
 */
private fun previewScreenState(uiState: ExerciseCreateUiState): ExerciseCreateScreenState =
    ExerciseCreateScreenState(
        uiState = uiState,
        onNameChange = {},
        onCategoryChange = {},
        onMeasuredByChange = {},
        onCommentChange = {},
        onSaveClick = {},
        onBackClick = {}
    )

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseCreateScreenContentEmptyPreview() {
    JetpackMyWorkoutsTheme {
        ExerciseCreateScreenContent(
            // Пустая форма: canSave = false, «Выберите категорию», REPS
            state = previewScreenState(ExerciseCreateUiState())
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseCreateScreenContentFilledPreview() {
    JetpackMyWorkoutsTheme {
        ExerciseCreateScreenContent(
            // name + category: canSave = true, без комментария
            state =
                previewScreenState(
                    ExerciseCreateUiState(
                        name = "Жим лёжа",
                        category = ExerciseCategory.PUSH
                    )
                )
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseCreateScreenContentWithCommentPreview() {
    JetpackMyWorkoutsTheme {
        ExerciseCreateScreenContent(
            // Заполненный комментарий — необязательное поле
            state =
                previewScreenState(
                    ExerciseCreateUiState(
                        name = "Жим лёжа",
                        category = ExerciseCategory.PUSH,
                        comment = "узкий хват"
                    )
                )
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseCreateScreenContentTimePreview() {
    JetpackMyWorkoutsTheme {
        ExerciseCreateScreenContent(
            // Тип измерения TIME — активен сегмент «длительность»
            state =
                previewScreenState(
                    ExerciseCreateUiState(
                        name = "Планка",
                        category = ExerciseCategory.CORE,
                        measuredBy = ExerciseMeasuredBy.TIME
                    )
                )
        )
    }
}
