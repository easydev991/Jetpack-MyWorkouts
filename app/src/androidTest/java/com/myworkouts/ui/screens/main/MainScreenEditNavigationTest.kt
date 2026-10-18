package com.myworkouts.ui.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.crash.NoopCrashReporter
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.screens.entry.EntryCreateScreen
import com.myworkouts.ui.viewmodel.EntryCreateViewModel
import com.myworkouts.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Навигационный тест редактирования записи (entry-edit-mode): long-press →
 * «Изменить» открывает `entry_create?entryId=N` с заголовком «Изменить
 * запись» и предзаполненной формой. NavHost-обёртка по образцу
 * [MainScreenFabNavigationTest] — репозиторий на `MutableStateFlow`, не Room.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenEditNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val repsExercise =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = "PULL",
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val listItem =
        EntryListItem(
            id = 1L,
            exerciseName = "Подтягивания",
            sets = 3,
            reps = 10,
            durationSec = null,
            date = 1_721_000_000_000L
        )

    /** Полная запись — ответ `getEntry` (проекция [listItem] неполная). */
    private val entry =
        ExerciseEntry(
            id = 1L,
            exerciseId = 1L,
            workoutId = null,
            date = 1_721_000_000_000L,
            sets = 3,
            reps = 10,
            weightKg = 12.5,
            durationSec = null,
            comment = "разминочный подход легче",
            createdAt = 1_700_000_000_000L,
            modifiedAt = 1_700_000_000_000L
        )

    /** In-memory репозиторий: каталог и список — реактивные, `getEntry` — запись. */
    private fun repository(): WorkoutDataRepository =
        object : WorkoutDataRepository {
            override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(listOf(repsExercise))

            override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(listOf(listItem))

            override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

            override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(0L)

            override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

            override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

            override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

            override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(entry)

            override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

            override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

            override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

            override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

            override suspend fun getAllExercises(): Result<List<Exercise>> = error("Не используется в этом тесте")

            override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = error("Не используется в этом тесте")

            // Новый метод контракта (entry-prefilled-reps-and-duration): заглушка.
            override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> =
                error("Не используется в этом тесте")

            override suspend fun importData(
                categories: List<Category>,
                exercises: List<Exercise>,
                entries: List<BackupEntry>
            ): Result<Unit> = error("Не используется в этом тесте")

            override suspend fun deleteAllData(): Result<Unit> = error("Не используется в этом тесте")

            // Новые методы контракта (exercises-archive, §2.7): заглушки —
            // тест их не вызывает.

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

    /**
     * Test-Harness: реальный [com.myworkouts.ui.screens.entry.EntryCreateScreen]
     * с ViewModel на `entryId` из navArgument — обёртка повторяет регистрацию
     * `RootScreen` (задача 5.2).
     */
    @Composable
    private fun MainScreenWithNavHost(
        repository: WorkoutDataRepository,
        onControllerReady: (NavHostController) -> Unit
    ) {
        val navController = rememberNavController()
        onControllerReady(navController)
        NavHost(navController = navController, startDestination = Screen.Main.route) {
            composable(Screen.Main.route) {
                MainScreen(
                    viewModel =
                        viewModel(
                            factory = MainViewModel.factory(repository, NoopAnalyticsProvider(), NoopCrashReporter())
                        ),
                    onAddEntryClick = {},
                    onEditEntryClick = { entryId ->
                        navController.navigate(Screen.EntryCreate.createRoute(entryId))
                    },
                    analyticsProvider = NoopAnalyticsProvider()
                )
            }
            composable(Screen.EntryCreate.route, arguments = Screen.EntryCreate.arguments) { backStackEntry ->
                val entryId =
                    backStackEntry.arguments?.getLong(Screen.EntryCreate.ARG_ENTRY_ID)
                        ?: Screen.EntryCreate.NO_ENTRY_ID
                EntryCreateScreen(
                    viewModel =
                        viewModel(
                            factory =
                                EntryCreateViewModel.factory(
                                    repository,
                                    entryId,
                                    AppSettingsDataStore.create(context),
                                    NoopAnalyticsProvider()
                                )
                        ),
                    onBackClick = {},
                    onOpenSettings = {}
                )
            }
        }
    }

    @Test
    fun main_screen_when_edit_selected_then_opens_edit_mode_with_prefilled_form() {
        // Given
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            MainScreenWithNavHost(
                repository = repository(),
                onControllerReady = { navController = it }
            )
        }

        // Given — строка записи отрисована
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText(listItem.exerciseName)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // When — long-press по строке → «Изменить»
        composeTestRule
            .onNodeWithText(listItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_edit))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — маршрут создания/редактирования с аргументом id записи
        assertEquals(
            Screen.EntryCreate.route,
            navController.currentDestination?.route
        )
        val entryId =
            navController.currentBackStackEntry
                ?.arguments
                ?.getLong(Screen.EntryCreate.ARG_ENTRY_ID)
                ?: Screen.EntryCreate.NO_ENTRY_ID
        assertEquals(listItem.id, entryId)

        // Then — заголовок режима редактирования
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_edit_title))
            .assertIsDisplayed()

        // Then — форма предзаполнена: комментарий отрендерён (end-to-end: навигация → загрузка записи)
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText("разминочный подход легче")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS: Long = 5_000L
    }
}
