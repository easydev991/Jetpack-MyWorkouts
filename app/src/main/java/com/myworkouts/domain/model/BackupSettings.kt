package com.myworkouts.domain.model

/**
 * Настройки вкладок обзора в резервной копии (overview-screen, design D8):
 * блок `settings` в JSON версии 2.
 *
 * @property tabVisibility Видимость вкладок; отсутствующие записи —
 *   «не применять» (ключ появился позже бэкапа — не трогаем)
 * @property lastOverviewTab Последняя выбранная вкладка
 */
data class BackupSettings(
    val tabVisibility: Map<OverviewTab, Boolean>,
    val lastOverviewTab: OverviewTab
) {
    companion object {
        /** Значения по умолчанию: все вкладки видимы, активна «Статистика». */
        fun defaults(): BackupSettings =
            BackupSettings(
                tabVisibility = OverviewTab.entries.associateWith { true },
                lastOverviewTab = OverviewTab.STATS
            )
    }
}
