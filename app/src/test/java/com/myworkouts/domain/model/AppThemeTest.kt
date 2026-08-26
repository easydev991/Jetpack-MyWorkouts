package com.myworkouts.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppThemeTest {
    @Test
    fun app_theme_names_should_match_data_store_keys() {
        // Then: ровно три значения с именами, которые AppSettingsDataStore
        // использует как ключи (THEME_KEY = stringPreferencesKey("theme"),
        // setTheme пишет theme.name).
        assertEquals(
            setOf("LIGHT", "DARK", "SYSTEM"),
            AppTheme.entries.mapTo(mutableSetOf()) { it.name }
        )
    }
}
