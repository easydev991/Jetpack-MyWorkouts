package com.myworkouts.ui.state

import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.CategoryTotal
import com.myworkouts.domain.usecase.DrillDateGroup
import com.myworkouts.domain.usecase.ExerciseRecordData
import com.myworkouts.domain.usecase.ExerciseTotal
import com.myworkouts.domain.usecase.HeatmapDayData
import com.myworkouts.domain.usecase.RecordsMode
import com.myworkouts.domain.usecase.StatsPeriod

/**
 * UiState вкладки «Статистика» экрана обзора (overview-screen, design
 * D9). Доменные типы расчётов ([CategoryTotal], [ExerciseTotal],
 * [DrillDateGroup]) переиспользуются как есть — в них уже есть нужные для
 * LazyColumn-ключей идентификаторы; `selected` — доменный
 * [ExerciseCategory] (драфтовый `StatsCategory` удалён).
 *
 * @property period Календарный период доната и легенды
 * @property visiblePeriods Видимые периоды пикера по дате самой ранней
 *  тренировки (overview-periods-and-shares, design D3); дефолт — все
 *  периоды, боевые значения приходят из [com.myworkouts.ui.viewmodel.OverviewViewModel]
 * @property selected Выбранная категория drill-down; `null` — обзор по категориям
 * @property categoryTotals Категории с REPS-объёмом > 0, убывание
 * @property exerciseTotals Суммы упражнений по категориям (drill-down легенда)
 * @property drillGroups Записи категорий по датам (drill-down списки)
 * @property isEmpty Нет записей за период ни одного типа измерения — пустое состояние
 */
data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val visiblePeriods: List<StatsPeriod> = StatsPeriod.entries.toList(),
    val selected: ExerciseCategory? = null,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val exerciseTotals: Map<ExerciseCategory, List<ExerciseTotal>> = emptyMap(),
    val drillGroups: Map<ExerciseCategory, List<DrillDateGroup>> = emptyMap(),
    val isEmpty: Boolean = true
)

/**
 * UiState вкладки «Рекорды» (design D9); строки — доменные
 * [ExerciseRecordData].
 *
 * @property mode Режим значения: подход или сутки
 * @property records Лучший результат каждого упражнения, дата убывание
 */
data class RecordsUiState(
    val mode: RecordsMode = RecordsMode.PER_SET,
    val records: List<ExerciseRecordData> = emptyList()
)

/**
 * UiState вкладки «Активность» (design D9): серии и хитмап.
 *
 * @property streakDays Текущая серия тренировок, дней
 * @property recordStreakDays Рекордная серия, дней
 * @property streakState Режим отображения карточки серии
 *  (Active / Pending / Lost — см. [StreakCardState])
 * @property period Окно хитмапа
 * @property visiblePeriods Видимые периоды пикера по дате самой ранней
 *  тренировки (overview-periods-and-shares, design D3); дефолт — все
 *  периоды, боевые значения приходят из [com.myworkouts.ui.viewmodel.OverviewViewModel]
 * @property heatmap Ячейки хитмапа (уровни 0..4), старт на понедельнике
 */
data class ActivityUiState(
    val streakDays: Int = 0,
    val recordStreakDays: Int = 0,
    val streakState: StreakCardState = StreakCardState.Active,
    val period: ActivityPeriod = ActivityPeriod.MONTH,
    val visiblePeriods: List<ActivityPeriod> = ActivityPeriod.entries.toList(),
    val heatmap: List<HeatmapDayData> = emptyList()
)

/**
 * UiState экрана обзора (overview-screen, design D9): выбранная вкладка +
 * состояния трёх вкладок. Расчёты чистые и дешёвые — все три считаются на
 * каждую эмиссию, дифинг Compose отсечёт лишние рекомпозиции.
 *
 * @property selectedTab Видимая выбранная вкладка
 * @property visibleTabs Вкладки, включённые в настройках (порядок enum)
 * @property stats Состояние «Статистики»
 * @property records Состояние «Рекордов»
 * @property activity Состояние «Активности»
 */
data class OverviewUiState(
    val selectedTab: OverviewTab = OverviewTab.STATS,
    val visibleTabs: List<OverviewTab> = OverviewTab.entries.toList(),
    val stats: StatsUiState = StatsUiState(),
    val records: RecordsUiState = RecordsUiState(),
    val activity: ActivityUiState = ActivityUiState()
)
