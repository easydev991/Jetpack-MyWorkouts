package com.myworkouts.ui.state

import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertEquals
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
            ExerciseCreateUiState(name = "Жим", categoryName = "PUSH")

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
                categoryName = "PUSH",
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
                categoryName = "PUSH",
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
                categoryName = "PUSH",
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
        val state = ExerciseCreateUiState(categoryName = "PULL")

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
                categoryName = "PULL",
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
                categoryName = "PULL",
                isEditMode = true,
                hasChanges = true
            )

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(result, "Edit с hasChanges требует подтверждения закрытия")
    }

    /* hasUnsavedChanges create-режима: выбранная иконка (iconNumber != null)
     * — несохранённое изменение; null («Без иконки») — дефолт, не считается
     * изменением (spec «Изменение иконки учитывается в hasUnsavedChanges и
     * hasChanges»). */

    @Test
    fun hasUnsavedChanges_when_create_iconNumberOnly_then_true() {
        // Given — только иконка (3) при пустых name/category/comment
        val state = ExerciseCreateUiState(iconNumber = 3)

        // When
        val result = state.hasUnsavedChanges

        // Then
        assertTrue(
            result,
            "Выбранная иконка (iconNumber != null) при пустых остальных полях — несохранённое изменение"
        )
    }

    @Test
    fun hasUnsavedChanges_when_create_iconNumberReverted_then_false() {
        // Given
        val filled = ExerciseCreateUiState(iconNumber = 3)
        val reverted = filled.copy(iconNumber = null)

        // When
        val result = reverted.hasUnsavedChanges

        // Then
        assertFalse(result, "Повторный выбор «Без иконки» гасит условие")
    }

    /* dropdownCategories (custom-categories, этап 8): активные + текущая
     * архивная в edit-режиме, без дублей активной текущей. */

    @Test
    fun dropdownCategories_when_noSelection_then_availableCategories() {
        // Given — create-режим, категория не выбрана
        val available = listOf(Category(id = 1L, name = "PUSH", colorArgb = 1, isCustom = false))
        val state = ExerciseCreateUiState(availableCategories = available)

        // When / Then
        assertEquals(
            available,
            state.dropdownCategories,
            "Без выбранной категории список дропдауна = активные категории"
        )
    }

    @Test
    fun dropdownCategories_when_currentActive_then_noDuplicate() {
        // Given — edit-режим, текущая категория активна
        val available =
            listOf(
                Category(id = 1L, name = "PUSH", colorArgb = 1, isCustom = false),
                Category(id = 2L, name = "PULL", colorArgb = 2, isCustom = false)
            )
        val state =
            ExerciseCreateUiState(
                categoryName = "PUSH",
                availableCategories = available
            )

        // When / Then
        assertEquals(
            listOf("PUSH", "PULL"),
            state.dropdownCategories.map { it.name },
            "Активная текущая категория не дублируется в дропдауне"
        )
    }

    @Test
    fun dropdownCategories_when_currentArchived_then_appendedToList() {
        // Given — edit-режим, текущая категория «Плечи» архивна (нет среди активных)
        val available =
            listOf(
                Category(id = 1L, name = "PUSH", colorArgb = 1, isCustom = false),
                Category(id = 2L, name = "PULL", colorArgb = 2, isCustom = false)
            )
        val state =
            ExerciseCreateUiState(
                categoryName = "Плечи",
                availableCategories = available
            )

        // When / Then
        assertEquals(
            listOf("PUSH", "PULL", "Плечи"),
            state.dropdownCategories.map { it.name },
            "Архивная текущая категория добавляется в конец списка дропдауна"
        )
    }
}
