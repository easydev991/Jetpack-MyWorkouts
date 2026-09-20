package com.myworkouts.ui.icons

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit-тесты контракта [ExerciseIcons.resolve] (exercise-icon-display):
 * известный номер из стабильной таблицы резолвится в `Ref`,
 * `null` и номер вне таблицы — тихий fallback в `null` без плейсхолдера.
 */
class ExerciseIconsTest {
    @Test
    fun resolve_whenKnownNumber_thenReturnsRef() {
        // Given — известные номера из таблицы: нижняя граница (1),
        // середина (18) и верхняя граница (19) диапазона 1..19
        listOf(1, 18, 19).forEach { number ->
            // When
            val ref = ExerciseIcons.resolve(number)

            // Then — номер резолвится в Ref с парой ресурсов
            assertNotNull(ref, "Иконка $number должна резолвиться в Ref")
            assertEquals(ExerciseIcons.byNumber[number], ref)
        }
    }

    @Test
    fun resolve_whenNullOrUnknownNumber_thenReturnsNull() {
        // Given — null (иконка не назначена) и номера вне таблицы:
        // 99 — за диапазоном, 0 — вне диапазона 1..19
        listOf<Int?>(null, 99, 0).forEach { number ->
            // When
            val ref = ExerciseIcons.resolve(number)

            // Then — тихий fallback: null, без исключения
            assertNull(ref, "Иконка $number должна резолвиться в null")
        }
    }
}
