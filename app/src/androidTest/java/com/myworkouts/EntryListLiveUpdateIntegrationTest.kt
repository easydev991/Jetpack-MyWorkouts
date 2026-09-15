package com.myworkouts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционные репро-тесты живого обновления списка ЗАПИСЕЙ на главном
 * экране (fix-entry-list-live-update): реальная `MainActivity`, реальный
 * manual DI, БД на диске, настоящий `RootScreen`/NavHost. Образец —
 * `ExerciseCreateLiveUpdateIntegrationTest`.
 *
 * Три сценария спеки «Живое обновление списка записей без перезапуска
 * приложения» (буквы — design.md Decision 2):
 * - B `app_whenEditedAfterRecreate_thenListShowsUpdatedEntry` — «Список
 *   актуален после пересоздания Activity»: детерминированный репро,
 *   `scenario.recreate()` вместо поворота;
 * - A `app_whenEditedAndReturnedQuickly_thenListShowsUpdatedEntry` —
 *   «Изменённая запись отображается при быстром возврате»;
 * - C `app_whenCreatedAndReturnedQuickly_thenListShowsNewEntry` —
 *   «Новая запись отображается при быстром возврате».
 *
 * Подготовка — вставка напрямую в файловую БД приложения вторым инстансом
 * (прецедент — образец). Чтобы подготовленная запись стала видна в списке,
 * подписка главного экрана приостанавливается уходом на вкладку «Ещё»
 * больше таймаута `WhileSubscribed(5000)` — пауза относится к ПОДГОТОВКЕ
 * (симуляция «приложение уже показывает список»), а не к дефектному окну.
 * Дефектное окно — возврат на главный экран ПОСЛЕ сохранения: все шаги
 * правки/создания быстрые, без пауз (design.md Decision 2/3).
 *
 * Имена упражнений уникальны на прогон — данные файловой БД переживают
 * прогоны, старые строки не маскируют assertions.
 */
@RunWith(AndroidJUnit4::class)
class EntryListLiveUpdateIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val uniqueSuffix = System.currentTimeMillis() % 100_000L
    private val editedOldName = "Редакт-старое-$uniqueSuffix"
    private val editedNewName = "Редакт-новое-$uniqueSuffix"
    private val createdName = "Создание-$uniqueSuffix"

    @Test
    fun app_whenEditedAfterRecreate_thenListShowsUpdatedEntry() {
        // Given — запись со «старым» упражнением видна в списке
        prepareEditableEntry()
        waitRowVisible(editedOldName)

        // When — пересоздание Activity (детерминированный триггер смены
        // конфигурации); запись обязана остаться в списке
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        waitRowVisible(editedOldName)

        // When — быстрое редактирование: смена упражнения, сохранение,
        // возврат на главный экран в окне < 5 с
        editEntryChangingExercise(from = editedOldName, to = editedNewName)

        // Then — строка показывает новое название без перезапуска приложения
        assertRowShows(editedNewName)
    }

    @Test
    fun app_whenEditedAndReturnedQuickly_thenListShowsUpdatedEntry() {
        // Given — запись со «старым» упражнением видна в списке
        prepareEditableEntry()
        waitRowVisible(editedOldName)

        // When — правка и сохранение БЕЗ recreate, возврат в окне < 5 с
        editEntryChangingExercise(from = editedOldName, to = editedNewName)

        // Then — строка показывает новое название
        assertRowShows(editedNewName)
    }

    @Test
    fun app_whenCreatedAndReturnedQuickly_thenListShowsNewEntry() {
        // Given — упражнение для новой записи вставлено напрямую в БД
        // (экран создания запросит каталог при открытии и его увидит)
        composeTestRule.waitForIdle()
        insertIntoAppDatabase { repository ->
            repository.addExercise(customExercise(createdName)).getOrThrow()
        }

        // When — создание записи через UI: выбрать упражнение, заполнить
        // подходы и повторы, сохранить (возврат автоматический, без паузы)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.add_workout))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_exercise)))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(createdName).performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_sets)))
            .performTextReplacement("3")
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_reps)))
            .performTextReplacement("10")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).performClick()
        composeTestRule.waitForIdle()

        // Then — новая запись в списке в позиции согласно сортировке
        assertRowShows(createdName)
    }

    /** Пара упражнений + запись со «старым» упражнением (сценарии A/B). */
    private fun prepareEditableEntry() {
        refreshList { repository ->
            val oldId = repository.addExercise(customExercise(editedOldName)).getOrThrow()
            repository.addExercise(customExercise(editedNewName)).getOrThrow()
            val now = System.currentTimeMillis()
            repository
                .addEntry(
                    ExerciseEntry(
                        id = 0L,
                        exerciseId = oldId,
                        workoutId = null,
                        date = now,
                        sets = 3,
                        reps = 10,
                        weightKg = null,
                        durationSec = null,
                        comment = null,
                        createdAt = now,
                        modifiedAt = now
                    )
                ).getOrThrow()
        }
    }

    /**
     * Уходит с главного экрана (вкладка «Ещё»), ждёт > таймаута подписки
     * `WhileSubscribed(5000)`, вставляет данные напрямую в файловую БД
     * и возвращается системным back — список перезапрашивается и видит
     * подготовленные данные.
     */
    private fun refreshList(insert: suspend (RoomWorkoutDataRepository) -> Unit) {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.more_title)).performClick()
        composeTestRule.waitForIdle()
        runBlocking { delay(5_300L) }
        insertIntoAppDatabase(insert)
        composeTestRule.runOnIdle {
            composeTestRule.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }
        composeTestRule.waitForIdle()
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
                standardExercises = listOf(StandardExercise(name = "Отжимания", category = ExerciseCategory.PUSH))
            )
        runBlocking { insert(repository) }
        database.close()
    }

    private fun customExercise(name: String): Exercise =
        Exercise(
            id = 0L,
            name = name,
            category = ExerciseCategory.PUSH,
            isCustom = true,
            comment = null,
            createdAt = System.currentTimeMillis(),
            measuredBy = ExerciseMeasuredBy.REPS
        )

    /** Ожидает строку [name] в списке и прокручивает к ней. */
    private fun waitRowVisible(
        name: String,
        timeoutMs: Long = 5_000
    ) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
            composeTestRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(name))
        composeTestRule.onNodeWithText(name).assertIsDisplayed()
    }

    /**
     * Long-press по строке [from] → «Изменить» → смена упражнения на [to]
     * в дропдауне → «Сохранить» (возврат на главный экран автоматический).
     * Все шаги быстрые, `waitForIdle` между ними (design.md Risk: окно <5 с).
     */
    private fun editEntryChangingExercise(
        from: String,
        to: String
    ) {
        composeTestRule.onNodeWithText(from).performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_list_menu_edit)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(from).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(to).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Ассерт быстрого возврата: строка [name] появляется в списке в окне
     * < 5 с (до перезапуска подписки `WhileSubscribed(5000)`). `waitUntil`
     * покрывает только асинхронный лаг invalidation Room, не таймаут
     * подписки.
     */
    private fun assertRowShows(name: String) =
        waitRowVisible(name, timeoutMs = 2_000)
}
