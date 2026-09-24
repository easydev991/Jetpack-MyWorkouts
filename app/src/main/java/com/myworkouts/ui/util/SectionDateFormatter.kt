package com.myworkouts.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.myworkouts.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Правило выбора формата даты-заголовка секции — как на главном экране
 * (`MainScreen.EntryListItems`): сегодня — без заголовка (`null`),
 * «Вчера», короткий `EEE, d MMM` — для дат текущего года,
 * `EEE, dd.MM.yyyy` — для прошлых лет. Переиспользуется обзором:
 * drill-down статистики и строки рекордов (overview-screen, design
 * «Форматтеры дат»).
 *
 * Форматтеры и подпись «Вчера» — параметры: чистая функция тестируется
 * на JVM, UI-обёртка [sectionDateLabelResource] подтягивает ресурсы.
 *
 * @param date Дата секции/записи
 * @param today «Сегодня» — точка отсчёта правил
 * @param yesterdayLabel Готовая локализованная подпись «Вчера»
 * @param currentYearFormatter Формат дат текущего года (`EEE, d MMM`)
 * @param archiveFormatter Формат дат прошлых лет (`EEE, dd.MM.yyyy`)
 * @return Готовый текст заголовка или `null` — заголовок не нужен
 */
internal fun sectionDateLabel(
    date: LocalDate,
    today: LocalDate,
    yesterdayLabel: String,
    currentYearFormatter: DateTimeFormatter,
    archiveFormatter: DateTimeFormatter
): String? =
    when {
        date == today -> null
        date == today.minusDays(1) -> yesterdayLabel
        date.year == today.year -> date.format(currentYearFormatter)
        else -> date.format(archiveFormatter)
    }

/**
 * UI-обёртка [sectionDateLabel]: подпись «Вчера» из ресурсов
 * (`entry_section_yesterday`), форматтеры — с локалью по умолчанию
 * (без ветвления ru/en, как на главном экране).
 */
@Composable
internal fun sectionDateLabelResource(
    date: LocalDate,
    today: LocalDate
): String? {
    val yesterdayLabel = stringResource(R.string.entry_section_yesterday)
    val currentYearFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }
    val archiveFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy") }
    return sectionDateLabel(
        date = date,
        today = today,
        yesterdayLabel = yesterdayLabel,
        currentYearFormatter = currentYearFormatter,
        archiveFormatter = archiveFormatter
    )
}
