package com.myworkouts.ui.state

import com.myworkouts.domain.model.EntryField

/**
 * UI State экрана «Настроить экран» (entry-settings-screen, design D4).
 *
 * @property visibility Видимость опциональных полей формы записи;
 *   отсутствие ключа трактуется как «поле отображается» (`true`)
 */
data class EntrySettingsUiState(
    val visibility: Map<EntryField, Boolean> = EntryField.entries.associateWith { true }
) {
    /** Есть хотя бы один выключенный переключатель — показать кнопку «Включить все». */
    val hasAnyDisabled: Boolean
        get() = visibility.any { !it.value }
}
