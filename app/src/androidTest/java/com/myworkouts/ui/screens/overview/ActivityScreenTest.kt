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
import com.myworkouts.ui.state.ActivityUiState
import com.myworkouts.ui.state.StreakCardState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные UI-тесты состояний секции серии [ActivityScreen] без
 * ViewModel (overview-screen, tasks 10.3; образец — `StatsScreenTest`;
 * логика маппинга покрыта JVM-тестами `OverviewViewModelTest`).
 *
 * Проверяют: Active — карточка со счётчиком; Pending — карточка с
 * «N дней подряд» и подписью рекорда; Pending + серия 1 — «1 день»;
 * Lost + рекорд > 1 — строка «Рекорд серии: …» вместо карточки;
 * Lost + рекорд <= 1 — секция серии отсутствует.
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
}
