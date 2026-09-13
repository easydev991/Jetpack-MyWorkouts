package com.myworkouts.ui.screens.main

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Регрессионные UI-тесты секций по дате на главном экране
 * (workout-entries-by-date-sections, tasks §8.1).
 *
 * Полный стек без Room: [MainScreen] рендерится на [MainViewModel] с
 * фейковым репозиторием на `MutableStateFlow` — отдельный
 * `TestMainViewModel` не нужен.
 *
 * Проверяет: секция «Сегодня» — без заголовка, записи сразу вверху
 * списка; заголовки «Вчера» / `dd.MM.yyyy`; sticky-заголовок остаётся
 * у верхнего края при скролле; long-press и удаление работают внутри
 * секции; локализация заголовков ru/en.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenSectionsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule(ComponentActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val zone: ZoneId = ZoneId.systemDefault()

    /** «Сегодня» теста — системная дата на момент запуска. */
    private val today: LocalDate = LocalDate.now()

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private lateinit var repository: SectionsFakeRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        repository =
            SectionsFakeRepository(
                initialEntries =
                    todayEntries() +
                        entry(id = 101L, name = "Приседания", date = today.minusDays(1), hour = 19) +
                        entry(id = 201L, name = "Планка", date = today.minusDays(5), hour = 8)
            )
        viewModel = MainViewModel(repository = repository)
    }

    /** Двенадцать записей сегодняшней секции — контент заведомо выше экрана. */
    private fun todayEntries(): List<EntryListItem> =
        (1..12).map { index ->
            entry(
                id = index.toLong(),
                name = "Упражнение №$index",
                date = today,
                hour = 9 + index % 10
            )
        }

    /** Рендер [MainScreen] в заданной локали (ru/en — для заголовков секций). */
    private fun setContentInLocale(languageTag: String) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides localizedContext(languageTag)) {
                JetpackMyWorkoutsTheme {
                    MainScreen(viewModel = viewModel, onAddEntryClick = {})
                }
            }
        }
    }

    /** Локализованный контекст (tasks §8.1: без `AppCompatDelegate`). */
    private fun localizedContext(languageTag: String): Context {
        val localeList = LocaleListCompat.forLanguageTags(languageTag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(localeList[0])
        return context.createConfigurationContext(config)
    }

    private fun entry(
        id: Long,
        name: String,
        date: LocalDate,
        hour: Int
    ): EntryListItem =
        EntryListItem(
            id = id,
            exerciseName = name,
            sets = 3,
            reps = 10,
            durationSec = null,
            date =
                date
                    .atTime(hour, 30)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
        )

    @Test
    fun sections_when_entries_across_dates_then_today_has_no_header_others_show_headers() {
        // Given — записи за сегодня, вчера и пять дней назад
        setContentInLocale("ru")
        composeTestRule.waitForIdle()

        // Then — сегодняшняя секция без заголовка: записи сразу вверху,
        // текста «Сегодня» нигде нет
        composeTestRule
            .onNodeWithText("Упражнение №1")
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Сегодня")
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_section_yesterday))
            .assertIsDisplayed()

        // When — прокрутка к нижней секции
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Планка"))
        composeTestRule.waitForIdle()

        // Then — заголовок датированной секции отображается
        composeTestRule
            .onNodeWithText(dateFormatter.format(today.minusDays(5)))
            .assertIsDisplayed()
    }

    @Test
    fun sections_when_scrolled_then_date_header_sticks_above_rows() {
        // Given — экран со всеми секциями
        setContentInLocale("ru")
        composeTestRule.waitForIdle()

        // When — прокрутка к записи нижней секции («Планка», пять дней назад)
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Планка"))
        composeTestRule.waitForIdle()

        // Then — заголовок датированной секции прилип выше строки
        val headerTop =
            composeTestRule
                .onNodeWithText(dateFormatter.format(today.minusDays(5)))
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot.top
        val rowTop =
            composeTestRule
                .onNodeWithText("Планка")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue(
            "Sticky-заголовок должен остаться у верхнего края над строкой секции",
            headerTop < rowTop
        )
    }

    @Test
    fun sections_when_long_press_in_section_then_context_menu_with_edit_and_delete() {
        // Given — запись «Приседания» в секции «Вчера»
        setContentInLocale("ru")
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Приседания"))
        composeTestRule.waitForIdle()

        // When — long-press по строке внутри секции
        composeTestRule
            .onNodeWithText("Приседания")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — меню с «Изменить» и «Удалить» открылось
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .assertIsDisplayed()
    }

    @Test
    fun sections_when_delete_confirmed_then_entry_disappears_from_section() {
        // Given — запись «Приседания» в секции «Вчера»
        setContentInLocale("ru")
        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Приседания"))
        composeTestRule.waitForIdle()

        // When — long-press → «Удалить» → подтверждение в алерте
        composeTestRule
            .onNodeWithText("Приседания")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — запись реактивно исчезла из секции
        composeTestRule
            .onAllNodesWithText("Приседания")
            .assertCountEquals(0)
    }

    @Test
    fun sections_when_en_locale_then_labels_english() {
        // Given — те же записи, локаль en
        setContentInLocale("en")
        composeTestRule.waitForIdle()

        // Then — английский заголовок «Yesterday», русского и
        // английского «Сегодня» нигде нет (секция без заголовка)
        composeTestRule
            .onNodeWithText(localizedContext("en").getString(R.string.entry_section_yesterday))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Сегодня")
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText("Today")
            .assertCountEquals(0)
    }
}

/**
 * Fake [WorkoutDataRepository] на `MutableStateFlow` для UI-тестов секций:
 * записи реактивные, `deleteEntry` убирает запись из Flow — удаление
 * доезжает до UI без Room (по образцу `MainViewModelFakeRepository`).
 */
private class SectionsFakeRepository(
    initialEntries: List<EntryListItem>
) : WorkoutDataRepository {
    private val entriesFlow = MutableStateFlow(initialEntries)

    override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override fun entries(): Flow<List<EntryListItem>> = entriesFlow

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> {
        entriesFlow.value = entriesFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

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

    // Новые методы контракта (exercises-archive, §2.7): заглушки —
    // тест их не вызывает.

    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> = Result.success(Unit)

    override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override fun archivedCount(): Flow<Int> = MutableStateFlow(0)
}
