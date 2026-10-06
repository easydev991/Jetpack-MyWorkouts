package com.myworkouts.ui.screens.exercises

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.myworkouts.ui.state.CategoryCreateUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Превью [CategoryCreateScreenContent] (custom-categories, спека
 * «Превью в обеих темах»): пять ключевых состояний — пустая create-форма,
 * форма с введённым именем, edit-форма с предзаполнением, ошибка дубликата,
 * ошибка сохранения под кнопкой — каждое в светлой и тёмной теме.
 * Плюс превью пикера цвета (custom-categories-color-change): компонент
 * отдельно в двух состояниях (цвет задан, `colorArgb == null` —
 * плейсхолдер) и кружок в create/edit-состояниях экрана.
 */
private fun previewScreenState(uiState: CategoryCreateUiState): CategoryCreateScreenState =
    CategoryCreateScreenState(
        uiState = uiState,
        onNameChange = {},
        onRegenerateColorClick = {},
        onSaveClick = {},
        onBackClick = {}
    )

/** Пустая create-форма: «Новая категория», поле пустое, «Сохранить» disabled. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryCreateScreenContentEmptyPreview() {
    JetpackMyWorkoutsTheme {
        CategoryCreateScreenContent(state = previewScreenState(CategoryCreateUiState()))
    }
}

/** Create-форма с введённым уникальным именем и сгенерированным цветом: «Сохранить» доступна. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryCreateScreenContentWithNamePreview() {
    JetpackMyWorkoutsTheme {
        CategoryCreateScreenContent(
            state =
                previewScreenState(
                    CategoryCreateUiState(name = "Спина", colorArgb = 0xFF2196F3.toInt())
                )
        )
    }
}

/** Edit-форма с предзаполнением: заголовок «Изменить категория», исходный цвет, сохранение disabled. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryCreateScreenContentEditPreview() {
    JetpackMyWorkoutsTheme {
        CategoryCreateScreenContent(
            state =
                previewScreenState(
                    CategoryCreateUiState(
                        name = "Плечи",
                        isEditMode = true,
                        colorArgb = 0xFFE91E63.toInt()
                    )
                )
        )
    }
}

/** Пикер с заданным цветом: заголовок, кнопка refresh, кружок залит цветом. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryColorPickerWithColorPreview() {
    JetpackMyWorkoutsTheme {
        CategoryColorPicker(
            colorArgb = 0xFF2196F3.toInt(),
            onRegenerateColorClick = {}
        )
    }
}

/** Пикер без сгенерированного цвета: кружок — плейсхолдер цвета темы. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryColorPickerWithoutColorPreview() {
    JetpackMyWorkoutsTheme {
        CategoryColorPicker(colorArgb = null, onRegenerateColorClick = {})
    }
}

/** Ошибка дубликата: подсказка под полем в `colorScheme.error`, кнопка disabled. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryCreateScreenContentDuplicatePreview() {
    JetpackMyWorkoutsTheme {
        CategoryCreateScreenContent(
            state =
                previewScreenState(
                    CategoryCreateUiState(name = "Спина", isDuplicateName = true)
                )
        )
    }
}

/** Ошибка сохранения: текст под кнопкой «Сохранить» в `colorScheme.error`. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryCreateScreenContentSaveErrorPreview() {
    JetpackMyWorkoutsTheme {
        CategoryCreateScreenContent(
            state =
                previewScreenState(
                    CategoryCreateUiState(name = "Спина", isSaveError = true)
                )
        )
    }
}
