package com.myworkouts.ui.screens.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.icons.EXERCISE_ICON_TEST_TAG
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.state.ArchivedExercisesUiState
import com.myworkouts.ui.viewmodel.ArchivedExercisesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные тесты экрана «Архив упражнений»
 * (exercises-archive, tasks §7).
 *
 * Рендер — через [ArchivedExercisesScreenContent] с готовым
 * [ArchivedExercisesState] (без ViewModel и fake-репозитория, паттерн
 * `ExercisesScreenTest`).
 */
@RunWith(AndroidJUnit4::class)
class ArchivedExercisesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val pullups =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = null,
            createdAt = 1_721_000_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS,
            isArchived = true
        )

    /** Рендер [ArchivedExercisesScreenContent] с готовым состоянием. */
    private fun setContentWith(uiState: ArchivedExercisesUiState) {
        composeTestRule.setContent {
            ArchivedExercisesScreenContent(
                state =
                    ArchivedExercisesState(
                        uiState = uiState,
                        onBackClick = {}
                    )
            )
        }
    }

    /**
     * §7.1: VM-обёртка с пустым архивом — заглушка «В архиве пусто»
     * по центру.
     */
    @Test
    fun archived_exercises_when_empty_then_shows_empty_state() {
        // Given
        setContentWith(ArchivedExercisesUiState.Success(exercises = emptyList()))

        // Then — заглушка видна
        composeTestRule
            .onNodeWithText(context.getString(R.string.archived_exercises_empty))
            .assertIsDisplayed()
        // And — никаких строк упражнений
        composeTestRule
            .onAllNodesWithText("Подтягивания")
            .assertCountEquals(0)
    }

    /**
     * exercise-icon-display, design Decision 4: архивная строка
     * переиспользует [ExerciseListItemRow], поэтому упражнение
     * с назначенной иконкой показывает её без отдельной правки экрана;
     * иконка хранится на упражнении и не зависит от архивного состояния.
     */
    @Test
    fun archived_row_when_icon_assigned_then_shows_icon() {
        // Given — архивное подтягивание с назначенной иконкой
        setContentWith(
            ArchivedExercisesUiState.Success(
                exercises = listOf(pullups.copy(iconNumber = ExerciseIcons.PULLUPS))
            )
        )

        // Then — строка отображается с иконкой
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    /**
     * §7.2: VM с одним архивным упражнением — список из одной строки;
     * long-press → меню с пунктом «Вернуть».
     */
    @Test
    fun archived_exercises_when_one_then_list_and_long_press_menu_unarchive() {
        // Given — одно упражнение + state-машина для меню
        composeTestRule.setContent {
            var menu by remember {
                mutableStateOf<ExerciseContextMenuState?>(null)
            }
            ArchivedExercisesScreenContent(
                state =
                    ArchivedExercisesState(
                        uiState = ArchivedExercisesUiState.Success(exercises = listOf(pullups)),
                        contextMenu = menu,
                        onContextMenuItemChange = { menu = it },
                        onUnarchive = {},
                        onBackClick = {}
                    )
            )
        }

        // Then — строка упражнения видна
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()

        // When — long-press на строке
        composeTestRule
            .onNodeWithText("Подтягивания")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — пункт «Вернуть» появился
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_unarchive))
            .assertIsDisplayed()
    }

    /**
     * Accessibility-канал long-press (accessibility-semantics-fixes):
     * архивная строка предоставляет custom-действие `a11y_open_menu`;
     * выполнение открывает то же меню, что long-press — всегда
     * единственный пункт «Вернуть», без зависимости от типа упражнения
     * (в отличие от каталога, проверки `isCustom` нет).
     */
    @Test
    fun archived_row_when_a11y_action_invoked_then_menu_with_unarchive() {
        // Given — одно упражнение + state-машина для меню
        composeTestRule.setContent {
            var menu by remember {
                mutableStateOf<ExerciseContextMenuState?>(null)
            }
            ArchivedExercisesScreenContent(
                state =
                    ArchivedExercisesState(
                        uiState = ArchivedExercisesUiState.Success(exercises = listOf(pullups)),
                        contextMenu = menu,
                        onContextMenuItemChange = { menu = it },
                        onUnarchive = {},
                        onBackClick = {}
                    )
            )
        }

        // When — выполнение custom-действия «Открыть меню» (как из
        // меню действий TalkBack)
        val openMenuAction =
            composeTestRule
                .onNodeWithText("Подтягивания")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                ?.firstOrNull { it.label == context.getString(R.string.a11y_open_menu) }
        assertNotNull(
            "Архивная строка должна предоставлять custom-действие «Открыть меню»",
            openMenuAction
        )
        checkNotNull(openMenuAction).action.invoke()
        composeTestRule.waitForIdle()

        // Then — меню всегда «Вернуть»
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_unarchive))
            .assertIsDisplayed()
    }

    /**
     * §7.3: тап «Вернуть» — fake-репозиторий вызвал
     * `setArchived(id, false)`; реактивный список обновился,
     * упражнение пропало. Реальный `ArchivedExercisesViewModel` + VM-fake
     * с управляемым `archived`-потоком: после `unarchive` fake
     * удаляет упражнение из списка, имитируя реактивный SQL-Flow.
     */
    @Test
    fun archived_exercises_when_unarchive_then_set_archived_false_and_row_removed() {
        // Given — VM с одним упражнением
        val fake =
            FakeArchivedRepository(
                initialArchived = listOf(pullups)
            )
        composeTestRule.setContent {
            ArchivedExercisesScreen(
                viewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = ArchivedExercisesViewModel.factory(fake, NoopAnalyticsProvider())
                    ),
                onBackClick = {}
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()

        // When — long-press → тап «Вернуть»
        composeTestRule
            .onNodeWithText("Подтягивания")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_unarchive))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — упражнение пропало из списка
        composeTestRule
            .onAllNodesWithText("Подтягивания")
            .assertCountEquals(0)
        // And — fake-репозиторий получил вызов `setArchived(id, false)`
        assertEquals(
            "setArchived должен быть вызван с isArchived = false",
            listOf(pullups.id to false),
            fake.setArchivedCalls
        )
    }

    /**
     * §7.4: тап стрелки «назад» в TopAppBar — сработал `onBackClick`.
     */
    @Test
    fun archived_exercises_when_back_arrow_clicked_then_onBackClick_fires() {
        // Given — экран с захватом `onBackClick`
        var backCount = 0
        composeTestRule.setContent {
            ArchivedExercisesScreenContent(
                state =
                    ArchivedExercisesState(
                        uiState = ArchivedExercisesUiState.Success(exercises = listOf(pullups)),
                        onBackClick = { backCount += 1 }
                    )
            )
        }

        // When — тап стрелки «назад»
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()

        // Then
        assertEquals("onBackClick должен быть вызван один раз", 1, backCount)
    }

    /**
     * Fake [com.myworkouts.data.repository.WorkoutDataRepository] для
     * теста §7.3 (VM-driven): `archived` управляется через `MutableStateFlow`
     * (тест обновляет как реактивный SQL-Flow); `setArchived` записывает
     * вызов и обновляет `archived` соответственно
     * (`isArchived = false` → удаление из списка; `isArchived = true`
     * — без действий).
     */
    private class FakeArchivedRepository(
        initialArchived: List<Exercise> = emptyList()
    ) : com.myworkouts.data.repository.WorkoutDataRepository {
        val archived: MutableStateFlow<List<Exercise>> = MutableStateFlow(initialArchived)

        val setArchivedCalls = mutableListOf<Pair<Long, Boolean>>()

        override fun archivedExercises(): Flow<List<Exercise>> = archived

        override fun archivedCount(): Flow<Int> = MutableStateFlow(archived.value.size)

        override suspend fun setArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> {
            setArchivedCalls.add(id to isArchived)
            if (!isArchived) {
                archived.value = archived.value.filterNot { it.id == id }
            }
            return Result.success(Unit)
        }

        override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

        override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

        override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

        override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

        override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

        override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

        override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

        override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

        override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

        override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

        override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

        override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(emptyList())

        override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

        // Новый метод контракта (entry-prefilled-reps-and-duration): заглушка.
        override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> =
            Result.success(emptyList())

        override suspend fun importData(
            exercises: List<Exercise>,
            entries: List<BackupEntry>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)
    }
}
