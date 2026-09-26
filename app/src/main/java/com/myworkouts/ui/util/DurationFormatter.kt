package com.myworkouts.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.myworkouts.R

/** Секунд в часе — для разложения длительности на части. */
internal const val SECONDS_PER_HOUR: Int = 3600

/** Секунд в минуте — для разложения длительности на части. */
internal const val SECONDS_PER_MINUTE: Int = 60

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
