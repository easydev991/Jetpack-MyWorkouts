package com.myworkouts.ui.screens.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.EntryCreateViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Компонентный тест (exercises-archive §8.6, багфикс): реальная
 * [EntryCreateViewModel] поверх Room in-memory — запись ссылается на
 * заархивированное ДО открытия экрана упражнение X. Поле «Упражнение»
 * показывает X, сохранение заблокировано без изменений (инвариант
 * edit-режима), после изменения подходов запись сохраняется с тем же
 * `exerciseId = X.id` (spec «Редактирование записи, упражнение которой
 * в архиве»). Исключение X из выпадающего списка покрыто
 * `EntryCreateScreenTest` §8.2 и JVM-тестами `EntryCreateViewModelTest`.
 */
@RunWith(AndroidJUnit4::class)
class EntryEditArchivedExerciseTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository

    private var exerciseId = 0L
    private var entryId = 0L

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        repository = RoomWorkoutDataRepository(database, standardExercises = emptyList())
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Ждёт закрытия экрана после сохранения: `onSaved` вызывается строго
     * после успешной записи в Room (EntryCreateViewModel.save), так что
     * `backClicked` — видимый сигнал приземлившейся записи. Поллинг
     * репозитория (runBlocking-запросы в waitUntil) конкурировал за
     * main-поток с самой сохраняющей coroutine и изредка не укладывался
     * в таймаут (ComposeTimeoutException).
     *
     * Таймаут ≠ «медленно»: [EntryCreateViewModel.save] молча выходит при
     * `!canSave`, а неудачное сохранение ставит `isSaveError` без навигации
     * — в обоих случаях сигнал не придёт никогда. Падаем сразу со снимком
     * состояния, чтобы причина была в сообщении, а не гаданием по логам.
     */
    private fun awaitSaved(
        viewModel: EntryCreateViewModel,
        backClicked: () -> Boolean
    ) {
        try {
            composeTestRule.waitUntil(timeoutMillis = 10_000) { backClicked() }
        } catch (e: ComposeTimeoutException) {
            val s = viewModel.uiState.value
            throw IllegalStateException(
                "Экран не закрылся после сохранения за 10 с: " +
                    "setsText=${s.setsText} canSave=${s.canSave} " +
                    "hasChanges=${s.hasChanges} isSaveError=${s.isSaveError} " +
                    "isEntryMissing=${s.isEntryMissing}",
                e
            )
        }
    }

    /** Запись с REPS-упражнением X; X заархивирован до открытия экрана. */
    private fun seedArchivedEntry() {
        runBlocking {
            // Дата минутной точности — как у записей, созданных приложением
            // (сохранение идёт через entryDateMillis); иначе черновик
            // префилла не совпадёт с оригиналом и hasChanges будет true.
            val seededDate =
                LocalDate
                    .now()
                    .minusDays(1)
                    .atTime(10, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            exerciseId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = "Жим лёжа",
                            category = ExerciseCategory.PUSH,
                            isCustom = true,
                            comment = null,
                            createdAt = 1L,
                            measuredBy = ExerciseMeasuredBy.REPS
                        )
                    ).getOrThrow()
            // Активное Y того же типа измерения — для проверки возврата к X
            repository
                .addExercise(
                    Exercise(
                        id = 0L,
                        name = "Отжимания",
                        category = ExerciseCategory.PUSH,
                        isCustom = true,
                        comment = null,
                        createdAt = 2L,
                        measuredBy = ExerciseMeasuredBy.REPS
                    )
                ).getOrThrow()
            entryId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = exerciseId,
                            workoutId = null,
                            date = seededDate,
                            sets = 3,
                            reps = 10,
                            weightKg = null,
                            durationSec = null,
                            comment = null,
                            createdAt = 1L,
                            modifiedAt = 1L
                        )
                    ).getOrThrow()
            repository.setArchived(exerciseId, true).getOrThrow()
        }
    }

    /**
     * Компонентный тест (exercises-archive §8.6 + §8.10, багфикс): реальная
     * [EntryCreateViewModel] поверх Room in-memory. Оба сценария — в одном
     * тесте: compose-тест-раннер (v2) исполняет каждый тест в собственном
     * runTest, и viewModelScope-корутины VM, созданной во втором тесте,
     * интерференция между runTest'ами не исполняет (клик по «Сохранить»
     * не приводит к записи в БД). Внутри одного теста проблема
     * отсутствует, поэтому повторное открытие экрана — второй setContent
     * того же метода.
     *
     * Проход 1 (§8.6): запись ссылается на заархивированное ДО открытия
     * упражнение X. Поле показывает X, сохранение заблокировано без
     * изменений (инвариант edit-режима), после изменения подходов запись
     * сохраняется с тем же `exerciseId = X.id`.
     * Проход 2 (§8.10): смена архивного X на активное Y и возврат —
     * X остаётся опцией дропдауна до сохранения, запись сохраняется
     * с `exerciseId = X.id` (spec «Смена архивного упражнения — исходное
     * остаётся в списке до сохранения»). Исключение X из выпадающего
     * списка для новых записей покрыто `EntryCreateScreenTest` §8.2
     * и JVM-тестами `EntryCreateViewModelTest`.
     */
    @Test
    fun entryEditScreen_when_exercise_archived_then_field_shows_it_and_save_after_change() {
        // Given — запись с архивным упражнением X открыта на редактирование
        seedArchivedEntry()
        var backClicked by mutableStateOf(false)
        var currentViewModel by mutableStateOf(
            EntryCreateViewModel(
                repository = repository,
                nowProvider = { System.currentTimeMillis() },
                entryId = entryId,
                analyticsProvider = NoopAnalyticsProvider()
            )
        )
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                EntryCreateScreen(
                    viewModel = currentViewModel,
                    onBackClick = { backClicked = true }
                )
            }
        }

        // Then — поле «Упражнение» показывает X (загружен без фильтра архива)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Жим лёжа").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Жим лёжа").assertIsDisplayed()

        // Then — без изменений сохранение неактивно (инвариант edit-режима)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsNotEnabled()

        // When — изменены подходы
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_sets)))
            .performTextReplacement("4")
        composeTestRule.waitForIdle()

        // Then — сохранение разблокировано; запись обновлена с exerciseId = X.id
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsEnabled()
            .performClick()
        awaitSaved(currentViewModel) { backClicked }

        val saved =
            checkNotNull(runBlocking { repository.getEntry(entryId).getOrNull() }) {
                "Отредактированная запись должна остаться в БД"
            }
        assertEquals("Упражнение записи не изменилось", exerciseId, saved.exerciseId)
        assertEquals("Изменение подходов сохранено", 4, saved.sets)
        assertTrue("После сохранения экран закрывается", backClicked)

        // Проход 2 — смена архивного X на активное Y и возврат к X:
        // экран открывается заново с новой VM на ту же запись
        val viewModel2 =
            EntryCreateViewModel(
                repository = repository,
                nowProvider = { System.currentTimeMillis() },
                entryId = entryId,
                analyticsProvider = NoopAnalyticsProvider()
            )
        composeTestRule.runOnIdle {
            backClicked = false
            currentViewModel = viewModel2
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Жим лёжа").fetchSemanticsNodes().isNotEmpty()
        }

        // When — смена на активное Y через дропдаун
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_field_exercise))
            .performClick()
        composeTestRule.onNodeWithText("Отжимания").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Then — поле показывает Y
        composeTestRule.onNodeWithText("Отжимания").assertIsDisplayed()

        // When — повторное открытие списка: исходное X доступно
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_field_exercise))
            .performClick()
        composeTestRule.onNodeWithText("Жим лёжа").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Then — поле снова показывает X
        composeTestRule.onNodeWithText("Жим лёжа").assertIsDisplayed()

        // When — изменены подходы, сохранение (4 → 5: правка относительно
        // сохранённого в проходе 1 значения)
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_sets)))
            .performTextReplacement("5")
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsEnabled()
            .performClick()
        awaitSaved(viewModel2) { backClicked }

        // Then — запись обновлена с exerciseId = X.id
        val saved2 =
            checkNotNull(runBlocking { repository.getEntry(entryId).getOrNull() }) {
                "Отредактированная запись должна остаться в БД"
            }
        assertEquals("Возвращено исходное упражнение X", exerciseId, saved2.exerciseId)
        assertEquals("Изменение подходов сохранено", 5, saved2.sets)
        assertTrue("После сохранения экран закрывается", backClicked)
    }
}
