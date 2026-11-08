package com.myworkouts.ui.screens.main

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.MyWorkoutsApplication
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Сценарий «пересоздание Activity на экране деталей записи»
 * (workout-entry-detail-screen, task 5.2): после recreate остаёмся на
 * детальном экране той же записи — заголовок «Детали записи» и данные
 * записи отображаются (маршрут `entry_detail/{entryId}` восстановлен
 * NavController'ом из saved state).
 *
 * Использует реальную [com.myworkouts.MainActivity] (а не
 * `ComponentActivity`), потому что `scenario.recreate()` пересоздаёт
 * Activity, и только `MainActivity.onCreate` вызывает `setContent`
 * (восстанавливает Compose-иерархию и NavHost); с `ComponentActivity`
 * Compose-дерево после recreate пустеет — `No compose hierarchies
 * found in the app` (kotlin-ui-testing, references/activity-integration.md).
 *
 * Данные вставляются через продакшен-репозиторий процесса приложения
 * (`MyWorkoutsApplication.repository`): один инстанс Room с одним
 * `InvalidationTracker` — реактивный Flow `MainViewModel.uiState` сразу
 * видит вставку. Имя упражнения уникально на прогон (метка времени) —
 * вставки предыдущих прогонов не маскируют ассерты.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenDetailCardRecreateTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<com.myworkouts.MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Уникальное имя на прогон — старые данные приложения не маскируют. */
    private val entryName = "Recreate-${System.currentTimeMillis() % 100_000L}"

    /**
     * Значение деталей записи «4 х 10 (+ 5 кг)» — собирается из ресурсов
     * (формат зависит от локали устройства).
     */
    private val entryValue: String
        get() {
            val separator = context.getString(R.string.entry_value_separator)
            val weightSuffix =
                " " + context.getString(R.string.entry_details_weight_suffix, "5")
            return "4$separator" + "10" + weightSuffix
        }

    @Test
    fun entry_detail_when_activity_recreated_then_remains_on_same_entry_route() {
        val app = ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()
        val repository = app.repository

        // Seed: вставляем кастомное упражнение + одну запись через
        // продакшен-репозиторий. Один инстанс Room — реактивный Flow
        // `MainViewModel.uiState` сразу подхватывает вставку.
        runBlocking {
            val exerciseId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = entryName,
                            category = "PULL",
                            isCustom = true,
                            comment = null,
                            createdAt = System.currentTimeMillis(),
                            measuredBy = ExerciseMeasuredBy.REPS
                        )
                    ).getOrThrow()
            val now = System.currentTimeMillis()
            repository
                .addEntry(
                    ExerciseEntry(
                        id = 0L,
                        exerciseId = exerciseId,
                        workoutId = null,
                        date = now,
                        sets = 4,
                        reps = 10,
                        weightKg = 5.0,
                        durationSec = null,
                        comment = "с фиксированным весом",
                        createdAt = now,
                        modifiedAt = now
                    )
                ).getOrThrow()
        }

        // Given — список с нашей записью виден
        composeTestRule.waitUntil(ENTRY_TEXT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(entryName).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(entryName).assertIsDisplayed()

        // When — тап по строке, открыт экран деталей
        composeTestRule
            .onNodeWithText(entryName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()

        // When — recreate Activity (имитация поворота / смены темы)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Then — после recreate остаёмся на детальном экране: заголовок
        // «Детали записи» виден (маршрут `entry_detail/{entryId}`
        // восстановлен NavController'ом; на главном экране его нет)
        composeTestRule.waitUntil(ENTRY_TEXT_TIMEOUT) {
            composeTestRule
                .onAllNodesWithText(context.getString(R.string.entry_detail_title))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()

        // Then — данные той же записи: имя, значение «4 х 10 (+ 5 кг)» и
        // комментарий читаются из БД по восстановленному entryId (по одной
        // ноде — контент экрана деталей, список главного не в композиции)
        composeTestRule.onNodeWithText(entryName).assertIsDisplayed()
        composeTestRule.onNodeWithText(entryValue).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("с фиксированным весом")
            .assertCountEquals(1)
    }

    private companion object {
        /** Таймаут ожидания появления текста. */
        const val ENTRY_TEXT_TIMEOUT: Long = 5_000L
    }
}
