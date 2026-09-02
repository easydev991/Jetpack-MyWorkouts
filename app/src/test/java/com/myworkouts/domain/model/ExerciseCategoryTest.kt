package com.myworkouts.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Unit-тесты для [ExerciseCategory].
 *
 * Контракт полного набора категорий важен: prepopulate стандартных
 * упражнений (`StandardExercises`) и Room-схема опираются на эти 5 значений
 * — если кто-то добавит/удалит категорию, тест напомнит о необходимости
 * синхронизировать prepopulate и миграцию БД.
 */
class ExerciseCategoryTest {
    @Test
    fun all_categories_contain_exactly_five_values() {
        // Given — enum ExerciseCategory
        // When
        val values = ExerciseCategory.values()

        // Then
        assertEquals(5, values.size)
    }

    @ParameterizedTest
    @EnumSource(ExerciseCategory::class)
    fun valueOf_roundtrips_for_known_name(category: ExerciseCategory) {
        // Given
        val name = category.name

        // When
        val parsed = ExerciseCategory.valueOf(name)

        // Then
        assertEquals(category, parsed)
    }

    @Test
    fun all_expected_categories_present() {
        // Given — ожидаемый контрактный набор
        val expected = setOf("PUSH", "PULL", "LEGS", "CORE", "CARDIO")

        // When
        val actual = ExerciseCategory.values().map { it.name }.toSet()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun categories_are_distinct() {
        // When
        val values = ExerciseCategory.values()

        // Then
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun valueOf_throws_for_unknown_name() {
        // Given
        val unknown = "MADE_UP"

        // When + Then
        // enum.valueOf бросает IllegalArgumentException — это документированное
        // поведение, на которое опирается Mappers.kt (design.md Decision 2).
        assertTrue(
            runCatching { ExerciseCategory.valueOf(unknown) }.isFailure
        )
    }
}
