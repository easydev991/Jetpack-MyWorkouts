package com.myworkouts.ui.screens.entry

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
