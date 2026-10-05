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
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ArchivedExercisesUiState
import com.myworkouts.ui.state.ArchivedTab
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview-компонентов экрана «Архив» (exercises-archive +
 * custom-categories, tasks 7.3). Конвенция проекта: светлая + тёмная
 * темы, `showBackground`, `locale = "ru"`, обёртка в
 * `JetpackMyWorkoutsTheme`. Ключевые состояния — заглушка пустого
 * архива, список строк, список с подсвеченной строкой и открытым
 * long-press меню «Вернуть» (точка нажатия — на первой строке, как в
 * приложении), таб-бар при двух типах, вкладка «Категории».
 */

private val previewPullups =
    Exercise(
        id = 1L,
        name = "Подтягивания",
        category = "PULL",
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
        category = "CORE",
        isCustom = false,
        comment = null,
        createdAt = 1_721_100_000_000L,
        measuredBy = ExerciseMeasuredBy.TIME,
        isArchived = true
    )

/** Архивная стандартная категория (превью). */
private val previewCoreCategory =
    Category(
        id = 10L,
        name = "CORE",
        colorArgb = 0xFF4CAF50.toInt(),
        isCustom = false,
        isArchived = true
    )

/** Архивная кастомная категория (превью; в списке — выше стандартной). */
private val previewCustomCategory =
    Category(
        id = 11L,
        name = "Дома",
        colorArgb = 0xFF9C27B0.toInt(),
        isCustom = true,
        isArchived = true
    )

/** Список архивных категорий в порядке сортировки SQL (`is_custom DESC`). */
private val previewCategories = listOf(previewCustomCategory, previewCoreCategory)

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

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ArchivedExercisesScreenContentTabsPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // В архиве оба типа — таб-бар под TopAppBar, вкладка
            // «Упражнения» выбрана (spec «Табы „Упражнения“ /
            // „Категории“»)
            ArchivedExercisesScreenContent(
                state =
                    previewArchivedState(
                        uiState =
                            ArchivedExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank),
                                categories = previewCategories
                            )
                    )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ArchivedExercisesScreenContentCategoriesTabPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Вкладка «Категории»: строки с цветовыми индикаторами,
            // кастомная выше стандартной (сортировка is_custom DESC)
            ArchivedExercisesScreenContent(
                state =
                    previewArchivedState(
                        uiState =
                            ArchivedExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank),
                                categories = previewCategories,
                                selectedTab = ArchivedTab.CATEGORIES
                            )
                    )
            )
        }
    }
}
