package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.RecordsMode
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.domain.usecase.computeCategoryStats
import com.myworkouts.domain.usecase.computeExerciseRecords
import com.myworkouts.domain.usecase.computeHeatmapLevels
import com.myworkouts.domain.usecase.computeStreaks
import com.myworkouts.domain.usecase.entryDay
import com.myworkouts.domain.usecase.visibleActivityPeriods
import com.myworkouts.domain.usecase.visibleStatsPeriods
import com.myworkouts.ui.state.ActivityUiState
import com.myworkouts.ui.state.OverviewUiState
import com.myworkouts.ui.state.RecordsUiState
import com.myworkouts.ui.state.StatsUiState
import com.myworkouts.ui.state.StreakCardState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Пользовательские состояния вкладок обзора (overview-screen, design D9):
 * группировка четырёх `MutableStateFlow` в один data class — transform
 * внешнего `combine` остаётся четырёхпараметричным (обход detekt
 * `LongParameterList`). Не персистятся — переживают поворот через VM.
 *
 * @property statsPeriod Период доната/легенды «Статистики»
 * @property selectedCategory Имя категории drill-down; `null` — обзор
 * @property recordsMode Режим значения рекордов
 * @property activityPeriod Окно хитмапа
 */
private data class OverviewUserState(
    val statsPeriod: StatsPeriod = StatsPeriod.WEEK,
    val selectedCategory: String? = null,
    val recordsMode: RecordsMode = RecordsMode.PER_SET,
    val activityPeriod: ActivityPeriod = ActivityPeriod.MONTH
)

/**
 * ViewModel экрана обзора (overview-screen, design D9): внешний `combine`
 * пяти потоков — видимость вкладок, сохранённая вкладка, записи с
 * данными упражнений, цвета категорий и [OverviewUserState] — в единый
 * [OverviewUiState]. Валидация вкладки: сохранённая, если видима, иначе
 * первая видимая (spec «Сохранённая вкладка скрыта настройками»).
 *
 * @property repository Репозиторий данных тренировок
 * @property dataStore DataStore настроек приложения
 * @property analyticsProvider Провайдер аналитики
 */
class OverviewViewModel(
    private val repository: WorkoutDataRepository,
    private val dataStore: AppSettingsDataStore,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    companion object {
        /**
         * Factory для создания [OverviewViewModel]. Ручной DI вместо Hilt.
         *
         * @param repository Репозиторий данных тренировок
         * @param dataStore DataStore настроек приложения
         * @param analyticsProvider Провайдер аналитики
         * @return Factory для создания ViewModel
         */
        fun factory(
            repository: WorkoutDataRepository,
            dataStore: AppSettingsDataStore,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { OverviewViewModel(repository, dataStore, analyticsProvider) }
            }
    }

    /** Пользовательские состояния трёх вкладок (см. [OverviewUserState]). */
    private val userState = MutableStateFlow(OverviewUserState())

    /** Сегодняшний день расчётов (системная зона — см. [combine]). */
    private val today: LocalDate = LocalDate.now()

    /**
     * Имя категории → `colorArgb` (custom-categories design Decision 10):
     * объединение активных и архивных категорий — архивная остаётся
     * в статистике, цвет нужен и ей. Отсутствующее имя (упражнение
     * ссылается на категорию мимо каталога) — UI берёт нейтральный цвет.
     */
    private val categoryColors: Flow<Map<String, Int>> =
        combine(
            repository.categories(),
            repository.archivedCategories()
        ) { active, archived ->
            (active + archived).associate { it.name to it.colorArgb }
        }

    /**
     * Единое состояние экрана: [OverviewUiState] со всеми тремя вкладками
     * (пересчёт на каждую эмиссию — расчёты дешёвые, design D9).
     */
    val uiState: StateFlow<OverviewUiState> =
        combine(
            dataStore.overviewTabVisibility,
            dataStore.lastOverviewTab,
            repository.entriesWithExercise(),
            categoryColors,
            userState
        ) { visibility, savedTab, entries, colors, user ->
            val visibleTabs = OverviewTab.entries.filter { visibility[it] ?: true }
            val selectedTab =
                if (savedTab in visibleTabs) {
                    savedTab
                } else {
                    visibleTabs.firstOrNull() ?: OverviewTab.STATS
                }
            // Самая ранняя тренировка — минимальный календарный день записей
            // (overview-periods-and-shares, design D2); проход minOf по уже
            // загруженному списку, без новых DAO-запросов.
            val earliest = entries.minOfOrNull { entryDay(it, ZoneId.systemDefault()) }
            val visibleStats = visibleStatsPeriods(earliest, today)
            val visibleActivity = visibleActivityPeriods(earliest, today)
            val statsPeriod =
                clampPeriod(user.statsPeriod, visibleStats, earliest != null)
            val activityPeriod =
                clampPeriod(user.activityPeriod, visibleActivity, earliest != null)
            val stats = computeCategoryStats(entries, statsPeriod, today)
            val streaks = computeStreaks(entries, today)
            OverviewUiState(
                selectedTab = selectedTab,
                visibleTabs = visibleTabs,
                stats =
                    StatsUiState(
                        period = statsPeriod,
                        visiblePeriods = visibleStats,
                        selected = user.selectedCategory,
                        categoryTotals = stats.categoryTotals,
                        exerciseTotals = stats.exerciseTotals,
                        drillGroups = stats.drillGroups,
                        categoryColors = colors,
                        isEmpty = stats.drillGroups.isEmpty()
                    ),
                records =
                    RecordsUiState(
                        mode = user.recordsMode,
                        records = computeExerciseRecords(entries, user.recordsMode)
                    ),
                activity =
                    ActivityUiState(
                        streakDays = streaks.currentStreak,
                        recordStreakDays = streaks.recordStreak,
                        streakState =
                            when {
                                streaks.currentStreak == 0 -> StreakCardState.Lost
                                streaks.workedOutToday -> StreakCardState.Active
                                else -> StreakCardState.Pending
                            },
                        period = activityPeriod,
                        visiblePeriods = visibleActivity,
                        heatmap = computeHeatmapLevels(entries, activityPeriod, today)
                    )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
            initialValue = OverviewUiState()
        )

    /**
     * Выбирает вкладку: аналитика (`stats|records|activity`, design D11),
     * затем запись в DataStore — восстановление в т.ч. после пересоздания
     * процесса (design D8).
     */
    fun selectTab(tab: OverviewTab) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.SELECT_OVERVIEW_TAB, tab.name.lowercase())
        )
        viewModelScope.launch { dataStore.setLastOverviewTab(tab) }
    }

    /**
     * Меняет период «Статистики»: drill-down сбрасывается (spec), аналитика
     * (`day|week|month|year`, design D11).
     */
    fun selectStatsPeriod(period: StatsPeriod) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.SELECT_STATS_PERIOD, period.name.lowercase())
        )
        userState.update { it.copy(statsPeriod = period, selectedCategory = null) }
    }

    /**
     * Выбирает категорию drill-down по имени (`null` — выход): аналитика —
     * имя категории или `back` (design D11).
     */
    fun selectStatsCategory(category: String?) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(
                UserActionType.SELECT_STATS_CATEGORY,
                category?.lowercase() ?: "back"
            )
        )
        userState.update { it.copy(selectedCategory = category) }
    }

    /** Меняет режим рекордов (аналитика `per_set|per_day`, design D11). */
    fun selectRecordsMode(mode: RecordsMode) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.SELECT_RECORDS_MODE, mode.name.lowercase())
        )
        userState.update { it.copy(recordsMode = mode) }
    }

    /** Меняет окно хитмапа (аналитика `month|quarter|halfyear|year`, design D11). */
    fun selectActivityPeriod(period: ActivityPeriod) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.SELECT_ACTIVITY_PERIOD, period.name.lowercase())
        )
        userState.update { it.copy(activityPeriod = period) }
    }
}

/** Выбранный период скрылся — самый длинный видимый, иначе выбор без изменений. */
private fun <T> clampPeriod(
    selected: T,
    visible: List<T>,
    hasData: Boolean
): T = if (!hasData || selected in visible) selected else visible.last()
