package com.myworkouts.ui.util

import androidx.annotation.StringRes
import com.myworkouts.R
import com.myworkouts.domain.model.EntryField
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.StatsPeriod

/*
 * Мапперы доменных enum'ов в строковые ресурсы: домен без `R.string` —
 * резолв названий живёт в `ui/`, рядом с
 * [standardCategoryLabelResId]. Ключи DataStore от enum.name не зависят
 * от этих ресурсов; добавление константы enum = строка здесь + строки
 * локализации.
 */

/** Название вкладки обзора. */
@StringRes
fun OverviewTab.labelResId(): Int =
    when (this) {
        OverviewTab.STATS -> R.string.overview_tab_stats
        OverviewTab.RECORDS -> R.string.overview_tab_records
        OverviewTab.ACTIVITY -> R.string.overview_tab_activity
    }

/** Название периода «Статистики». */
@StringRes
fun StatsPeriod.labelResId(): Int =
    when (this) {
        StatsPeriod.DAY -> R.string.overview_period_day
        StatsPeriod.WEEK -> R.string.overview_period_week
        StatsPeriod.MONTH -> R.string.overview_period_month
        StatsPeriod.YEAR -> R.string.overview_period_year
    }

/** Название периода хитмапа «Активности». */
@StringRes
fun ActivityPeriod.labelResId(): Int =
    when (this) {
        ActivityPeriod.MONTH -> R.string.overview_period_month
        ActivityPeriod.QUARTER -> R.string.overview_period_quarter
        ActivityPeriod.HALFYEAR -> R.string.overview_period_halfyear
        ActivityPeriod.YEAR -> R.string.overview_period_year
    }

/** Название опционального поля формы записи (экран настроек записи). */
@StringRes
fun EntryField.labelResId(): Int =
    when (this) {
        EntryField.SETS -> R.string.entry_visibility_sets_label
        EntryField.WEIGHT -> R.string.entry_visibility_weight_label
        EntryField.DATE -> R.string.entry_visibility_date_label
        EntryField.TIME -> R.string.entry_visibility_time_label
        EntryField.COMMENT -> R.string.entry_visibility_comment_label
    }

/** Подсказка под названием опционального поля (`null` — без подсказки). */
@StringRes
fun EntryField.hintResId(): Int? =
    when (this) {
        EntryField.SETS -> R.string.entry_visibility_sets_hint
        EntryField.DATE -> R.string.entry_visibility_date_hint
        EntryField.TIME -> R.string.entry_visibility_time_hint
        EntryField.WEIGHT, EntryField.COMMENT -> null
    }
