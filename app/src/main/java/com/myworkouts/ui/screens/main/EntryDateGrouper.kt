package com.myworkouts.ui.screens.main

import com.myworkouts.domain.model.EntryListItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Секция списка записей главного экрана: календарная дата и записи
 * этого дня (workout-entries-by-date-sections, design Decision 1).
 *
 * @property date Календарная дата секции (в зоне устройства)
 * @property items Записи секции, по убыванию `date` (epoch millis)
 */
data class DateSection(
    val date: LocalDate,
    val items: List<EntryListItem>
)

/**
 * Группирует записи по календарной дате в зоне [zone]
 * (spec «Список записей разделён на секции по дате»).
 *
 * Контракт (design Decision 1): вход сортируется по `date` убыванием
 * до `groupBy` — секции и записи строго по убыванию независимо от
 * порядка входа и без требований к SQL `ORDER BY`; пустых секций
 * не бывает.
 */
internal fun groupEntriesByDate(
    entries: List<EntryListItem>,
    zone: ZoneId = ZoneId.systemDefault()
): List<DateSection> =
    entries
        .sortedByDescending { it.date }
        .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
        .map { (date, items) -> DateSection(date = date, items = items) }
