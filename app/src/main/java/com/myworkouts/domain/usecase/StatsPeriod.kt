package com.myworkouts.domain.usecase

/**
 * Скользящее окно периода вкладки «Статистика» (overview-screen, design
 * Decision 3): окно фиксированной длины [days] от сегодня назад, оканчивается
 * сегодня включительно, системная зона. Соседствует с калькуляторами —
 * используется расчётами и ViewModel, Compose его не хранит.
 * Локализованное название — маппер `labelResId()`
 * (ui/util/DomainEnumLabels.kt).
 *
 * @property days Длина окна в днях
 */
@Suppress("MagicNumber")
enum class StatsPeriod(
    val days: Int
) {
    DAY(1),
    WEEK(7),
    MONTH(30),
    YEAR(365)
}
