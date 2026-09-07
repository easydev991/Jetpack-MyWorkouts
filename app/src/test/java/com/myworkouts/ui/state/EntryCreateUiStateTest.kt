package com.myworkouts.ui.state

import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit-тесты вычисляемого [EntryCreateUiState.hasUnsavedChanges] — условия
 * показа диалога подтверждения закрытия экрана записи
 * (add-entry-close-confirmation, create-режим; edit-режим — дополнен ниже).
 *
 * Состояние чистое: без ViewModel, корутин и фейков — только конструктор
 * и свойство, TDD на JVM.
 */
class EntryCreateUiStateTest {
    private val repsExercise =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    @Test
    fun hasUnsavedChanges_when_default_create_form_then_false() {
        // Given
        val state = EntryCreateUiState()

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Дефолтная форма создания закрывается без диалога")
    }

    @Test
    fun hasUnsavedChanges_when_exercise_selected_then_true() {
        // Given
        val state = EntryCreateUiState(selectedExercise = repsExercise)

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Выбранное упражнение — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_sets_not_one_then_true() {
        // Given
        val state = EntryCreateUiState(setsText = "3")

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Подходы, отличные от 1, — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_reps_not_empty_then_true() {
        // Given
        val state = EntryCreateUiState(repsText = "10")

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Непустые повторы — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_duration_selected_then_true() {
        // Given
        val state = EntryCreateUiState(durationSec = 60)

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Выбранная длительность — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_weight_not_zero_then_true() {
        // Given
        val state = EntryCreateUiState(weightText = "12.5")

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Доп. вес, отличный от 0, — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_comment_not_empty_then_true() {
        // Given
        val state = EntryCreateUiState(comment = "Хорошая техника")

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Непустой комментарий — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_weight_zero_decimal_then_false() {
        // Given
        val state = EntryCreateUiState(weightText = "0.0")

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Вес «0.0» численно равен дефолту «без веса»")
    }

    @Test
    fun hasUnsavedChanges_when_only_date_changed_then_false() {
        // Given
        val state = EntryCreateUiState(date = LocalDate.of(2024, 7, 10))

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Дата предзаполняется моментом открытия — не изменение")
    }

    @Test
    fun hasUnsavedChanges_when_only_time_changed_then_false() {
        // Given
        val state = EntryCreateUiState(hour = 14, minute = 30)

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Время предзаполняется моментом открытия — не изменение")
    }

    @Test
    fun hasUnsavedChanges_when_input_reverted_to_default_then_false() {
        // Given
        val filled = EntryCreateUiState(comment = "Хорошая техника")
        val reverted = filled.copy(comment = "")

        // When
        val result = reverted.hasUnsavedChanges

        // Then
        assertFalse(result, "Откат ввода до дефолта отменяет подтверждение")
    }

    @Test
    fun hasUnsavedChanges_when_edit_form_without_changes_then_false() {
        // Given
        val state =
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "3",
                repsText = "10",
                isEditMode = true,
                hasChanges = false
            )

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Форма редактирования без отличий закрывается без диалога")
    }

    @Test
    fun hasUnsavedChanges_when_edit_form_changed_and_valid_then_true() {
        // Given
        val state =
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "4",
                repsText = "12",
                isEditMode = true,
                hasChanges = true
            )

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Изменённая валидная форма редактирования требует подтверждения")
    }

    @Test
    fun hasUnsavedChanges_when_edit_form_changed_and_invalid_then_false() {
        // Given
        val state =
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "3",
                repsText = "",
                isEditMode = true,
                hasChanges = true
            )

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Изменённая, но невалидная форма закрывается без диалога")
    }
}
