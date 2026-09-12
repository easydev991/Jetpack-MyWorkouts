package com.myworkouts.ui.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.navigation.Screen
import com.myworkouts.navigation.Screen.EntryCreate
import com.myworkouts.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Навигационный тест FAB главного экрана: тап по FAB открывает маршрут
 * `entry_create` (NavHost-обёртка по образцу `MoreScreenTest`).
 *
 * `MainScreen` теперь принимает [MainViewModel] — конструкция через
 * `MainViewModel.factory(repository)` с in-memory репозиторием
 * (workout-entries-list, задача 5.5).
 */
@RunWith(AndroidJUnit4::class)
class MainScreenFabNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * In-memory [WorkoutDataRepository] на `MutableStateFlow`: каталог и
     * записи пустые — для FAB-теста этого достаточно (список не рендерится,
     * нужен только экран с FAB). Реализация [MainViewModel] сама подпишется
     * на пустой `entries()`.
     */
    private fun emptyRepository(): WorkoutDataRepository =
        object : WorkoutDataRepository {
            override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

            override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

            override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(0L)

            override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(0L)

            override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

            override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

            override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

            override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

            override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

            override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

            override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

            override suspend fun getAllExercises(): Result<List<Exercise>> = error("Не используется в этом тесте")

            override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = error("Не используется в этом тесте")

            override suspend fun importData(
                exercises: List<Exercise>,
                entries: List<BackupEntry>
            ): Result<Unit> = error("Не используется в этом тесте")

            override suspend fun deleteAllData(): Result<Unit> = error("Не используется в этом тесте")
        }

    /**
     * Test-Harness: [MainScreen] стартовым destination + заглушка маршрута
     * создания записи — реальный экран тестируется отдельно
     * (`EntryCreateScreenTest`).
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
                            factory = MainViewModel.factory(repository)
                        ),
                    onAddEntryClick = { navController.navigate(EntryCreate.createRoute()) }
                )
            }
            composable(Screen.EntryCreate.route, arguments = Screen.EntryCreate.arguments) {
                // Заглушка — реальный экран тестируется отдельно.
            }
        }
    }

    @Test
    fun main_screen_when_fab_clicked_then_navigates_to_entry_create_route() {
        // Given
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            MainScreenWithNavHost(
                repository = emptyRepository(),
                onControllerReady = { navController = it }
            )
        }

        // When — тап по FAB (contentDescription «Добавить тренировку»)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.add_workout))
            .performClick()

        // Then — ждём, пока NavController обработает навигацию
        composeTestRule.waitForIdle()
        val actualRoute = navController.currentDestination?.route
        assertTrue(
            "Ожидался переход на маршрут \"${Screen.EntryCreate.route}\", " +
                "но текущий маршрут — \"$actualRoute\"",
            actualRoute == Screen.EntryCreate.route
        )
    }
}
