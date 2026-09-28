package com.myworkouts.domain.usecase

import androidx.annotation.StringRes
import com.myworkouts.R

/**
 * Скользящее окно периода вкладки «Статистика» (overview-screen, design
 * Decision 3): окно фиксированной длины [days] от сегодня назад, оканчивается
 * сегодня включительно, системная зона. Соседствует с калькуляторами —
 * используется расчётами и ViewModel, Compose его не хранит.
 *
 * @property days Длина окна в днях
 */
@Suppress("MagicNumber")
enum class StatsPeriod(
    val days: Int,
    @StringRes val labelResId: Int
) {
    DAY(1, R.string.overview_period_day),
    WEEK(7, R.string.overview_period_week),
    MONTH(30, R.string.overview_period_month),
    YEAR(365, R.string.overview_period_year)
}
