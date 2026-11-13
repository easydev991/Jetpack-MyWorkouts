package com.myworkouts.ui.state

import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.CategoryStatsResult
import com.myworkouts.domain.usecase.ExerciseRecordData
import com.myworkouts.domain.usecase.HeatmapDayData
import com.myworkouts.domain.usecase.RecordsMode
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.domain.usecase.StatsWindow
import com.myworkouts.domain.usecase.window
import java.time.LocalDate

/**
 * UiState вкладки «Статистика» экрана обзора (overview-screen, design
 * D9; custom-categories design Decision 10; add-stats-period-pager,
 * design D5). Единственный источник агрегатов — карта [statsByOffset]:
 * страница читается записью карты по [windowOffset] с дефолтом —
 * пустая страница для отсутствующего ключа (чтение без `!!`/`getValue`);
 * пустота страницы — производная от её `drillGroups`. Доменные типы
 * расчётов ([CategoryStatsResult]) переиспользуются как есть — в них уже
 * есть нужные для LazyColumn-ключей идентификаторы; `selected` — имя
 * категории (стандартный ключ или кастомное имя).
 *
 * @property period Календарный период доната и легенды
 * @property visiblePeriods Видимые периоды пикера по дате самой ранней
 *  тренировки (overview-periods-and-shares, design D3); дефолт — все
 *  периоды, боевые значения приходят из [com.myworkouts.ui.viewmodel.OverviewViewModel]
 * @property windowOffset Смещение текущего окна назад (0 — актуальное
 *  окно); прижимается к глубине истории — `min(offset, maxOffset)`
 *  (add-stats-period-pager, design D5)
 * @property window Границы текущего окна (`windowOffset`), обе включительно
 * @property selected Имя выбранной категории drill-down; `null` — обзор по категориям
 * @property statsByOffset Агрегаты по окнам периода: ключи — все смещения
 *  `0..maxOffset` без дыр, окно без записей — пустой результат
 *  (add-stats-period-pager, design D2); пустая карта (дефолт) — пустая
 *  страница
 * @property categoryColors Имя категории → `colorArgb` из модели (активные
 *  и архивные — архивная остаётся в статистике, Decision 10); отсутствующее
 *  имя — UI берёт нейтральный цвет темы
 */
data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val visiblePeriods: List<StatsPeriod> = StatsPeriod.entries.toList(),
    val windowOffset: Int = 0,
    val window: StatsWindow = StatsPeriod.WEEK.window(LocalDate.now()),
    val selected: String? = null,
    val statsByOffset: Map<Int, CategoryStatsResult> = emptyMap(),
    val categoryColors: Map<String, Int> = emptyMap()
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
