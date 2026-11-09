package com.myworkouts.ui.screens.entrydetail

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.SubscriptionTimeoutRule
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.crash.NoopCrashReporter
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.preferences.ReviewCountersDataStore
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.review.ReviewFlowLauncherFactory
import com.myworkouts.ui.review.ReviewSessionHolder
import com.myworkouts.ui.screens.root.RootScreen
import com.myworkouts.ui.state.EntryDetailUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.util.formatDetailDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * UI-тесты экрана деталей записи (workout-entry-detail-screen, tasks §5.4
 * и §5.5). Часть сценариев «карточки деталей» перенесена сюда из
 * `MainScreenDetailCardTest` (длинный комментарий, отсутствие блока без
 * комментария) — карточки на главном экране больше нет.
 *
 * Две группы:
 * 1. Содержимое (§5.4): [EntryDetailScreenContent] с готовым
 *    [EntryDetailScreenState] без ViewModel (паттерн
 *    `EntryCreateScreenTest`), а также несуществующий id → заглушка
 *    «Запись не найдена» без actions, отсутствие нижней навигации,
 *    insets-позиционные ассерты.
 * 2. Навигация (§5.5): полный стек через реальный [RootScreen] поверх
 *    Room in-memory (образец — `MainScreenEditNavigationTest` +
 *    `RootScreenTest`): Detail → Edit (предзаполнение / сохранение /
 *    отмена / системный Back), удаление через алерт (Отмена / Удалить),
 *    скрытие нижнего бара на всём пути Main → Detail → Edit, `pressBack`
 *    из Detail → Main.
 */
@RunWith(AndroidJUnit4::class)
class EntryDetailScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule(ComponentActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Для навигационных тестов — Room in-memory + реальный RootScreen.
    // Стандартный набор упражнений пустой, чтобы тесты были изолированы
    // от каталога приложения.
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository
    private lateinit var initialLocale: Locale

    private val pendingAddWorkout = MutableStateFlow(false)

    /** Запись с весом — для проверки «4 х 10 (+ 5 кг)». */
    private val repsItem =
        EntryListItem(
            id = 1L,
            exerciseName = "Подтягивания",
            sets = 4,
            reps = 10,
            durationSec = null,
            weightKg = 5.0,
            comment = "с фиксированным весом",
            date = 1_721_000_000_000L
        )

    /** Запись на время — для проверки «3 х 20 сек» (без веса). */
    private val timeItem =
        EntryListItem(
            id = 2L,
            exerciseName = "Планка",
            sets = 3,
            reps = null,
            durationSec = 20,
            weightKg = null,
            comment = null,
            date = 1_721_100_000_000L
        )

    /** Длинный комментарий (>2 строк) — для теста отсутствия обрезки. */
    private val longComment =
        "очень длинный комментарий, который точно занимает больше двух строк " +
            "и не должен обрезаться многоточием в блоке комментария экрана деталей"
    private val repsWithLongComment = repsItem.copy(comment = longComment)

    /** Записи без комментария и с blank-комментарием — разные имена для поиска. */
    private val noCommentItem = repsItem.copy(exerciseName = "Упражнение A", comment = null)
    private val blankCommentItem = repsItem.copy(exerciseName = "Упражнение B", comment = "   ")

    @Before
    fun setUp() {
        initialLocale = Locale.getDefault()
        Locale.setDefault(Locale("ru"))
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(appContext, WorkoutDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = emptyList()
            )
    }

    @After
    fun tearDown() {
        Locale.setDefault(initialLocale)
        database.close()
    }

    // ===== Helpers (содержимое) =====

    /** Рендер [EntryDetailScreenContent] с готовым состоянием, без ViewModel. */
    private fun setContentWithState(
        uiState: EntryDetailUiState,
        pendingDelete: Boolean = false
    ) {
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                EntryDetailScreenContent(
                    state =
                        EntryDetailScreenState(
                            uiState = uiState,
                            pendingDelete = pendingDelete,
                            onBackClick = {},
                            onEditClick = {},
                            onDeleteClick = {},
                            onDeleteConfirm = {},
                            onDeleteDismiss = {}
                        )
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Ожидаемая строка значения: «4 х 10 (+ 5 кг)» / «3 х 20 сек».
     * Повторяет логику `formatEntryDetailsValue` в терминах
     * локализованных ресурсов.
     */
    private fun expectedDetailsValue(item: EntryListItem): String {
        val value =
            item.reps?.toString()
                ?: "${item.durationSec} ${context.getString(R.string.entry_duration_seconds_short)}"
        val separator = context.getString(R.string.entry_value_separator)
        val setsPrefix = if (item.sets > 1) "${item.sets}$separator" else ""
        val weightSuffix =
            item.weightKg?.takeIf { it > 0.0 }?.let {
                " " +
                    context.getString(R.string.entry_details_weight_suffix, it.toString().removeSuffix(".0"))
            } ?: ""
        return "$setsPrefix$value$weightSuffix"
    }

    /**
     * Локализованное представление даты/времени: паттерн
     * `d MMMM yyyy, HH:mm` в локали ru (как [formatDetailDateTime]).
     */
    private fun expectedDetailDate(dateMillis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("ru"))
        return Instant
            .ofEpochMilli(dateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter)
    }

    // ===== Helpers (навигация) =====

    /**
     * Вставляет запись и её упражнение в [repository], возвращает id записи.
     * Тип измерения выбирается по наличию `reps`/`durationSec` (как в UI).
     * Дата выравнивается на минуту: `EntryCreateViewModel` при предзаполнении
     * формы отсекает секунды (`entryDateMillis` собирает дату из
     * час+минута), иначе черновик != оригинал и `hasUnsavedChanges` =
     * `true` — системный Back показывает диалог «Закрыть?».
     */
    private suspend fun insertEntry(item: EntryListItem): Long {
        val measuredBy =
            if (item.reps != null) ExerciseMeasuredBy.REPS else ExerciseMeasuredBy.TIME
        val alignedDate = item.date - item.date % 60_000
        val exerciseId =
            repository
                .addExercise(
                    Exercise(
                        id = 0L,
                        name = item.exerciseName,
                        category = "PULL",
                        isCustom = true,
                        comment = null,
                        createdAt = 0L,
                        measuredBy = measuredBy
                    )
                ).getOrThrow()
        return repository
            .addEntry(
                ExerciseEntry(
                    id = 0L,
                    exerciseId = exerciseId,
                    workoutId = null,
                    date = alignedDate,
                    sets = item.sets,
                    reps = item.reps,
                    weightKg = item.weightKg,
                    durationSec = item.durationSec,
                    comment = item.comment,
                    createdAt = alignedDate,
                    modifiedAt = alignedDate
                )
            ).getOrThrow()
    }

    /**
     * Полный стек: реальный [RootScreen] (нижний бар, NavHost, продакшен-
     * проводка Main → Detail → Edit) поверх Room in-memory. Запись
     * вставляется до рендера; возвращается её id.
     */
    private fun setContentWithRootScreen(item: EntryListItem): Long {
        val entryId = runBlocking { insertEntry(item) }
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                RootScreen(
                    dataStore = AppSettingsDataStore.create(context),
                    repository = repository,
                    reviewCountersDataStore = ReviewCountersDataStore.create(context),
                    reviewFlowLauncher = ReviewFlowLauncherFactory.create(context),
                    reviewSessionHolder = ReviewSessionHolder(),
                    analyticsProvider = NoopAnalyticsProvider(),
                    crashReporter = NoopCrashReporter(),
                    pendingAddWorkout = pendingAddWorkout
                )
            }
        }
        // Строка записи отрисована
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText(item.exerciseName)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        return entryId
    }

    /** Тап по строке списка открывает экран деталей (канал 4.3). */
    private fun openDetailFromList(item: EntryListItem) {
        composeTestRule
            .onNodeWithText(item.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
    }

    /** Тап по иконке «Изменить» в TopAppBar деталей открывает форму. */
    private fun openEditFromDetail() {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_list_menu_edit))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_edit_title))
            .assertIsDisplayed()
    }

    /** Нижняя навигация скрыта: лейбла «Ещё» в дереве нет. */
    private fun assertBottomBarHidden() {
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText(context.getString(R.string.more_title))
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun assertBottomBarShown() {
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText(context.getString(R.string.more_title))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /**
     * Ждёт предзаполнение формы: комментарий записи отрендерен в поле.
     * Без ожидания `hasUnsavedChanges` может быть ещё `true` (оригинал не
     * загружен) — BackHandler покажет диалог «Закрыть?».
     */
    private fun waitFormPrefilled(item: EntryListItem) {
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodes(hasSetTextAction() and hasText(checkNotNull(item.comment)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    // ===== Содержимое (§5.4) =====

    /** Success с весом: значение «4 х 10 (+ 5 кг)», название, дата/время, комментарий. */
    @Test
    fun content_when_success_with_weight_then_shows_value_name_date_and_comment() {
        setContentWithState(EntryDetailUiState.Success(repsItem))

        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedDetailsValue(repsItem))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedDetailDate(repsItem.date))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(checkNotNull(repsItem.comment))
            .assertIsDisplayed()

        // Actions TopAppBar «Изменить»/«Удалить» видны только в Success
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_list_menu_delete))
            .assertIsDisplayed()
    }

    /** Один подход записи на время — значение «3 х 20 сек» без суффикса веса. */
    @Test
    fun content_when_time_entry_then_shows_duration_value_without_weight() {
        setContentWithState(EntryDetailUiState.Success(timeItem))

        composeTestRule
            .onNodeWithText(expectedDetailsValue(timeItem))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedDetailDate(timeItem.date))
            .assertIsDisplayed()
    }

    /** Длинный комментарий отображается целиком — многострочный, без обрезки. */
    @Test
    fun content_when_long_comment_then_displays_completely() {
        setContentWithState(EntryDetailUiState.Success(repsWithLongComment))

        // Полный текст найден ровно один раз (в блоке комментария)
        composeTestRule
            .onAllNodesWithText(longComment)
            .assertCountEquals(1)

        // Высота ноды больше одной-двух строк: комментарий многострочный,
        // без обрезки (в семантике текст всегда полный, обрезка — визуал;
        // см. анализ в `MainScreenDetailCardTest`).
        val heights =
            composeTestRule
                .onAllNodesWithText(longComment)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot.height }
        assertTrue(
            "Комментарий должен быть многострочным (высоты: $heights px)",
            heights.any { it > MIN_MULTILINE_HEIGHT_PX }
        )
    }

    /** Запись без комментария (null) — блок комментария не рендерится. */
    @Test
    fun content_when_no_comment_then_no_comment_block() {
        setContentWithState(EntryDetailUiState.Success(noCommentItem))
        composeTestRule
            .onNodeWithText(noCommentItem.exerciseName)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("   ")
            .assertCountEquals(0)
    }

    /** Blank-комментарий treated как отсутствие — блок не рендерится. */
    @Test
    fun content_when_blank_comment_then_no_comment_block() {
        setContentWithState(EntryDetailUiState.Success(blankCommentItem))
        composeTestRule
            .onNodeWithText(blankCommentItem.exerciseName)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("   ")
            .assertCountEquals(0)
    }

    /**
     * Несуществующий id (`Error`) — заглушка «Запись не найдена», actions
     * «Изменить»/«Удалить» скрыты, стрелка назад на месте.
     */
    @Test
    fun content_when_not_found_then_stub_without_actions() {
        setContentWithState(EntryDetailUiState.Error)

        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_not_found))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithContentDescription(context.getString(R.string.entry_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithContentDescription(context.getString(R.string.entry_list_menu_delete))
            .assertCountEquals(0)
    }

    /**
     * Insets (§5.4, позиционные ассерты): заголовок TopAppBar ниже
     * статус-бара, значение записи выше панели навигации — контент не
     * заходит под системные панели.
     */
    @Test
    fun content_when_success_then_content_within_system_bars() {
        setContentWithState(EntryDetailUiState.Success(repsItem))

        val statusBarPx = systemBarHeightPx("status_bar_height")
        val navBarPx = systemBarHeightPx("navigation_bar_height")
        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot

        // Заголовок «Детали записи» — ниже статус-бара
        val titleTop =
            composeTestRule
                .onNodeWithText(context.getString(R.string.entry_detail_title))
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue(
            "Заголовок должен быть ниже статус-бара " +
                "(top=$titleTop, statusBar=$statusBarPx)",
            titleTop >= statusBarPx
        )

        // Значение записи — выше панели навигации
        val valueBottom =
            composeTestRule
                .onNodeWithText(expectedDetailsValue(repsItem))
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        assertTrue(
            "Значение не должно заходить под панель навигации " +
                "(bottom=$valueBottom, limit=${rootBounds.height - navBarPx})",
            valueBottom <= rootBounds.height - navBarPx
        )
    }

    // ===== Навигация (§5.5) =====

    /** Main → Detail: экран открыт, нижний бар скрыт, actions видны. */
    @Test
    fun navigation_when_row_tapped_then_detail_opened_and_bottom_bar_hidden() {
        setContentWithRootScreen(repsItem)
        assertBottomBarShown()

        openDetailFromList(repsItem)

        composeTestRule
            .onNodeWithText(expectedDetailsValue(repsItem))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_list_menu_delete))
            .assertIsDisplayed()
        assertBottomBarHidden()
    }

    /** Detail → Edit: форма предзаполнена (комментарий записи), нижний бар скрыт. */
    @Test
    fun detail_when_edit_tapped_then_form_prefilled_and_bottom_bar_hidden() {
        setContentWithRootScreen(repsItem)
        openDetailFromList(repsItem)

        openEditFromDetail()

        // Форма предзаполнена: комментарий записи отрендерен в поле
        // (end-to-end: навигация → загрузка записи по id)
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodes(hasSetTextAction() and hasText(checkNotNull(repsItem.comment)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertBottomBarHidden()
    }

    /**
     * Parity-тест актуализации (§5.5): Detail → Edit → изменение →
     * сохранение → возврат на Detail с обновлёнными данными.
     */
    @Test
    fun detail_edit_when_saved_then_back_on_detail_with_updated_data() {
        setContentWithRootScreen(repsItem)
        openDetailFromList(repsItem)
        openEditFromDetail()

        // When — меняем комментарий и сохраняем
        composeTestRule
            .onNode(hasSetTextAction() and hasText(checkNotNull(repsItem.comment)))
            .performTextReplacement("обновлённый комментарий")
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .performClick()

        // Then — возврат на Detail с обновлённым комментарием
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText("обновлённый комментарий")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(checkNotNull(repsItem.comment))
            .assertCountEquals(0)
    }

    /** Detail → Edit → стрелка назад: возврат на Detail без изменений. */
    @Test
    fun detail_edit_when_back_arrow_tapped_then_returns_to_detail_without_changes() {
        setContentWithRootScreen(repsItem)
        openDetailFromList(repsItem)
        openEditFromDetail()
        waitFormPrefilled(repsItem)

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
        // Данные без изменений — исходный комментарий
        composeTestRule
            .onNodeWithText(checkNotNull(repsItem.comment))
            .assertIsDisplayed()
    }

    /**
     * Detail → Edit → системный Back без изменений: возврат на Detail
     * без диалога подтверждения (канон
     * `EntryCreateScreenTest.content_when_no_changes_and_system_back…`).
     */
    @Test
    fun detail_edit_when_system_back_without_changes_then_returns_without_dialog() {
        setContentWithRootScreen(repsItem)
        openDetailFromList(repsItem)
        openEditFromDetail()
        waitFormPrefilled(repsItem)

        // When — системный «назад» без несохранённых изменений
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Then — возврат на Detail, диалога не было
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
    }

    /** Удаление: алерт → «Отмена» — остались на Detail, запись на месте. */
    @Test
    fun detail_when_delete_cancelled_then_stays_on_detail() {
        setContentWithRootScreen(repsItem)
        openDetailFromList(repsItem)

        // When — «Удалить» открывает алерт подтверждения
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertIsDisplayed()

        // When — «Отмена»
        composeTestRule
            .onNodeWithText(context.getString(R.string.cancel))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт закрыт, остались на Detail, данные на месте
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assertIsDisplayed()
    }

    /**
     * Удаление: алерт → «Удалить» — запись удалена, открыт главный экран
     * (нижний бар вернулся, строки записи больше нет).
     */
    @Test
    fun detail_when_delete_confirmed_then_entry_deleted_and_main_opened() {
        setContentWithRootScreen(repsItem)
        openDetailFromList(repsItem)

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .performClick()

        // Then — Detail закрыт, вернулись на главный экран
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText(context.getString(R.string.entry_detail_title))
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeTestRule
            .onAllNodesWithText(repsItem.exerciseName)
            .assertCountEquals(0)
        assertBottomBarShown()
    }

    /** Системный Back из Detail → Main (запись осталась в списке). */
    @Test
    fun detail_when_system_back_pressed_then_main_opened() {
        setContentWithRootScreen(repsItem)
        openDetailFromList(repsItem)

        // When — системный «назад»
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Then — главный экран со строкой записи
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_detail_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assertIsDisplayed()
        assertBottomBarShown()
    }

    /** Стрелка назад TopAppBar из Detail → Main (запись осталась в списке). */
    @Test
    fun detail_when_back_arrow_tapped_then_main_opened() {
        setContentWithRootScreen(repsItem)
        openDetailFromList(repsItem)

        // When — стрелка назад в TopAppBar деталей
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — главный экран со строкой записи
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_detail_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assertIsDisplayed()
        assertBottomBarShown()
    }

    /**
     * Нижний бар скрыт на всём пути Main → Detail → Edit (design: нижняя
     * навигация только на табах).
     */
    @Test
    fun bottom_bar_when_main_detail_edit_then_hidden_on_detail_and_edit() {
        setContentWithRootScreen(repsItem)
        assertBottomBarShown()

        openDetailFromList(repsItem)
        assertBottomBarHidden()

        openEditFromDetail()
        waitFormPrefilled(repsItem)
        assertBottomBarHidden()

        // Возврат стрелкой: Detail — бар снова скрыт, Main — показан
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()
        composeTestRule.waitForIdle()
        assertBottomBarHidden()

        Espresso.pressBack()
        composeTestRule.waitForIdle()
        assertBottomBarShown()
    }

    // ===== Утилиты =====

    /** Высота системной панели по имени dimen-ресурса Android (0 — если нет). */
    private fun systemBarHeightPx(name: String): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private companion object {
        const val TIMEOUT_MS: Long = 5_000L

        /**
         * Минимальная высота ноды комментария в пикселях, при которой текст
         * считается многострочным (см. комментарий в `MainScreenDetailCardTest`).
         */
        const val MIN_MULTILINE_HEIGHT_PX = 80f

        /** Таймаут подписки StateFlow — 100 мс на время тест-класса. */
        @JvmField
        @ClassRule
        val subscriptionTimeout = SubscriptionTimeoutRule()
    }
}
