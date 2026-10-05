package com.myworkouts.ui.screens.overview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.data.db.standardCategories
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.usecase.CategoryTotal
import com.myworkouts.domain.usecase.DrillDateGroup
import com.myworkouts.domain.usecase.ExerciseTotal
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.ui.state.StatsUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

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
 * overview-periods-and-shares tasks 5.2).
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

    /** Непустая «Статистика» недели: PUSH 3 подхода, запись сегодня. */
    private fun weekState(selected: String? = null): StatsUiState {
        val today = LocalDate.now()
        return StatsUiState(
            period = StatsPeriod.WEEK,
            selected = selected,
            categoryTotals =
                listOf(
                    CategoryTotal(
                        category = "PUSH",
                        totalSets = 3
                    )
                ),
            exerciseTotals =
                mapOf(
                    "PUSH" to
                        listOf(ExerciseTotal("Отжимания", totalSets = 3))
                ),
            drillGroups =
                mapOf(
                    "PUSH" to
                        listOf(
                            DrillDateGroup(
                                date = today,
                                entries =
                                    listOf(
                                        EntryWithExercise(
                                            id = 1L,
                                            exerciseId = 2L,
                                            exerciseName = "Отжимания",
                                            iconNumber = null,
                                            category = "PUSH",
                                            measuredBy = ExerciseMeasuredBy.REPS,
                                            exerciseComment = null,
                                            sets = 1,
                                            reps = 15,
                                            durationSec = null,
                                            weightKg = null,
                                            comment = null,
                                            date = today.toEpochDay() * 86_400_000L
                                        )
                                    )
                            )
                        )
                ),
            categoryColors = categoryColors,
            isEmpty = false
        )
    }

    /** Только-TIME «Статистика» недели: CARDIO (бег), запись сегодня. */
    private fun timeState(selected: String? = null): StatsUiState {
        val today = LocalDate.now()
        return StatsUiState(
            period = StatsPeriod.WEEK,
            selected = selected,
            categoryTotals =
                listOf(
                    CategoryTotal(
                        category = "CARDIO",
                        totalSets = 2
                    )
                ),
            exerciseTotals =
                mapOf(
                    "CARDIO" to
                        listOf(ExerciseTotal("Бег", totalSets = 2))
                ),
            drillGroups =
                mapOf(
                    "CARDIO" to
                        listOf(
                            DrillDateGroup(
                                date = today,
                                entries =
                                    listOf(
                                        EntryWithExercise(
                                            id = 1L,
                                            exerciseId = 3L,
                                            exerciseName = "Бег",
                                            iconNumber = null,
                                            category = "CARDIO",
                                            measuredBy = ExerciseMeasuredBy.TIME,
                                            exerciseComment = null,
                                            sets = 2,
                                            reps = null,
                                            durationSec = 1800,
                                            weightKg = null,
                                            comment = null,
                                            date = today.toEpochDay() * 86_400_000L
                                        )
                                    )
                            )
                        )
                ),
            categoryColors = categoryColors,
            isEmpty = false
        )
    }

    /**
     * Рендер с управляемым состоянием: колбэки мутат [state] так, как это
     * делает [com.myworkouts.ui.viewmodel.OverviewViewModel] (смена периода
     * сбрасывает drill-down), — вёрстка проверяется в изоляции.
     */
    private fun setContentWith(state: StatsUiState) {
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                // remember обязателен: без него рекомпозиция пересоздаёт
                // State с исходным значением и затирает результат клика
                var current by remember { mutableStateOf(state) }
                StatsScreen(
                    state = current,
                    onPeriodChange = { period ->
                        current = current.copy(period = period, selected = null)
                    },
                    onCategoryClick = { category ->
                        current = current.copy(selected = category)
                    }
                )
            }
        }
    }

    /** Скроллит LazyColumn к ноде с текстом [text] (на компактном AVD
     * донат и пикер вытесняют drill-down/легенду за границу вьюпорта). */
    private fun scrollTo(text: String) {
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(text))
        composeTestRule.waitForIdle()
    }

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
        composeTestRule
            .onNodeWithText(pushLabel)
            .assertIsDisplayed()

        // When — тап по категории «Толкающие»
        composeTestRule
            .onNodeWithText(pushLabel)
            .performClick()
        composeTestRule.waitForIdle()

        // Then — drill-down открыт: строка возврата (скролл — на компактном
        // AVD она за нижней границей из-за пикера и доната) и запись
        scrollTo(context.getString(R.string.overview_back_to_categories))
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_back_to_categories))
            .assertIsDisplayed()

        // Then — «Отжимания» дважды: строка легенды упражнений и запись
        // drill-down списка; строка легенды — процент единственной строки
        composeTestRule
            .onAllNodesWithText("Отжимания")
            .assertCountEquals(2)
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
        composeTestRule
            .onNodeWithText(cardioLabel)
            .assertIsDisplayed()
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
}
