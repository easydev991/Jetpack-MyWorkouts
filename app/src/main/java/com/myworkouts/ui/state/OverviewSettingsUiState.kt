package com.myworkouts.ui.state

import com.myworkouts.domain.model.OverviewTab

/**
 * UI State экрана настроек обзора (overview-screen, design D9). Образец —
 * [EntrySettingsUiState].
 *
 * @property visibility Видимость вкладок обзора; отсутствие ключа
 *   трактуется как «вкладка отображается» (`true`)
 */
data class OverviewSettingsUiState(
    val visibility: Map<OverviewTab, Boolean> = OverviewTab.entries.associateWith { true }
) {
    /** Есть хотя бы одна выключенная вкладка — показать кнопку «Включить все». */
    val hasAnyDisabled: Boolean
        get() = visibility.any { !it.value }
}
