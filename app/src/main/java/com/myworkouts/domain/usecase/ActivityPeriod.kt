package com.myworkouts.domain.usecase

/**
 * Период хитмапа вкладки «Активность» (overview-screen, design
 * Decisions 3 и 7): окно фиксированной длины от сегодня назад — сетка
 * всегда заканчивается сегодня. [days] задаёт число дней окна и,
 * вместе с сеткой 7 строк, число колонок. Локализованное название —
 * маппер `labelResId()` (ui/util/DomainEnumLabels.kt).
 *
 * `MagicNumber` подавлен осознанно: значения [days] — именованные
 * длины окон из макета, enum-константа и есть именованная константа.
 *
 * @property days Длина окна в днях (30/91/182/365, как в макете)
 */
@Suppress("MagicNumber")
enum class ActivityPeriod(
    val days: Int
) {
    MONTH(30),
    QUARTER(91),
    HALFYEAR(182),
    YEAR(365)
}
