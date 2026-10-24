package com.myworkouts.navigation

import com.myworkouts.analytics.UserActionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit-тесты чистой логики решения по intent шортката «Добавить
 * тренировку»: разбор action и guard от дублирования открытого экрана.
 */
class ShortcutLaunchResolverTest {
    @Test
    fun isAddWorkoutAction_whenOurAction_thenTrue() {
        // Given
        val action = "com.myworkouts.action.ADD_WORKOUT"

        // When + Then
        assertTrue(isAddWorkoutAction(action), "Наш action должен распознаваться")
    }

    @Test
    fun isAddWorkoutAction_whenForeignAction_thenFalse() {
        // Given
        val action = "android.intent.action.MAIN"

        // When + Then
        assertFalse(isAddWorkoutAction(action), "Чужой action не должен распознаваться")
    }

    @Test
    fun isAddWorkoutAction_whenNullAction_thenFalse() {
        // Given
        val action: String? = null

        // When + Then
        assertFalse(isAddWorkoutAction(action), "Null action не должен распознаваться")
    }

    @Test
    fun resolve_whenEntryCreateNotInStack_thenReturnsOpenEntryCreate() {
        // Given
        val entryCreateOnStack = false

        // When
        val decision = resolve(entryCreateOnStack)

        // Then
        assertEquals(
            UserActionType.OPEN_ENTRY_CREATE,
            decision,
            "Отсутствие формы в стеке должно давать решение «открыть создание записи»"
        )
    }

    @Test
    fun resolve_whenEntryCreateInStack_thenReturnsNull() {
        // Given
        val entryCreateOnStack = true

        // When
        val decision = resolve(entryCreateOnStack)

        // Then
        assertNull(
            decision,
            "Повторный шорткат при форме в стеке (в том числе под настройками записи) не должен дублировать её"
        )
    }
}
