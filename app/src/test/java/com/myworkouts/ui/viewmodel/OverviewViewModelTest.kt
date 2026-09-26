package com.myworkouts.ui.viewmodel

import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.RecordsMode
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.ui.state.StreakCardState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit-тесты для [OverviewViewModel] (overview-screen, §4.3):
 * восстановление вкладки, fallback при скрытой сохранённой, запись
 * пользовательских состояний и пересчёт при новой эмиссии записей.
 *
 * Репозиторий и DataStore — `mockk` + stub Flow на `MutableStateFlow`
 * (паттерн [EntrySettingsViewModelTest]); аналитика —
 * [FakeAnalyticsProvider].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {
    private lateinit var mockRepository: WorkoutDataRepository
    private lateinit var mockDataStore: AppSettingsDataStore
    private val fakeAnalytics = FakeAnalyticsProvider()
    private lateinit var testDispatcher: TestDispatcher

    /** Stub Flow записей с данными упражнений, управляемый тестом. */
    private lateinit var entriesFlow: MutableStateFlow<List<EntryWithExercise>>

    /** Stub Flow видимости вкладок. */
    private lateinit var visibilityFlow: MutableStateFlow<Map<OverviewTab, Boolean>>

    /** Stub Flow последней вкладки. */
    private lateinit var lastTabFlow: MutableStateFlow<OverviewTab>

    @BeforeEach
    fun setUp() {
        mockRepository = mockk(relaxed = true)
        mockDataStore = mockk(relaxed = true)
        entriesFlow = MutableStateFlow(emptyList())
        visibilityFlow = MutableStateFlow(OverviewTab.entries.associateWith { true })
        lastTabFlow = MutableStateFlow(OverviewTab.STATS)
        every { mockRepository.entriesWithExercise() } returns entriesFlow
        every { mockDataStore.overviewTabVisibility } returns visibilityFlow
        every { mockDataStore.lastOverviewTab } returns lastTabFlow
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** VM с подпиской на uiState через backgroundScope (запускает WhileSubscribed). */
    private fun createViewModel(): OverviewViewModel = OverviewViewModel(mockRepository, mockDataStore, fakeAnalytics)

    @Test
    fun ui_state_when_default_tab_then_stats() =
        runTest(testDispatcher) {
            // Given/When
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertEquals(
                OverviewTab.STATS,
                viewModel.uiState.value.selectedTab,
                "Дефолт (нет сохранённой вкладки) — «Статистика»"
            )
            assertEquals(
                OverviewTab.entries.toList(),
                viewModel.uiState.value.visibleTabs,
                "По умолчанию видны все вкладки"
            )
        }

    @Test
    fun ui_state_when_saved_tab_records_then_restored() =
        runTest(testDispatcher) {
            // Given — сохранённая вкладка «Рекорды»
            lastTabFlow.value = OverviewTab.RECORDS
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertEquals(
                OverviewTab.RECORDS,
                viewModel.uiState.value.selectedTab,
                "Сохранённая вкладка восстанавливается из preferences"
            )
        }

    @Test
    fun ui_state_when_saved_tab_hidden_then_fallback_first_visible() =
        runTest(testDispatcher) {
            // Given — сохранена «Активность», но она скрыта настройками
            // (spec «Сохранённая вкладка скрыта настройками»)
            lastTabFlow.value = OverviewTab.ACTIVITY
            visibilityFlow.value =
                OverviewTab.entries.associateWith { true } +
                mapOf(OverviewTab.ACTIVITY to false)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — первая видимая (в порядке enum)
            assertEquals(
                OverviewTab.STATS,
                viewModel.uiState.value.selectedTab,
                "Скрытая сохранённая вкладка — fallback на первую видимую"
            )
        }

    @Test
    fun selectTab_then_writes_datastore_and_logs() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.selectTab(OverviewTab.RECORDS)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockDataStore.setLastOverviewTab(OverviewTab.RECORDS) }
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.SELECT_OVERVIEW_TAB, "records")),
                fakeAnalytics.events,
                "selectTab логирует UserAction(SELECT_OVERVIEW_TAB, records)"
            )
        }

    @Test
    fun selectStatsPeriod_then_resets_drilldown_and_logs() =
        runTest(testDispatcher) {
            // Given — выбрана категория drill-down
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.selectStatsCategory(ExerciseCategory.PUSH)
            advanceUntilIdle()

            // When
            viewModel.selectStatsPeriod(StatsPeriod.MONTH)
            advanceUntilIdle()

            // Then — drill-down сброшен, период сменён, событие залогировано
            val stats = viewModel.uiState.value.stats
            assertEquals(StatsPeriod.MONTH, stats.period, "Период сменён")
            assertEquals(null, stats.selected, "Смена периода сбрасывает drill-down")
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(UserActionType.SELECT_STATS_CATEGORY, "push"),
                    AnalyticsEvent.UserAction(UserActionType.SELECT_STATS_PERIOD, "month")
                ),
                fakeAnalytics.events,
                "Смена периода логирует UserAction(SELECT_STATS_PERIOD, month)"
            )
        }

    @Test
    fun selectStatsCategory_then_back_logs_category_name_and_back() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.selectStatsCategory(ExerciseCategory.LEGS)
            advanceUntilIdle()

            // Then — выбранная категория в состоянии статистики
            assertEquals(
                ExerciseCategory.LEGS,
                viewModel.uiState.value.stats.selected,
                "Выбранная категория в состоянии статистики"
            )

            // When — выход из drill-down
            viewModel.selectStatsCategory(null)
            advanceUntilIdle()

            // Then — события по порядку: имя категории, затем back
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(UserActionType.SELECT_STATS_CATEGORY, "legs"),
                    AnalyticsEvent.UserAction(UserActionType.SELECT_STATS_CATEGORY, "back")
                ),
                fakeAnalytics.events,
                "Выбор категории логирует имя, выход — back"
            )
            assertEquals(
                null,
                viewModel.uiState.value.stats.selected,
                "Выход из drill-down сбрасывает категорию"
            )
        }

    @Test
    fun selectRecordsMode_then_logs() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.selectRecordsMode(RecordsMode.PER_DAY)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.SELECT_RECORDS_MODE, "per_day")),
                fakeAnalytics.events,
                "Смена режима логирует UserAction(SELECT_RECORDS_MODE, per_day)"
            )
            assertEquals(
                RecordsMode.PER_DAY,
                viewModel.uiState.value.records.mode,
                "Режим рекордов в состоянии"
            )
        }

    @Test
    fun selectActivityPeriod_then_logs() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.selectActivityPeriod(ActivityPeriod.YEAR)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.SELECT_ACTIVITY_PERIOD, "year")),
                fakeAnalytics.events,
                "Смена периода логирует UserAction(SELECT_ACTIVITY_PERIOD, year)"
            )
            assertEquals(
                ActivityPeriod.YEAR,
                viewModel.uiState.value.activity.period,
                "Период хитмапа в состоянии"
            )
        }

    @Test
    fun ui_state_when_entries_emitted_then_all_tabs_recalculated() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — запись подтягиваний (REPS) сегодня
            entriesFlow.value = listOf(pullUpsEntry(daysAgo = 0, sets = 3, reps = 10))
            advanceUntilIdle()

            // Then — все три вкладки пересчитаны
            val state = viewModel.uiState.value
            val stats = state.stats
            assertEquals(
                listOf(ExerciseCategory.PULL to 3),
                stats.categoryTotals.map { it.category to it.totalSets },
                "Донат: подтягивания, 3 подхода"
            )
            assertEquals(false, stats.isEmpty, "Есть данные — пустое состояние снято")
            assertEquals(
                1,
                state.records.records.size,
                "Рекорды: одна строка по упражнению"
            )
            assertEquals(
                1,
                state.activity.streakDays,
                "Серия: запись сегодня — 1 день"
            )
            assertEquals(
                1,
                state.activity.heatmap
                    .last()
                    .count,
                "Хитмап: последняя ячейка (сегодня) — 1 запись"
            )
        }

    @Test
    fun ui_state_when_only_time_entries_then_not_empty_and_category_in_donut() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — только TIME-записи (планка) за период
            entriesFlow.value = listOf(plankEntry(daysAgo = 0, sets = 3, durationSec = 90))
            advanceUntilIdle()

            // Then — пустое состояние снято, категория в донате по подходам
            val stats = viewModel.uiState.value.stats
            assertEquals(false, stats.isEmpty, "TIME-записи за период — не пустое состояние")
            assertEquals(
                listOf(ExerciseCategory.CORE to 3),
                stats.categoryTotals.map { it.category to it.totalSets },
                "TIME-категория в донате, доля по подходам"
            )
        }

    @Test
    fun ui_state_when_no_entries_then_stats_empty() =
        runTest(testDispatcher) {
            // Given — нет записей ни одного типа
            entriesFlow.value = emptyList()
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertEquals(
                true,
                viewModel.uiState.value.stats.isEmpty,
                "Нет записей ни с повторами, ни со временем — пустой период"
            )
        }

    @Test
    fun ui_state_when_period_day_then_yesterday_entry_out_of_stats() =
        runTest(testDispatcher) {
            // Given — запись вчера
            entriesFlow.value = listOf(pullUpsEntry(daysAgo = 1, sets = 2, reps = 8))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.selectStatsPeriod(StatsPeriod.DAY)
            advanceUntilIdle()

            // Then — окно «День» не содержит вчерашнюю запись
            val stats = viewModel.uiState.value.stats
            assertEquals(true, stats.isEmpty, "Вчерашняя запись вне периода «День»")
            assertEquals(
                emptyList<ExerciseCategory>(),
                stats.categoryTotals.map { it.category },
                "Донат пуст за сегодня"
            )
        }

    @Test
    fun ui_state_when_entry_today_then_streak_state_active() =
        runTest(testDispatcher) {
            // Given — запись сегодня
            entriesFlow.value = listOf(pullUpsEntry(daysAgo = 0, sets = 3, reps = 10))

            // When
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertEquals(
                StreakCardState.Active,
                viewModel.uiState.value.activity.streakState,
                "Тренировка сегодня — карточка Active"
            )
        }

    @Test
    fun ui_state_when_entry_yesterday_then_streak_state_pending() =
        runTest(testDispatcher) {
            // Given — последняя запись вчера, сегодня записей нет
            entriesFlow.value = listOf(pullUpsEntry(daysAgo = 1, sets = 3, reps = 10))

            // When
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — серия жива, но сегодня ещё не тренировались
            assertEquals(
                StreakCardState.Pending,
                viewModel.uiState.value.activity.streakState,
                "Серия жива без сегодняшней записи — карточка Pending"
            )
        }

    @Test
    fun ui_state_when_entry_two_days_ago_then_streak_state_lost() =
        runTest(testDispatcher) {
            // Given — последняя запись позавчера, серия прервана
            entriesFlow.value = listOf(pullUpsEntry(daysAgo = 2, sets = 3, reps = 10))

            // When
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertEquals(
                StreakCardState.Lost,
                viewModel.uiState.value.activity.streakState,
                "Пропуск вчерашнего дня — карточка Lost"
            )
        }

    @Test
    fun ui_state_when_entries_spanning_ten_days_then_visible_periods_from_earliest() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — записи сегодня и 10 дней назад; минимальный день — «минус 10»
            entriesFlow.value =
                listOf(
                    workoutEntry(id = 11L, daysAgo = 0),
                    workoutEntry(id = 12L, daysAgo = 10)
                )
            advanceUntilIdle()

            // Then — видимость по самой ранней (минимальной) дате, а не по первой записи
            // (spec-сценарий «Видимость периодов по глубине истории»)
            assertEquals(
                listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH),
                viewModel.uiState.value.stats.visiblePeriods,
                "Минус 10 дней — «День», «Неделя», «Месяц»; «Год» скрыт"
            )
            assertEquals(
                listOf(ActivityPeriod.MONTH),
                viewModel.uiState.value.activity.visiblePeriods,
                "Минус 10 дней — у хитмапа виден только «Месяц»"
            )
        }

    @Test
    fun ui_state_when_no_entries_then_visible_periods_degrade_to_base() =
        runTest(testDispatcher) {
            // Given — пустой список (защитный случай, вкладка обзора недостижима)
            entriesFlow.value = emptyList()
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — только базовые периоды (spec «защитный случай»)
            assertEquals(
                listOf(StatsPeriod.DAY),
                viewModel.uiState.value.stats.visiblePeriods,
                "Нет записей — у статистики виден только «День»"
            )
            assertEquals(
                listOf(ActivityPeriod.MONTH),
                viewModel.uiState.value.activity.visiblePeriods,
                "Нет записей — у хитмапа виден только «Месяц»"
            )
        }

    @Test
    fun ui_state_when_default_week_and_first_workout_today_then_clamped_to_day() =
        runTest(testDispatcher) {
            // Given — первая тренировка сегодня, дефолтный период «Неделя»
            entriesFlow.value = listOf(workoutEntry(id = 21L, daysAgo = 0))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — «Неделя» скрыта, выбран самый длинный видимый «День»
            // (spec «Только сегодняшняя история — пикер скрыт»); подмена без
            // явного выбора пользователя аналитики не отправляет
            val stats = viewModel.uiState.value.stats
            assertEquals(
                listOf(StatsPeriod.DAY),
                stats.visiblePeriods,
                "Только сегодняшняя история — виден только «День»"
            )
            assertEquals(
                StatsPeriod.DAY,
                stats.period,
                "Дефолтная «Неделя» подменена самым длинным видимым «Днём»"
            )
            assertEquals(
                true,
                fakeAnalytics.events.isEmpty(),
                "Clamp дефолта — без событий SELECT_STATS_PERIOD"
            )
        }

    @Test
    fun selectStatsPeriod_when_year_hidden_after_deletion_then_clamped_to_month() =
        runTest(testDispatcher) {
            // Given — история глубиной 100 дней, явно выбран «Год»
            entriesFlow.value =
                listOf(
                    workoutEntry(id = 31L, daysAgo = 40),
                    workoutEntry(id = 32L, daysAgo = 100)
                )
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.selectStatsPeriod(StatsPeriod.YEAR)
            advanceUntilIdle()
            assertEquals(
                StatsPeriod.YEAR,
                viewModel.uiState.value.stats.period,
                "Год виден — явный выбор применён"
            )

            // When — удаление старых записей: самая ранняя осталась «минус 15 дней»
            entriesFlow.value = listOf(workoutEntry(id = 33L, daysAgo = 15))
            advanceUntilIdle()

            // Then — «Год» скрыт, подмена самым длинным видимым
            // (spec-сценарий «Clamp выбранного периода»); аналитика —
            // только явный выбор пользователя
            val stats = viewModel.uiState.value.stats
            assertEquals(
                listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH),
                stats.visiblePeriods,
                "Минус 15 дней — «Год» скрыт без перезапуска приложения"
            )
            assertEquals(
                StatsPeriod.MONTH,
                stats.period,
                "Выбранный «Год» подменён самым длинным видимым «Месяцем»"
            )
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.SELECT_STATS_PERIOD, "year")),
                fakeAnalytics.events,
                "Подмена не отправляет SELECT_STATS_PERIOD"
            )
        }

    @Test
    fun selectActivityPeriod_when_year_hidden_after_deletion_then_clamped_to_halfyear() =
        runTest(testDispatcher) {
            // Given — история глубиной 200 дней, явно выбран «Год» хитмапа
            entriesFlow.value = listOf(workoutEntry(id = 41L, daysAgo = 200))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.selectActivityPeriod(ActivityPeriod.YEAR)
            advanceUntilIdle()
            assertEquals(
                ActivityPeriod.YEAR,
                viewModel.uiState.value.activity.period,
                "Год виден — явный выбор применён"
            )

            // When — удаление записей старше 182 дней: осталась «минус 100 дней»
            entriesFlow.value = listOf(workoutEntry(id = 42L, daysAgo = 100))
            advanceUntilIdle()

            // Then — видны «Месяц», «Квартал», «Полгода», выбранным становится
            // «Полгода» (spec-сценарий «Clamp выбранного периода хитмапа»);
            // аналитика — только явный выбор пользователя
            val activity = viewModel.uiState.value.activity
            assertEquals(
                listOf(ActivityPeriod.MONTH, ActivityPeriod.QUARTER, ActivityPeriod.HALFYEAR),
                activity.visiblePeriods,
                "Минус 100 дней — «Год» хитмапа скрыт"
            )
            assertEquals(
                ActivityPeriod.HALFYEAR,
                activity.period,
                "Выбранный «Год» подменён самым длинным видимым «Полугодом»"
            )
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.SELECT_ACTIVITY_PERIOD, "year")),
                fakeAnalytics.events,
                "Подмена не отправляет SELECT_ACTIVITY_PERIOD"
            )
        }

    /** Запись подтягиваний (PULL, REPS, 3x10) `daysAgo` дней назад в 18:30. */
    private fun workoutEntry(
        id: Long,
        daysAgo: Long
    ): EntryWithExercise {
        val date =
            LocalDate
                .now()
                .minusDays(daysAgo)
                .atTime(18, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        return EntryWithExercise(
            id = id,
            exerciseId = 2L,
            exerciseName = "Подтягивания",
            category = ExerciseCategory.PULL,
            measuredBy = ExerciseMeasuredBy.REPS,
            sets = 3,
            reps = 10,
            durationSec = null,
            date = date
        )
    }

    /** Запись подтягиваний (PULL, REPS) [daysAgo] дней назад в 18:30. */
    private fun pullUpsEntry(
        daysAgo: Long,
        sets: Int,
        reps: Int
    ): EntryWithExercise {
        val date =
            LocalDate
                .now()
                .minusDays(daysAgo)
                .atTime(18, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        return EntryWithExercise(
            id = 1L,
            exerciseId = 2L,
            exerciseName = "Подтягивания",
            category = ExerciseCategory.PULL,
            measuredBy = ExerciseMeasuredBy.REPS,
            sets = sets,
            reps = reps,
            durationSec = null,
            date = date
        )
    }

    /** TIME-запись планки (CORE) `daysAgo` дней назад. */
    private fun plankEntry(
        daysAgo: Long,
        sets: Int,
        durationSec: Int
    ): EntryWithExercise {
        val date =
            LocalDate
                .now()
                .minusDays(daysAgo)
                .atTime(18, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        return EntryWithExercise(
            id = 3L,
            exerciseId = 4L,
            exerciseName = "Планка",
            category = ExerciseCategory.CORE,
            measuredBy = ExerciseMeasuredBy.TIME,
            sets = sets,
            reps = null,
            durationSec = durationSec,
            date = date
        )
    }
}
