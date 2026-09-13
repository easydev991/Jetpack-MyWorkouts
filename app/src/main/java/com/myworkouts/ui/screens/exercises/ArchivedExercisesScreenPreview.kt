package com.myworkouts.ui.screens.exercises

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ArchivedExercisesUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview-компонентов экрана «Архив упражнений» (exercises-archive,
 * §9.2). Конвенция проекта: светлая + тёмная темы, `showBackground`,
 * `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`. Ключевые
 * состояния — заглушка пустого архива, список строк, список с
 * подсвеченной строкой и открытым long-press меню «Вернуть»
 * (точка нажатия — на первой строке, как в приложении).
 */

private val previewPullups =
    Exercise(
        id = 1L,
        name = "Подтягивания",
        category = ExerciseCategory.PULL,
        isCustom = false,
        comment = "широкий хват",
        createdAt = 1_721_000_000_000L,
        measuredBy = ExerciseMeasuredBy.REPS,
        isArchived = true
    )

private val previewPlank =
    Exercise(
        id = 2L,
        name = "Планка",
        category = ExerciseCategory.CORE,
        isCustom = false,
        comment = null,
        createdAt = 1_721_100_000_000L,
        measuredBy = ExerciseMeasuredBy.TIME,
        isArchived = true
    )

private fun previewArchivedState(
    uiState: ArchivedExercisesUiState,
    contextMenu: ExerciseContextMenuState? = null
): ArchivedExercisesState =
    ArchivedExercisesState(
        uiState = uiState,
        contextMenu = contextMenu,
        onBackClick = {}
    )

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ArchivedExercisesScreenContentEmptyPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Пустой архив — заглушка «В архиве пусто» по центру
            ArchivedExercisesScreenContent(
                state =
                    previewArchivedState(
                        uiState = ArchivedExercisesUiState.Success(exercises = emptyList())
                    )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ArchivedExercisesScreenContentListPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Список: строка с комментарием и без
            ArchivedExercisesScreenContent(
                state =
                    previewArchivedState(
                        uiState =
                            ArchivedExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank)
                            )
                    )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ArchivedExercisesScreenContentListSelectedPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Долгое нажатие на первой строке: строка подсвечена, меню
            // «Вернуть» открывается от точки нажатия, как в приложении
            val pressPoint =
                with(LocalDensity.current) {
                    Offset(x = 48.dp.toPx(), y = 110.dp.toPx())
                }
            ArchivedExercisesScreenContent(
                state =
                    previewArchivedState(
                        uiState =
                            ArchivedExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank)
                            ),
                        contextMenu =
                            ExerciseContextMenuState(
                                item = previewPullups,
                                offsetInRoot = pressPoint
                            )
                    )
            )
        }
    }
}
