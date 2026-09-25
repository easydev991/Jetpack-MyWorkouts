package com.myworkouts.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.myworkouts.R
import kotlin.math.roundToInt

/** Секунд в часе — для разложения длительности на части. */
internal const val SECONDS_PER_HOUR: Int = 3600

/** Секунд в минуте — для разложения длительности на части. */
internal const val SECONDS_PER_MINUTE: Int = 60

/** Секунд в сутках — верхняя граница часовой единицы компактного формата. */
internal const val SECONDS_PER_DAY: Int = 86_400

/**
 * Форматирует длительность в секундах в человекочитаемую строку:
 * нулевые части опускаются («5 сек», «1 мин», «2 мин 30 сек», «1 час»).
 *
 * Общая функция экрана создания записи и строки списка главного экрана.
 *
 * @param seconds Длительность в секундах
 * @param hoursText Готовый локализованный текст часовой части
 *   («1 час»/«2 часа» — plurals-ресурс); часовая часть добавляется,
 *   только если [seconds] ≥ 3600
 * @param minutesShort Локализованное сокращение минут («мин»/«min») —
 *   не склоняется
 * @param secondsShort Локализованное сокращение секунд («сек»/«sec») —
 *   не склоняется
 */
internal fun formatDuration(
    seconds: Int,
    hoursText: String,
    minutesShort: String,
    secondsShort: String
): String {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    return buildList {
        if (hours > 0) add(hoursText)
        if (minutes > 0) add("$minutes $minutesShort")
        if (secs > 0) add("$secs $secondsShort")
    }.joinToString(separator = " ")
}

/**
 * UI-обёртка над [formatDuration]: подтягивает локализованные части
 * из ресурсов (слово «час» — plurals, минуты/секунды — сокращения).
 */
@Composable
internal fun formatDurationResource(seconds: Int): String {
    val minutesShort = stringResource(R.string.entry_duration_minutes_short)
    val secondsShort = stringResource(R.string.entry_duration_seconds_short)
    val hours = seconds / SECONDS_PER_HOUR
    val hoursText =
        if (hours > 0) {
            pluralStringResource(R.plurals.entry_duration_hours, hours, hours)
        } else {
            ""
        }
    return formatDuration(
        seconds = seconds,
        hoursText = hoursText,
        minutesShort = minutesShort,
        secondsShort = secondsShort
    )
}

/** Округление секунд до целого числа заданной единицы (90 сек → 2 мин). */
private fun Int.roundToUnit(unitSeconds: Int): Int = (toDouble() / unitSeconds).roundToInt()

/**
 * Компактная одноединицная форма длительности для значений легенды
 * статистики (overview-stats-time-support design Decision 3): одна
 * адаптивная единица — менее минуты «N сек.», менее часа «N мин.»,
 * менее суток «N ч.», иначе «N дн.»; границы строгие (ровно 3600 →
 * «1 ч.», ровно 86 400 → «1 дн.»), дробное округляется до ближайшего
 * целого (90 сек → «2 мин.»).
 *
 * @param seconds Длительность в секундах
 * @param secondsUnit Локализованная единица секунд с точкой («сек.»)
 * @param minutesUnit Локализованная единица минут с точкой («мин.»)
 * @param hoursUnit Локализованная единица часов с точкой («ч.»)
 * @param daysUnit Локализованная единица дней с точкой («дн.»)
 */
internal fun formatStatsDurationCompact(
    seconds: Int,
    secondsUnit: String,
    minutesUnit: String,
    hoursUnit: String,
    daysUnit: String
): String {
    val (value, unit) =
        when {
            seconds < SECONDS_PER_MINUTE -> seconds to secondsUnit
            seconds < SECONDS_PER_HOUR -> seconds.roundToUnit(SECONDS_PER_MINUTE) to minutesUnit
            seconds < SECONDS_PER_DAY -> seconds.roundToUnit(SECONDS_PER_HOUR) to hoursUnit
            else -> seconds.roundToUnit(SECONDS_PER_DAY) to daysUnit
        }
    return "$value $unit"
}

/**
 * UI-обёртка над [formatStatsDurationCompact]: единицы из ресурсов.
 */
@Composable
internal fun formatStatsDurationCompactResource(seconds: Int): String =
    formatStatsDurationCompact(
        seconds = seconds,
        secondsUnit = stringResource(R.string.stats_duration_unit_seconds),
        minutesUnit = stringResource(R.string.stats_duration_unit_minutes),
        hoursUnit = stringResource(R.string.stats_duration_unit_hours),
        daysUnit = stringResource(R.string.stats_duration_unit_days)
    )

/**
 * Значение строки легенды статистики по составу записей категории или
 * упражнения (overview-stats-time-support design Decision 2): только
 * повторы → текст повторов, только длительность → текст длительности,
 * смешанный состав → оба значения через разделитель. Чистая функция —
 * готовые тексты приходят параметрами.
 *
 * @param totalReps Сумма «подходы × повторы» REPS-записей
 * @param totalDurationSec Сумма «подходы × длительность» TIME-записей
 * @param repsText Готовый локализованный текст повторов («45 повт.»)
 * @param durationText Готовый компактный текст длительности («2 ч.»)
 * @param separator Локализованный разделитель (« · »)
 */
internal fun legendValueText(
    totalReps: Int,
    totalDurationSec: Int,
    repsText: String,
    durationText: String,
    separator: String
): String =
    when {
        totalDurationSec <= 0 -> repsText
        totalReps <= 0 -> durationText
        else -> repsText + separator + durationText
    }

/**
 * UI-обёртка над [legendValueText]: текст повторов — `overview_unit_reps`,
 * длительность — [formatStatsDurationCompactResource], разделитель — ресурс.
 */
@Composable
internal fun legendValueTextResource(
    totalReps: Int,
    totalDurationSec: Int
): String =
    legendValueText(
        totalReps = totalReps,
        totalDurationSec = totalDurationSec,
        repsText = stringResource(R.string.overview_unit_reps, totalReps),
        durationText = formatStatsDurationCompactResource(totalDurationSec),
        separator = stringResource(R.string.stats_value_separator)
    )
