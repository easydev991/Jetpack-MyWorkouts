@file:Suppress("TooManyFunctions")

package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/*
 * DRAFT экрана «Активность» по макету `progress-drafts/iter4_v1.html`
 * (вкладка activity): карточка серии тренировок с градиентом + пикер
 * периода + хитмап-сетка «день × неделя».
 *
 * Черновик в одном файле: экран, модели и демо-данные. Цвета и стили
 * макета не копируются (кроме демо-градиента и уровней зелёного — они
 * помечены DRAFT) — только расположение и иерархия элементов.
 * При переносе в фичу:
 * - [ActivityScreenContent] и модели → `ActivityScreen.kt` (+ UiState/VM),
 * - тексты «Месяц»/«Квартал»/«Полгода», формы слова «день» → strings.xml
 *   (для дней — plurals),
 * - расчёт серии и уровней дней → use-case/VM по TDD (сначала тест).
 */

/** Период хитмапа: длина в днях задаёт число колонок сетки. */
internal enum class ActivityPeriod(
    val days: Int
) {
    MONTH(30),
    QUARTER(91),
    HALFYEAR(182),
    YEAR(365)
}

/** UiState экрана «Активность». */
internal data class ActivityUiState(
    /** Текущая серия тренировок, дней. */
    val streakDays: Int,
    /** Рекордная серия, дней. */
    val recordStreakDays: Int,
    val period: ActivityPeriod
)

/**
 * Содержимое экрана «Активность»: карточка серии, пикер периода
 * («Месяц»/«Квартал»/«Полгода», как `.period` макета) и карточка
 * с хитмап-сеткой.
 */
@Composable
internal fun ActivityScreenContent(
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
        StreakCard(
            streakDays = state.streakDays,
            recordStreakDays = state.recordStreakDays
        )
        Spacer(modifier = Modifier.height(16.dp))
        // DRAFT: строки захардкожены, при переносе — строковые ресурсы
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ActivityPeriod.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = state.period == period,
                    onClick = { onPeriodChange(period) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ActivityPeriod.entries.size)
                ) {
                    Text(
                        text =
                            when (period) {
                                ActivityPeriod.MONTH -> "Месяц"
                                ActivityPeriod.QUARTER -> "Квартал"
                                ActivityPeriod.HALFYEAR -> "Полгода"
                                ActivityPeriod.YEAR -> "Год"
                            }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            WeekdayLabels(modifier = Modifier.padding(top = 16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            HeatmapCard(period = state.period, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        LegendRow()
    }
}

/**
 * Подписи дней недели слева от хитмапа: две буквы на каждую строку
 * сетки. Высота подписи (12dp) и зазор (4dp) повторяют вертикальный
 * ритм ячеек, `padding(top = 16.dp)` — внутренний верхний отступ
 * карточки, чтобы строки совпали с ячейками.
 */
@Composable
private fun WeekdayLabels(modifier: Modifier = Modifier) =
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // DRAFT: строки захардкожены, при переносе — строковые ресурсы
        listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс").forEach { label ->
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

/**
 * Карточка серии (`.streak-card` макета): иконка огня слева, справа
 * колонка — «14 дней» крупно и «Рекорд серии: 21 день» мелко. Градиент
 * и белый текст — демо-цвета макета (DRAFT).
 */
@Composable
private fun StreakCard(
    streakDays: Int,
    recordStreakDays: Int
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(listOf(Color(0xFFFF5722), Color(0xFFFF9800))),
                    shape = RoundedCornerShape(16.dp)
                ).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // DRAFT: эмодзи как в макете; при переносе — иконка/строковый ресурс
        Text(text = "🔥", fontSize = 28.sp)
        Column {
            Text(
                text = "$streakDays ${daysWord(streakDays)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Рекорд серии: $recordStreakDays ${daysWord(recordStreakDays)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/**
 * Карточка хитмапа (`.heatmap` макета): сетка «7 дней в высоту, недели
 * в ширину» из квадратиков 12dp с скруглением 3dp; уровень дня 0 —
 * пустая ячейка, 1..4 — оттенки зелёного. На «Год» сетка из 53 колонок
 * заметно шире экрана — скроллится горизонтально внутри карточки
 * (как `overflow-x` в макете), подписи дней слева и легенда снизу
 * остаются на месте.
 */
@Composable
private fun HeatmapCard(
    period: ActivityPeriod,
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
        val cell = 12.dp
        val gap = 4.dp
        val levels = demoLevels(period)
        val columns = (levels.size + 6) / 7
        Canvas(
            modifier =
                Modifier
                    .padding(16.dp)
                    .horizontalScroll(rememberScrollState())
                    .size(
                        width = cell * columns + gap * (columns - 1),
                        height = cell * 7 + gap * 6
                    )
        ) {
            val cellPx = cell.toPx()
            val stepPx = cellPx + gap.toPx()
            levels.forEachIndexed { index, level ->
                drawRoundRect(
                    color =
                        if (level == 0) {
                            emptyColor
                        } else {
                            heatmapGreens[level - 1]
                        },
                    topLeft = Offset((index / 7) * stepPx, (index % 7) * stepPx),
                    size = Size(cellPx, cellPx),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}

/**
 * Мини-легенда хитмапа (аналог легенды GitHub): «Меньше» + пять образцов
 * от пустой ячейки к самому тёмному зелёному + «Больше», прижаты вправо.
 */
@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // DRAFT: строки захардкожены, при переносе — строковые ресурсы
        Text(
            text = "Меньше",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        (listOf(MaterialTheme.colorScheme.surfaceContainerHighest) + heatmapGreens.reversed())
            .forEach { color ->
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(color = color, shape = RoundedCornerShape(2.dp))
                )
            }
        Text(
            text = "Больше",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Русская форма слова «день»: 1 день / 2 дня / 14 дней / 21 день.
 * DRAFT: при переносе — строковые ресурсы с plurals
 */
private fun daysWord(days: Int): String =
    when {
        days % 10 == 1 && days % 100 != 11 -> "день"
        days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
        else -> "дней"
    }

// ===== Демо-данные (черновик данных будущего use-case) =====

/** Уровни зелёного хитмапа из макета (l1..l4), одинаковые для обеих тем. */
private val heatmapGreens =
    listOf(
        Color(0xFF1B5E20),
        Color(0xFF2E7D32),
        Color(0xFF4CAF50),
        Color(0xFF81C784)
    )

/** Демо-уровни дней 0..4: детерминированная волна — формула из макета. */
private fun demoLevels(period: ActivityPeriod): List<Int> =
    List(period.days) { index ->
        floor(abs(sin(index * 0.7 + 1.3)) * 5).toInt().coerceIn(0, 4)
    }

/** Демо-UiState экрана: числа серии из макета (14 дней, рекорд 21 день). */
internal fun demoActivityState(period: ActivityPeriod): ActivityUiState =
    ActivityUiState(streakDays = 14, recordStreakDays = 21, period = period)

// ===== Превью (конвенция: светлая + тёмная темы, showBackground, locale = "ru") =====

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityScreenMonthPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreenContent(
                state = demoActivityState(ActivityPeriod.MONTH),
                onPeriodChange = {}
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityScreenQuarterPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreenContent(
                state = demoActivityState(ActivityPeriod.QUARTER),
                onPeriodChange = {}
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityScreenHalfYearPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreenContent(
                state = demoActivityState(ActivityPeriod.HALFYEAR),
                onPeriodChange = {}
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ActivityScreenYearPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ActivityScreenContent(
                state = demoActivityState(ActivityPeriod.YEAR),
                onPeriodChange = {}
            )
        }
    }
}
