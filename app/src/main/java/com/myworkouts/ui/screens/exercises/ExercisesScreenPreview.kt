package com.myworkouts.ui.screens.exercises

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.myworkouts.R
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
 * превью (он часть [ExercisesScreenContent]). Отдельно — long-press меню,
 * алерт удаления, алерт архивации и подсветка строки (edit-delete-exercise
 * §9.1; exercises-archive §9.1, §9.3, §9.5).
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

private val previewBench =
    Exercise(
        id = 100L,
        name = "Жим",
        category = ExerciseCategory.PUSH,
        isCustom = true,
        comment = null,
        createdAt = 1_721_300_000_000L,
        measuredBy = ExerciseMeasuredBy.REPS
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
            // Обычная строка: с комментарием и лейблом «повторы»
            ExerciseListItemRow(exercise = previewPullups)
            // Подсвеченная — как при открытом long-press меню
            ExerciseListItemRow(exercise = previewPlank, isSelected = true)
        }
    }
}

/**
 * §9.3 exercises-archive: два состояния long-press меню рядом —
 * 3 пункта (Edit / Archive / Delete) для кастомного упражнения и
 * 1 пункт («Архивировать») для стандартного.
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseContextMenuPreview() {
    JetpackMyWorkoutsTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Кастомное (3 пункта): ${previewBench.name}")
            Box {
                Text(text = previewBench.name)
                ExerciseContextMenu(
                    offset = DpOffset.Zero,
                    mode = ExerciseMenuMode.Active,
                    isCustom = true,
                    onMenuDismiss = {},
                    onEditClick = {},
                    onArchiveClick = {},
                    onDeleteClick = {}
                )
            }
            Text(text = "Стандартное (1 пункт): ${previewRunning.name}")
            Box {
                Text(text = previewRunning.name)
                ExerciseContextMenu(
                    offset = DpOffset.Zero,
                    mode = ExerciseMenuMode.Active,
                    isCustom = false,
                    onMenuDismiss = {},
                    onEditClick = {},
                    onArchiveClick = {},
                    onDeleteClick = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseDeleteDialogPreview() {
    JetpackMyWorkoutsTheme {
        // Алерт подтверждения: confirm «Удалить» в error-цвете
        ExerciseConfirmDialog(
            itemName = previewRunning.name,
            titleRes = R.string.exercise_delete_dialog_title,
            textRes = R.string.exercise_delete_dialog_text,
            confirmRes = R.string.exercise_delete_dialog_confirm,
            cancelRes = R.string.exercise_delete_dialog_cancel,
            confirmInError = true,
            onConfirm = {},
            onCancel = {}
        )
    }
}

/**
 * §9.1 exercises-archive: алерт подтверждения архивации — обычный
 * цвет confirm (НЕ error).
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseArchiveDialogPreview() {
    JetpackMyWorkoutsTheme {
        ExerciseConfirmDialog(
            itemName = previewRunning.name,
            titleRes = R.string.exercise_archive_dialog_title,
            textRes = R.string.exercise_archive_dialog_text,
            confirmRes = R.string.exercise_archive_dialog_confirm,
            cancelRes = R.string.exercise_archive_dialog_cancel,
            confirmInError = false,
            onConfirm = {},
            onCancel = {}
        )
    }
}

/**
 * §9.5 exercises-archive: ключевые состояния `archivedCount` рядом
 * (конвенция AGENTS.md «Preview» — ключевые состояния рядом) — кнопка
 * архива в TopAppBar видна при `archivedCount = 1`, скрыта при
 * `archivedCount = 0`.
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesScreenContentArchivedCountPreview() {
    JetpackMyWorkoutsTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Кнопка архива в TopAppBar скрыта (`archivedCount = 0`)
            ExercisesScreenContent(
                state =
                    previewScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank),
                                archivedCount = 0
                            )
                    ),
                modifier = Modifier.weight(1f)
            )
            // Кнопка архива в TopAppBar видна (`archivedCount = 1`,
            // Icons.Filled.Archive)
            ExercisesScreenContent(
                state =
                    previewScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank),
                                archivedCount = 1
                            )
                    ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
