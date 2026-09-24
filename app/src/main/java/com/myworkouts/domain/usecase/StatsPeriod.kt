package com.myworkouts.domain.usecase

import androidx.annotation.StringRes
import com.myworkouts.R

/**
 * Календарный период вкладки «Статистика» (overview-screen, design
 * Decision 3): день = сегодня, неделя = ISO-неделя с понедельника,
 * месяц и год — календарные; системная зона. Соседствует с калькуляторами —
 * используется расчётами и ViewModel, Compose его не хранит.
 */
enum class StatsPeriod(
    @StringRes val labelResId: Int
) {
    DAY(R.string.overview_period_day),
    WEEK(R.string.overview_period_week),
    MONTH(R.string.overview_period_month),
    YEAR(R.string.overview_period_year)
}
