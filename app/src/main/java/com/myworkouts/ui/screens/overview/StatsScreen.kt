@file:Suppress("TooManyFunctions")

package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.myworkouts.R
import com.myworkouts.data.db.standardCategories
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.usecase.CategoryStatsResult
import com.myworkouts.domain.usecase.CategoryTotal
import com.myworkouts.domain.usecase.DrillDateGroup
import com.myworkouts.domain.usecase.ExerciseTotal
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.domain.usecase.percentOf
import com.myworkouts.domain.usecase.window
import com.myworkouts.ui.screens.main.SectionHeader
import com.myworkouts.ui.state.StatsUiState
import com.myworkouts.ui.theme.DARK_THEME_LUMINANCE_THRESHOLD
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.util.categoryLabel
import com.myworkouts.ui.util.formatDurationResource
import com.myworkouts.ui.util.formatEntryValue
import com.myworkouts.ui.util.formatWeightKg
import com.myworkouts.ui.util.labelResId
import com.myworkouts.ui.util.sectionDateLabelResource
import com.myworkouts.ui.util.statsWindowLabelResource
import java.time.LocalDate

/*
 * Вкладка «Статистика» экрана обзора (overview-screen, макеты
 * `progress-drafts/iter4_v1.html` — портрет и `iter5_v1.html` —
 * ландшафт): фиксированный блок «пикер периода + подпись дат окна»,
 * пагер окон периода; страница окна — донат по категориям, легенда с
 * drill-down в упражнения и список записей категории. В ландшафте —
 * строка [донат | легенда | записи], у легенды и записей свои скроллы.
 */

/** Толщина кольца доната, dp (макет). */
private const val DONUT_STROKE_DP = 24

/** Старт первого сектора доната — 12 часов. */
private const val DONUT_START_ANGLE_DEGREES = -90f

/** Полный оборот доната, градусов. */
private const val DONUT_FULL_SWEEP_DEGREES = 360f

/** Границы светлоты оттенка категории в HSL. */
private const val SHADE_L_MIN = 0.08f

/** Верхняя граница светлоты оттенка категории в HSL. */
private const val SHADE_L_MAX = 0.92f

/** Делитель перевода дельты светлоты в долю HSL. */
private const val SHALE_DELTA_DIVISOR = 100f

/** Число компонент HSL. */
private const val HSL_COMPONENTS = 3

/**
 * Предельное смещение листалки (add-stats-period-pager, design D2/D3) —
 * максимум ключей карты агрегатов: ключи `0..maxOffset` без дыр; пустая
 * карта (дефолтное состояние) даёт 0 — единственное актуальное окно.
 */
private fun maxStatsOffset(state: StatsUiState): Int = state.statsByOffset.keys.maxOrNull() ?: 0

/**
 * Агрегаты окна [offset] из карты (add-stats-period-pager, design D5):
 * чтение с дефолтом — отсутствующий ключ и пустая карта трактуется как
 * пустая страница (чтение без `!!`/`getValue`).
 */
private fun statsOf(
    state: StatsUiState,
    offset: Int
): CategoryStatsResult =
    state.statsByOffset[offset]
        ?: CategoryStatsResult(emptyList(), emptyMap(), emptyMap())

/**
 * Вкладка «Статистика»: фиксированный блок «пикер периода + подпись дат
 * окна» сверху, под ним [HorizontalPager] окон периода — страница
 * рендерит донат, легенду и (в drill-down) список записей категории
 * своего окна (add-stats-period-pager, design D7). Пустое окно —
 * серый донат и подсказка «Нет тренировок за выбранный период».
 *
 * Порядок страниц обратный смещению (D4): актуальное окно — последняя
 * (правая) страница, `pageOf(offset) = maxOffset - offset`; свайп
 * содержимого вправо открывает более раннее окно, влево от актуального
 * упирается в последнюю страницу.
 *
 * @param state Состояние вкладки ([StatsUiState])
 * @param onPeriodChange Смена периода (смещение и drill-down сбрасываются в VM)
 * @param onCategoryClick Тап по категории легенды; `null` — выход из drill-down
 * @param onWindowChange Смена окна периода (смещение назад, свайп пагера;
 *  drill-down сбрасывается в VM). Без значения по умолчанию: непроведённая
 *  проводка не должна проходить компиляцию молча (add-stats-period-pager, D5)
 * @param modifier Модификатор корневого контейнера
 */
@Composable
internal fun StatsScreen(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCategoryClick: (String?) -> Unit,
    onWindowChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxOffset = maxStatsOffset(state)
    // Начальная страница — страница текущего смещения (D7): первый «settle»
    // совпадает со смещением, ложного события аналитики не возникает
    val pagerState =
        rememberPagerState(
            initialPage = maxOffset - state.windowOffset,
            pageCount = { maxOffset + 1 }
        )
    // Флаг программной синхронизации: scrollToPage выполняется под флагом,
    // снимаемым после следующего «settle» — сменой окна считается только
    // пользовательский свайп (D7)
    var pendingSync by remember { mutableStateOf(false) }
    // Свежие значения для корутин: замыкания LaunchedEffect устаревают
    val currentWindowOffset by rememberUpdatedState(state.windowOffset)
    val currentMaxOffset by rememberUpdatedState(maxOffset)
    val currentOnWindowChange by rememberUpdatedState(onWindowChange)

    // Принудительная синхронизация страницы со смещением: смена типа
    // периода, изменение глубины истории (в том числе прижим смещения к
    // новой границе, D5) и сброс смещения без смены периода (повторный тап
    // выбранного сегмента — onClick срабатывает и для выбранного; design D7:
    // источник истины — смещение в состоянии, пагер его отображает).
    // Пользовательский свайп меняет смещение, совпадающее со страницей
    // settle: цель равна currentPage, эффект остаётся no-op и страницу
    // не трогает (D7)
    LaunchedEffect(state.period, maxOffset, state.windowOffset) {
        val target = currentMaxOffset - currentWindowOffset
        if (pagerState.currentPage != target) {
            pendingSync = true
            pagerState.scrollToPage(target)
        }
    }

    // Смена окна — по «страница остановилась» (settledPage): страница,
    // не совпавшая со страницей текущего смещения, означает пользовательский
    // свайп (D7/D8)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (pendingSync) {
                pendingSync = false
            } else {
                val offset = currentMaxOffset - settled
                if (offset != currentWindowOffset) {
                    currentOnWindowChange(offset)
                }
            }
        }
    }

    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        StatsScreenLandscapeBody(
            state = state,
            pagerState = pagerState,
            onPeriodChange = onPeriodChange,
            onCategoryClick = onCategoryClick,
            modifier = modifier
        )
    } else {
        StatsScreenPortraitBody(
            state = state,
            pagerState = pagerState,
            onPeriodChange = onPeriodChange,
            onCategoryClick = onCategoryClick,
            modifier = modifier
        )
    }
}

/**
 * Портретная раскладка (add-stats-period-pager, design D7): фиксированный
 * блок «пикер + подпись дат» сверху (вынесен из скролла), ниже
 * [HorizontalPager] — страница окна рендерит [StatsPortraitPage] со своим
 * вертикальным скроллом.
 *
 * Смещение листалки берётся из состояния ([maxStatsOffset]) — то же
 * значение, на котором [StatsScreen] строит `pageCount` пагера (D2/D3).
 */
@Composable
private fun StatsScreenPortraitBody(
    state: StatsUiState,
    pagerState: PagerState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCategoryClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxOffset = maxStatsOffset(state)
    Column(modifier = modifier.fillMaxSize()) {
        StatsWindowHeader(
            state = state,
            onPeriodChange = onPeriodChange,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            StatsPortraitPage(
                state = state,
                pageOffset = maxOffset - page,
                onCategoryClick = onCategoryClick
            )
        }
    }
}

/**
 * Страница окна пагера в портрете: LazyColumn как корневой скролл —
 * шапка (донат, легенда или подсказка пустого окна) одним элементом,
 * записи drill-down — ленивыми элементами (список может быть в сотни
 * записей). Drill-down применяется только на странице текущего окна:
 * во время свайпа соседняя страница показывает обзор, drill-down
 * сбрасывается при settle (add-stats-period-pager, design D5/D7).
 *
 * @param pageOffset Смещение окна этой страницы (D4): `maxOffset - page`
 */
@Composable
private fun StatsPortraitPage(
    state: StatsUiState,
    pageOffset: Int,
    onCategoryClick: (String?) -> Unit
) {
    val page = statsOf(state, pageOffset)
    val selected = if (pageOffset == state.windowOffset) state.selected else null
    val showDrill = page.drillGroups.isNotEmpty() && selected != null
    val today = remember { LocalDate.now() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header") {
            Column(
                modifier =
                    if (showDrill) {
                        // + spacedBy(8) = прежние 16 до первого заголовка даты
                        Modifier.padding(bottom = 8.dp)
                    } else {
                        Modifier
                    }
            ) {
                // Донат и блок под ним общие для пустого и непустого окна:
                // пустое окно — серый донат и подсказка без рамки вместо легенды
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (page.drillGroups.isEmpty()) {
                        EmptyWindowDonut(contentDescriptionText = stringResource(R.string.overview_stats_empty))
                    } else {
                        DonutChart(slices = donutSlices(page, selected, state.categoryColors))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (page.drillGroups.isEmpty()) {
                    EmptyPeriodHint(modifier = Modifier.fillMaxWidth())
                } else {
                    LegendSection(
                        items = legendItems(page, selected, state.categoryColors),
                        onCategoryClick = onCategoryClick
                    )
                }
            }
        }
        if (showDrill) {
            drillItems(groups = page.drillGroups[selected].orEmpty(), today = today)
        }
    }
}

/**
 * Ландшафтная раскладка (макет `iter5_v1.html`, add-stats-period-pager
 * design D7): фиксированный блок «пикер + подпись дат» сверху, ниже
 * [HorizontalPager] со страницей окна ([StatsLandscapePage]).
 *
 * Смещение листалки берётся из состояния ([maxStatsOffset]) — то же
 * значение, на котором [StatsScreen] строит `pageCount` пагера (D2/D3).
 */
@Composable
private fun StatsScreenLandscapeBody(
    state: StatsUiState,
    pagerState: PagerState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCategoryClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxOffset = maxStatsOffset(state)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
    ) {
        StatsWindowHeader(state = state, onPeriodChange = onPeriodChange)
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            StatsLandscapePage(
                state = state,
                pageOffset = maxOffset - page,
                onCategoryClick = onCategoryClick
            )
        }
    }
}

/**
 * Страница окна пагера в ландшафте (макет `iter5_v1.html`): строка
 * [донат | легенда | записи] с гэпом 12. Донат — квадрат до 160dp,
 * вписанный в высоту строки (минимум с ней гарантирует круг на низких
 * экранах), по центру вертикали; легенда (в обзоре — сетка в две колонки,
 * в drill-down — одна колонка) и список записей делят остаток ширины
 * поровну и скроллятся независимо. Без drill-down список записей скрыт,
 * как `display: none` макета. Drill-down применяется только на странице
 * текущего окна (design D5/D7 — см. [StatsPortraitPage]).
 *
 * @param pageOffset Смещение окна этой страницы (D4): `maxOffset - page`
 */
@Composable
private fun StatsLandscapePage(
    state: StatsUiState,
    pageOffset: Int,
    onCategoryClick: (String?) -> Unit
) {
    val page = statsOf(state, pageOffset)
    val selected = if (pageOffset == state.windowOffset) state.selected else null
    if (page.drillGroups.isEmpty()) {
        // Пустое окно: серый донат на месте обычного (вписан в высоту
        // строки), подсказка — справа, в колонке легенды, без рамки
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints {
                EmptyWindowDonut(
                    size = minOf(maxHeight, 160.dp),
                    contentDescriptionText = stringResource(R.string.overview_stats_empty)
                )
            }
            EmptyPeriodHint(modifier = Modifier.weight(1f))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints {
                // Донат — квадрат, вписанный в высоту строки: фиксированные
                // 160dp на низких экранах (S25 и уже) сплющивались Canvas-ом
                // в эллипс и наезжали на колонку легенды справа
                DonutChart(
                    slices = donutSlices(page, selected, state.categoryColors),
                    size = minOf(maxHeight, 160.dp)
                )
            }
            if (selected == null) {
                OverviewLegendGrid(
                    items = legendItems(page, selected, state.categoryColors),
                    onCategoryClick = onCategoryClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LegendSection(
                    items = legendItems(page, selected, state.categoryColors),
                    onCategoryClick = onCategoryClick,
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                )
                DrillEntriesLazyList(
                    groups = page.drillGroups[selected].orEmpty(),
                    today = remember { LocalDate.now() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Фиксированный блок над пагером (add-stats-period-pager, design D7):
 * пикер периода — сегмент-контрол на всю ширину (при двух и более видимых
 * периодах) и подпись дат окна под ним. Подпись остаётся и при скрытом
 * пикере (меньше двух видимых периодов) и обновляется вместе с окном
 * пагера — [statsWindowLabelResource] читает границы окна состояния.
 */
@Composable
private fun StatsWindowHeader(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.visiblePeriods.size >= 2) {
            StatsPeriodPicker(
                selected = state.period,
                onPeriodChange = onPeriodChange,
                visiblePeriods = state.visiblePeriods
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = statsWindowLabelResource(window = state.window, period = state.period),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Подсказка пустого периода. */
@Composable
private fun EmptyPeriodHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.overview_stats_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Цвет категории из модели (`colorArgb`, custom-categories design
 * Decision 10); отсутствующее в каталоге имя — нейтральный цвет темы.
 */
@Composable
private fun categoryColor(
    name: String,
    colors: Map<String, Int>
): Color = colors[name]?.let(::Color) ?: MaterialTheme.colorScheme.outline

/**
 * Секторы доната: обзор — по категориям (цвет `colorArgb` из модели),
 * drill-down — по упражнениям (оттенки цвета категории, [shadeDelta]).
 * Доля — по сумме подходов (overview-stats-time-support Decision 1).
 */
@Composable
private fun donutSlices(
    page: CategoryStatsResult,
    selected: String?,
    colors: Map<String, Int>
): List<Pair<Color, Int>> =
    if (selected == null) {
        page.categoryTotals.map { total ->
            categoryColor(total.category, colors) to total.totalSets
        }
    } else {
        val base = categoryColor(selected, colors)
        page.exerciseTotals[selected].orEmpty().mapIndexed { index, total ->
            shade(base, shadeDelta(index)) to total.totalSets
        }
    }

/**
 * Строки легенды: категории (кликабельны) или упражнения drill-down.
 * Значение — процент от суммы подходов отображаемых строк уровня
 * (overview-periods-and-shares, design D6): база у уровня категорий —
 * общая, в drill-down — своя у каждой категории; расчёт — [percentOf],
 * формат — ресурсом по локали (ru «N %», en «N%»).
 */
@Composable
private fun legendItems(
    page: CategoryStatsResult,
    selected: String?,
    colors: Map<String, Int>
): List<StatsLegendRow> =
    if (selected == null) {
        val base = page.categoryTotals.sumOf { it.totalSets }
        page.categoryTotals.map { total ->
            StatsLegendRow(
                label = categoryLabel(total.category),
                valueText = stringResource(R.string.overview_legend_percent, percentOf(total.totalSets, base)),
                color = categoryColor(total.category, colors),
                category = total.category
            )
        }
    } else {
        val totals = page.exerciseTotals[selected].orEmpty()
        val base = totals.sumOf { it.totalSets }
        val drillBaseColor = categoryColor(selected, colors)
        totals.mapIndexed { index, total ->
            StatsLegendRow(
                label = total.exerciseName,
                valueText = stringResource(R.string.overview_legend_percent, percentOf(total.totalSets, base)),
                color = shade(drillBaseColor, shadeDelta(index)),
                category = null
            )
        }
    }

/** Строка легенды: значение уже готовым текстом (собирается в [legendItems]). */
private data class StatsLegendRow(
    val label: String,
    val valueText: String,
    val color: Color,
    /** Имя категории для drill-down; `null` у строк упражнений (некликабельны). */
    val category: String?
)

/**
 * Легенда: в обзоре — строки категорий по отдельности (клик открывает
 * drill-down), в drill-down — сверху кнопка «← Категории» и одна
 * карточка со строками упражнений: тот же стиль, что в карточке
 * рекордов — контейнер `surfaceContainerLow`, между строками divider
 * `surfaceContainerHighest`.
 */
@Composable
private fun LegendSection(
    items: List<StatsLegendRow>,
    onCategoryClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDrill = items.firstOrNull()?.category == null
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isDrill) {
            DrillBackRow(onClick = { onCategoryClick(null) })
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
            ) {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        // Дефолт divider (outlineVariant) темноват на светлой
                        // карточке — берём тон на шаг выше контейнера
                        // карточки (surfaceContainerLow)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                    LegendItemContent(item = item)
                }
            }
        } else {
            items.forEach { item ->
                Card(
                    onClick = { item.category?.let(onCategoryClick) },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                ) {
                    LegendItemContent(item = item)
                }
            }
        }
    }
}

/**
 * Легенда обзора в ландшафте (макет `iter5_v1.html`): сетка в две
 * колонки одинаковой ширины, своя вертикальная прокрутка; строки —
 * те же карточки категорий, что и в портрете.
 */
@Composable
private fun OverviewLegendGrid(
    items: List<StatsLegendRow>,
    onCategoryClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { item ->
                    Card(
                        onClick = { item.category?.let(onCategoryClick) },
                        modifier = Modifier.weight(1f),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                    ) {
                        LegendItemContent(item = item)
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Пикер периода: День / Неделя / Месяц / Год — сегменты только из
 * видимых периодов (overview-periods-and-shares). Показ решает
 * вызывающий ([StatsWindowHeader] рендерит его только при двух и более
 * видимых периодах).
 */
@Composable
private fun StatsPeriodPicker(
    selected: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    visiblePeriods: List<StatsPeriod>
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        visiblePeriods.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onPeriodChange(period) },
                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = visiblePeriods.size
                    )
            ) {
                Text(text = stringResource(id = period.labelResId()))
            }
        }
    }
}

/** Донат: секторы без зазоров, начиная с 12 часов; центр пустой. */
@Composable
private fun DonutChart(
    slices: List<Pair<Color, Int>>,
    size: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val total = slices.sumOf { it.second }.coerceAtLeast(1)
        val stroke = Stroke(width = DONUT_STROKE_DP.dp.toPx())
        // Stroke центрируется на дуге: без инсета внешняя половина штриха
        // выходит за границы Canvas и срезается клипом
        val arcSize =
            Size(
                width = this.size.width - stroke.width,
                height = this.size.height - stroke.width
            )
        val topLeft = Offset(stroke.width / 2, stroke.width / 2)
        var startAngle = DONUT_START_ANGLE_DEGREES
        slices.forEach { (color, value) ->
            val sweep = value.toFloat() / total * DONUT_FULL_SWEEP_DEGREES
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            startAngle += sweep
        }
    }
}

/**
 * Донат пустого окна: один нейтральный сектор (в светлой теме — `outline`,
 * в тёмной — `onSurfaceVariant`) — виден в светлой и тёмной темах;
 * размеры и расположение — как у доната непустого окна. Семантика
 * [contentDescriptionText] даёт Canvas-у доступность для скрин-ридера и
 * точку поиска в UI-тестах.
 */
@Composable
private fun EmptyWindowDonut(
    contentDescriptionText: String,
    size: Dp = 200.dp
) {
    // Тема — эффективная схема приложения (учитывает настройку «Внешний вид»),
    // а не системная: определяем по яркости фона.
    val emptyColor =
        if (MaterialTheme.colorScheme.surface.luminance() < DARK_THEME_LUMINANCE_THRESHOLD) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.outline
        }
    DonutChart(
        slices = listOf(emptyColor to 1),
        size = size,
        modifier = Modifier.semantics { contentDescription = contentDescriptionText }
    )
}

/** Содержимое строки легенды: образец цвета, название, жирное значение справа. */
@Composable
private fun LegendItemContent(item: StatsLegendRow) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier =
                Modifier
                    .size(14.dp)
                    .background(color = item.color, shape = RoundedCornerShape(4.dp))
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = item.valueText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Кнопка возврата из drill-down («← Категории»); иконка декоративная. */
@Composable
private fun DrillBackRow(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(text = stringResource(R.string.overview_back_to_categories))
    }
}

/**
 * Записи drill-down как элементы LazyColumn: заголовки секций дат
 * («Сегодня» для текущего дня — в отличие от главного экрана, [SectionHeader]
 * с heading-семантикой) и строки записей. Общее для портрета и ландшафта.
 * Ключи — id записи.
 */
private fun LazyListScope.drillItems(
    groups: List<DrillDateGroup>,
    today: LocalDate
) {
    groups.forEach { group ->
        item(key = "hdr-${group.date.toEpochDay()}") {
            SectionHeader(
                title =
                    sectionDateLabelResource(group.date, today)
                        ?: stringResource(R.string.entry_section_today)
            )
        }
        items(group.entries, key = { entry -> entry.id }) { entry ->
            DrillEntryRow(entry = entry)
        }
    }
}

/**
 * Список записей категории в ландшафте: собственный скролл, составляются
 * только видимые строки.
 */
@Composable
private fun DrillEntriesLazyList(
    groups: List<DrillDateGroup>,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        drillItems(groups = groups, today = today)
    }
}

/**
 * Значение записи drill-down: «подходы х повторы» для REPS,
 * «подходы х длительность» для TIME.
 */
@Composable
private fun drillEntryValueText(entry: EntryWithExercise): String {
    val separator = stringResource(R.string.entry_value_separator)
    return entry.reps
        ?.let { reps -> formatEntryValue(sets = entry.sets, value = "$reps", separator = separator) }
        ?: entry.durationSec
            ?.let { durationSec ->
                formatEntryValue(
                    sets = entry.sets,
                    value = formatDurationResource(seconds = durationSec),
                    separator = separator
                )
            }.orEmpty()
}

/**
 * Строка записи drill-down: «название — значение», ниже дополнительный
 * вес и комментарий при наличии. Образец — строка записи главного
 * экрана; значение — `formatEntryValue` («подходы х значение»).
 */
@Composable
private fun DrillEntryRow(entry: EntryWithExercise) {
    val weightLabel = stringResource(R.string.entry_list_weight_label)
    val weightValue = stringResource(R.string.entry_list_weight_value, formatWeightKg(entry.weightKg ?: 0.0))
    val valueText = drillEntryValueText(entry = entry)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.exerciseName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            val weightKg = entry.weightKg
            if (weightKg != null && weightKg > 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = weightLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = weightValue,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            val comment = entry.comment
            if (!comment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ===== Оттенки долей упражнений внутри категории =====

/**
 * Оттенки долей упражнений: чередуем светлее/темнее для контраста
 * соседних долей (макет `SHADE_DELTAS`).
 *
 * `MagicNumber` подавлен осознанно: числа — сами значения дельт
 * светлоты, единственная правляемая точка (паттерн
 * `HEATMAP_THRESHOLDS`).
 */
@Suppress("MagicNumber")
private val shadeDeltas = listOf(0, 32, -28, 45, -40)

private fun shadeDelta(index: Int): Int = shadeDeltas[index.coerceAtMost(shadeDeltas.lastIndex)]

/**
 * Оттенок цвета категории: сдвиг светлоты в HSL, hue и saturation
 * сохраняются.
 */
private fun shade(
    base: Color,
    deltaL: Int
): Color {
    val hsl = FloatArray(size = HSL_COMPONENTS)
    ColorUtils.colorToHSL(base.toArgb(), hsl)
    hsl[2] = (hsl[2] + deltaL / SHALE_DELTA_DIVISOR).coerceIn(SHADE_L_MIN, SHADE_L_MAX)
    return Color(ColorUtils.HSLToColor(hsl))
}

// ===== Демо-данные превью (переиспользуются OverviewScreen.kt) =====

/** Миллисекунды в сутках — перевод дат демо-записей drill-down. */
private const val PREVIEW_DAY_MILLIS = 86_400_000L

/** Миллисекунды «вчера» для демо-записи в группе вчерашнего дня. */
private val previewYesterdayMillis = (LocalDate.now().toEpochDay() - 1) * PREVIEW_DAY_MILLIS

/** Демо-цвета категорий: `colorArgb` стандартного набора (Decision 10). */
private val demoCategoryColors: Map<String, Int> =
    standardCategories.associate { it.name to it.colorArgb }

/** Демо-легенда: доли по подходам убывание (уровень категорий). */
internal val demoCategoryTotals =
    listOf(
        CategoryTotal(category = "PUSH", totalSets = 6),
        CategoryTotal(category = "CORE", totalSets = 5),
        CategoryTotal(category = "PULL", totalSets = 4),
        CategoryTotal(category = "CARDIO", totalSets = 2)
    )

private val demoExerciseTotals =
    mapOf(
        "PUSH" to
            listOf(
                ExerciseTotal(exerciseName = "Отжимания", totalSets = 3),
                ExerciseTotal(exerciseName = "Отжимания на брусьях", totalSets = 3)
            ),
        "CORE" to
            listOf(
                ExerciseTotal(exerciseName = "Скручивания", totalSets = 3),
                ExerciseTotal(exerciseName = "Планка", totalSets = 2)
            ),
        "PULL" to
            listOf(ExerciseTotal(exerciseName = "Подтягивания", totalSets = 4)),
        "CARDIO" to
            listOf(ExerciseTotal(exerciseName = "Бег", totalSets = 2))
    )

private val demoDrillGroups =
    mapOf(
        "PUSH" to
            listOf(
                DrillDateGroup(
                    date = LocalDate.now(),
                    entries =
                        listOf(
                            previewEntry(
                                id = 1L,
                                name = "Отжимания",
                                category = "PUSH",
                                sets = 3,
                                reps = 15
                            )
                        )
                ),
                DrillDateGroup(
                    date = LocalDate.now().minusDays(1),
                    entries =
                        listOf(
                            previewEntry(
                                id = 2L,
                                name = "Отжимания на брусьях",
                                category = "PUSH",
                                sets = 3,
                                reps = 10
                            ).copy(date = previewYesterdayMillis)
                        )
                )
            ),
        "PULL" to
            listOf(
                DrillDateGroup(
                    date = LocalDate.now(),
                    entries =
                        listOf(
                            previewEntry(
                                id = 3L,
                                name = "Подтягивания",
                                category = "PULL",
                                sets = 4,
                                reps = 10
                            )
                        )
                )
            ),
        "CORE" to
            listOf(
                DrillDateGroup(
                    date = LocalDate.now(),
                    entries =
                        listOf(
                            previewEntry(
                                id = 4L,
                                name = "Скручивания",
                                category = "CORE",
                                sets = 3,
                                reps = 12
                            ),
                            previewEntry(
                                id = 5L,
                                name = "Планка",
                                category = "CORE",
                                sets = 2,
                                durationSec = 90
                            )
                        )
                )
            ),
        "CARDIO" to
            listOf(
                DrillDateGroup(
                    date = LocalDate.now(),
                    entries =
                        listOf(
                            previewEntry(
                                id = 6L,
                                name = "Бег",
                                category = "CARDIO",
                                sets = 2,
                                durationSec = 1800
                            )
                        )
                )
            )
    )

/**
 * Демо-запись drill-down: REPS при [reps], иначе TIME по [durationSec].
 * Свёртывать параметры в объект не стоит — фабрика демо-данных.
 */
@Suppress("LongParameterList")
private fun previewEntry(
    id: Long,
    name: String,
    category: String,
    sets: Int,
    reps: Int? = null,
    durationSec: Int? = null
): EntryWithExercise =
    EntryWithExercise(
        id = id,
        exerciseId = id,
        exerciseName = name,
        iconNumber = null,
        category = category,
        measuredBy =
            if (reps != null) {
                ExerciseMeasuredBy.REPS
            } else {
                ExerciseMeasuredBy.TIME
            },
        exerciseComment = null,
        sets = sets,
        reps = reps,
        durationSec = durationSec,
        weightKg = null,
        comment = null,
        date = LocalDate.now().toEpochDay() * PREVIEW_DAY_MILLIS
    )

/**
 * Демо-состояние «Статистики» для превью (StatsScreen и каркас
 * OverviewScreen): доли категорий по подходам, суммы упражнений и записи
 * drill-down — донат, легенда и списки рендерятся как в рантайме;
 * TIME-категория (CARDIO) и смешанная (CORE) видны в легенде. Агрегаты
 * кладутся в карту окна [windowOffset]; ключи `0..windowOffset` без дыр
 * (контракт D2) — окна без данных дают пустой результат; дефолтное
 * смещение 0 (актуальное окно) сохраняет прежнее поведение
 * (add-stats-period-pager, design D5). [selected] открывает drill-down
 * выбранной категории.
 */
internal fun demoStatsState(
    period: StatsPeriod,
    selected: String? = null,
    visiblePeriods: List<StatsPeriod> = StatsPeriod.entries.toList(),
    windowOffset: Int = 0
): StatsUiState {
    val today = LocalDate.now()
    return StatsUiState(
        period = period,
        visiblePeriods = visiblePeriods,
        windowOffset = windowOffset,
        window = period.window(today, windowOffset),
        selected = selected,
        statsByOffset =
            (0..windowOffset).associateWith { offset ->
                if (offset == windowOffset) {
                    CategoryStatsResult(demoCategoryTotals, demoExerciseTotals, demoDrillGroups)
                } else {
                    CategoryStatsResult(emptyList(), emptyMap(), emptyMap())
                }
            },
        categoryColors = demoCategoryColors
    )
}

// ===== Превью (обе темы, showBackground, locale = "ru") =====

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsWeekPreview() {
    StatsScreenPreview(period = StatsPeriod.WEEK)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsEmptyPreview() {
    StatsScreenPreview(period = StatsPeriod.WEEK, isEmpty = true)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsDrillDownPreview() {
    StatsScreenPreview(period = StatsPeriod.WEEK, selected = "PUSH")
}

/** Пикер скрыт: один видимый период, контент поднят на его место. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsPeriodHiddenPreview() {
    StatsScreenPreview(period = StatsPeriod.MONTH, visiblePeriods = listOf(StatsPeriod.MONTH))
}

/** Прошлое окно недели (смещение назад на 1): подпись дат сдвинулась за окном. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsWeekPastWindowPreview() {
    StatsScreenPreview(period = StatsPeriod.WEEK, windowOffset = 1)
}

// Ландшафт (макет iter5_v1.html): размер превью шире высоты —
// LocalConfiguration даёт ORIENTATION_LANDSCAPE.

@Preview(widthDp = 891, heightDp = 411, showBackground = true, locale = "ru")
@Preview(widthDp = 891, heightDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsLandscapeWeekPreview() {
    StatsScreenPreview(period = StatsPeriod.WEEK)
}

@Preview(widthDp = 891, heightDp = 411, showBackground = true, locale = "ru")
@Preview(widthDp = 891, heightDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsLandscapeDrillDownPreview() {
    StatsScreenPreview(period = StatsPeriod.WEEK, selected = "PUSH")
}

/** Ландшафт: пустое окно — серый донат, подсказка справа без рамки. */
@Preview(widthDp = 891, heightDp = 411, showBackground = true, locale = "ru")
@Preview(widthDp = 891, heightDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsLandscapeEmptyPreview() {
    StatsScreenPreview(period = StatsPeriod.WEEK, isEmpty = true)
}

/** Ландшафт: пикер скрыт при одном видимом периоде. */
@Preview(widthDp = 891, heightDp = 411, showBackground = true, locale = "ru")
@Preview(widthDp = 891, heightDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsLandscapePeriodHiddenPreview() {
    StatsScreenPreview(period = StatsPeriod.MONTH, visiblePeriods = listOf(StatsPeriod.MONTH))
}

@Composable
private fun StatsScreenPreview(
    period: StatsPeriod,
    selected: String? = null,
    isEmpty: Boolean = false,
    visiblePeriods: List<StatsPeriod> = StatsPeriod.entries.toList(),
    windowOffset: Int = 0
) {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StatsScreen(
                state =
                    if (isEmpty) {
                        StatsUiState(period = period, visiblePeriods = visiblePeriods)
                    } else {
                        demoStatsState(
                            period = period,
                            selected = selected,
                            visiblePeriods = visiblePeriods,
                            windowOffset = windowOffset
                        )
                    },
                onPeriodChange = {},
                onCategoryClick = {},
                onWindowChange = {}
            )
        }
    }
}
