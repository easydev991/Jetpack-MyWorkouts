package com.myworkouts.ui.screens.exercises

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.ExercisesViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционный репро-тест живого обновления каталога (create-exercise,
 * этап 5, ручная проверка): реальный Room in-memory + реальная
 * [ExercisesViewModel] + настоящий `NavHost`. Воспроизводит проводку
 * продакшена — «Exercises → уход с экрана → insert → возврат» — и
 * проверяет, что список отражает вставку без перезапуска приложения.
 *
 * DAO/invalidation-уровень изолирован и доказан зелёным в
 * `RoomWorkoutDataRepositoryTest` (обе таблицы эмитят при живой подписке);
 * здесь проверяется связка ViewModel + stateIn + навигация.
 */
@RunWith(AndroidJUnit4::class)
class ExercisesLiveUpdateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises =
                    listOf(
                        StandardExercise(name = "Подтягивания", category = ExerciseCategory.PULL)
                    )
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun insertCustomExercise() {
        runBlocking {
            repository
                .addExercise(
                    Exercise(
                        id = 0L,
                        name = "Жим",
                        category = ExerciseCategory.PUSH,
                        isCustom = true,
                        comment = null,
                        createdAt = 1L,
                        measuredBy = ExerciseMeasuredBy.REPS
                    )
                )
        }
    }

    /**
     * Сценарий продакшена: каталог открыт → уход на другой экран →
     * вставка упражнения → возврат. Список обязан показать новое
     * упражнение (подписка WhileSubscribed ещё активна — таймаут 5 с
     * не истёк, invalidation доставляется живому upstream).
     */
    @Test
    fun exercisesScreen_whenReturnedQuickly_afterInsert_thenShowsNewExercise() {
        // Given — NavHost с Exercises на реальной VM/Room и заглушкой
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            JetpackMyWorkoutsTheme {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Exercises.route
                ) {
                    composable(Screen.Exercises.route) {
                        ExercisesScreen(
                            viewModel =
                                viewModel(
                                    factory = ExercisesViewModel.factory(repository)
                                ),
                            onAddExerciseClick = { navController.navigate("stub") },
                            onArchivedExercisesClick = {}
                        )
                    }
                    composable("stub") {
                        // Заглушка «другого экрана» (форма создания).
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_title))
            .assertIsDisplayed()

        // When — уход, вставка, быстрый возврат (< таймаута подписки)
        composeTestRule.runOnIdle { navController.navigate("stub") }
        composeTestRule.waitForIdle()
        insertCustomExercise()
        composeTestRule.runOnIdle { navController.popBackStack() }
        composeTestRule.waitForIdle()

        // Then — новое упражнение видно в каталоге без перезапуска
        composeTestRule.onNodeWithText("Жим").assertIsDisplayed()
    }

    /**
     * Репро баги из ручной проверки этапа 5 — ТОЧНАЯ навигация
     * продакшена: Exercises открыт как вкладка (`popUpTo(start) {
     * saveState = true }; launchSingleTop; restoreState = true`),
     * затем уход на не-tab экран и возврат `popBackStack()`. Вставка
     * между уходом и возвратом. Пауза ~5.3 с — таймаут подписки
     * истёк (медленный тест, пауза — часть сценария).
     */
    @Test
    fun exercisesScreen_whenTabDestinationReturnedAfterTimeout_afterInsert_thenShowsNewExercise() {
        // Given — граф как в RootScreen: Main (стартовый), Exercises
        // (открывается таб-навигацией с saveState/restoreState), заглушка
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            JetpackMyWorkoutsTheme {
                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        // Заглушка стартового маршрута (Main).
                    }
                    composable(Screen.Exercises.route) {
                        ExercisesScreen(
                            viewModel =
                                viewModel(
                                    factory = ExercisesViewModel.factory(repository)
                                ),
                            onAddExerciseClick = { navController.navigate("stub") },
                            onArchivedExercisesClick = {}
                        )
                    }
                    composable("stub") {
                        // Заглушка «другого экрана» (форма создания).
                    }
                }
            }
        }

        // When — Exercises открыт таб-навигацией (опции из RootScreen)
        composeTestRule.runOnIdle {
            navController.navigate(Screen.Exercises.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_title))
            .assertIsDisplayed()

        // When — уход на «форму», пауза больше таймаута подписки, вставка,
        // возврат popBackStack (как onSaved → popBackStack в продакшене)
        composeTestRule.runOnIdle { navController.navigate("stub") }
        composeTestRule.waitForIdle()
        runBlocking { kotlinx.coroutines.delay(5_300L) }
        insertCustomExercise()
        composeTestRule.runOnIdle { navController.popBackStack() }
        composeTestRule.waitForIdle()

        // Then — повторная подписка обязана подтянуть вставку
        composeTestRule.onNodeWithText("Жим").assertIsDisplayed()
    }

    /**
     * Контрольный сценарий (фиксирует harness): переход на каталог ПОСЛЕ
     * вставки показывает новое упражнение — свежая подписка, initial query.
     */
    @Test
    fun exercisesScreen_whenOpenedAfterInsert_thenShowsNewExercise() {
        // Given — вставка до открытия каталога
        insertCustomExercise()
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            JetpackMyWorkoutsTheme {
                NavHost(
                    navController = navController,
                    startDestination = "stub"
                ) {
                    composable("stub") {
                        // Заглушка стартового маршрута.
                    }
                    composable(Screen.Exercises.route) {
                        ExercisesScreen(
                            viewModel =
                                viewModel(
                                    factory = ExercisesViewModel.factory(repository)
                                ),
                            onAddExerciseClick = {},
                            onArchivedExercisesClick = {}
                        )
                    }
                }
            }
        }

        // When — переход на каталог
        composeTestRule.runOnIdle { navController.navigate(Screen.Exercises.route) }
        composeTestRule.waitForIdle()

        // Then — вставленное упражнение видно
        composeTestRule.onNodeWithText("Жим").assertIsDisplayed()
    }
}
