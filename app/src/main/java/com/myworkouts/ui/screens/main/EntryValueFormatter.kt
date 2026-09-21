package com.myworkouts.ui.screens.main

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Форматирует значение записи для отображения в строке списка главного экрана.
 *
 * Логика одна для повторов и длительности: при нескольких подходах —
 * «подходы × значение», при одном — только значение. [value] — готовая
 * к показу строка: повторы или отформатированная длительность
 * (`formatDurationResource`). Разделитель — параметр, чтобы функция
 * не зависела от локализованных ресурсов и тестировалась на JVM.
 *
 * @param sets Количество подходов (≥ 1)
 * @param value Готовый текст значения: повторов или отформатированной
 *   длительности
 * @param separator Локализованный разделитель (« х »/« x »)
 * @return Строка значения для отображения
 */
internal fun formatEntryValue(
    sets: Int,
    value: String,
    separator: String
): String = if (sets > 1) "$sets$separator$value" else value

/**
 * Форматирует значение записи для карточки деталей (workout-detail-view):
 * базовый «подходы × значение» плюс опциональный суффикс веса одной строкой
 * через пробел («4 х 10 (+ 5 кг)»).
 *
 * Суффикс веса уже локализован на стороне UI (`entry_details_weight_suffix`):
 * сюда передаётся готовой строкой либо `null`, если веса нет.
 *
 * @param sets Количество подходов (≥ 1)
 * @param value Готовый текст значения: повторов или отформатированной
 *   длительности
 * @param separator Локализованный разделитель (« х »/« x »)
 * @param weightSuffix Локализованный суффикс веса «(+ N кг)» или `null`
 * @return Строка «значение» либо «значение + суффикс»
 */
internal fun formatEntryDetailsValue(
    sets: Int,
    value: String,
    separator: String,
    weightSuffix: String?
): String =
    formatEntryValue(sets = sets, value = value, separator = separator) +
        weightSuffix?.let { " $it" }.orEmpty()

/**
 * Форматирует дополнительный вес записи для строки списка
 * (spec «Дополнительный вес в строке записи»; design Decision 3).
 *
 * Целые килограммы — без дробной части («5», не «5.0»), дробные —
 * с дробной частью как введена («2.5»). `Double.toString()` даёт
 * кратчайшее представление, хвостовых нулей после парса ввода не бывает.
 * Единица измерения («кг»/«kg») — в ресурсе `entry_list_weight_value`,
 * сюда не входит.
 *
 * @param weightKg Дополнительный вес в килограммах (> 0)
 * @return Числовая часть значения веса для отображения
 */
internal fun formatWeightKg(weightKg: Double): String = weightKg.toString().removeSuffix(".0")

/**
 * Форматирует дату/время записи для карточки деталей (workout-detail-view):
 * паттерн `d MMMM yyyy, HH:mm` с локалью и зоной из аргументов.
 *
 * Локаль и зона — необязательные параметры: по умолчанию используются
 * текущие [Locale.getDefault] / [ZoneId.systemDefault]. Параметризация
 * нужна для детерминированных JVM-тестов без мутации process defaults.
 *
 * @param dateMillis Момент записи в epoch millis
 * @param locale Локаль форматирования месяца («ru» → «сентября», «en» → «September»)
 * @param zoneId Зона интерпретации millis (даты в Москве отличаются от UTC)
 * @return Локализованная строка даты/времени («20 сентября 2025, 18:30»)
 */
internal fun formatDetailDateTime(
    dateMillis: Long,
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale)
    return Instant
        .ofEpochMilli(dateMillis)
        .atZone(zoneId)
        .toLocalDateTime()
        .format(formatter)
}
