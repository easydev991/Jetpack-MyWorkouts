package com.myworkouts.ui.screens.exercises

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.state.ExercisesUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные тесты для экрана «Упражнения» (exercises-tab, task 5.1).
 *
 * Рендер — через [ExercisesScreenContent] с готовым
 * [ExercisesScreenState] (без ViewModel и fake-репозитория, design
 * Decision 3). Навигация — NavHost-harness по образцу
 * `MoreScreenTest.MoreScreenWithNavHost`: заглушки-destinations +
 * `onControllerReady`, нижнего бара нет — проверяется только, что
 * `navigate(Screen.Exercises.route)` открывает экран «Упражнения»
 * (FAB-навигация не проверяется — заглушка без навигации).
 */
@RunWith(AndroidJUnit4::class)
class ExercisesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val pullups =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = "широкий хват",
            createdAt = 1_721_000_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val plank =
        Exercise(
            id = 2L,
            name = "Планка",
            category = ExerciseCategory.CORE,
            isCustom = false,
            comment = null,
            createdAt = 1_721_100_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    /** Рендер [ExercisesScreenContent] с готовым состоянием. */
    private fun setContentWith(uiState: ExercisesUiState) {
        composeTestRule.setContent {
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = uiState,
                        onAddExerciseClick = {}
                    )
            )
        }
    }

    /**
     * Test-Harness: `NavHost` со стартовой заглушкой и destination
     * `Screen.Exercises.route` на [ExercisesScreenContent] (без
     * ViewModel). Без графа `navigate(...)` бросает
     * `IllegalArgumentException: Navigation graph has not been set`.
     */
    @Composable
    private fun ExercisesScreenWithNavHost(onControllerReady: (NavHostController) -> Unit = {}) {
        val navController = rememberNavController()
        onControllerReady(navController)
        NavHost(navController = navController, startDestination = "stub") {
            composable("stub") {
                // Заглушка стартового маршрута.
            }
            composable(Screen.Exercises.route) {
                ExercisesScreenContent(
                    state =
                        ExercisesScreenState(
                            uiState = ExercisesUiState.Success(exercises = listOf(pullups, plank)),
                            onAddExerciseClick = {}
                        )
                )
            }
        }
    }

    /** Проверяет, что список рендерит название и лейбл типа измерения. */
    @Test
    fun exercises_screen_when_listed_then_shows_name_and_measured_by_label() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = listOf(pullups, plank)))

        // Then
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_measured_by_reps))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Планка")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_measured_by_time))
            .assertIsDisplayed()
    }

    /** Проверяет, что комментарий виден при наличии и отсутствует при null. */
    @Test
    fun exercises_screen_row_comment_visible_only_when_present() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = listOf(pullups, plank)))

        // Then — комментарий подтягиваний виден
        composeTestRule
            .onNodeWithText("широкий хват")
            .assertIsDisplayed()

        // Then — у планки (comment = null) комментария нет: в дереве
        // нет текста отсутствующего комментария, а её название есть
        composeTestRule
            .onNodeWithText("Планка")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("тело — прямая линия")
            .assertDoesNotExist()
    }

    /** Проверяет пустое состояние при пустом каталоге. */
    @Test
    fun exercises_screen_when_empty_then_shows_empty_state() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = emptyList()))

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_list_empty))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertDoesNotExist()
    }

    /** Проверяет, что FAB отображается. */
    @Test
    fun exercises_screen_when_displayed_then_shows_fab() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = listOf(pullups)))

        // Then
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
            .assertIsDisplayed()
    }

    /**
     * Проверяет, что destination `Screen.Exercises.route` открывает
     * экран «Упражнения» (заголовок TopAppBar + контент списка).
     */
    @Test
    fun exercises_screen_when_navigated_to_exercises_route_then_opens_exercises() {
        // Given
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            ExercisesScreenWithNavHost(onControllerReady = { navController = it })
        }

        // When
        composeTestRule.runOnIdle {
            navController.navigate(Screen.Exercises.route)
        }

        // Then
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()
    }
}
