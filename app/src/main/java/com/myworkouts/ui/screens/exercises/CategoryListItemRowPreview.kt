// TooManyFunctions подавлен осознанно: 3 preview-функции × 2 темы —
// требование конвенции проекта (AGENTS.md), не раздувание логики.
@file:Suppress("TooManyFunctions")

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
import com.myworkouts.domain.model.Category
import com.myworkouts.ui.state.ExercisesTab
import com.myworkouts.ui.state.ExercisesUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview вкладки «Категории» (custom-categories, tasks 5.4):
 * строка списка (стандартная с локализованным label + кастомная с
 * именем как есть, цветовые маркеры), вкладка целиком (список) и
 * пустое состояние. Конвенция: светлая + тёмная темы, `showBackground`,
 * `locale = "ru"`, обёртка в [JetpackMyWorkoutsTheme].
 */

private val previewPush =
    Category(
        id = 1L,
        name = "PUSH",
        colorArgb = 0xFF4E6EF2.toInt(),
        isCustom = false
    )

private val previewShoulders =
    Category(
        id = 2L,
        name = "Плечи",
        colorArgb = 0xFF22AA66.toInt(),
        isCustom = true
    )

/** Строки списка рядом (мелкие компоненты — конвенция допускает). */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryListItemRowPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            Column {
                CategoryListItemRow(category = previewPush)
                CategoryListItemRow(category = previewShoulders)
            }
        }
    }
}

/** Вкладка «Категории» со списком: FAB «Добавить категорию», строки. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesScreenContentCategoriesTabPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = emptyList(),
                                categories = listOf(previewPush, previewShoulders),
                                selectedTab = ExercisesTab.CATEGORIES
                            )
                    )
            )
        }
    }
}

/** Вкладка «Категории» пуста (все заархивированы): «Нет категорий». */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesScreenContentCategoriesTabEmptyPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize().padding(0.dp)) {
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = emptyList(),
                                categories = emptyList(),
                                selectedTab = ExercisesTab.CATEGORIES
                            )
                    )
            )
        }
    }
}
