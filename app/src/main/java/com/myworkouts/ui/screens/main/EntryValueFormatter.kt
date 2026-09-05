package com.myworkouts.ui.screens.main

/**
 * Форматирует значение записи для отображения в строке списка главного экрана
 * (spec «Формат значения записи»; design Decision 3).
 *
 * Логика одна для повторов и длительности: при нескольких подходах —
 * «подходы × значение», при одном — только значение. Разделитель — параметр,
 * чтобы функция не зависела от локализованных ресурсов и тестировалась на JVM.
 *
 * Caller вычисляет [value] (`reps ?: durationSec`) и обрабатывает
 * состояние «оба null» (недостижимо при валидной записи — `EntryValidator`).
 *
 * @param sets Количество подходов (≥ 1)
 * @param value Значение повторений или длительности (в секундах)
 * @param separator Локализованный разделитель (« х »/« x »)
 * @return Строка значения для отображения
 */
internal fun formatEntryValue(
    sets: Int,
    value: Int,
    separator: String
): String = if (sets > 1) "$sets$separator$value" else "$value"
