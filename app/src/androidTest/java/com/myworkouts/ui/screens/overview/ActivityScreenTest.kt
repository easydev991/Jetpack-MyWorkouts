package com.myworkouts.ui.screens.overview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.HeatmapDayData
import com.myworkouts.ui.state.ActivityUiState
import com.myworkouts.ui.state.StreakCardState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Компонентные UI-тесты состояний секции серии [ActivityScreen] без
 * ViewModel (overview-screen, tasks 10.3; образец — `StatsScreenTest`;
 * логика маппинга покрыта JVM-тестами `OverviewViewModelTest`).
 *
 * Проверяют: Active — карточка со счётчиком; Pending — карточка с
 * «N дней подряд» и подписью рекорда; Pending + серия 1 — «1 день»;
 * Lost + рекорд > 1 — строка «Рекорд серии: …» вместо карточки;
 * Lost + рекорд <= 1 — секция серии отсутствует; один видимый период —
 * заголовок месяцев вместо пикера (записи в одном месяце — заголовок
 * одного месяца, в двух — join разделителем); два видимых —
 * только их сегменты (overview-periods-and-shares tasks 5.3).
 */
@RunWith(AndroidJUnit4::class)
class ActivityScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Счётчик серии «N дней подряд» (ресурс, локаль-независимо). */
    private fun daysInRowText(days: Int): String =
        context.resources.getQuantityString(R.plurals.overview_streak_days_in_a_row, days, days)

    /** Счётчик «N дней» / «1 день» без «подряд» (притязание на карточку из одного дня). */
    private fun daysText(days: Int): String =
        context.resources.getQuantityString(R.plurals.overview_streak_days, days, days)

    /** Подпись рекорда «Рекорд серии: N дней» (как на карточке, так и в строке Lost). */
    private fun recordText(days: Int): String =
        context.getString(R.string.overview_streak_record) + " " + daysText(days)

    private fun setContentWith(
        streakDays: Int,
        recordStreakDays: Int,
        streakState: StreakCardState
    ) {
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                ActivityScreen(
                    state =
                        ActivityUiState(
                            streakDays = streakDays,
                            recordStreakDays = recordStreakDays,
                            streakState = streakState,
                            period = ActivityPeriod.MONTH
                        ),
                    onPeriodChange = {}
                )
            }
        }
    }

    @Test
    fun streak_when_active_then_card_shows_days_in_a_row() {
        // Given — тренировка сегодня, серия 3 из рекорда 3
        setContentWith(streakDays = 3, recordStreakDays = 3, streakState = StreakCardState.Active)

        // Then — карточка со счётчиком, подписи рекорда нет (равенство)
        composeTestRule
            .onNodeWithText(daysInRowText(3))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(recordText(3))
            .assertCountEquals(0)
    }

    @Test
    fun streak_when_pending_then_card_shows_days_in_a_row_and_record() {
        // Given — серия жива (последняя тренировка вчера), рекорд больше
        setContentWith(streakDays = 3, recordStreakDays = 7, streakState = StreakCardState.Pending)

        // Then — приглушённая карточка остаётся: счётчик и подпись рекорда
        composeTestRule
            .onNodeWithText(daysInRowText(3))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(recordText(7))
            .assertIsDisplayed()
    }

    @Test
    fun streak_when_pending_and_streak_one_day_then_card_shows_single_day() {
        // Given — тренировка только вчера (серия 1), рекорд 1
        setContentWith(streakDays = 1, recordStreakDays = 1, streakState = StreakCardState.Pending)

        // Then — «1 день» без «подряд», подписи рекорда нет (равенство)
        composeTestRule
            .onNodeWithText(daysText(1))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(daysInRowText(1))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(recordText(1))
            .assertCountEquals(0)
    }

    @Test
    fun streak_when_lost_and_record_above_one_then_record_row_replaces_card() {
        // Given — серия прервана, рекорд 3
        setContentWith(streakDays = 0, recordStreakDays = 3, streakState = StreakCardState.Lost)

        // Then — строка рекорда вместо карточки, счётчика серии нет
        composeTestRule
            .onNodeWithText(recordText(3))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(daysText(0))
            .assertCountEquals(0)
    }

    @Test
    fun streak_when_lost_and_record_at_most_one_then_section_hidden() {
        // Given — серия прервана, рекорд 1 (одна тренировка вразброс)
        setContentWith(streakDays = 0, recordStreakDays = 1, streakState = StreakCardState.Lost)

        // Then — секции серии нет ни в каком виде
        composeTestRule
            .onAllNodesWithText(recordText(1))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(daysText(0))
            .assertCountEquals(0)
    }

    @Test
    fun activity_when_single_visible_period_then_months_header_replaces_picker() {
        // Given — один видимый период (после clamp), хитмап полугода
        val heatmap = demoHeatmap(ActivityPeriod.HALFYEAR)
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                ActivityScreen(
                    state =
                        ActivityUiState(
                            streakDays = 3,
                            recordStreakDays = 3,
                            streakState = StreakCardState.Active,
                            period = ActivityPeriod.HALFYEAR,
                            heatmap = heatmap,
                            visiblePeriods = listOf(ActivityPeriod.HALFYEAR)
                        ),
                    onPeriodChange = {}
                )
            }
        }

        // Then — сегментов пикера нет, вместо него заголовок месяцев
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_period_month))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(monthsHeaderText(heatmap))
            .assertIsDisplayed()
    }

    @Test
    fun activity_when_two_visible_periods_then_only_visible_segments_shown() {
        // Given — видимые периоды: Месяц и Квартал
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                ActivityScreen(
                    state =
                        ActivityUiState(
                            streakDays = 3,
                            recordStreakDays = 3,
                            streakState = StreakCardState.Active,
                            period = ActivityPeriod.MONTH,
                            heatmap = demoHeatmap(ActivityPeriod.MONTH),
                            visiblePeriods = listOf(ActivityPeriod.MONTH, ActivityPeriod.QUARTER)
                        ),
                    onPeriodChange = {}
                )
            }
        }

        // Then — ровно два сегмента, скрытый «Год» не рендерится
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_period_month))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_period_quarter))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_period_year))
            .assertCountEquals(0)
    }

    @Test
    fun activity_when_entries_in_one_month_then_header_shows_single_month() {
        // Given — записи только в текущем месяце, виден один период
        val today = LocalDate.now()
        val heatmap = heatmapOf((0 until today.dayOfMonth).map { today.minusDays(it.toLong()) })
        setContentMonthsHeader(heatmap = heatmap)

        // Then — заголовок одного месяца (без разделителя), пикера нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_period_month))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(monthsHeaderText(heatmap))
            .assertIsDisplayed()
    }

    @Test
    fun activity_when_entries_in_two_months_then_header_joins_with_separator() {
        // Given — записи в текущем и в предыдущем месяце (ровно два месяца)
        val today = LocalDate.now()
        val heatmap =
            heatmapOf(
                (0 until today.dayOfMonth).map { today.minusDays(it.toLong()) } +
                    (0..5).map { today.withDayOfMonth(1).minusDays(it.toLong()) }
            )
        setContentMonthsHeader(heatmap = heatmap)

        // Then — «Предыдущий – Текущий», разделитель из строкового ресурса
        composeTestRule
            .onNodeWithText(monthsHeaderText(heatmap))
            .assertIsDisplayed()
    }

    /**
     * Содержимое экрана с одним видимым периодом «Месяц»: вместо пикера —
     * заголовок месяцев из [heatmap].
     */
    private fun setContentMonthsHeader(heatmap: List<HeatmapDayData>) {
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                ActivityScreen(
                    state =
                        ActivityUiState(
                            streakDays = 3,
                            recordStreakDays = 3,
                            streakState = StreakCardState.Active,
                            period = ActivityPeriod.MONTH,
                            heatmap = heatmap,
                            visiblePeriods = listOf(ActivityPeriod.MONTH)
                        ),
                    onPeriodChange = {}
                )
            }
        }
    }

    /** Хитмап из дат с ненулевым счётом; даты — по возрастанию (контракт). */
    private fun heatmapOf(dates: List<LocalDate>): List<HeatmapDayData> =
        dates
            .distinct()
            .sorted()
            .map { date -> HeatmapDayData(date = date, count = 1, level = 1) }

    /**
     * Ожидаемый заголовок месяцев — тем же ICU-путём, что и
     * [ActivityScreen] (standalone-названия по локали, первая буква
     * заглавная, разделитель из ресурса).
     */
    private fun monthsHeaderText(heatmap: List<HeatmapDayData>): String {
        val locale = context.resources.configuration.locales[0]
        val monthFormat = DateTimeFormatter.ofPattern("LLLL", locale)
        return heatmap
            .asSequence()
            .filter { it.count > 0 }
            .map { YearMonth.from(it.date) }
            .distinct()
            .sorted()
            .joinToString(separator = context.getString(R.string.overview_months_separator)) { month ->
                monthFormat
                    .format(month)
                    .replaceFirstChar { it.uppercase(locale) }
            }
    }
}
