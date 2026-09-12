package com.myworkouts.ui.state

import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit-тесты вычисляемых [ExerciseCreateUiState.canSave] и
 * [ExerciseCreateUiState.hasUnsavedChanges] — edit-delete-exercise,
 * раздел 6: create-режим без изменений (регрессия), edit-режим требует
 * `hasChanges`, guard `isExerciseMissing` отсекает сохранение всегда.
 *
 * Состояние чистое: без ViewModel, корутин и фейков — только конструктор
 * и свойства, TDD на JVM.
 */
class ExerciseCreateUiStateTest {
    @Test
    fun canSave_when_createMode_validForm_then_true() {
        // Given
        val state =
            ExerciseCreateUiState(name = "Жим", category = ExerciseCategory.PUSH)

        // When
        val result = state.canSave

        // Then
        assertTrue(result, "Валидная create-форма сохраняется (прежнее поведение)")
    }

    @Test
    fun canSave_when_createMode_emptyForm_then_false() {
        // Given
        val state = ExerciseCreateUiState()

        // When
        val result = state.canSave

        // Then
        assertFalse(result, "Пустая create-форма не сохраняется (прежнее поведение)")
    }

    @Test
    fun canSave_when_editMode_validForm_withoutChanges_then_false() {
        // Given
        val state =
            ExerciseCreateUiState(
                name = "Жим",
                category = ExerciseCategory.PUSH,
                isEditMode = true,
                hasChanges = false
            )

        // When
        val result = state.canSave

        // Then
        assertFalse(result, "Edit без изменений не сохраняется")
    }

    @Test
    fun canSave_when_editMode_validForm_withChanges_then_true() {
        // Given
        val state =
            ExerciseCreateUiState(
                name = "Жим",
                category = ExerciseCategory.PUSH,
                isEditMode = true,
                hasChanges = true
            )

        // When
        val result = state.canSave

        // Then
        assertTrue(result, "Edit с изменениями и валидной формой сохраняется")
    }

    @Test
    fun canSave_when_exerciseMissing_validFormAndChanges_then_false() {
        // Given
        val state =
            ExerciseCreateUiState(
                name = "Жим",
                category = ExerciseCategory.PUSH,
                isEditMode = true,
                hasChanges = true,
                isExerciseMissing = true
            )

        // When
        val result = state.canSave

        // Then
        assertFalse(result, "isExerciseMissing отсекает сохранение независимо от формы")
    }

    @Test
    fun canSave_when_exerciseMissing_emptyForm_then_false() {
        // Given
        val state = ExerciseCreateUiState(isExerciseMissing = true)

        // When
        val result = state.canSave

        // Then
        assertFalse(result, "isExerciseMissing при пустой форме — сохранение недоступно")
    }

    @Test
    fun hasUnsavedChanges_when_defaultCreateForm_then_false() {
        // Given
        val state = ExerciseCreateUiState()

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Дефолтная create-форма закрывается без диалога")
    }

    @Test
    fun hasUnsavedChanges_when_createNameFilled_then_true() {
        // Given
        val state = ExerciseCreateUiState(name = "Жим")

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Введённое название — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_createNameBlank_then_false() {
        // Given
        val state = ExerciseCreateUiState(name = "   ")

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Blank-название отсекается isNullOrBlank")
    }

    @Test
    fun hasUnsavedChanges_when_createCategoryOnly_then_true() {
        // Given
        val state = ExerciseCreateUiState(category = ExerciseCategory.PULL)

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Выбранная категория — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_createCommentOnly_then_true() {
        // Given
        val state = ExerciseCreateUiState(comment = "узкий хват")

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Введённый комментарий — несохранённое изменение")
    }

    @Test
    fun hasUnsavedChanges_when_createMeasuredByOnly_then_false() {
        // Given
        val state = ExerciseCreateUiState(measuredBy = ExerciseMeasuredBy.TIME)

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "measuredBy в create-формулу не входит")
    }

    @Test
    fun hasUnsavedChanges_when_createInputReverted_then_false() {
        // Given
        val filled = ExerciseCreateUiState(name = "Жим")
        val reverted = filled.copy(name = "")

        // When
        val result = reverted.hasUnsavedChanges

        // Then
        assertFalse(result, "Откат ввода до пустого значения гасит условие")
    }

    @Test
    fun hasUnsavedChanges_when_editPrefilled_withoutChanges_then_false() {
        // Given: предзаполненные поля сами по себе не делают форму изменённой
        val state =
            ExerciseCreateUiState(
                name = "Подтягивания",
                category = ExerciseCategory.PULL,
                comment = "широкий хват",
                isEditMode = true,
                hasChanges = false
            )

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertFalse(result, "Edit без hasChanges закрывается без диалога")
    }

    @Test
    fun hasUnsavedChanges_when_edit_hasChanges_then_true() {
        // Given
        val state =
            ExerciseCreateUiState(
                name = "Подтягивания",
                category = ExerciseCategory.PULL,
                isEditMode = true,
                hasChanges = true
            )

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Edit с hasChanges требует подтверждения закрытия")
    }
}
