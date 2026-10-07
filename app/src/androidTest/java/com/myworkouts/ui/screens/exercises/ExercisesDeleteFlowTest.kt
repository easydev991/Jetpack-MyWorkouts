package com.myworkouts.ui.screens.exercises

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.LinkedEntriesDeletionException
import com.myworkouts.data.repository.StandardExerciseDeletionException
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.ExercisesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * VM-driven UI-тесты для потока удаления упражнения (edit-delete-exercise,
 * задачи 4.4, 4.7, 4.8). Использует реальный [ExercisesViewModel] +
 * fake-репозиторий на `MutableStateFlow`, чтобы протестировать реактивное
 * обновление списка (4.4) и снекбары ошибок (4.7, 4.8).
 *
 * Тесты §4.1–§4.3, §4.5 без VM (Content-only и state-машина) живут
 * в `ExercisesScreenTest.kt`.
 */
@RunWith(AndroidJUnit4::class)
class ExercisesDeleteFlowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val customExercise =
        Exercise(
            id = 100L,
            name = "Жим",
            category = "PUSH",
            isCustom = true,
            comment = null,
            createdAt = 1_721_200_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    /**
     * Fake [WorkoutDataRepository] для androidTest: реактивный каталог +
     * `deleteExercise` управляется через [deleteResult]. При успехе —
     * удаляет упражнение из `catalog`, имитируя реальный Room-сценарий.
     * `exerciseIdsWithLinkedEntries` управляется через [linkedEntries]
     * (для тестов §4.8 pre-check UX).
     */
    private val plankExercise =
        Exercise(
            id = 200L,
            name = "Планка",
            category = "CORE",
            isCustom = false,
            comment = null,
            createdAt = 1_721_300_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private class FakeRepository(
        initialCatalog: List<Exercise>
    ) : WorkoutDataRepository {
        val catalog = MutableStateFlow(initialCatalog)
        val linkedEntries = MutableStateFlow<Set<Long>>(emptySet())
        var deleteResult: Result<Unit> = Result.success(Unit)

        override fun exercises(query: String): Flow<List<Exercise>> = catalog

        override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

        override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

        override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

        override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

        override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

        override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

        override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

        override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun deleteExercise(id: Long): Result<Unit> =
            deleteResult.also { result ->
                if (result.isSuccess) {
                    catalog.value = catalog.value.filterNot { it.id == id }
                }
            }

        override suspend fun getExercise(id: Long): Result<Exercise?> =
            Result.success(
                catalog.value.firstOrNull {
                    it.id ==
                        id
                }
            )

        override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = linkedEntries

        override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

        override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

        // Новый метод контракта (entry-prefilled-reps-and-duration): заглушка.
        override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> =
            Result.success(emptyList())

        override suspend fun importData(
            categories: List<Category>,
            exercises: List<Exercise>,
            entries: List<BackupEntry>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)

        // Новые методы контракта (exercises-archive, §2.7): заглушки —
        // delete-тесты их не вызывают.

        override suspend fun setArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

        override fun hasArchived(): Flow<Boolean> = MutableStateFlow(false)

        // Новый контракт категорий (custom-categories, этап 2): заглушки.
        override suspend fun addCategory(
            name: String,
            colorArgb: Int
        ): Result<Category> =
            Result.success(Category(id = 0L, name = name.trim(), colorArgb = colorArgb, isCustom = true))

        override suspend fun updateCategory(
            id: Long,
            newName: String,
            colorArgb: Int
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteCategory(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun setCategoryArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun categories(): Flow<List<Category>> = MutableStateFlow(emptyList())

        override fun archivedCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())

        override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun getAllCategories(): Result<List<Category>> = Result.success(emptyList())
    }

    /** Регистрирует [ExercisesScreen] с реальной VM на fake-репозитории. */
    private fun setUpScreen(repository: FakeRepository) {
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                ExercisesScreen(
                    viewModel =
                        viewModel(
                            factory =
                                ExercisesViewModel.factory(
                                    repository,
                                    AppSettingsDataStore.create(context),
                                    NoopAnalyticsProvider()
                                )
                        ),
                    onAddExerciseClick = {},
                    onAddCategoryClick = {},
                    onArchivedExercisesClick = {},
                    analyticsProvider = NoopAnalyticsProvider()
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * §4.4: long-press на кастомном → тап «Удалить» в меню → алерт
     * подтверждения → тап «Удалить» в алерте → fake-репозиторий
     * выполняет удаление → каталог обновляется реактивно → упражнение
     * пропадает из списка (design Decision 3 + spec
     * edit-delete-exercise «подтверждение удаляет упражнение и закрывает
     * алерт»).
     */
    @Test
    fun delete_flow_when_confirm_then_exercise_removed_from_list() {
        // Given — кастомное упражнение в каталоге
        val fake = FakeRepository(initialCatalog = listOf(customExercise, plankExercise))
        setUpScreen(fake)
        composeTestRule
            .onNodeWithText("Жим")
            .assertIsDisplayed()

        // When — long-press → тап «Удалить» → тап «Удалить» в алерте
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()
        // Алерт открыт: confirm-кнопка текстом «Удалить»
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_delete_dialog_title))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_delete_dialog_confirm))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — упражнение пропало из списка, алерт закрыт
        composeTestRule
            .onAllNodesWithText("Жим")
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_delete_dialog_title))
            .assertCountEquals(0)
        assertEquals(
            "После удаления в каталоге остаётся только Планка",
            listOf(plankExercise),
            fake.catalog.value
        )
    }
}

/**
 * §4.7: typed error → конкретный снекбар (параметризованный по трём
 * причинам failure: FK / Standard / Other). После onErrorShown() —
 * повторный requestDelete + confirmDelete не показывает снекбар
 * (one-shot-семантика, design Decision 6).
 */
@RunWith(Parameterized::class)
class ExercisesErrorSnackbarTest(
    @Suppress("UNUSED_PARAMETER") private val label: String,
    private val failure: Throwable,
    private val expectedTextRes: Int
) {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val customExercise =
        Exercise(
            id = 100L,
            name = "Жим",
            category = "PUSH",
            isCustom = true,
            comment = null,
            createdAt = 1_721_200_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    // Стандартное второе активное: строка теста не «последний активный»
    private val plankExercise =
        Exercise(
            id = 200L,
            name = "Планка",
            category = "CORE",
            isCustom = false,
            comment = null,
            createdAt = 1_721_300_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private class FakeRepository(
        initialCatalog: List<Exercise>
    ) : WorkoutDataRepository {
        val catalog = MutableStateFlow(initialCatalog)
        val linkedEntries = MutableStateFlow<Set<Long>>(emptySet())
        var deleteResult: Result<Unit> = Result.success(Unit)

        override fun exercises(query: String): Flow<List<Exercise>> = catalog

        override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

        override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

        override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

        override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

        override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

        override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

        override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

        override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun deleteExercise(id: Long): Result<Unit> = deleteResult

        override suspend fun getExercise(id: Long): Result<Exercise?> =
            Result.success(
                catalog.value.firstOrNull {
                    it.id ==
                        id
                }
            )

        override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = linkedEntries

        override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

        override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

        // Новый метод контракта (entry-prefilled-reps-and-duration): заглушка.
        override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> =
            Result.success(emptyList())

        override suspend fun importData(
            categories: List<Category>,
            exercises: List<Exercise>,
            entries: List<BackupEntry>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)

        // Новые методы контракта (exercises-archive, §2.7): заглушки —
        // delete-тесты их не вызывают.

        override suspend fun setArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

        override fun hasArchived(): Flow<Boolean> = MutableStateFlow(false)

        // Новый контракт категорий (custom-categories, этап 2): заглушки.
        override suspend fun addCategory(
            name: String,
            colorArgb: Int
        ): Result<Category> =
            Result.success(Category(id = 0L, name = name.trim(), colorArgb = colorArgb, isCustom = true))

        override suspend fun updateCategory(
            id: Long,
            newName: String,
            colorArgb: Int
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteCategory(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun setCategoryArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun categories(): Flow<List<Category>> = MutableStateFlow(emptyList())

        override fun archivedCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())

        override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun getAllCategories(): Result<List<Category>> = Result.success(emptyList())
    }

    @Test
    fun delete_failure_then_snackbar_text_then_one_shot_resets_per_failure() {
        // Given — fake возвращает конкретную failure на deleteExercise
        val fake =
            FakeRepository(initialCatalog = listOf(customExercise, plankExercise)).apply {
                deleteResult = Result.failure(failure)
            }
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                ExercisesScreen(
                    viewModel =
                        viewModel(
                            factory =
                                ExercisesViewModel.factory(
                                    fake,
                                    AppSettingsDataStore.create(context),
                                    NoopAnalyticsProvider()
                                )
                        ),
                    onAddExerciseClick = {},
                    onAddCategoryClick = {},
                    onArchivedExercisesClick = {},
                    analyticsProvider = NoopAnalyticsProvider()
                )
            }
        }
        composeTestRule.waitForIdle()

        // When — long-press → Delete → confirm (первый failure)
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_delete_dialog_confirm))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — виден снекбар с конкретным текстом (маппинг DeleteExerciseError)
        composeTestRule
            .onNodeWithText(context.getString(expectedTextRes))
            .assertIsDisplayed()

        // Then — упражнение осталось в списке (failure)
        composeTestRule
            .onNodeWithText("Жим")
            .assertIsDisplayed()

        // When — ждём автоисчезновения первого снекбара по таймауту
        // (`SnackbarDuration.Short` ≈4с) и подтверждаем, что после
        // `onErrorShown()` повторный failure показывает НОВЫЙ снекбар:
        // one-shot per-snackbar-instance, не per-session (design
        // Decision 6 — конвенция «одноразовые события» AGENTS.md).
        composeTestRule.mainClock.advanceTimeBy(10_000L)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_delete_dialog_confirm))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — новый снекбар с тем же текстом виден (one-shot reset
        // работает: каждый новый failure получает свою эмиссию
        // `deleteError` → свой снекбар)
        composeTestRule
            .onNodeWithText(context.getString(expectedTextRes))
            .assertIsDisplayed()
    }

    companion object {
        // Метка вместо {0}=Throwable.toString(): FQCN + русское сообщение
        // раздувало display name до >255 байт — AGP не мог создать
        // logcat-файл теста («File name too long»).
        @JvmStatic
        @Parameterized.Parameters(name = "[{0}]")
        fun failures(): List<Array<Any>> =
            listOf(
                arrayOf(
                    "linked",
                    LinkedEntriesDeletionException(),
                    R.string.exercise_delete_error_used_in_workouts
                ),
                arrayOf(
                    "standard",
                    StandardExerciseDeletionException(),
                    R.string.exercise_delete_error_standard
                ),
                arrayOf(
                    "io",
                    IllegalStateException("io"),
                    R.string.exercise_delete_error_generic
                )
            )
    }
}

/**
 * §4.8: pre-check UX — если на кастомное упражнение есть записи,
 * long-press → тап «Удалить» → меню закрывается, алерт НЕ появляется,
 * сразу виден снекбар «Это упражнение уже используется в тренировках»,
 * упражнение остаётся в списке (design Decision 8).
 */
@RunWith(AndroidJUnit4::class)
class ExercisesPreCheckSnackbarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val customExerciseWithEntries =
        Exercise(
            id = 100L,
            name = "Жим",
            category = "PUSH",
            isCustom = true,
            comment = null,
            createdAt = 1_721_200_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    // Стандартное второе активное: строка теста не «последний активный»
    private val plankExercise =
        Exercise(
            id = 200L,
            name = "Планка",
            category = "CORE",
            isCustom = false,
            comment = null,
            createdAt = 1_721_300_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private class FakeRepository(
        initialCatalog: List<Exercise>,
        initialLinked: Set<Long> = emptySet()
    ) : WorkoutDataRepository {
        val catalog = MutableStateFlow(initialCatalog)
        val linkedEntries = MutableStateFlow(initialLinked)
        var deleteResult: Result<Unit> = Result.success(Unit)

        override fun exercises(query: String): Flow<List<Exercise>> = catalog

        override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

        override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

        override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

        override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

        override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

        override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

        override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

        override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun deleteExercise(id: Long): Result<Unit> = deleteResult

        override suspend fun getExercise(id: Long): Result<Exercise?> =
            Result.success(
                catalog.value.firstOrNull {
                    it.id ==
                        id
                }
            )

        override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = linkedEntries

        override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

        override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

        // Новый метод контракта (entry-prefilled-reps-and-duration): заглушка.
        override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> =
            Result.success(emptyList())

        override suspend fun importData(
            categories: List<Category>,
            exercises: List<Exercise>,
            entries: List<BackupEntry>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)

        // Новые методы контракта (exercises-archive, §2.7): заглушки —
        // delete-тесты их не вызывают.

        override suspend fun setArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

        override fun hasArchived(): Flow<Boolean> = MutableStateFlow(false)

        // Новый контракт категорий (custom-categories, этап 2): заглушки.
        override suspend fun addCategory(
            name: String,
            colorArgb: Int
        ): Result<Category> =
            Result.success(Category(id = 0L, name = name.trim(), colorArgb = colorArgb, isCustom = true))

        override suspend fun updateCategory(
            id: Long,
            newName: String,
            colorArgb: Int
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteCategory(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun setCategoryArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun categories(): Flow<List<Category>> = MutableStateFlow(emptyList())

        override fun archivedCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())

        override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun getAllCategories(): Result<List<Category>> = Result.success(emptyList())
    }

    @Test
    fun delete_when_precheck_fk_then_snackbar_no_dialog_and_kept() {
        // Given — кастомное упражнение помечено как «с записями»
        val fake =
            FakeRepository(
                initialCatalog = listOf(customExerciseWithEntries, plankExercise),
                initialLinked = setOf(customExerciseWithEntries.id)
            )
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                ExercisesScreen(
                    viewModel =
                        viewModel(
                            factory =
                                ExercisesViewModel.factory(
                                    fake,
                                    AppSettingsDataStore.create(context),
                                    NoopAnalyticsProvider()
                                )
                        ),
                    onAddExerciseClick = {},
                    onAddCategoryClick = {},
                    onArchivedExercisesClick = {},
                    analyticsProvider = NoopAnalyticsProvider()
                )
            }
        }
        composeTestRule.waitForIdle()

        // When — long-press → тап «Удалить»
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — меню закрыто (нет ни меню, ни алерта), виден снекбар
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_list_menu_delete))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_delete_dialog_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_delete_error_used_in_workouts))
            .assertIsDisplayed()
        // And — упражнение остаётся в списке
        composeTestRule
            .onNodeWithText("Жим")
            .assertIsDisplayed()
    }
}
