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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.myworkouts.R
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.usecase.CategoryTotal
import com.myworkouts.domain.usecase.DrillDateGroup
import com.myworkouts.domain.usecase.ExerciseTotal
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.ui.screens.main.SectionHeader
import com.myworkouts.ui.screens.main.formatEntryValue
import com.myworkouts.ui.screens.main.formatWeightKg
import com.myworkouts.ui.state.StatsUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.theme.color
import com.myworkouts.ui.util.formatDurationResource
import com.myworkouts.ui.util.sectionDateLabelResource
import java.time.LocalDate

/*
 * Вкладка «Статистика» экрана обзора (overview-screen, макеты
 * `progress-drafts/iter4_v1.html` — портрет и `iter5_v1.html` —
 * ландшафт): пикер периода, донат по категориям, легенда с drill-down
 * в упражнения и список записей категории. В ландшафте — строка
 * [донат | легенда | записи], у легенды и записей свои скроллы.
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
 * Вкладка «Статистика»: пикер периода, донат, легенда и (в drill-down)
 * список записей категории. Пустой период — подсказка «Нет тренировок
 * за выбранный период».
 *
 * @param state Состояние вкладки ([StatsUiState])
 * @param onPeriodChange Смена периода (drill-down сбрасывается в VM)
 * @param onCategoryClick Тап по категории легенды; `null` — выход из drill-down
 * @param modifier Модификатор корневого контейнера
 */
@Composable
internal fun StatsScreen(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCategoryClick: (ExerciseCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        StatsScreenLandscapeBody(
            state = state,
            onPeriodChange = onPeriodChange,
            onCategoryClick = onCategoryClick,
            modifier = modifier
        )
    } else {
        StatsScreenPortraitBody(
            state = state,
            onPeriodChange = onPeriodChange,
            onCategoryClick = onCategoryClick,
            modifier = modifier
        )
    }
}

/**
 * Портретная раскладка: LazyColumn как корневой скролл страницы —
 * шапка (пикер, донат, легенда) одним элементом, записи drill-down —
 * ленивыми элементами (список может быть в сотни записей).
 */
@Composable
private fun StatsScreenPortraitBody(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCategoryClick: (ExerciseCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    val showDrill = !state.isEmpty && state.selected != null
    val today = remember { LocalDate.now() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                StatsPeriodPicker(
                    selected = state.period,
                    onPeriodChange = onPeriodChange
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (state.isEmpty) {
                    EmptyPeriodHint()
                } else {
                    val slices = donutSlices(state)
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DonutChart(slices = slices)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LegendSection(
                        items = legendItems(state),
                        onCategoryClick = onCategoryClick
                    )
                }
            }
        }
        if (showDrill) {
            drillItems(groups = state.drillGroups[state.selected].orEmpty(), today = today)
        }
    }
}

/**
 * Ландшафтная раскладка (макет `iter5_v1.html`): период сверху, ниже
 * строка [донат | легенда | записи] с гэпом 12. Донат — квадрат до 160dp,
 * вписанный в высоту строки (минимум с ней гарантирует круг на низких
 * экранах), по центру вертикали; легенда (в обзоре — сетка в две колонки,
 * в drill-down — одна колонка) и список записей делят остаток ширины
 * поровну и скроллятся независимо. Без drill-down список записей скрыт,
 * как `display: none` макета.
 */
@Composable
private fun StatsScreenLandscapeBody(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCategoryClick: (ExerciseCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
    ) {
        StatsPeriodPicker(
            selected = state.period,
            onPeriodChange = onPeriodChange
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyPeriodHint()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints {
                    // Донат — квадрат, вписанный в высоту строки: фиксированные
                    // 160dp на низких экранах (S25 и уже) сплющивались Canvas-ом
                    // в эллипс и наезжали на колонку легенды справа
                    DonutChart(
                        slices = donutSlices(state),
                        size = minOf(maxHeight, 160.dp)
                    )
                }
                if (state.selected == null) {
                    OverviewLegendGrid(
                        items = legendItems(state),
                        onCategoryClick = onCategoryClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LegendSection(
                        items = legendItems(state),
                        onCategoryClick = onCategoryClick,
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                    )
                    DrillEntriesLazyList(
                        groups = state.drillGroups[state.selected].orEmpty(),
                        today = remember { LocalDate.now() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Подсказка пустого периода. */
@Composable
private fun EmptyPeriodHint(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.overview_stats_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
        )
    }
}

/**
 * Секторы доната: обзор — по категориям (цвет [ExerciseCategory.color]),
 * drill-down — по упражнениям (оттенки цвета категории, [shadeDelta]).
 */
private fun donutSlices(state: StatsUiState): List<Pair<Color, Int>> {
    val selected = state.selected
    return if (selected == null) {
        state.categoryTotals.map { it.category.color to it.totalReps }
    } else {
        state.exerciseTotals[selected].orEmpty().mapIndexed { index, total ->
            shade(selected.color, shadeDelta(index)) to total.totalReps
        }
    }
}

/** Строки легенды: категории (кликабельны) или упражнения drill-down. */
@Composable
private fun legendItems(state: StatsUiState): List<StatsLegendRow> {
    val selected = state.selected
    return if (selected == null) {
        state.categoryTotals.map { total ->
            StatsLegendRow(
                label = stringResource(total.category.labelResId),
                value = total.totalReps,
                color = total.category.color,
                category = total.category
            )
        }
    } else {
        state.exerciseTotals[selected].orEmpty().mapIndexed { index, total ->
            StatsLegendRow(
                label = total.exerciseName,
                value = total.totalReps,
                color = shade(selected.color, shadeDelta(index)),
                category = null
            )
        }
    }
}

/** Строка легенды: значение резолвится в текст в [LegendItemContent]. */
private data class StatsLegendRow(
    val label: String,
    val value: Int,
    val color: Color,
    /** Категория для drill-down; `null` у строк упражнений (некликабельны). */
    val category: ExerciseCategory?
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
    onCategoryClick: (ExerciseCategory?) -> Unit,
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
    onCategoryClick: (ExerciseCategory?) -> Unit,
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

/** Пикер периода: День / Неделя / Месяц / Год. */
@Composable
private fun StatsPeriodPicker(
    selected: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        StatsPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onPeriodChange(period) },
                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = StatsPeriod.entries.size
                    )
            ) {
                Text(text = stringResource(id = period.labelResId))
            }
        }
    }
}

/** Донат: секторы без зазоров, начиная с 12 часов; центр пустой. */
@Composable
private fun DonutChart(
    slices: List<Pair<Color, Int>>,
    size: Dp = 200.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val total = slices.sumOf { it.second }.coerceAtLeast(1)
        val stroke = Stroke(width = DONUT_STROKE_DP.dp.toPx())
        var startAngle = DONUT_START_ANGLE_DEGREES
        slices.forEach { (color, value) ->
            val sweep = value.toFloat() / total * DONUT_FULL_SWEEP_DEGREES
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = stroke
            )
            startAngle += sweep
        }
    }
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
            text = stringResource(R.string.overview_unit_reps, item.value),
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

/** Демо-легенда: три категории, объёмы убывают. */
internal val demoCategoryTotals =
    listOf(
        CategoryTotal(category = ExerciseCategory.PUSH, totalReps = 45),
        CategoryTotal(category = ExerciseCategory.PULL, totalReps = 36),
        CategoryTotal(category = ExerciseCategory.CORE, totalReps = 18)
    )

private val demoExerciseTotals =
    mapOf(
        ExerciseCategory.PUSH to
            listOf(
                ExerciseTotal(exerciseName = "Отжимания", totalReps = 30),
                ExerciseTotal(exerciseName = "Отжимания на брусьях", totalReps = 15)
            ),
        ExerciseCategory.PULL to
            listOf(ExerciseTotal(exerciseName = "Подтягивания", totalReps = 36)),
        ExerciseCategory.CORE to
            listOf(ExerciseTotal(exerciseName = "Скручивания", totalReps = 18))
    )

private val demoDrillGroups =
    mapOf(
        ExerciseCategory.PUSH to
            listOf(
                DrillDateGroup(
                    date = LocalDate.now(),
                    entries =
                        listOf(
                            previewEntry(
                                id = 1L,
                                name = "Отжимания",
                                category = ExerciseCategory.PUSH,
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
                                category = ExerciseCategory.PUSH,
                                sets = 3,
                                reps = 10
                            ).copy(date = previewYesterdayMillis)
                        )
                )
            ),
        ExerciseCategory.PULL to
            listOf(
                DrillDateGroup(
                    date = LocalDate.now(),
                    entries =
                        listOf(
                            previewEntry(
                                id = 3L,
                                name = "Подтягивания",
                                category = ExerciseCategory.PULL,
                                sets = 4,
                                reps = 10
                            )
                        )
                )
            ),
        ExerciseCategory.CORE to
            listOf(
                DrillDateGroup(
                    date = LocalDate.now(),
                    entries =
                        listOf(
                            previewEntry(
                                id = 4L,
                                name = "Скручивания",
                                category = ExerciseCategory.CORE,
                                sets = 3,
                                reps = 12
                            )
                        )
                )
            )
    )

/** Демо-запись drill-down: REPS при [reps], иначе TIME по [durationSec]. */
private fun previewEntry(
    id: Long,
    name: String,
    category: ExerciseCategory,
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
 * OverviewScreen): три категории с объёмами, суммы упражнений и записи
 * drill-down — донат, легенда и списки рендерятся как в рантайме.
 * [selected] открывает drill-down выбранной категории.
 */
internal fun demoStatsState(
    period: StatsPeriod,
    selected: ExerciseCategory? = null
): StatsUiState =
    StatsUiState(
        period = period,
        selected = selected,
        categoryTotals = demoCategoryTotals,
        exerciseTotals = demoExerciseTotals,
        drillGroups = demoDrillGroups,
        isEmpty = false
    )

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
    StatsScreenPreview(period = StatsPeriod.WEEK, selected = ExerciseCategory.PUSH)
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
    StatsScreenPreview(period = StatsPeriod.WEEK, selected = ExerciseCategory.PUSH)
}

@Composable
private fun StatsScreenPreview(
    period: StatsPeriod,
    selected: ExerciseCategory? = null,
    isEmpty: Boolean = false
) {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StatsScreen(
                state =
                    if (isEmpty) {
                        StatsUiState(period = period)
                    } else {
                        demoStatsState(period = period, selected = selected)
                    },
                onPeriodChange = {},
                onCategoryClick = {}
            )
        }
    }
}
