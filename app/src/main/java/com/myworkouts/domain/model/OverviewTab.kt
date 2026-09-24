package com.myworkouts.domain.model

import androidx.annotation.StringRes
import com.myworkouts.R

/**
 * Вкладки экрана обзора (overview-screen, design Decision 8). Образец —
 * [EntryField]: `@StringRes`-лейбл, без Compose-зависимостей — DataStore
 * и use-cases импортируют enum свободно, ключи настроек деривируются из
 * [name].
 *
 * @property labelResId Ресурс названия вкладки
 */
enum class OverviewTab(
    @StringRes val labelResId: Int
) {
    STATS(R.string.overview_tab_stats),
    RECORDS(R.string.overview_tab_records),
    ACTIVITY(R.string.overview_tab_activity)
}
