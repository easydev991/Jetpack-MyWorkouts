@file:Suppress("TooManyFunctions")

package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myworkouts.R
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.HeatmapDayData
import com.myworkouts.ui.state.ActivityUiState
import com.myworkouts.ui.state.StreakCardState
import com.myworkouts.ui.theme.HeatmapLevel1
import com.myworkouts.ui.theme.HeatmapLevel2
import com.myworkouts.ui.theme.HeatmapLevel3
import com.myworkouts.ui.theme.HeatmapLevel4
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.theme.StreakOrangeEnd
import com.myworkouts.ui.theme.StreakOrangeStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/*
 * Вкладка «Активность» экрана обзора (overview-screen, макет
 * `progress-drafts/iter4_v1.html`, вкладка activity): карточка серии
 * тренировок с градиентом + пикер периода + хитмап-сетка «день × неделя».
 */

/** Геометрия ячейки хитмапа, dp (макет). */
private const val HEATMAP_CELL_DP = 12

/** Зазор между ячейками хитмапа, dp (макет). */
private const val HEATMAP_GAP_DP = 4

/** Скругление ячейки хитмапа, dp (макет). */
private const val HEATMAP_CORNER_RADIUS_DP = 3

/** Число строк сетки хитмапа — дни недели. */
private const val HEATMAP_ROWS = 7

/** Внутренний отступ карточки хитмапа, dp. */
private const val HEATMAP_PADDING_DP = 16

/** Прозрачность подписи рекорда на градиентной карточке. */
private const val STREAK_SUBTITLE_ALPHA = 0.9f

/**
 * Альфа оригинального оранжевого для бледного фона
 * [StreakCardState.Pending] — «сохраняем теплоту» серии.
 */
private const val PENDING_ORANGE_START_ALPHA = 0.28f
private const val PENDING_ORANGE_END_ALPHA = 0.18f

/** Альфа огонька в [StreakCardState.Pending]. */
private const val FLAME_ALPHA_PENDING = 0.3f

/**
 * Вкладка «Активность»: карточка серии, пикер периода и карточка
 * с хитмап-сеткой.
 *
 * @param state Состояние вкладки ([ActivityUiState])
 * @param onPeriodChange Смена окна хитмапа
 * @param modifier Модификатор корневого контейнера
 */
@Composable
internal fun ActivityScreen(
    state: ActivityUiState,
    onPeriodChange: (ActivityPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
    ) {
        when {
            state.streakState == StreakCardState.Lost &&
                state.recordStreakDays > 1 ->
                StreakRecordRow(recordStreakDays = state.recordStreakDays)
            state.streakState != StreakCardState.Lost ->
                StreakCard(
                    streakDays = state.streakDays,
                    recordStreakDays = state.recordStreakDays,
                    state = state.streakState
                )
            // Lost + record == 1: единственная «запись о рекорде» не несёт
            // смысла — место StreakRecordRow остаётся пустым, период хитапапа
            // подтягивается ближе к верху.
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state.visiblePeriods.size >= 2) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                state.visiblePeriods.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = state.period == period,
                        onClick = { onPeriodChange(period) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = state.visiblePeriods.size)
                    ) {
                        Text(text = stringResource(id = period.labelResId))
                    }
                }
            }
        } else {
            MonthsHeader(heatmap = state.heatmap)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayLabels(modifier = Modifier.padding(top = 16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            HeatmapCard(heatmap = state.heatmap, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        LegendRow()
    }
}

/**
 * Жирный заголовок с месяцами данных (вместо пикера, когда виден один
 * период; overview-periods-and-shares, design D5): уникальные месяцы
 * записей из [heatmap] (дни с `count > 0`) по возрастанию,
 * standalone-названия ICU по локали, первая буква каждого — заглавная
 * (актуально только для ru — у en standalone-форма и так с заглавной),
 * разделитель — строковый ресурс (одинаков для ru/en).
 */
@Composable
private fun MonthsHeader(heatmap: List<HeatmapDayData>) {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val header =
        heatmap
            .filter { it.count > 0 }
            .map { YearMonth.from(it.date) }
            // heatmap строится по возрастанию дат — distinct() сохраняет порядок
            .distinct()
            .joinToString(separator = stringResource(R.string.overview_months_separator)) { month ->
                month.month
                    .getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    .replaceFirstChar { it.uppercase(locale) }
            }
    Text(
        text = header,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

/**
 * Подпись рекорда «Рекорд серии: N дней» — общая для строки [StreakRecordRow]
 * (состояние Lost) и подписи на карточке [StreakCard]. Число дней — без
 * «подряд» (см. спеку «Серии тренировок»).
 */
@Composable
private fun recordLabel(recordStreakDays: Int): String =
    stringResource(R.string.overview_streak_record) + " " +
        pluralStringResource(
            R.plurals.overview_streak_days,
            recordStreakDays,
            recordStreakDays
        )

/**
 * В Lost карточка серии не рисуется — вместо неё одна строка текста
 * «Рекорд серии: N дней», прижатая к левому краю. Маленький верхний
 * отступ не даёт ей прилипать к верхнему краю контента; Spacer 16dp
 * снизу (в [ActivityScreen]) сохраняет общий вертикальный ритм экрана.
 *
 * Вызывается только при [recordStreakDays] > 1 — для рекорда в один день
 * строка бессмысленна, и место в [ActivityScreen] остаётся пустым.
 */
@Composable
private fun StreakRecordRow(recordStreakDays: Int) {
    Text(
        text = recordLabel(recordStreakDays),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/**
 * Подписи дней недели слева от хитмапа: локализованные сокращения ICU
 * (`DayOfWeek.getDisplayName`, SHORT) на каждую строку сетки — подписи
 * «пн»…«вс» для ru и «Mon»… для прочих локалей даёт сама платформа,
 * отдельные ресурсы не нужны. Локаль берётся из конфигурации (per-app
 * language), чтобы текст совпадал с локалью ресурсов. Высота подписи
 * (12dp) и зазор (4dp) повторяют вертикальный ритм ячеек,
 * `padding(top = 16.dp)` — внутренний верхний отступ карточки, чтобы
 * строки совпали с ячейками.
 */
@Composable
private fun WeekdayLabels(modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val labels =
        remember(locale) {
            DayOfWeek.entries.map { day ->
                day.getDisplayName(TextStyle.SHORT, locale)
            }
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEach { label ->
            Box(
                modifier = Modifier.height(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Карточка серии: иконка огня слева, справа колонка — «14 дней подряд»
 * крупно и «Рекорд серии: 21 день» мелко. Подпись рекорда показывается,
 * только когда текущая серия меньше рекордной (в непрерывной серии
 * current = record — подпись избыточна, design Decision 5).
 *
 * В [StreakCardState.Pending] фон приглушается полупрозрачным оранжевым
 * и огонёк делается бледнее; текст переключается на тёмный, чтобы
 * читался на бледном фоне. Карточка для [StreakCardState.Lost] из
 * [ActivityScreen] сюда не доходит — её место занимает [StreakRecordRow].
 */
@Composable
private fun StreakCard(
    streakDays: Int,
    recordStreakDays: Int,
    state: StreakCardState
) {
    val isPending = state == StreakCardState.Pending
    val backgroundBrush =
        if (isPending) {
            Brush.linearGradient(
                listOf(
                    StreakOrangeStart.copy(alpha = PENDING_ORANGE_START_ALPHA),
                    StreakOrangeEnd.copy(alpha = PENDING_ORANGE_END_ALPHA)
                )
            )
        } else {
            Brush.linearGradient(listOf(StreakOrangeStart, StreakOrangeEnd))
        }
    // На бледном фоне белый текст не читается — переходим на secondary.
    val primaryText = if (isPending) MaterialTheme.colorScheme.secondary else Color.White
    val secondaryText =
        if (isPending) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            Color.White.copy(alpha = STREAK_SUBTITLE_ALPHA)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    brush = backgroundBrush,
                    shape = RoundedCornerShape(16.dp)
                ).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "🔥",
            fontSize = 28.sp,
            // Декоративная иконка — смысл несут подписи рядом
            modifier =
                Modifier
                    .alpha(if (isPending) FLAME_ALPHA_PENDING else 1f)
                    .clearAndSetSemantics { }
        )
        Column {
            Text(
                // Один день — "1 день", иначе — "N дней подряд".
                text =
                    pluralStringResource(
                        if (streakDays == 1) {
                            R.plurals.overview_streak_days
                        } else {
                            R.plurals.overview_streak_days_in_a_row
                        },
                        streakDays,
                        streakDays
                    ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )
            if (streakDays < recordStreakDays) {
                Text(
                    text = recordLabel(recordStreakDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText
                )
            }
        }
    }
}

/**
 * Карточка хитмапа: сетка «7 дней в высоту, недели в ширину» из
 * квадратиков 12dp со скруглением 3dp; уровень дня 0 — пустая ячейка,
 * 1..4 — оттенки зелёного ([HeatmapLevel1]..[HeatmapLevel4]). На «Год»
 * сетка заметно шире экрана — скроллится горизонтально внутри карточки,
 * подписи дней слева и легенда снизу остаются на месте.
 */
@Composable
private fun HeatmapCard(
    heatmap: List<HeatmapDayData>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
    ) {
        val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
        val cell = HEATMAP_CELL_DP.dp
        val gap = HEATMAP_GAP_DP.dp
        val columns = (heatmap.size + HEATMAP_ROWS - 1) / HEATMAP_ROWS
        val activeDays = heatmap.count { it.level > 0 }
        // Вычисляем a11y-строку в @Composable-контексте (внутри .semantics {}
        // это нельзя — лямбда SemanticsPropertyReceiver не @Composable).
        val heatmapA11y =
            pluralStringResource(
                R.plurals.a11y_activity_heatmap,
                activeDays,
                activeDays
            )
        Canvas(
            modifier =
                Modifier
                    .padding(HEATMAP_PADDING_DP.dp)
                    .horizontalScroll(rememberScrollState())
                    .size(
                        width = cell * columns + gap * (columns - 1),
                        height = cell * HEATMAP_ROWS + gap * (HEATMAP_ROWS - 1)
                    )
                    // Сетка — данные, а не декорация: суммарное описание
                    .semantics { contentDescription = heatmapA11y }
        ) {
            val cellPx = cell.toPx()
            val stepPx = cellPx + gap.toPx()
            heatmap.forEachIndexed { index, day ->
                drawRoundRect(
                    color =
                        if (day.level == 0) {
                            emptyColor
                        } else {
                            heatmapGreens[day.level - 1]
                        },
                    topLeft = Offset((index / HEATMAP_ROWS) * stepPx, (index % HEATMAP_ROWS) * stepPx),
                    size = Size(cellPx, cellPx),
                    cornerRadius = CornerRadius(HEATMAP_CORNER_RADIUS_DP.dp.toPx())
                )
            }
        }
    }
}

/**
 * Уровни 1..4 хитмапа: от светлого к тёмному
 * (порядок [HeatmapLevel1]..[HeatmapLevel4]).
 */
private val heatmapGreens = listOf(HeatmapLevel1, HeatmapLevel2, HeatmapLevel3, HeatmapLevel4)

/**
 * Мини-легенда хитмапа: «Меньше» + пять образцов + «Больше», прижаты вправо.
 * Порядок шкалы зависит от темы: в светлой — от пустой ячейки к самому
 * тёмному зелёному; в тёмной — в обратном порядке (серый «пустой» тон
 * соседствует с тёмно-зелёным, и градиент остаётся плавным).
 */
@Composable
private fun LegendRow() {
    val legendGreens =
        if (isSystemInDarkTheme()) {
            heatmapGreens.reversed()
        } else {
            heatmapGreens
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.activity_less),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        (listOf(MaterialTheme.colorScheme.surfaceContainerHighest) + legendGreens)
            .forEach { color ->
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(color = color, shape = RoundedCornerShape(2.dp))
                )
            }
        Text(
            text = stringResource(R.string.activity_more),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===== Превью (обе темы, showBackground, locale = "ru") =====
// Демо-данные — литеральные значения, детерминированная волна уровней
// хитмапа повторяет формулу макета.

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityMonthPreview() {
    ActivityScreenPreview(period = ActivityPeriod.MONTH)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityQuarterPreview() {
    ActivityScreenPreview(period = ActivityPeriod.QUARTER)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityHalfYearPreview() {
    ActivityScreenPreview(period = ActivityPeriod.HALFYEAR)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityYearPreview() {
    ActivityScreenPreview(period = ActivityPeriod.YEAR)
}

/**
 * Pending: вчера была тренировка и шёл стрик 3 дня, рекорд 7;
 * сегодня ещё не тренировались — карточка приглушается (бледный фон
 * и приглушённый огонёк), остальной экран без изменений.
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityStreakPendingPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreen(
                state =
                    ActivityUiState(
                        streakDays = 3,
                        recordStreakDays = 7,
                        streakState = StreakCardState.Pending,
                        period = ActivityPeriod.MONTH,
                        heatmap = demoHeatmap(ActivityPeriod.MONTH)
                    ),
                onPeriodChange = {}
            )
        }
    }
}

/**
 * Lost: стрик прервался после 3 дней; рекорд 3. StreakCard не рисуется,
 * вместо неё — обычная строка «Рекорд серии: 3 дня» с базовыми
 * вертикальными отступами, как и остальные секции экрана.
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityStreakLostPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreen(
                state =
                    ActivityUiState(
                        streakDays = 0,
                        recordStreakDays = 3,
                        streakState = StreakCardState.Lost,
                        period = ActivityPeriod.MONTH,
                        heatmap = demoHeatmap(ActivityPeriod.MONTH)
                    ),
                onPeriodChange = {}
            )
        }
    }
}

/**
 * Empty: стрика нет ни активного, ни исторического — но одна тренировка
 * существует (отдельная запись вразброс, без последовательных дней),
 * поэтому хитмап не пустой: одна активная ячейка, остальные пустые.
 * Секция стрика не рисуется (Lost + record ≤ 1).
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityStreakEmptyPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreen(
                state =
                    ActivityUiState(
                        streakDays = 0,
                        recordStreakDays = 1,
                        streakState = StreakCardState.Lost,
                        period = ActivityPeriod.MONTH,
                        heatmap = singleTrainingHeatmap()
                    ),
                onPeriodChange = {}
            )
        }
    }
}

/**
 * No data: новый пользователь, ни одной тренировки вообще — `streakDays = 0`,
 * `recordStreakDays = 0`, хитмап пустой. Секция стрика не рисуется,
 * хитмап показывает пустые ячейки за весь период.
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityNoDataPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreen(
                state =
                    ActivityUiState(
                        streakDays = 0,
                        recordStreakDays = 0,
                        streakState = StreakCardState.Lost,
                        period = ActivityPeriod.MONTH,
                        heatmap = emptyList()
                    ),
                onPeriodChange = {}
            )
        }
    }
}

/** Хитмап для [ActivityStreakEmptyPreview]: одна активная ячейка на всё окно. */
private fun singleTrainingHeatmap(): List<HeatmapDayData> {
    val today = LocalDate.now()
    val center = ActivityPeriod.MONTH.days / 2
    return List(ActivityPeriod.MONTH.days) { index ->
        val date = today.minusDays((ActivityPeriod.MONTH.days - 1 - index).toLong())
        if (index == center) {
            HeatmapDayData(date = date, count = 1, level = 1)
        } else {
            HeatmapDayData(date = date, count = 0, level = 0)
        }
    }
}

/** Текущая серия равна рекордной — подпись рекорда скрыта (D5). */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityStreakEqualsRecordPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreen(
                state =
                    ActivityUiState(
                        streakDays = 21,
                        recordStreakDays = 21,
                        period = ActivityPeriod.MONTH,
                        heatmap = demoHeatmap(ActivityPeriod.MONTH)
                    ),
                onPeriodChange = {}
            )
        }
    }
}

@Composable
private fun ActivityScreenPreview(
    period: ActivityPeriod,
    visiblePeriods: List<ActivityPeriod> = ActivityPeriod.entries.toList()
) {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreen(
                state =
                    ActivityUiState(
                        streakDays = 14,
                        recordStreakDays = 21,
                        period = period,
                        heatmap = demoHeatmap(period),
                        visiblePeriods = visiblePeriods
                    ),
                onPeriodChange = {}
            )
        }
    }
}

/** Один видимый период: вместо пикера — жирный заголовок месяцев данных. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityMonthsHeaderPreview() {
    ActivityScreenPreview(period = ActivityPeriod.HALFYEAR, visiblePeriods = listOf(ActivityPeriod.HALFYEAR))
}

/**
 * Демо-хитмап превью (переиспользуется OverviewScreen.kt и тестом):
 * детерминированная волна уровней 0..4, окно заканчивается сегодня.
 * `MagicNumber` подавлен осознанно: литералы — сама формула волны
 * из макета, единственная правляемая точка.
 */
@Suppress("MagicNumber")
internal fun demoHeatmap(period: ActivityPeriod): List<HeatmapDayData> {
    val today = LocalDate.now()
    return List(period.days) { index ->
        val level =
            floor(abs(sin(index * 0.7 + 1.3)) * 5).toInt().coerceIn(0, 4)
        HeatmapDayData(
            date = today.minusDays((period.days - 1 - index).toLong()),
            count = level,
            level = level
        )
    }
}
