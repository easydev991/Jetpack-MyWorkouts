// TooManyFunctions подавлен осознанно: 11 preview-функций — это требование
// конвенции проекта (светлая + тёмная тема для каждого ключевого состояния,
// AGENTS.md), а не раздувание логики; дробить превью на файлы хуже подавления.
@file:Suppress("TooManyFunctions")

package com.myworkouts.ui.screens.exercises

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.state.ExercisesUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview-компонентов экрана «Упражнения» (конвенция проекта: светлая +
 * тёмная темы, `showBackground`, `locale = "ru"`, обёртка в
 * `JetpackMyWorkoutsTheme`). Ключевые состояния: список с комментарием
 * и без, пустое состояние, Loading — FAB виден на каждом полноэкранном
 * превью (он часть [ExercisesScreenContent]). Отдельно — long-press меню,
 * алерт удаления, алерт архивации (с чекбоксом «Больше не показывать»
 * снятым/поставленным) и подсветка строки (edit-delete-exercise §9.1;
 * exercises-archive §9.1, §9.3, §9.5; archive-alert-hide §6.1–§6.3).
 * Алерты рендерятся windowless-сэмплом [ExerciseConfirmDialogSample]:
 * окно AlertDialog в Compose Preview не рисуется.
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
        iconNumber = ExerciseIcons.PULLUPS
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
        iconNumber = ExerciseIcons.PUSHUPS
    )

private val previewRunning =
    Exercise(
        id = 3L,
        name = "Бег",
        category = ExerciseCategory.CARDIO,
        isCustom = false,
        comment = null,
        createdAt = 1_721_200_000_000L,
        measuredBy = ExerciseMeasuredBy.TIME,
        iconNumber = ExerciseIcons.RUN
    )

private val previewBench =
    Exercise(
        id = 100L,
        name = "Жим",
        category = ExerciseCategory.PUSH,
        isCustom = true,
        comment = null,
        createdAt = 1_721_300_000_000L,
        measuredBy = ExerciseMeasuredBy.REPS,
        // Кастомное упражнение без назначенной иконки — состояние
        // «строка без иконки» (exercise-icon-display)
        iconNumber = null
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
        // Surface задаёт LocalContentColor = onSurface — без него тексты
        // (без явного цвета) остаются чёрными в тёмной теме
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                // Обычная строка: с комментарием и лейблом «повторы»
                ExerciseListItemRow(exercise = previewPullups)
                // Подсвеченная — как при открытом long-press меню
                ExerciseListItemRow(exercise = previewPlank, isSelected = true)
            }
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
        // Surface задаёт LocalContentColor = onSurface — без него тексты
        // подписей (без явного цвета) остаются чёрными в тёмной теме
        Surface {
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
}

/**
 * Windowless-сэмпл контента [ExerciseConfirmDialog]: окно AlertDialog
 * не рендерится в Compose Preview (диалог живёт в отдельном окне,
 * Layoutlib рисует только основное), поэтому алерт-превью показывают
 * контент напрямую — те же строки, слот `suppressContent` и цвета
 * confirm-кнопки ([exerciseConfirmButtonColors]), что и у настоящего
 * диалога. Ограничение: M3-хром окна (реальные отступы и раскладка
 * кнопок) в превью не воспроизводится.
 */
@Suppress("LongParameterList")
@Composable
private fun ExerciseConfirmDialogSample(
    itemName: String,
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
    @StringRes confirmRes: Int,
    @StringRes cancelRes: Int,
    confirmInError: Boolean,
    suppressContent: (@Composable () -> Unit)? = null
) {
    Surface(shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(textRes, itemName),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (suppressContent != null) {
                suppressContent()
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {}) {
                    Text(text = stringResource(cancelRes))
                }
                TextButton(
                    onClick = {},
                    colors = exerciseConfirmButtonColors(confirmInError)
                ) {
                    Text(text = stringResource(confirmRes))
                }
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
        ExerciseConfirmDialogSample(
            itemName = previewRunning.name,
            titleRes = R.string.exercise_delete_dialog_title,
            textRes = R.string.exercise_delete_dialog_text,
            confirmRes = R.string.exercise_delete_dialog_confirm,
            cancelRes = R.string.exercise_delete_dialog_cancel,
            confirmInError = true
        )
    }
}

/**
 * archive-alert-hide, §6.2: алерт архивации с заполненным слотом
 * `suppressContent` — чекбокс «Больше не показывать» изначально снят
 * (`suppress = false`), как в production-коде `SuccessBody`.
 * Состояние поднимается здесь через `remember` и передаётся в
 * [ArchiveSuppressSlot]; замыкание `onConfirm` диалога его игнорирует
 * (production-сигнатура диалога — `() -> Unit`, диалог не знает про
 * `suppress`; design.md Decision 4).
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseArchiveDialogWithSuppressPreview() {
    var suppress by remember { mutableStateOf(false) }
    JetpackMyWorkoutsTheme {
        ExerciseConfirmDialogSample(
            itemName = previewRunning.name,
            titleRes = R.string.exercise_archive_dialog_title,
            textRes = R.string.exercise_archive_dialog_text,
            confirmRes = R.string.exercise_archive_dialog_confirm,
            cancelRes = R.string.exercise_archive_dialog_cancel,
            confirmInError = false,
            suppressContent = {
                ArchiveSuppressSlot(
                    checked = suppress,
                    onCheckedChange = { suppress = it }
                )
            }
        )
    }
}

/**
 * archive-alert-hide, §6.3: тот же диалог архивации с чекбоксом,
 * изначально поставленным (`suppress = true`) — отдельное превью
 * второго ключевого состояния компонента (конвенция AGENTS.md
 * «Preview»: одно состояние — одно превью).
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseArchiveDialogWithSuppressPreviewChecked() {
    var suppress by remember { mutableStateOf(true) }
    JetpackMyWorkoutsTheme {
        ExerciseConfirmDialogSample(
            itemName = previewRunning.name,
            titleRes = R.string.exercise_archive_dialog_title,
            textRes = R.string.exercise_archive_dialog_text,
            confirmRes = R.string.exercise_archive_dialog_confirm,
            cancelRes = R.string.exercise_archive_dialog_cancel,
            confirmInError = false,
            suppressContent = {
                ArchiveSuppressSlot(
                    checked = suppress,
                    onCheckedChange = { suppress = it }
                )
            }
        )
    }
}

/**
 * §9.5 exercises-archive: состояние `archivedCount = 0` — кнопка
 * архива в TopAppBar скрыта.
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesScreenContentArchivedCountHiddenPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExercisesScreenContent(
                state =
                    previewScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank),
                                archivedCount = 0
                            )
                    )
            )
        }
    }
}

/**
 * §9.5 exercises-archive: состояние `archivedCount = 1` — кнопка
 * архива в TopAppBar видна (Icons.Filled.Archive).
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesScreenContentArchivedCountVisiblePreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExercisesScreenContent(
                state =
                    previewScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(previewPullups, previewPlank),
                                archivedCount = 1
                            )
                    )
            )
        }
    }
}
