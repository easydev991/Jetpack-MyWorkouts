package com.myworkouts.ui.screens.entry

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Секунд в часе — для разложения длительности на части. */
internal const val SECONDS_PER_HOUR: Int = 3600

/** Секунд в минуте — для разложения длительности на части. */
internal const val SECONDS_PER_MINUTE: Int = 60

/**
 * Форматирует длительность в секундах в человекочитаемую строку:
 * нулевые части опускаются («5 сек», «1 мин», «2 мин 30 сек», «1 час»).
 *
 * @param seconds Длительность в секундах
 * @param hoursWord Функция локализованного слова «час» с числительным
 *   (склоняется: «1 час»/«2 часа»/«5 часов»; в UI — plurals-ресурс)
 * @param minutesShort Локализованное сокращение минут («мин»/«min») —
 *   не склоняется
 * @param secondsShort Локализованное сокращение секунд («сек»/«sec») —
 *   не склоняется
 */
internal fun formatDuration(
    seconds: Int,
    hoursWord: (Int) -> String,
    minutesShort: String,
    secondsShort: String
): String {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    return buildList {
        if (hours > 0) add(hoursWord(hours))
        if (minutes > 0) add("$minutes $minutesShort")
        if (secs > 0) add("$secs $secondsShort")
    }.joinToString(separator = " ")
}

/**
 * Конвертирует [LocalDate] в millis для `DatePicker`: пикер работает в
 * UTC (классическая ловушка смещения даты — design.md Risk), поэтому
 * берётся полночь даты именно в UTC.
 */
internal fun localDateToPickerMillis(date: LocalDate): Long =
    date
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/** Обратная конвертация millis из `DatePicker` в [LocalDate] (UTC). */
internal fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant
        .ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()

/**
 * Собирает дату записи: `LocalDate + час/минута` в epoch millis
 * системной зоны ([zone] — явная, для JVM-тестов на фиксированной зоне).
 */
internal fun entryDateMillis(
    date: LocalDate,
    hour: Int,
    minute: Int,
    zone: ZoneId = ZoneId.systemDefault()
): Long =
    date
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
