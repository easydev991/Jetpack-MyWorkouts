package com.myworkouts.ui.screens.root

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.MainActivity
import com.myworkouts.R
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.BottomTabs
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Инструментальные тесты уровня [com.myworkouts.ui.screens.root.RootScreen].
 *
 * 1. Accessibility нижней навигации (accessibility-semantics-fixes,
 *    tasks 3.3/3.4): полный стек через реальную `MainActivity` (образец —
 *    `EntryListLiveUpdateIntegrationTest`), нижний бар рендерится только
 *    внутри приватного `AppBottomBar`.
 * 2. Навигация main → overview → overview_settings (overview-screen,
 *    tasks 7.6): на обоих маршрутах нижняя панель навигации скрыта
 *    (`assertCountEquals(0)` по лейблу «Ещё», который живёт только в баре).
 *
 * Данные для навигационного теста — вставка напрямую в файловую БД
 * приложения вторым инстансом (прецедент — `EntryListLiveUpdate`);
 * имя упражнения уникально на прогон, данные БД переживают прогоны.
 */
@RunWith(AndroidJUnit4::class)
class RootScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule(MainActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bottom_bar_when_rendered_then_tab_icons_have_no_content_description() {
        // Given — приложение открыто на главной вкладке, нижний бар виден
        composeTestRule.waitForIdle()

        // Then — нижний бар отрисован: лейбл «Ещё» виден (этот текст
        // живёт только в баре — экран «Ещё» на старте не открыт)
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_title))
            .assertExists()

        // Then — ни одна вкладка не дублирует название через
        // contentDescription иконки (скрытая вкладка даёт 0 автоматически)
        BottomTabs.forEach { tab ->
            composeTestRule
                .onAllNodesWithContentDescription(context.getString(tab.titleResId))
                .assertCountEquals(0)
        }
    }

    /**
     * Маршрут main → overview → overview_settings и обратно (tasks 7.6):
     * на маршрутах `overview` и `overview_settings` нижний бар скрыт,
     * системный back с настроек возвращает на обзор, с обзора — на главный.
     */
    @Test
    fun app_when_navigating_overview_and_settings_then_bottom_bar_hidden_and_back_returns() {
        // Given — запись в файловой БД: список непустой, кнопка обзора видна
        val uniqueName = "ОбзорНав-${System.currentTimeMillis() % 100_000L}"
        insertIntoAppDatabase { repository ->
            val now = System.currentTimeMillis()
            val id =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = uniqueName,
                            category = "PUSH",
                            isCustom = true,
                            comment = null,
                            createdAt = now,
                            measuredBy = ExerciseMeasuredBy.REPS
                        )
                    ).getOrThrow()
            repository
                .addEntry(
                    ExerciseEntry(
                        id = 0L,
                        exerciseId = id,
                        workoutId = null,
                        date = now,
                        sets = 1,
                        reps = 10,
                        weightKg = null,
                        durationSec = null,
                        comment = null,
                        createdAt = now,
                        modifiedAt = now
                    )
                ).getOrThrow()
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithContentDescription(context.getString(R.string.a11y_open_overview))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // When — переход на обзор
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.a11y_open_overview))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — обзор открыт, нижний бар скрыт («Ещё» живёт только в баре)
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_title))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.more_title))
            .assertCountEquals(0)

        // When — шестерёнка → настройки обзора
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.a11y_overview_settings))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — настройки открыты, нижний бар по-прежнему скрыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_settings_title))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.more_title))
            .assertCountEquals(0)

        // When — «назад» в TopAppBar
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — обзор снова, бар скрыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_title))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.more_title))
            .assertCountEquals(0)

        // When — системный back с обзора
        composeTestRule.runOnIdle {
            composeTestRule.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }
        composeTestRule.waitForIdle()

        // Then — главный экран: «Тренировки» дважды — title TopAppBar и
        // лейбл вкладки в вернувшемся нижнем баре
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.workouts_title))
            .assertCountEquals(2)
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_title))
            .assertIsDisplayed()
    }

    /** Вставка в файловую БД приложения вторым инстансом (как в образце). */
    private fun insertIntoAppDatabase(insert: suspend (RoomWorkoutDataRepository) -> Unit) {
        val database =
            Room
                .databaseBuilder(context, WorkoutDatabase::class.java, WorkoutDatabase.DATABASE_NAME)
                .build()
        val repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = listOf(StandardExercise(name = "Отжимания", category = "PUSH"))
            )
        runBlocking { insert(repository) }
        database.close()
    }
}
