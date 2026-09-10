package com.myworkouts.ui.screens.exercises

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExercisesUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview-компонентов экрана «Упражнения» (конвенция проекта: светлая +
 * тёмная темы, `showBackground`, `locale = "ru"`, обёртка в
 * `JetpackMyWorkoutsTheme`). Ключевые состояния: список с комментарием
 * и без, пустое состояние, Loading — FAB виден на каждом полноэкранном
 * превью (он часть [ExercisesScreenContent]).
 */

private val previewPullups =
    Exercise(
        id = 1L,
        name = "Подтягивания",
        category = ExerciseCategory.PULL,
        isCustom = false,
        comment = "широкий хват",
        createdAt = 1_721_000_000_000L,
        measuredBy = ExerciseMeasuredBy.REPS
    )

private val previewPlank =
    Exercise(
        id = 2L,
        name = "Планка",
        category = ExerciseCategory.CORE,
        isCustom = false,
        comment = null,
        createdAt = 1_721_100_000_000L,
        measuredBy = ExerciseMeasuredBy.TIME
    )

private val previewRunning =
    Exercise(
        id = 3L,
        name = "Бег",
        category = ExerciseCategory.CARDIO,
        isCustom = false,
        comment = null,
        createdAt = 1_721_200_000_000L,
        measuredBy = ExerciseMeasuredBy.TIME
    )

private fun previewScreenState(uiState: ExercisesUiState): ExercisesScreenState =
    ExercisesScreenState(uiState = uiState, onAddExerciseClick = {})

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesScreenContentListPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Список: строка с комментарием (Подтягивания) и без (Планка, Бег)
            ExercisesScreenContent(
                state =
                    previewScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank, previewRunning)
                            )
                    )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesScreenContentEmptyPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Пустое состояние: «Нет упражнений» по центру, FAB на месте
            ExercisesScreenContent(
                state = previewScreenState(uiState = ExercisesUiState.Success(exercises = emptyList()))
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesScreenContentLoadingPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Начальное состояние до первой эмиссии: тело пустое, FAB виден
            ExercisesScreenContent(state = previewScreenState(uiState = ExercisesUiState.Loading))
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseListItemRowPreview() {
    JetpackMyWorkoutsTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            // С комментарием и лейблом «повторы»
            ExerciseListItemRow(exercise = previewPullups)
            // Без комментария, лейбл «длительность»
            ExerciseListItemRow(exercise = previewPlank)
        }
    }
}
