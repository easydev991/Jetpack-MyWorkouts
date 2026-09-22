@file:Suppress("TooManyFunctions", "LongParameterList", "ReturnCount", "MaxLineLength")

package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * DRAFT экрана «Статистика» по макетам `progress-drafts/iter4_v1.html`
 * (вкладка stats, портрет) и `progress-drafts/iter5_v1.html`
 * (ландшафт): пикер периода, донат по категориям, легенда
 * с drill-down в упражнения и список записей категории. В ландшафте —
 * строка [донат | легенда | записи], у легенды и записей свои скроллы.
 *
 * Черновик в одном файле: экран, модели, расчёт долей/оттенков и
 * демо-данные. Цвета и стили макета не копируются (кроме демо-цветов
 * категорий — помечены DRAFT) — только расположение и иерархия.
 * При переносе в фичу:
 * - [StatsScreenContent] и модели → `StatsScreen.kt` (+ UiState/VM),
 * - тексты периодов/«повт.»/«← Категории»/подсказок → strings.xml,
 * - расчёт долей и записей периода → use-case/VM по TDD (сначала тест),
 * - аккордеон легенды из макета не портирован: в JS итерации 4 класс
 *   `.expanded` не добавляется, клик сразу ведёт в drill-down.
 */

/** Период статистики (`.period` макета: День/Неделя/Месяц/Год). */
internal enum class StatsPeriod { DAY, WEEK, MONTH, YEAR }

/**
 * Категория упражнений: демо-цвет и название из макета (DRAFT; при
 * переносе — решение по палитре категорий и строковые ресурсы).
 */
internal enum class StatsCategory(
    val label: String,
    val baseColor: Color
) {
    PUSH("Толкающие", Color(0xFFFF5252)),
    PULL("Тянущие", Color(0xFF448AFF)),
    LEGS("Ноги", Color(0xFF69F0AE)),
    CORE("Кор", Color(0xFFE040FB)),
    CARDIO("Кардио", Color(0xFFFFD740))
}

/** Доля доната: цвет сектора и суммарное значение (повторы). */
internal data class DonutSlice(
    val color: Color,
    val value: Int
)

/** Строка легенды: в обзоре — категория (кликабельна), в drill-down — упражнение. */
internal data class StatsLegendItem(
    val label: String,
    val value: Int,
    val color: Color,
    /** Категория для drill-down; `null` у строк упражнений (некликабельны). */
    val category: StatsCategory?
)

/** Запись в drill-down категории (упрощённая демо-модель). */
internal data class DrillEntry(
    val exerciseName: String,
    val date: LocalDate,
    val sets: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val comment: String? = null
)

/** UiState экрана «Статистика» (аналог `renderStats` макета). */
internal data class StatsUiState(
    val period: StatsPeriod,
    /** Выбранная категория drill-down; `null` — обзор по категориям. */
    val selected: StatsCategory?,
    val slices: List<DonutSlice>,
    val legend: List<StatsLegendItem>,
    val isEmpty: Boolean,
    /** Записи выбранной категории по датам, убывание (только drill-down). */
    val drillGroups: List<Pair<LocalDate, List<DrillEntry>>> = emptyList()
)

/**
 * Содержимое экрана «Статистика»: пикер периода, донат, легенда и
 * (в drill-down) список записей категории. Пустой период — подсказка
 * «Нет тренировок за выбранный период», как `.empty-hint` макета.
 * В ландшафте (макет iter5_v1.html) раскладка перестраивается в строку
 * [донат | легенда | записи] со скроллами внутри областей.
 */
@Composable
internal fun StatsScreenContent(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCategoryClick: (StatsCategory?) -> Unit,
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
    onCategoryClick: (StatsCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    val showDrill = !state.isEmpty && state.selected != null
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
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DonutChart(slices = state.slices)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LegendSection(
                        items = state.legend,
                        onCategoryClick = onCategoryClick
                    )
                }
            }
        }
        if (showDrill) {
            drillItems(groups = state.drillGroups)
        }
    }
}

/**
 * Ландшафтная раскладка (`@media (min-aspect-ratio: 1/1)` iter5_v1.html):
 * период сверху, ниже строка [донат | легенда | записи] с гэпом 12.
 * Донат уменьшен до 160dp и центрирован по вертикали; легенда (в обзоре —
 * сетка в две колонки, в drill-down — одна колонка) и список записей
 * делят остаток ширины поровну и скроллятся независимо. Без drill-down
 * список записей скрыт, как `display: none` макета.
 */
@Composable
private fun StatsScreenLandscapeBody(
    state: StatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onCategoryClick: (StatsCategory?) -> Unit,
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
                DonutChart(slices = state.slices, size = 160.dp)
                if (state.selected == null) {
                    OverviewLegendGrid(
                        items = state.legend,
                        onCategoryClick = onCategoryClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LegendSection(
                        items = state.legend,
                        onCategoryClick = onCategoryClick,
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                    )
                    DrillEntriesLazyList(
                        groups = state.drillGroups,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Подсказка пустого периода (`.empty-hint` макета). */
@Composable
private fun EmptyPeriodHint(modifier: Modifier = Modifier) {
    // DRAFT: строка захардкожена, при переносе — строковый ресурс
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Нет тренировок за выбранный период",
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
 * Легенда обзора в ландшафте (`.legend` iter5_v1.html): сетка в две
 * колонки одинаковой ширины, своя вертикальная прокрутка; строки —
 * те же карточки категорий, что и в портрете.
 */
@Composable
private fun OverviewLegendGrid(
    items: List<StatsLegendItem>,
    onCategoryClick: (StatsCategory?) -> Unit,
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
    // DRAFT: строки захардкожены, при переносе — строковые ресурсы
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
                Text(
                    text =
                        when (period) {
                            StatsPeriod.DAY -> "День"
                            StatsPeriod.WEEK -> "Неделя"
                            StatsPeriod.MONTH -> "Месяц"
                            StatsPeriod.YEAR -> "Год"
                        }
                )
            }
        }
    }
}

/**
 * Донат (`.donut` макета): секторы без зазоров, начиная с 12 часов;
 * толщина кольца 24dp при размере 200dp (в ландшафте — 160dp, как
 * `.donut-wrap` iter5_v1.html), центр пустой.
 */
@Composable
private fun DonutChart(
    slices: List<DonutSlice>,
    size: Dp = 200.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val total = slices.sumOf { it.value }.coerceAtLeast(1)
        val stroke = Stroke(width = 24.dp.toPx())
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = slice.value.toFloat() / total * 360f
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = stroke
            )
            startAngle += sweep
        }
    }
}

/**
 * Легенда (`.legend` макета): в обзоре — строки категорий по отдельности
 * (клик открывает drill-down), в drill-down — сверху кнопка «← Категории»
 * и одна карточка со строками упражнений: тот же стиль, что в карточке
 * рекордов — контейнер `surfaceContainerLow`, между строками divider
 * `surfaceContainerHighest`.
 */
@Composable
private fun LegendSection(
    items: List<StatsLegendItem>,
    onCategoryClick: (StatsCategory?) -> Unit,
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

/** Содержимое строки легенды: образец цвета, название, жирное значение справа. */
@Composable
private fun LegendItemContent(item: StatsLegendItem) {
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
        // DRAFT: «повт.» — при переносе строковый ресурс
        Text(
            text = "${item.value} повт.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Кнопка возврата из drill-down (`.legend-back` макета). */
@Composable
private fun DrillBackRow(onClick: () -> Unit) {
    // DRAFT: строка захардкожена, при переносе — строковый ресурс
    FilledTonalButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(text = "Категории")
    }
}

/**
 * Записи drill-down (`.workout-list` макета) как элементы LazyColumn:
 * заголовки секций дат («линия — дата — линия», сегодня — без
 * заголовка) и строки записей. Общее для портрета и ландшафта.
 * Ключи — дата + название упражнения
 * (DRAFT: у DrillEntry нет id; при переносе — id записи).
 */
private fun LazyListScope.drillItems(groups: List<Pair<LocalDate, List<DrillEntry>>>) {
    groups.forEach { (date, entries) ->
        val label = sectionDateLabel(date)
        if (label != null) {
            item(key = "hdr-$date") { SectionHeader(label = label) }
        }
        items(entries, key = { entry -> "$date ${entry.exerciseName}" }) { entry ->
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
    groups: List<Pair<LocalDate, List<DrillEntry>>>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        drillItems(groups = groups)
    }
}

/** Заголовок секции даты (`.section-hdr` макета). */
@Composable
private fun SectionHeader(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * Строка записи drill-down (`.entry-row` макета): «название — значение»,
 * ниже доп. вес и комментарий при наличии. Образец — строка записи
 * главного экрана.
 */
@Composable
private fun DrillEntryRow(entry: DrillEntry) {
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
                    // DRAFT: разделитель « х » — при переносе строковый
                    // ресурс (formatEntryValue главного экрана)
                    text = "${entry.sets} х ${entry.reps}",
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
                        // DRAFT: строка захардкожена, при переносе —
                        // строковый ресурс
                        text = "Дополнительный вес",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatWeightText(weightKg),
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

// ===== Демо-данные (черновик данных будущего use-case) =====

/** Данные категории за период: сумма и разбивка по упражнениям. */
private data class DemoCatData(
    val total: Int,
    val exercises: List<DemoExerciseAmount>
)

/** Упражнение в категории: название и сумма за период. */
private data class DemoExerciseAmount(
    val name: String,
    val amount: Int
)

// Демо-данные периодов — числа из макета (DATA в iter4_v1.html)
private val demoDayData: Map<StatsCategory, DemoCatData> =
    mapOf(
        StatsCategory.PUSH to
            DemoCatData(
                80,
                listOf(
                    DemoExerciseAmount("Отжимания", 60),
                    DemoExerciseAmount("Отжимания узким хватом", 20)
                )
            ),
        StatsCategory.PULL to
            DemoCatData(
                45,
                listOf(
                    DemoExerciseAmount("Подтягивания", 30),
                    DemoExerciseAmount("Австралийские подтягивания", 15)
                )
            ),
        StatsCategory.LEGS to
            DemoCatData(
                70,
                listOf(
                    DemoExerciseAmount("Приседания", 50),
                    DemoExerciseAmount("Выпады", 20)
                )
            ),
        StatsCategory.CORE to DemoCatData(0, emptyList()),
        StatsCategory.CARDIO to
            DemoCatData(30, listOf(DemoExerciseAmount("Бёрпи", 30)))
    )

private val demoWeekData: Map<StatsCategory, DemoCatData> =
    mapOf(
        StatsCategory.PUSH to
            DemoCatData(
                480,
                listOf(
                    DemoExerciseAmount("Отжимания", 360),
                    DemoExerciseAmount("Отжимания узким хватом", 120)
                )
            ),
        StatsCategory.PULL to
            DemoCatData(
                240,
                listOf(
                    DemoExerciseAmount("Подтягивания", 180),
                    DemoExerciseAmount("Австралийские подтягивания", 60)
                )
            ),
        StatsCategory.LEGS to
            DemoCatData(
                380,
                listOf(
                    DemoExerciseAmount("Приседания", 280),
                    DemoExerciseAmount("Выпады", 100)
                )
            ),
        StatsCategory.CORE to
            DemoCatData(
                200,
                listOf(
                    DemoExerciseAmount("Скручивания", 130),
                    DemoExerciseAmount("Подъём ног", 70)
                )
            ),
        StatsCategory.CARDIO to
            DemoCatData(
                180,
                listOf(
                    DemoExerciseAmount("Бёрпи", 120),
                    DemoExerciseAmount("Скакалка", 60)
                )
            )
    )

private val demoMonthData: Map<StatsCategory, DemoCatData> =
    mapOf(
        StatsCategory.PUSH to
            DemoCatData(
                2100,
                listOf(
                    DemoExerciseAmount("Отжимания", 1500),
                    DemoExerciseAmount("Отжимания узким хватом", 600)
                )
            ),
        StatsCategory.PULL to
            DemoCatData(
                1050,
                listOf(
                    DemoExerciseAmount("Подтягивания", 750),
                    DemoExerciseAmount("Австралийские подтягивания", 300)
                )
            ),
        StatsCategory.LEGS to
            DemoCatData(
                1700,
                listOf(
                    DemoExerciseAmount("Приседания", 1200),
                    DemoExerciseAmount("Выпады", 500)
                )
            ),
        StatsCategory.CORE to
            DemoCatData(
                880,
                listOf(
                    DemoExerciseAmount("Скручивания", 560),
                    DemoExerciseAmount("Подъём ног", 320)
                )
            ),
        StatsCategory.CARDIO to
            DemoCatData(
                720,
                listOf(
                    DemoExerciseAmount("Бёрпи", 480),
                    DemoExerciseAmount("Скакалка", 240)
                )
            )
    )

private val demoYearData: Map<StatsCategory, DemoCatData> =
    mapOf(
        StatsCategory.PUSH to
            DemoCatData(
                24000,
                listOf(
                    DemoExerciseAmount("Отжимания", 18000),
                    DemoExerciseAmount("Отжимания узким хватом", 6000)
                )
            ),
        StatsCategory.PULL to
            DemoCatData(
                12000,
                listOf(
                    DemoExerciseAmount("Подтягивания", 9000),
                    DemoExerciseAmount("Австралийские подтягивания", 3000)
                )
            ),
        StatsCategory.LEGS to
            DemoCatData(
                19500,
                listOf(
                    DemoExerciseAmount("Приседания", 14000),
                    DemoExerciseAmount("Выпады", 5500)
                )
            ),
        StatsCategory.CORE to
            DemoCatData(
                10500,
                listOf(
                    DemoExerciseAmount("Скручивания", 6800),
                    DemoExerciseAmount("Подъём ног", 3700)
                )
            ),
        StatsCategory.CARDIO to
            DemoCatData(
                8400,
                listOf(
                    DemoExerciseAmount("Бёрпи", 5600),
                    DemoExerciseAmount("Скакалка", 2800)
                )
            )
    )

private val demoDataByPeriod: Map<StatsPeriod, Map<StatsCategory, DemoCatData>> =
    mapOf(
        StatsPeriod.DAY to demoDayData,
        StatsPeriod.WEEK to demoWeekData,
        StatsPeriod.MONTH to demoMonthData,
        StatsPeriod.YEAR to demoYearData
    )

/** Записи drill-down по категориям: даты покрывают сегодня (без
 * заголовка), «Вчера», текущий год и архивный формат прошлого года. */
private val demoDrillEntries: Map<StatsCategory, List<DrillEntry>> =
    mapOf(
        StatsCategory.PUSH to
            listOf(
                drillEntry("Отжимания", daysAgo = 0, sets = 4, reps = 20, weightKg = 7.5, comment = "темп медленный"),
                drillEntry("Отжимания узким хватом", daysAgo = 1, sets = 3, reps = 12),
                drillEntry("Отжимания", daysAgo = 1, sets = 5, reps = 15),
                drillEntry("Отжимания", daysAgo = 3, sets = 4, reps = 18, weightKg = 10.0),
                drillEntry(
                    "Отжимания узким хватом",
                    daysAgo = 270,
                    sets = 3,
                    reps = 10,
                    comment =
                        "Держал спину прямо, лопатки сведены; в следующий раз " +
                            "добавить вес и отдохнуть подольше между подходами"
                )
            ),
        StatsCategory.PULL to
            listOf(
                drillEntry("Подтягивания", daysAgo = 0, sets = 3, reps = 10),
                drillEntry(
                    "Австралийские подтягивания",
                    daysAgo = 2,
                    sets = 3,
                    reps = 15,
                    comment = "ноги на опоре, тело в прямой линии"
                )
            ),
        StatsCategory.LEGS to
            listOf(
                drillEntry("Приседания", daysAgo = 1, sets = 4, reps = 30, weightKg = 12.5),
                drillEntry("Выпады", daysAgo = 5, sets = 3, reps = 20)
            ),
        StatsCategory.CORE to
            listOf(
                drillEntry("Скручивания", daysAgo = 1, sets = 3, reps = 30),
                drillEntry("Подъём ног", daysAgo = 4, sets = 3, reps = 15)
            ),
        StatsCategory.CARDIO to
            listOf(
                drillEntry("Бёрпи", daysAgo = 0, sets = 4, reps = 15, comment = "сразу после сна"),
                drillEntry("Скакалка", daysAgo = 6, sets = 3, reps = 80)
            )
    )

private fun drillEntry(
    name: String,
    daysAgo: Long,
    sets: Int,
    reps: Int,
    weightKg: Double? = null,
    comment: String? = null
): DrillEntry =
    DrillEntry(
        exerciseName = name,
        date = LocalDate.now().minusDays(daysAgo),
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        comment = comment
    )

/**
 * Вес записи: «10 кг» для целых, «7,5 кг» для половинок.
 * DRAFT: при переносе — formatWeightKg и строковый ресурс
 */
private fun formatWeightText(weightKg: Double): String =
    if (weightKg % 1.0 == 0.0) {
        "${weightKg.toInt()} кг"
    } else {
        "$weightKg".replace('.', ',') + " кг"
    }

/**
 * Заголовок секции даты — правила главного экрана: сегодня без
 * заголовка (`null`), «Вчера», «пн, 22 сент.» (текущий год),
 * «пт, 26.12.2025» (прошлый год). DRAFT: локаль захардкожена.
 */
private fun sectionDateLabel(date: LocalDate): String? {
    val today = LocalDate.now()
    return when {
        date == today -> null
        date == today.minusDays(1) -> "Вчера"
        date.year == today.year ->
            date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale("ru")))
        else ->
            date.format(DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale("ru")))
    }
}

// Оттенки долей упражнений внутри категории (SHADE_DELTAS макета):
// чередуем светлее/темнее для контраста соседних долей
private val shadeDeltas = listOf(0, 32, -28, 45, -40)

private fun shadeDelta(index: Int): Int = shadeDeltas[index.coerceAtMost(shadeDeltas.lastIndex)]

/**
 * Оттенок цвета категории: сдвиг светлоты в HSL, hue и saturation
 * сохраняются (аналог `shade` макета).
 */
private fun shade(
    base: Color,
    deltaL: Int
): Color {
    val hsl = FloatArray(size = 3)
    ColorUtils.colorToHSL(base.toArgb(), hsl)
    hsl[2] = (hsl[2] + deltaL / 100f).coerceIn(0.08f, 0.92f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * UiState для демо-данных: обзор по категориям или drill-down
 * в выбранную категорию (пустые категории и упражнения отфильтрованы,
 * как `renderStats` макета).
 */
internal fun demoStatsState(
    period: StatsPeriod,
    selected: StatsCategory? = null
): StatsUiState {
    val data = demoDataByPeriod.getValue(period)
    val visible = StatsCategory.entries.filter { (data[it]?.total ?: 0) > 0 }
    if (visible.isEmpty()) {
        return StatsUiState(period, null, emptyList(), emptyList(), isEmpty = true)
    }
    val sel = selected?.takeIf { it in visible }
    if (sel == null) {
        return StatsUiState(
            period = period,
            selected = null,
            slices = visible.map { DonutSlice(it.baseColor, data.getValue(it).total) },
            legend =
                visible.map {
                    StatsLegendItem(it.label, data.getValue(it).total, it.baseColor, category = it)
                },
            isEmpty = false
        )
    }
    val exercises = data.getValue(sel).exercises.filter { it.amount > 0 }
    return StatsUiState(
        period = period,
        selected = sel,
        slices =
            exercises.mapIndexed { index, exercise ->
                DonutSlice(shade(sel.baseColor, shadeDelta(index)), exercise.amount)
            },
        legend =
            exercises.mapIndexed { index, exercise ->
                StatsLegendItem(
                    exercise.name,
                    exercise.amount,
                    shade(sel.baseColor, shadeDelta(index)),
                    category = null
                )
            },
        isEmpty = false,
        drillGroups =
            demoDrillEntries
                .getValue(sel)
                .groupBy { it.date }
                .toList()
                .sortedByDescending { it.first }
    )
}

// ===== Превью (конвенция: светлая + тёмная темы, showBackground, locale = "ru") =====

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsScreenWeekPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StatsScreenContent(
                state = demoStatsState(StatsPeriod.WEEK),
                onPeriodChange = {},
                onCategoryClick = {}
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsScreenYearPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StatsScreenContent(
                state = demoStatsState(StatsPeriod.YEAR),
                onPeriodChange = {},
                onCategoryClick = {}
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsScreenDrillDownPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StatsScreenContent(
                state = demoStatsState(StatsPeriod.WEEK, selected = StatsCategory.PUSH),
                onPeriodChange = {},
                onCategoryClick = {}
            )
        }
    }
}

// Ландшафт (макет iter5_v1.html): размер превью шире высоты —
// LocalConfiguration даёт ORIENTATION_LANDSCAPE.

@Preview(widthDp = 891, heightDp = 411, showBackground = true, locale = "ru")
@Preview(widthDp = 891, heightDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsScreenLandscapeWeekPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StatsScreenContent(
                state = demoStatsState(StatsPeriod.WEEK),
                onPeriodChange = {},
                onCategoryClick = {}
            )
        }
    }
}

@Preview(widthDp = 891, heightDp = 411, showBackground = true, locale = "ru")
@Preview(widthDp = 891, heightDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun StatsScreenLandscapeDrillDownPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StatsScreenContent(
                state = demoStatsState(StatsPeriod.WEEK, selected = StatsCategory.PUSH),
                onPeriodChange = {},
                onCategoryClick = {}
            )
        }
    }
}
