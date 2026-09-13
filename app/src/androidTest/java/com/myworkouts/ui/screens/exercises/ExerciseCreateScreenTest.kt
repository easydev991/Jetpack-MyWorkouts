package com.myworkouts.ui.screens.exercises

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.state.ExerciseCreateUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.ExerciseCreateViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные UI-тесты для [ExerciseCreateScreenContent] через plain state
 * без ViewModel (образец — `EntryCreateScreenTest`; логика VM покрыта
 * JVM-тестами `ExerciseCreateViewModelTest`).
 *
 * Проверяют состав полей формы, дефолт REPS и переключение сегмента,
 * валидацию canSave (name + category), блок ошибки сохранения и
 * BackHandler/диалог подтверждения закрытия.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseCreateScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun stateFor(uiState: ExerciseCreateUiState = ExerciseCreateUiState()): ExerciseCreateScreenState =
        ExerciseCreateScreenState(
            uiState = uiState,
            onNameChange = {},
            onCategoryChange = {},
            onMeasuredByChange = {},
            onCommentChange = {},
            onSaveClick = {},
            onBackClick = {}
        )

    private fun setContent(uiState: ExerciseCreateUiState = ExerciseCreateUiState()) {
        composeTestRule.setContent {
            ExerciseCreateScreenContent(state = stateFor(uiState))
        }
    }

    /** Пустая форма: заголовок, 4 поля и disabled «Сохранить». */
    @Test
    fun content_when_opened_then_shows_empty_form_and_disabled_save() {
        // Given — только что открытая форма (все поля null)
        setContent()

        // Then — заголовок и все 4 поля рендерятся
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_create_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_field_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_field_category)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_measured_by_reps)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_measured_by_time)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_field_comment)).assertIsDisplayed()

        // Then — категория не выбрана: локализованный лейбл отсутствует
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_category_push)).assertDoesNotExist()

        // Then — кнопка «Сохранить» неактивна
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsNotEnabled()
    }

    /** Сегмент REPS активен по умолчанию, TIME не выбран. */
    @Test
    fun content_when_opened_then_reps_segment_selected_by_default() {
        // Given — только что открытая форма
        setContent()

        // Then
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_measured_by_reps)).assertIsSelected()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_measured_by_time)).assertIsNotSelected()
    }

    /** Ввод одного названия без категории не активирует «Сохранить». */
    @Test
    fun content_when_name_typed_then_save_still_disabled() {
        // Given — форма с локальным state, как это делает ViewModel
        composeTestRule.setContent {
            var uiState by remember { mutableStateOf(ExerciseCreateUiState()) }
            ExerciseCreateScreenContent(
                state =
                    stateFor(uiState).copy(
                        onNameChange = { text -> uiState = uiState.copy(name = text) }
                    )
            )
        }

        // When — ввод названия
        composeTestRule
            .onNode(hasText(context.getString(R.string.exercise_field_name)))
            .performTextReplacement("Жим")

        // Then — поле показывает ввод, Save всё ещё неактивна (категория не выбрана)
        composeTestRule.onNodeWithText("Жим").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsNotEnabled()
    }

    /** Выбор категории после ввода названия активирует «Сохранить». */
    @Test
    fun content_when_category_selected_then_save_enabled() {
        // Given — название введено, категория не выбрана
        composeTestRule.setContent {
            var uiState by remember { mutableStateOf(ExerciseCreateUiState(name = "Жим")) }
            ExerciseCreateScreenContent(
                state =
                    stateFor(uiState).copy(
                        onCategoryChange = { category -> uiState = uiState.copy(category = category) }
                    )
            )
        }

        // When — открыли меню и выбрали категорию
        composeTestRule
            .onNode(hasText(context.getString(R.string.exercise_field_category)))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_category_push)).performClick()

        // Then — выбранная категория отображается, Save активна
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_category_push)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsEnabled()
    }

    /** Переключение сегмента REPS → TIME. */
    @Test
    fun content_when_time_segment_clicked_then_time_selected() {
        // Given — форма с локальным state
        composeTestRule.setContent {
            var uiState by remember { mutableStateOf(ExerciseCreateUiState()) }
            ExerciseCreateScreenContent(
                state =
                    stateFor(uiState).copy(
                        onMeasuredByChange = { measuredBy -> uiState = uiState.copy(measuredBy = measuredBy) }
                    )
            )
        }

        // When — тап по сегменту «длительность»
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_measured_by_time)).performClick()

        // Then — TIME выбран, REPS нет
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_measured_by_time)).assertIsSelected()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_measured_by_reps)).assertIsNotSelected()
    }

    /** Пустой комментарий не блокирует сохранение. */
    @Test
    fun content_when_comment_empty_then_save_enabled() {
        // Given — name + category заполнены, комментарий пустой
        setContent(ExerciseCreateUiState(name = "Жим", category = ExerciseCategory.PUSH))

        // Then — комментарий необязателен, Save активна
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsEnabled()
    }

    /** Блок ошибки: при isSaveError = true текст ошибки виден под кнопкой. */
    @Test
    fun content_when_save_error_then_shows_error_message() {
        // Given — провал сохранения
        setContent(ExerciseCreateUiState(name = "Жим", category = ExerciseCategory.PUSH, isSaveError = true))

        // Then — сообщение об ошибке под кнопкой «Сохранить»
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_save_error)).assertIsDisplayed()
    }

    /** Блок ошибки: при isSaveError = false текста ошибки нет. */
    @Test
    fun content_when_no_save_error_then_error_text_absent() {
        // Given — стартовое состояние без ошибки
        setContent()

        // Then
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_save_error)).assertDoesNotExist()
    }

    /** Стрелка TopAppBar при несохранённых изменениях показывает диалог. */
    @Test
    fun content_when_unsaved_changes_and_back_arrow_clicked_then_confirm_dialog_shown() {
        // Given — введённое название (hasUnsavedChanges = true)
        var backClickedCount = 0
        composeTestRule.setContent {
            ExerciseCreateScreenContent(
                state =
                    stateFor(ExerciseCreateUiState(name = "Жим"))
                        .copy(onBackClick = { backClickedCount++ })
            )
        }

        // When — тап по стрелке «назад» в TopAppBar
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()

        // Then — диалог подтверждения с заголовком и кнопками, экран не закрыт
        composeTestRule.onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_confirm_close)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.cancel)).assertIsDisplayed()
        assertEquals("Диалог показан — экран не должен закрыться сразу", 0, backClickedCount)
    }

    /** «Закрыть» в диалоге вызывает onBackClick ровно один раз. */
    @Test
    fun content_when_confirm_dialog_close_clicked_then_on_back_click_called_once() {
        // Given — диалог подтверждения открыт
        var backClickedCount = 0
        composeTestRule.setContent {
            ExerciseCreateScreenContent(
                state =
                    stateFor(ExerciseCreateUiState(name = "Жим"))
                        .copy(onBackClick = { backClickedCount++ })
            )
        }
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title)).assertIsDisplayed()

        // When — «Закрыть» в диалоге
        composeTestRule.onNodeWithText(context.getString(R.string.entry_confirm_close)).performClick()

        // Then — экран закрывается без сохранения ровно один раз
        assertEquals("Подтверждение должно закрывать экран (onBackClick) один раз", 1, backClickedCount)
    }

    /** «Отмена» в диалоге скрывает диалог и не вызывает onBackClick. */
    @Test
    fun content_when_confirm_dialog_cancel_clicked_then_on_back_click_not_called() {
        // Given — диалог подтверждения открыт
        var backClickedCount = 0
        composeTestRule.setContent {
            ExerciseCreateScreenContent(
                state =
                    stateFor(ExerciseCreateUiState(name = "Жим"))
                        .copy(onBackClick = { backClickedCount++ })
            )
        }
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title)).assertIsDisplayed()

        // When — «Отмена» в диалоге
        composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

        // Then — диалог скрыт, экран открыт, введённые данные на месте
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
        composeTestRule.onNodeWithText("Жим").assertIsDisplayed()
        assertEquals("Отмена не должна закрывать экран", 0, backClickedCount)
    }

    /** Стрелка TopAppBar при пустой форме закрывает экран без диалога. */
    @Test
    fun content_when_no_changes_and_back_arrow_clicked_then_on_back_click_without_dialog() {
        // Given — дефолтная форма (hasUnsavedChanges = false)
        var backClickedCount = 0
        composeTestRule.setContent {
            ExerciseCreateScreenContent(
                state = stateFor().copy(onBackClick = { backClickedCount++ })
            )
        }

        // When — тап по стрелке «назад»
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()

        // Then — экран закрылся сразу, без диалога
        assertEquals("Без изменений стрелка должна закрывать экран сразу", 1, backClickedCount)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
    }

    /** Системный back при несохранённых изменениях показывает тот же диалог. */
    @Test
    fun content_when_unsaved_changes_and_system_back_pressed_then_confirm_dialog_shown() {
        // Given — есть несохранённые изменения
        setContent(ExerciseCreateUiState(name = "Жим"))

        // When — системная кнопка/жест «назад»
        Espresso.pressBack()

        // Then — тот же диалог подтверждения, что и для стрелки TopAppBar
        composeTestRule.onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title)).assertIsDisplayed()
    }

    // region edit-delete-exercise §8 (edit-режим UI через реальную VM)

    /** Кастомное упражнение в fake-репозитории для edit-тестов (§8). */
    private val editableExercise =
        Exercise(
            id = 100L,
            name = "Жим лёжа",
            category = ExerciseCategory.PUSH,
            isCustom = true,
            comment = "3 подхода",
            createdAt = 1_721_200_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    /**
     * Fake [WorkoutDataRepository] для VM-тестов edit-режима: каталог на
     * `MutableStateFlow`, `getExercise`/`updateExercise` работают по каталогу
     * (образец — `ExercisesDeleteFlowTest.FakeRepository`).
     */
    private class EditFakeRepository(
        initialCatalog: List<Exercise>
    ) : WorkoutDataRepository {
        val catalog = MutableStateFlow(initialCatalog)

        override fun exercises(query: String): Flow<List<Exercise>> = catalog

        override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

        override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

        override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

        override suspend fun updateExercise(exercise: Exercise): Result<Unit> =
            Result.success(Unit).also {
                catalog.value = catalog.value.map { current -> if (current.id == exercise.id) exercise else current }
            }

        override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

        override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

        override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(catalog.value.firstOrNull { it.id == id })

        override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

        override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

        override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

        override suspend fun importData(
            exercises: List<Exercise>,
            entries: List<BackupEntry>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)

        // Новые методы контракта (exercises-archive, §2.7): заглушки —
        // edit-тесты их не вызывают.

        override suspend fun setArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

        override fun archivedCount(): Flow<Int> = MutableStateFlow(0)
    }

    /**
     * Test-Harness: `NavHost` со стартовым `Screen.Exercises.route`
     * (заглушка каталога) и destination `Screen.ExerciseCreate.route` с
     * реальной [ExerciseCreateScreen] на VM над [repository] — как в
     * `RootScreen` (edit-delete-exercise §5.2). `onBackClick` ведёт
     * `popBackStack()`, как `navigateUp` в проде.
     */
    @Composable
    private fun ExerciseCreateScreenWithNavHost(
        repository: EditFakeRepository,
        onControllerReady: (NavHostController) -> Unit
    ) {
        val navController = rememberNavController()
        onControllerReady(navController)
        NavHost(navController = navController, startDestination = Screen.Exercises.route) {
            composable(Screen.Exercises.route) {
                // Заглушка каталога: сценарии проверяют только возврат маршрута.
            }
            composable(Screen.ExerciseCreate.route, arguments = Screen.ExerciseCreate.arguments) { entry ->
                val exerciseId =
                    entry.arguments
                        ?.getLong(Screen.ExerciseCreate.ARG_EXERCISE_ID, Screen.ExerciseCreate.NO_EXERCISE_ID)
                        ?: Screen.ExerciseCreate.NO_EXERCISE_ID
                ExerciseCreateScreen(
                    viewModel = viewModel(factory = ExerciseCreateViewModel.factory(repository, exerciseId)),
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

    /** Открывает edit-режим с [exerciseId] через harness и ждёт загрузки. */
    private fun openEdit(
        repository: EditFakeRepository,
        exerciseId: Long
    ): NavHostController {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                ExerciseCreateScreenWithNavHost(repository) { navController = it }
            }
        }
        composeTestRule.runOnIdle {
            navController.navigate(Screen.ExerciseCreate.createRoute(exerciseId))
        }
        composeTestRule.waitForIdle()
        return navController
    }

    /**
     * §8.1: открытие формы с `exerciseId` — заголовок «Изменить
     * упражнение», поля предзаполнены из БД, «Сохранить» disabled
     * (`isEditMode && !hasChanges`).
     */
    @Test
    fun edit_when_opened_with_exercise_id_then_title_prefilled_and_save_disabled() {
        // Given — кастомное упражнение в fake-репозитории
        val fake = EditFakeRepository(listOf(editableExercise))

        // When — открытие edit-режима
        openEdit(fake, editableExercise.id)

        // Then — заголовок редактирования, не создания
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_edit_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_create_title)).assertDoesNotExist()

        // Then — поля предзаполнены из упражнения
        composeTestRule.onNodeWithText("Жим лёжа").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_category_push)).assertIsDisplayed()
        composeTestRule.onNodeWithText("3 подхода").assertIsDisplayed()

        // Then — без изменений «Сохранить» неактивна
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsNotEnabled()
    }

    /**
     * §8.2: изменение name включает «Сохранить» (`hasChanges == true`);
     * возврат к исходному значению снова выключает.
     */
    @Test
    fun edit_when_name_changed_then_save_enabled_and_reverting_disables() {
        // Given — edit-режим без изменений
        val fake = EditFakeRepository(listOf(editableExercise))
        openEdit(fake, editableExercise.id)
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsNotEnabled()

        // When — изменили название
        composeTestRule
            .onNode(hasText(context.getString(R.string.exercise_field_name)))
            .performTextReplacement("Жим стоя")

        // Then — «Сохранить» активна
        composeTestRule.onNodeWithText("Жим стоя").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsEnabled()

        // When — вернули исходное название
        composeTestRule
            .onNode(hasText(context.getString(R.string.exercise_field_name)))
            .performTextReplacement("Жим лёжа")

        // Then — «Сохранить» снова неактивна (draft == original)
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsNotEnabled()
    }

    /**
     * §8.3: тап «Сохранить» после изменения — обновление в fake-репозитории
     * и навигация назад (маршрут вернулся на `exercises`).
     */
    @Test
    fun edit_when_save_clicked_then_repository_updated_and_navigates_back() {
        // Given — edit-режим с изменённым названием
        val fake = EditFakeRepository(listOf(editableExercise))
        val navController = openEdit(fake, editableExercise.id)
        composeTestRule
            .onNode(hasText(context.getString(R.string.exercise_field_name)))
            .performTextReplacement("Жим стоя")

        // When — тап «Сохранить»
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).performClick()
        composeTestRule.waitForIdle()

        // Then — маршрут вернулся на каталог
        composeTestRule.runOnIdle {
            assertEquals(
                "После сохранения должен произойти возврат на exercises",
                Screen.Exercises.route,
                navController.currentDestination?.route
            )
        }

        // Then — fake-репозиторий содержит упражнение с новым названием
        assertEquals(
            "Жим стоя",
            fake.catalog.value
                .single()
                .name
        )
    }

    /** §8.3: системный back без изменений закрывает экран без диалога. */
    @Test
    fun edit_when_no_changes_and_system_back_then_closed_without_dialog() {
        // Given — edit-режим без изменений
        val fake = EditFakeRepository(listOf(editableExercise))
        val navController = openEdit(fake, editableExercise.id)

        // When — системный back
        Espresso.pressBack()

        // Then — вернулись на каталог, диалог подтверждения не появлялся
        composeTestRule.runOnIdle {
            assertEquals(
                "Без изменений системный back должен закрывать экран",
                Screen.Exercises.route,
                navController.currentDestination?.route
            )
        }
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
    }

    /**
     * §8.5: `exerciseId` упражнения, которого нет в репозитории, —
     * подсказка «Упражнение не найдено» под формой, «Сохранить» disabled
     * (design Decision 5).
     */
    @Test
    fun edit_when_exercise_missing_then_hint_shown_and_save_disabled() {
        // Given — пустой каталог: getExercise вернёт success(null)
        val fake = EditFakeRepository(initialCatalog = emptyList())

        // When — открытие edit-режима с несуществующим id
        openEdit(fake, exerciseId = 999L)

        // Then — подсказка видна, «Сохранить» неактивна
        composeTestRule.onNodeWithText(context.getString(R.string.exercise_hint_exercise_missing)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsNotEnabled()
    }
    // endregion
}
