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
 */
private fun previewScreenState(uiState: CategoryCreateUiState): CategoryCreateScreenState =
    CategoryCreateScreenState(
        uiState = uiState,
        onNameChange = {},
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

/** Create-форма с введённым уникальным именем: «Сохранить» доступна. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryCreateScreenContentWithNamePreview() {
    JetpackMyWorkoutsTheme {
        CategoryCreateScreenContent(
            state = previewScreenState(CategoryCreateUiState(name = "Спина"))
        )
    }
}

/** Edit-форма с предзаполнением: заголовок «Изменить категория», сохранение disabled. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryCreateScreenContentEditPreview() {
    JetpackMyWorkoutsTheme {
        CategoryCreateScreenContent(
            state =
                previewScreenState(
                    CategoryCreateUiState(name = "Плечи", isEditMode = true)
                )
        )
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
