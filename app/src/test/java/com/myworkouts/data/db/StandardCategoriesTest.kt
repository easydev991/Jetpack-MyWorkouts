package com.myworkouts.data.db

import com.myworkouts.R
import com.myworkouts.ui.util.standardCategoryLabelResId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Контракт-тест стандартного набора категорий [standardCategories]
 * (custom-categories, задача 1.1; преемник ExerciseCategoryTest).
 *
 * Закрепляет: пять имён-ключей (значения в БД и бэкапах не меняются),
 * цвета — значения статического `when` из `ui/theme/Color.kt` на момент
 * change (после его удаления — единственный источник) и связь ключей
 * с label-ресурсами через [standardCategoryLabelResId]. Если кто-то
 * добавит/удалит категорию — тест напомнит синхронизировать prepopulate,
 * миграцию v3→v4 и хелпер.
 */
class StandardCategoriesTest {
    @Test
    fun standardCategories_containExactlyFiveKeys() {
        // Given — контрактный набор имён-ключей (бывший enum ExerciseCategory)
        val expected = setOf("PUSH", "PULL", "LEGS", "CORE", "CARDIO")

        // When
        val actual = standardCategories.map { it.name }.toSet()

        // Then
        assertEquals(expected, actual)
        assertEquals(5, standardCategories.size)
    }

    @Test
    fun standardCategories_areDistinct() {
        // When
        val names = standardCategories.map { it.name }

        // Then
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun standardCategories_colorsMatchLegacyColorKtValues() {
        // Given — цвета стандартного набора, унаследованные от статического
        // when в ui/theme/Color.kt на момент change и зафиксированные
        // контрактом (после удаления when — значения-эталон здесь)
        val expectedByName =
            mapOf(
                "PUSH" to 0xFFFF5252.toInt(),
                "PULL" to 0xFF448AFF.toInt(),
                "LEGS" to 0xFF69F0AE.toInt(),
                "CORE" to 0xFFE040FB.toInt(),
                "CARDIO" to 0xFFFFD740.toInt()
            )

        // When + Then
        standardCategories.forEach { category ->
            assertEquals(
                expectedByName[category.name],
                category.colorArgb,
                "Цвет «${category.name}» должен совпадать со значением из Color.kt"
            )
        }
    }

    @Test
    fun labelResId_resolvesExpectedResource_forEveryStandardKey() {
        // Given — соответствие label-ресурсов (бывшие значения enum)
        val expectedByName =
            mapOf(
                "PUSH" to R.string.exercise_category_push,
                "PULL" to R.string.exercise_category_pull,
                "LEGS" to R.string.exercise_category_legs,
                "CORE" to R.string.exercise_category_core,
                "CARDIO" to R.string.exercise_category_cardio
            )

        // When + Then
        standardCategories.forEach { category ->
            assertEquals(
                expectedByName[category.name],
                standardCategoryLabelResId(category.name),
                "Label-ресурс для «${category.name}»"
            )
        }
    }

    @Test
    fun labelResId_returnsNull_forCustomName() {
        // Given — кастомное имя

        // When
        val resId = standardCategoryLabelResId("Спина")

        // Then — кастомная категория отображается по имени, без ресурса
        assertNull(resId)
    }
}
