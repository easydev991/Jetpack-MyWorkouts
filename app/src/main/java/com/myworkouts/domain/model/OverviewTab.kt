package com.myworkouts.domain.model

/**
 * Вкладки экрана обзора (overview-screen, design Decision 8): DataStore
 * и use-cases импортируют enum свободно, ключи настроек деривируются из
 * [name]. Локализованные названия — маппер `labelResId()`
 * (ui/util/DomainEnumLabels.kt; домен без `R.string`).
 */
enum class OverviewTab {
    STATS,
    RECORDS,
    ACTIVITY
}
