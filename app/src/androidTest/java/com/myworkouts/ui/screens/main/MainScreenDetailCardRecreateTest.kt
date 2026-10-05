package com.myworkouts.ui.screens.main

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Сценарий «поворот экрана при открытой карточке деталей»
 * (workout-detail-view, task 4.6): карточка остаётся открытой с
 * данными той же записи, выделение строки сохраняется.
 *
 * Использует реальную [com.myworkouts.MainActivity] (а не
 * `ComponentActivity`), потому что `scenario.recreate()` пересоздаёт
 * Activity, и только `MainActivity.onCreate` вызывает `setContent`
 * (восстанавливает Compose-иерархию); с `ComponentActivity`
 * Compose-дерево после recreate пустеет — `No compose hierarchies
 * found in the app` (kotlin-ui-testing, references/activity-integration.md).
 *
 * Данные вставляются через продакшен-репозиторий процесса приложения
 * (`MyWorkoutsApplication.repository`): один инстанс Room с одним
 * `InvalidationTracker` — реактивный Flow `MainViewModel.uiState`
 * сразу видит вставку, без отдельного окна > `WhileSubscribed(5000)`
 * и без навигационных трюков. Имя упражнения уникально на прогон
 * (метка времени) — вставки предыдущих прогонов не маскируют ассерты.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenDetailCardRecreateTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<com.myworkouts.MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Уникальное имя на прогон — старые данные приложения не маскируют. */
    private val entryName = "Recreate-${System.currentTimeMillis() % 100_000L}"

    @Test
    fun card_when_activity_recreated_then_remains_open_with_same_data() {
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

        // When — тап по строке, карточка открыта
        composeTestRule
            .onNodeWithText(entryName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()

        // When — recreate Activity (имитация поворота / смены темы)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Then — после recreate список виден (наша запись — кастомная,
        // уникальная по имени)
        composeTestRule.waitUntil(ENTRY_TEXT_TIMEOUT) {
            composeTestRule.onAllNodesWithText(entryName).fetchSemanticsNodes().isNotEmpty()
        }

        // Then — карточка осталась открытой (крестик виден — `rememberSaveable`
        // для `detailsEntryId` сохранил состояние)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()

        // Then — карточка показывает данные из БД (комментарий вставлен в
        // БД; rememberSaveable сохраняет только `detailsEntryId`,
        // содержимое карточки читается из `success.entries` уже после
        // recreate). После открытия карточки текст есть и в строке списка,
        // и в карточке (две ноды).
        composeTestRule
            .onAllNodesWithText("с фиксированным весом")
            .assertCountEquals(2)

        // Then — выделение строки сохранилось (Selected=true ровно у одной
        // ноды с именем записи — у строки списка; у самой карточки
        // Selected не выставляется)
        composeTestRule
            .onAllNodesWithText(entryName)
            .filter(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertCountEquals(1)
    }

    private companion object {
        /** Таймаут ожидания появления строки в списке. */
        const val ENTRY_TEXT_TIMEOUT: Long = 5_000L
    }
}
