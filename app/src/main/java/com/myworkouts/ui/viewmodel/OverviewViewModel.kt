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
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.RecordsMode
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.domain.usecase.computeCategoryStats
import com.myworkouts.domain.usecase.computeExerciseRecords
import com.myworkouts.domain.usecase.computeHeatmapLevels
import com.myworkouts.domain.usecase.computeStreaks
import com.myworkouts.ui.state.ActivityUiState
import com.myworkouts.ui.state.OverviewUiState
import com.myworkouts.ui.state.RecordsUiState
import com.myworkouts.ui.state.StatsUiState
import com.myworkouts.ui.state.StreakCardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Пользовательские состояния вкладок обзора (overview-screen, design D9):
 * группировка четырёх `MutableStateFlow` в один data class — transform
 * внешнего `combine` остаётся четырёхпараметричным (обход detekt
 * `LongParameterList`). Не персистятся — переживают поворот через VM.
 *
 * @property statsPeriod Период доната/легенды «Статистики»
 * @property selectedCategory Категория drill-down; `null` — обзор
 * @property recordsMode Режим значения рекордов
 * @property activityPeriod Окно хитмапа
 */
private data class OverviewUserState(
    val statsPeriod: StatsPeriod = StatsPeriod.WEEK,
    val selectedCategory: ExerciseCategory? = null,
    val recordsMode: RecordsMode = RecordsMode.PER_SET,
    val activityPeriod: ActivityPeriod = ActivityPeriod.MONTH
)

/**
 * ViewModel экрана обзора (overview-screen, design D9): внешний `combine`
 * четырёх потоков — видимость вкладок, сохранённая вкладка, записи с
 * данными упражнений и [OverviewUserState] — в единый [OverviewUiState].
 * Валидация вкладки: сохранённая, если видима, иначе первая видимая
 * (spec «Сохранённая вкладка скрыта настройками»).
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

    /** Сегодняшний день расчётов (системная зона — внутри калькуляторов). */
    private val today: LocalDate = LocalDate.now()

    /**
     * Единое состояние экрана: [OverviewUiState] со всеми тремя вкладками
     * (пересчёт на каждую эмиссию — расчёты дешёвые, design D9).
     */
    val uiState: StateFlow<OverviewUiState> =
        combine(
            dataStore.overviewTabVisibility,
            dataStore.lastOverviewTab,
            repository.entriesWithExercise(),
            userState
        ) { visibility, savedTab, entries, user ->
            val visibleTabs = OverviewTab.entries.filter { visibility[it] ?: true }
            val selectedTab =
                if (savedTab in visibleTabs) {
                    savedTab
                } else {
                    visibleTabs.firstOrNull() ?: OverviewTab.STATS
                }
            val stats = computeCategoryStats(entries, user.statsPeriod, today)
            val streaks = computeStreaks(entries, today)
            OverviewUiState(
                selectedTab = selectedTab,
                visibleTabs = visibleTabs,
                stats =
                    StatsUiState(
                        period = user.statsPeriod,
                        selected = user.selectedCategory,
                        categoryTotals = stats.categoryTotals,
                        exerciseTotals = stats.exerciseTotals,
                        drillGroups = stats.drillGroups,
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
                        period = user.activityPeriod,
                        heatmap = computeHeatmapLevels(entries, user.activityPeriod, today)
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
     * Выбирает категорию drill-down (`null` — выход): аналитика — имя
     * категории или `back` (design D11).
     */
    fun selectStatsCategory(category: ExerciseCategory?) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(
                UserActionType.SELECT_STATS_CATEGORY,
                category?.name?.lowercase() ?: "back"
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
