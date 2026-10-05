package com.myworkouts.ui.screens.exercises

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.Category
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview [CategoryDropdownField] — отдельные состояния компонента по
 * конвенции (светлая + тёмная темы, `showBackground`, `locale = "ru"`,
 * обёртка в [JetpackMyWorkoutsTheme]): пустой выбор (placeholder) и
 * выбранная категория. Раскрытое меню не превьюируется — `expanded`
 * внутренний `rememberSaveable` компонента, в превью не поднимается.
 */

private val previewDropdownCategories =
    listOf(
        Category(
            id = 1L,
            name = "PUSH",
            colorArgb = 0xFF4E6EF2.toInt(),
            isCustom = false
        ),
        Category(
            id = 2L,
            name = "Плечи",
            colorArgb = 0xFF22AA66.toInt(),
            isCustom = true
        )
    )

/** Поле без выбора: placeholder «Выберите категорию». */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryDropdownFieldEmptyPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.padding(8.dp)) {
            CategoryDropdownField(
                categories = previewDropdownCategories,
                selectedCategory = null,
                onCategoryChange = {}
            )
        }
    }
}

/** Поле с выбранной категорией (edit-режим). */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun CategoryDropdownFieldSelectedPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.padding(8.dp)) {
            CategoryDropdownField(
                categories = previewDropdownCategories,
                selectedCategory = "Плечи",
                onCategoryChange = {}
            )
        }
    }
}
