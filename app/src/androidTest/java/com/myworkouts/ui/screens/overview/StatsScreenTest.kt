package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.data.db.standardCategories
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.usecase.CategoryStatsResult
import com.myworkouts.domain.usecase.CategoryTotal
import com.myworkouts.domain.usecase.DrillDateGroup
import com.myworkouts.domain.usecase.ExerciseTotal
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.domain.usecase.window
import com.myworkouts.ui.state.StatsUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.util.formatStatsWindowLabel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * Компонентные UI-тесты для [StatsScreen] без ViewModel (overview-screen,
 * tasks 7.5; образец — `EntrySettingsScreenTest`; логика VM покрыта
 * JVM-тестами `OverviewViewModelTest`, расчёты — `ComputeCategoryStatsTest`).
 *
 * Проверяют: пустой период — подсказка без легенды; клик по категории
 * открывает drill-down (строка «Категории» + записи); «Категории»
 * возвращает к легенде; смена периода сбрасывает drill-down; TIME-категория
 * видна в легенде с процентом и открывает drill-down; значения легенды —
 * проценты от подходов (overview-stats-time-support tasks 4.2,
 * overview-periods-and-shares tasks 5.2). Листание окон (add-stats-period-pager
 * tasks 4.2–4.3): подпись дат окна под пикером и при скрытом пикере, свайп
 * вправо открывает более раннее окно с его подписью, свайп влево от
 * актуального окна ничего не меняет, свайп за самое старое окно и свайп
 * вправо при единственном окне ничего не меняют (глубина листалки — самой
 * ранней записи), свайп сбрасывает drill-down, страница окна без записей —
 * серый донат и подсказка без рамки (в ландшафте подсказка справа от
 * доната), повторный тап выбранного периода возвращает
 * пагер на актуальное окно (инвариант D7 «состояние → страница пагера»).
 * Ландшафтный свайп (сценарий «Свайп в ландшафте» требования
 * «Ландшафтная раскладка статистики»): зеркала портретных свайп-тестов —
 * ландшафтная ветка экрана задаётся override-ом `LocalConfiguration`
 * ([StatsScreen] ветвится по orientation), реальный поворот активности
 * компонентному тесту не нужен. Стартовая проверка ландшафтного свайп-теста —
 * guard сценария «Поворот сохраняет состояние»: окно со смещением не
 * сбрасывается на актуальное при ландшафтном рендере (полный поворот
 * с recreate — ручная проверка tasks 5.3; ViewModel-интеграция —
 * kotlin-ui-testing правило 12).
 */
@RunWith(AndroidJUnit4::class)
class StatsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Название категории в легенде (ресурс ["PUSH"]). */
    private val pushLabel: String = context.getString(R.string.exercise_category_push)

    /** Название TIME-категории в легенде (ресурс ["CARDIO"]). */
    private val cardioLabel: String = context.getString(R.string.exercise_category_cardio)

    /** Текст значения легенды: единственная строка уровня — «100 %». */
    private val hundredPercentText: String = context.getString(R.string.overview_legend_percent, 100)

    /** Цвета категорий для фикстур: `colorArgb` стандартного набора. */
    private val categoryColors: Map<String, Int> =
        standardCategories.associate { it.name to it.colorArgb }

    /**
     * Локаль подписи дат: та же, что [com.myworkouts.ui.util.statsWindowLabelResource]
     * берёт из `LocalConfiguration` (контекст композиции — targetContext).
     */
    private val screenLocale: Locale
        get() = context.resources.configuration.locales[0]

    /** Разделитель диапазона подписи (ресурс экрана). */
    private val windowLabelSeparator: String =
        context.getString(R.string.overview_months_separator)

    /**
     * Ожидаемая подпись дат окна [period] со смещением [offset] назад —
     * считается той же чистой функцией [formatStatsWindowLabel], что и на
     * экране (add-stats-period-pager, design D6).
     */
    private fun windowLabel(
        period: StatsPeriod,
        offset: Int
    ): String =
        formatStatsWindowLabel(
            window = period.window(LocalDate.now(), offset),
            period = period,
            locale = screenLocale,
            separator = windowLabelSeparator
        )

    /**
     * Агрегаты недели с единственной категорией [category]: одна строка
     * легенды уровня и одна запись [date] в drill-down (фикстуры — данные
     * минимальны, расчёты покрыты JVM-тестами домена).
     */
    @Suppress("LongParameterList")
    private fun singleCategoryWeekResult(
        category: String,
        exerciseName: String,
        exerciseId: Long,
        measuredBy: ExerciseMeasuredBy,
        totalSets: Int,
        entrySets: Int,
        reps: Int?,
        durationSec: Int?,
        date: LocalDate
    ): CategoryStatsResult =
        CategoryStatsResult(
            categoryTotals =
                listOf(
                    CategoryTotal(
                        category = category,
                        totalSets = totalSets
                    )
                ),
            exerciseTotals =
                mapOf(
                    category to
                        listOf(ExerciseTotal(exerciseName, totalSets = totalSets))
                ),
            drillGroups =
                mapOf(
                    category to
                        listOf(
                            DrillDateGroup(
                                date = date,
                                entries =
                                    listOf(
                                        EntryWithExercise(
                                            id = 1L,
                                            exerciseId = exerciseId,
                                            exerciseName = exerciseName,
                                            iconNumber = null,
                                            category = category,
                                            measuredBy = measuredBy,
                                            exerciseComment = null,
                                            sets = entrySets,
                                            reps = reps,
                                            durationSec = durationSec,
                                            weightKg = null,
                                            comment = null,
                                            date = date.toEpochDay() * 86_400_000L
                                        )
                                    )
                            )
                        )
                )
        )

    /** Непустая «Статистика» недели: PUSH 3 подхода, запись сегодня. */
    private fun weekState(selected: String? = null): StatsUiState =
        StatsUiState(
            period = StatsPeriod.WEEK,
            windowOffset = 0,
            window = StatsPeriod.WEEK.window(LocalDate.now(), 0),
            selected = selected,
            statsByOffset =
                mapOf(
                    0 to
                        singleCategoryWeekResult(
                            category = "PUSH",
                            exerciseName = "Отжимания",
                            exerciseId = 2L,
                            measuredBy = ExerciseMeasuredBy.REPS,
                            totalSets = 3,
                            entrySets = 1,
                            reps = 15,
                            durationSec = null,
                            date = LocalDate.now()
                        )
                ),
            categoryColors = categoryColors
        )

    /** Только-TIME «Статистика» недели: CARDIO (бег), запись сегодня. */
    private fun timeState(selected: String? = null): StatsUiState =
        StatsUiState(
            period = StatsPeriod.WEEK,
            windowOffset = 0,
            window = StatsPeriod.WEEK.window(LocalDate.now(), 0),
            selected = selected,
            statsByOffset =
                mapOf(
                    0 to
                        singleCategoryWeekResult(
                            category = "CARDIO",
                            exerciseName = "Бег",
                            exerciseId = 3L,
                            measuredBy = ExerciseMeasuredBy.TIME,
                            totalSets = 2,
                            entrySets = 2,
                            reps = null,
                            durationSec = 1800,
                            date = LocalDate.now()
                        )
                ),
            categoryColors = categoryColors
        )

    /**
     * Неделя с двумя окнами (add-stats-period-pager): ключ 0 — PUSH,
     * запись сегодня; ключ 1 — [pastIsEmpty] ? пустой результат (окно без
     * записей, контракт D2) : PUSH с записью 8 дней назад (прошлое окно).
     * Ключи 0..1 без дыр, windowOffset = 0 — актуальное окно.
     */
    private fun twoWindowState(
        pastIsEmpty: Boolean = false,
        selected: String? = null
    ): StatsUiState {
        val today = LocalDate.now()
        val currentWindow =
            singleCategoryWeekResult(
                category = "PUSH",
                exerciseName = "Отжимания",
                exerciseId = 2L,
                measuredBy = ExerciseMeasuredBy.REPS,
                totalSets = 3,
                entrySets = 1,
                reps = 15,
                durationSec = null,
                date = today
            )
        val pastWindow =
            if (pastIsEmpty) {
                CategoryStatsResult(emptyList(), emptyMap(), emptyMap())
            } else {
                singleCategoryWeekResult(
                    category = "PUSH",
                    exerciseName = "Отжимания",
                    exerciseId = 2L,
                    measuredBy = ExerciseMeasuredBy.REPS,
                    totalSets = 3,
                    entrySets = 1,
                    reps = 15,
                    durationSec = null,
                    date = today.minusDays(8)
                )
            }
        return StatsUiState(
            period = StatsPeriod.WEEK,
            windowOffset = 0,
            window = StatsPeriod.WEEK.window(today, 0),
            selected = selected,
            statsByOffset = mapOf(0 to currentWindow, 1 to pastWindow),
            categoryColors = categoryColors
        )
    }

    /**
     * Неделя с тремя окнами (guard сценария «Поворот сохраняет состояние»):
     * ключ 0 — PUSH (запись сегодня), ключ 1 — CARDIO (запись 8 дней назад),
     * ключ 2 — PUSH (запись 15 дней назад); разные категории окон 0 и 1
     * различают страницы пагера по содержимому. Ключи 0..2 без дыр,
     * [windowOffset] — смещение состояния (state.window — границы этого окна).
     */
    private fun threeWindowState(windowOffset: Int = 0): StatsUiState {
        val today = LocalDate.now()
        return StatsUiState(
            period = StatsPeriod.WEEK,
            windowOffset = windowOffset,
            window = StatsPeriod.WEEK.window(today, windowOffset),
            selected = null,
            statsByOffset =
                mapOf(
                    0 to
                        singleCategoryWeekResult(
                            category = "PUSH",
                            exerciseName = "Отжимания",
                            exerciseId = 2L,
                            measuredBy = ExerciseMeasuredBy.REPS,
                            totalSets = 3,
                            entrySets = 1,
                            reps = 15,
                            durationSec = null,
                            date = today
                        ),
                    1 to
                        singleCategoryWeekResult(
                            category = "CARDIO",
                            exerciseName = "Бег",
                            exerciseId = 3L,
                            measuredBy = ExerciseMeasuredBy.TIME,
                            totalSets = 2,
                            entrySets = 2,
                            reps = null,
                            durationSec = 1800,
                            date = today.minusDays(8)
                        ),
                    2 to
                        singleCategoryWeekResult(
                            category = "PUSH",
                            exerciseName = "Отжимания",
                            exerciseId = 2L,
                            measuredBy = ExerciseMeasuredBy.REPS,
                            totalSets = 3,
                            entrySets = 1,
                            reps = 15,
                            durationSec = null,
                            date = today.minusDays(15)
                        )
                ),
            categoryColors = categoryColors
        )
    }

    /**
     * Рендер с управляемым состоянием: колбэки мутат [state] так, как это
     * делает [com.myworkouts.ui.viewmodel.OverviewViewModel] (смена периода
     * сбрасывает смещение и drill-down, смена окна — смещение, границы окна
     * и drill-down, add-stats-period-pager design D5) — вёрстка проверяется
     * в изоляции.
     *
     * @param landscape Рендерить ландшафтную ветку экрана: [StatsScreen]
     *   ветвится по `LocalConfiguration.current.orientation`, поэтому
     *   ландшафт задаётся override-ом `LocalConfiguration` — конфигурация
     *   копируется из targetContext с сохранением локали (подпись дат окна
     *   рендерится по той же локали, что и ожидаемая), меняется только
     *   orientation.
     */
    private fun setContentWith(
        state: StatsUiState,
        landscape: Boolean = false
    ) {
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                val screen: @Composable () -> Unit = {
                    // remember обязателен: без него рекомпозиция пересоздаёт
                    // State с исходным значением и затирает результат клика
                    var current by remember { mutableStateOf(state) }
                    StatsScreen(
                        state = current,
                        onPeriodChange = { period ->
                            current =
                                current.copy(
                                    period = period,
                                    windowOffset = 0,
                                    window = period.window(LocalDate.now(), 0),
                                    selected = null
                                )
                        },
                        onCategoryClick = { category ->
                            current = current.copy(selected = category)
                        },
                        onWindowChange = { offset ->
                            current =
                                current.copy(
                                    windowOffset = offset,
                                    window = current.period.window(LocalDate.now(), offset),
                                    selected = null
                                )
                        }
                    )
                }
                if (landscape) {
                    val landscapeConfiguration =
                        Configuration(context.resources.configuration).apply {
                            orientation = Configuration.ORIENTATION_LANDSCAPE
                        }
                    CompositionLocalProvider(
                        LocalConfiguration provides landscapeConfiguration
                    ) {
                        screen()
                    }
                } else {
                    screen()
                }
            }
        }
    }

    /**
     * Скроллит вертикальный список страницы к ноде с текстом [text] (на
     * компактном AVD пикер с подписью и донат вытесняют drill-down/легенду
     * за границу вьюпорта). Ось задана явно: у пагера своя горизонтальная
     * scroll-семантика, и общий матчер `hasScrollAction()` нашёл бы два узла.
     */
    private fun scrollTo(text: String) {
        composeTestRule
            .onNode(hasVerticalScrollAction())
            .performScrollToNode(hasText(text))
        composeTestRule.waitForIdle()
    }

    /** Вертикальный скролл (LazyColumn страницы пагера) — без горизонтальной
     *  оси самого пагера. */
    private fun hasVerticalScrollAction(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Test
    fun stats_when_empty_period_then_shows_empty_hint_without_legend() {
        // Given — пустой период (дефолтный StatsUiState)
        setContentWith(StatsUiState())

        // Then — подсказка отображена
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_stats_empty))
            .assertIsDisplayed()

        // Then — легенды и записей нет
        composeTestRule
            .onAllNodesWithText(pushLabel)
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_back_to_categories))
            .assertCountEquals(0)
    }

    @Test
    fun stats_when_category_clicked_then_drill_down_shows_entries_and_back_row() {
        // Given — неделя с данными, обзор по категориям
        setContentWith(weekState())

        // Then — легенда категории отображена
        scrollTo(pushLabel)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()

        // When — тап по категории «Толкающие»
        composeTestRule
            .onNodeWithText(pushLabel)
            .performClick()
        composeTestRule.waitForIdle()

        // Then — drill-down открыт: строка возврата (скролл — на компактном
        // AVD она за нижней границей из-за пикера, подписи и доната) и запись
        scrollTo(context.getString(R.string.overview_back_to_categories))
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_back_to_categories))
            .assertIsDisplayed()

        // Then — «Отжимания» дважды: строка легенды упражнений и запись
        // drill-down списка; строка легенды — процент единственной строки
        composeTestRule
            .onAllNodesWithText("Отжимания")
            .assertCountEquals(2)
        scrollTo(hundredPercentText)
        composeTestRule
            .onNodeWithText(hundredPercentText)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_drill_down_opened_then_today_section_has_today_header() {
        // Given — drill-down категории PUSH с записью за сегодня
        setContentWith(weekState(selected = "PUSH"))

        // Then — секция сегодняшних записей озаглавлена «Сегодня»
        // (в отличие от главного экрана, где секция без заголовка)
        scrollTo(context.getString(R.string.entry_section_today))
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_section_today))
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_back_to_categories_clicked_then_drill_down_closes() {
        // Given — drill-down категории PUSH
        setContentWith(weekState(selected = "PUSH"))
        scrollTo(context.getString(R.string.overview_back_to_categories))
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_back_to_categories))
            .assertIsDisplayed()

        // When — тап «Категории»
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_back_to_categories))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — легенда вернулась, строки возврата нет
        scrollTo(pushLabel)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_back_to_categories))
            .assertCountEquals(0)
    }

    @Test
    fun stats_when_period_changed_then_drill_down_resets() {
        // Given — drill-down категории PUSH
        setContentWith(weekState(selected = "PUSH"))
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_period_month))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — drill-down сброшен: легенда снова (скролл вверх к ней),
        // строки возврата нет
        scrollTo(pushLabel)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_back_to_categories))
            .assertCountEquals(0)
    }

    @Test
    fun stats_when_time_category_then_legend_shows_percent() {
        // Given — только TIME-записи за период (2 подхода, единственная
        // строка уровня)
        setContentWith(timeState())

        // Then — TIME-категория в легенде с процентом «100 %»,
        // подсказка пустого периода не показана
        scrollTo(cardioLabel)
        composeTestRule
            .onNodeWithText(cardioLabel)
            .assertIsDisplayed()
        scrollTo(hundredPercentText)
        composeTestRule
            .onNodeWithText(hundredPercentText)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_stats_empty))
            .assertCountEquals(0)
    }

    @Test
    fun stats_when_time_category_clicked_then_drill_down_shows_entries() {
        // Given — только TIME-записи за период, обзор по категориям
        setContentWith(timeState())

        // When — тап по TIME-категории «Кардио»
        composeTestRule
            .onNodeWithText(cardioLabel)
            .performClick()
        composeTestRule.waitForIdle()

        // Then — drill-down открыт: строка возврата (скролл — на компактном
        // AVD она за нижней границей) и запись «Бег» дважды — строка легенды
        // упражнений и запись drill-down списка; легенда — процент «100 %»
        scrollTo(context.getString(R.string.overview_back_to_categories))
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_back_to_categories))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Бег")
            .assertCountEquals(2)
        scrollTo(hundredPercentText)
        composeTestRule
            .onNodeWithText(hundredPercentText)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_single_visible_period_then_picker_hidden_and_content_rises() {
        // Given — один видимый период (overview-periods-and-shares)
        setContentWith(weekState().copy(visiblePeriods = listOf(StatsPeriod.WEEK)))

        // Then — сегментов пикера нет (за окно этого состояния выходят
        // только они), контент поднят на место пикера
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_period_month))
            .assertCountEquals(0)
        scrollTo(pushLabel)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_two_visible_periods_then_only_visible_segments_shown() {
        // Given — видимые периоды: День и Неделя
        setContentWith(
            weekState().copy(visiblePeriods = listOf(StatsPeriod.DAY, StatsPeriod.WEEK))
        )

        // Then — в пикере ровно два сегмента, скрытый «Год» не рендерится
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_period_day))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_period_week))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_period_year))
            .assertCountEquals(0)
    }

    // ===== Листание окон (add-stats-period-pager tasks 4.2–4.3) =====

    @Test
    fun stats_when_window_label_then_shown_under_period_picker() {
        // Given — неделя с данными, пикер со всеми периодами
        setContentWith(weekState())
        val weekSegment = context.getString(R.string.overview_period_week)
        val label = windowLabel(StatsPeriod.WEEK, offset = 0)

        // Then — подпись дат окна отображена и расположена под пикером
        composeTestRule
            .onNodeWithText(label)
            .assertIsDisplayed()
        val pickerTop =
            composeTestRule
                .onNodeWithText(weekSegment)
                .fetchSemanticsNode()
                .positionInRoot.y
        val labelTop =
            composeTestRule
                .onNodeWithText(label)
                .fetchSemanticsNode()
                .positionInRoot.y
        assertTrue("Подпись дат окна расположена под пикером периода", labelTop > pickerTop)
    }

    @Test
    fun stats_when_swiped_right_then_earlier_window_opens_with_its_label() {
        // Given — неделя с двумя окнами, актуальное окно (смещение 0)
        setContentWith(twoWindowState())

        // When — свайп содержимого вправо (палец вправо — в прошлое, D4)
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        // Then — открылось окно смещения 1: подпись сдвинулась за окном,
        // легенда показывает данные прошлого окна
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(windowLabel(StatsPeriod.WEEK, offset = 0))
            .assertCountEquals(0)
        scrollTo(pushLabel)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_swiped_left_from_current_window_then_nothing_changes() {
        // Given — неделя с двумя окнами, актуальное окно — последняя страница
        setContentWith(twoWindowState())

        // When — свайп влево от актуального окна (более позднего окна нет)
        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        // Then — состояние не изменилось: подпись актуального окна, легенда
        // прежняя
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 0))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertCountEquals(0)
        scrollTo(pushLabel)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_swiped_right_beyond_oldest_window_then_nothing_changes() {
        // Given — неделя с тремя окнами; два свайпа вправо открывают самое
        // старое окно (смещение 2)
        setContentWith(threeWindowState())
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 2))
            .assertIsDisplayed()

        // When — ещё один свайп вправо (глубже самой ранней записи окна нет,
        // pageCount = maxOffset + 1, страница не существует)
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        // Then — состояние не изменилось: подпись самого старого окна,
        // легенда прежняя
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 2))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertCountEquals(0)
        scrollTo(pushLabel)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_swipe_right_unavailable_then_nothing_changes() {
        // Given — записи только в актуальном окне (одно окно, maxOffset = 0)
        setContentWith(weekState())

        // When — свайп содержимого вправо (более раннего окна нет)
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        // Then — состояние не изменилось: подпись актуального окна
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 0))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertCountEquals(0)
    }

    @Test
    fun stats_when_swiped_then_drill_down_resets() {
        // Given — drill-down категории PUSH в актуальном окне
        setContentWith(twoWindowState(selected = "PUSH"))
        scrollTo(context.getString(R.string.overview_back_to_categories))
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_back_to_categories))
            .assertIsDisplayed()

        // When — свайп вправо к более раннему окну
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        // Then — drill-down сброшен: легенда категорий, строки возврата нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_back_to_categories))
            .assertCountEquals(0)
        scrollTo(pushLabel)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_window_page_has_no_entries_then_shows_empty_hint() {
        // Given — прошлое окно (смещение 1) без записей: пустой результат
        // в карте агрегатов (контракт D2)
        setContentWith(twoWindowState(pastIsEmpty = true))

        // When — свайп вправо к пустому окну
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        // Then — серый донат (семантика contentDescription) и подсказка
        // без рамки; подпись дат окна; легенды нет
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.overview_stats_empty))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_stats_empty))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(pushLabel)
            .assertCountEquals(0)
    }

    @Test
    fun stats_when_window_page_has_no_entries_in_landscape_then_hint_right_of_donut() {
        // Given — прошлое окно (смещение 1) без записей, ландшафтная
        // раскладка
        setContentWith(twoWindowState(pastIsEmpty = true), landscape = true)

        // When — свайп вправо к пустому окну
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        // Then — подсказка без рамки расположена справа от серого доната
        // (в колонке легенды), подпись дат окна на месте
        val donut =
            composeTestRule
                .onNodeWithContentDescription(context.getString(R.string.overview_stats_empty))
                .fetchSemanticsNode()
        val hint =
            composeTestRule
                .onNodeWithText(context.getString(R.string.overview_stats_empty))
                .fetchSemanticsNode()
        assertTrue(
            "Подсказка пустого окна расположена справа от серого доната",
            hint.positionInRoot.x > donut.positionInRoot.x + donut.size.width
        )
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(pushLabel)
            .assertCountEquals(0)
    }

    @Test
    fun stats_when_selected_period_tapped_again_then_current_window_restored() {
        // Given — неделя с двумя окнами (прошлое пустое), свайп к прошлому
        setContentWith(twoWindowState(pastIsEmpty = true))
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertIsDisplayed()

        // When — повторный тап по уже выбранному сегменту «Неделя»:
        // SegmentedButton вызывает onClick и для выбранного, состояние
        // сбрасывает смещение в 0 без смены периода — пагер обязан вернуться
        // на страницу актуального окна (инвариант D7 «состояние → страница»)
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_period_week))
            .performClick()

        // Программная синхронизация страницы (scrollToPage) применяется
        // кадром позже рекомпозиции состояния — ждём появления легенды
        // актуального окна (kotlin-ui-testing: условия по кадрам — waitUntil,
        // не Thread.sleep). Подсказка пустого прошлого окна в дереве не
        // проверяется: после программного snap пагер оставляет семантику
        // ушедшей страницы — подсчёт её узлов по дереву недостоверен
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText(pushLabel)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Then — подпись и данные относятся к одному окну (окно 0): подпись
        // дат актуального окна, легенда окна 0 видна (вертикальный свайп —
        // на компактных экранах легенда ниже границы вьюпорта; окно прошлой
        // страницы на свайп не отвечает — жест вертикальный), подписи
        // прошлого окна нет
        composeTestRule.onRoot().performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 0))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_picker_hidden_then_window_label_remains() {
        // Given — один видимый период: пикер скрыт
        setContentWith(weekState().copy(visiblePeriods = listOf(StatsPeriod.WEEK)))

        // Then — подпись дат окна осталась, сегментов пикера нет
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 0))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_period_week))
            .assertCountEquals(0)
    }

    @Test
    fun stats_when_swiped_right_in_landscape_then_earlier_window_opens_with_its_label() {
        // Given — неделя с тремя окнами, ландшафтная ветка экрана; состояние
        // со смещением 1 — как после поворота экрана (окно не актуальное)
        setContentWith(threeWindowState(windowOffset = 1), landscape = true)

        // Then — стартовый guard сценария «Поворот сохраняет состояние»
        // (частичное компонентное покрытие без recreate; полный поворот —
        // ручная проверка tasks 5.3, activity-интеграция — kotlin-ui-testing
        // правило 12): окно НЕ сброшено на актуальное — подпись и легенда
        // окна 1, подписи актуального окна нет
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(windowLabel(StatsPeriod.WEEK, offset = 0))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(cardioLabel)
            .assertIsDisplayed()

        // When — свайп строки контента вправо (палец вправо — в прошлое, D4):
        // от окна 1 к более раннему окну 2
        composeTestRule.onRoot().performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        // Then — открылось окно смещения 2: подпись сдвинулась за окном,
        // легенда показывает данные окна 2 (в ландшафте легенда — колонка
        // строки контента, видна без вертикального скролла)
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 2))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
    }

    @Test
    fun stats_when_swiped_left_in_landscape_from_current_window_then_nothing_changes() {
        // Given — неделя с двумя окнами в ландшафте, актуальное окно —
        // последняя страница
        setContentWith(twoWindowState(), landscape = true)

        // When — свайп влево от актуального окна (более позднего окна нет)
        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        // Then — состояние не изменилось: подпись актуального окна, легенда
        // прежняя
        composeTestRule
            .onNodeWithText(windowLabel(StatsPeriod.WEEK, offset = 0))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(windowLabel(StatsPeriod.WEEK, offset = 1))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()
    }
}
