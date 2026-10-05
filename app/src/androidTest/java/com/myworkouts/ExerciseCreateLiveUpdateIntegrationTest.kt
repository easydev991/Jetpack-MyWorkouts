package com.myworkouts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционный репро-тест живого обновления каталога на НАСТОЯЩЕМ
 * приложении (create-exercise, этап 5): реальная `MainActivity`, реальный
 * manual DI, БД на диске, настоящий `RootScreen`/NavHost.
 *
 * Сценарий ручной проверки, на которой дефект стабильно воспроизводился:
 * каталог открыт → уход с экрана (FAB на форму создания) → пауза больше
 * таймаута подписки (в тестах rule понижает его до 100 мс) → вставка
 * упражнения → возврат на каталог → каталог ОБЯЗАН показать новое
 * упражнение без перезапуска приложения.
 *
 * Вставка идёт напрямую в БД приложения (тест живёт в том же процессе,
 * файл `workout_database` доступен) — форма не заполняется, чтобы
 * изолировать проверку живого обновления от UI-заполнения. Возврат —
 * системный back с пустой формы (BackHandler отключён — диалога нет).
 *
 * Имя упражнения уникально на прогон — вставки предыдущих прогонов не
 * маскируют замороженный список. Тест быстрый: пауза 300 мс —
 * сценарий сохранён, ожидания нет.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseCreateLiveUpdateIntegrationTest {
    companion object {
        @JvmField
        @ClassRule
        val timeoutRule = SubscriptionTimeoutRule()
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val exerciseName = "Int-${System.currentTimeMillis() % 100_000L}"

    @Test
    fun app_whenReturnedToCatalogAfterInsert_thenShowsNewExercise() {
        // Given — приложение запущено; включаем вкладку «Упражнения»,
        // если выключена (данные приложения переживают прогоны)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.more_title)).performClick()
        composeTestRule.waitForIdle()
        val showTabNodes =
            composeTestRule
                .onAllNodesWithText(context.getString(R.string.more_exercises_tab_show))
                .fetchSemanticsNodes()
        if (showTabNodes.isNotEmpty()) {
            composeTestRule
                .onNodeWithText(context.getString(R.string.more_exercises_tab_show))
                .performClick()
            composeTestRule.waitForIdle()
        }

        // When — каталог открыт (клик по вкладке)
        composeTestRule.onNodeWithText(context.getString(R.string.exercises_title)).performClick()
        composeTestRule.waitForIdle()

        // When — уходим на форму создания (FAB), ждём больше таймаута
        // подписки, вставляем упражнение напрямую в БД, возвращаемся
        // системным back (форма пуста — BackHandler отключён, диалога нет)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_create_title))
            .assertIsDisplayed()
        runBlocking { delay(300L) }
        insertExerciseIntoAppDatabase()

        composeTestRule.runOnIdle {
            composeTestRule.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }

        // Then — каталог показывает новое упражнение без перезапуска.
        // LazyColumn сохраняет позицию прокрутки (закрепление за первым
        // видимым элементом), а новое упражнение вставляется первым —
        // выше текущего viewport; скроллим к началу списка
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(exerciseName))
        composeTestRule.onNodeWithText(exerciseName).assertIsDisplayed()
    }

    /** Вставка кастомного упражнения в файловую БД приложения. */
    private fun insertExerciseIntoAppDatabase() {
        val database =
            Room
                .databaseBuilder(context, WorkoutDatabase::class.java, WorkoutDatabase.DATABASE_NAME)
                .build()
        val repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = listOf(StandardExercise(name = "Отжимания", category = "PUSH"))
            )
        runBlocking {
            repository
                .addExercise(
                    Exercise(
                        id = 0L,
                        name = exerciseName,
                        category = "PUSH",
                        isCustom = true,
                        comment = null,
                        createdAt = 1L,
                        measuredBy = ExerciseMeasuredBy.REPS
                    )
                )
            database.close()
        }
    }
}
