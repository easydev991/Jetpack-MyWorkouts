package com.myworkouts.domain.usecase

import androidx.annotation.StringRes
import com.myworkouts.R

/**
 * Период хитмапа вкладки «Активность» (overview-screen, design
 * Decisions 3 и 7): окно фиксированной длины от сегодня назад — сетка
 * всегда заканчивается сегодня. [days] задаёт число дней окна и,
 * вместе с сеткой 7 строк, число колонок.
 *
 * `MagicNumber` подавлен осознанно: значения [days] — именованные
 * длины окон из макета, enum-константа и есть именованная константа.
 *
 * @property days Длина окна в днях (30/91/182/365, как в макете)
 */
@Suppress("MagicNumber")
enum class ActivityPeriod(
    val days: Int,
    @StringRes val labelResId: Int
) {
    MONTH(30, R.string.overview_period_month),
    QUARTER(91, R.string.overview_period_quarter),
    HALFYEAR(182, R.string.overview_period_halfyear),
    YEAR(365, R.string.overview_period_year)
}
