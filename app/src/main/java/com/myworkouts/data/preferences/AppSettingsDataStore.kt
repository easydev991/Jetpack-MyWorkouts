package com.myworkouts.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myworkouts.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore для настроек оформления приложения (тема, динамические цвета).
 *
 * Настройки сохраняются постоянно, чтобы при каждом запуске приложения
 * выбор пользователя не сбрасывался.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "appearance_prefs")

/**
 * Ключи для хранения настроек в DataStore.
 */
private object PreferencesKeys {
    val THEME_KEY = stringPreferencesKey("theme")
    val USE_DYNAMIC_COLORS_KEY = booleanPreferencesKey("use_dynamic_colors")
}

/**
 * Реализация DataStore для настроек оформления приложения.
 *
 * @param context контекст приложения (должен быть applicationContext —
 * иначе DataStore ругнётся на утечку при уничтожении Activity)
 */
// ponytail: class, не MyWorkoutsApplication (одна зависимость — лишняя
// сущность). Хранит ровно 2 ключа (theme/use_dynamic_colors). Новые
// ключи — точечно по фичам, имя файла appearance_prefs НЕ менять
// (миграция DataStore). Если появится 2-я зависимость от DataStore —
// вынести в MyWorkoutsApplication (см. design.md Decisions 5, 13).
class AppSettingsDataStore(
    private val context: Context
) {
    /**
     * Сохраняет выбранную тему приложения.
     *
     * @param theme Выбранная тема
     */
    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_KEY] = theme.name
        }
    }

    /**
     * Сохраняет настройку использования динамических цветов.
     *
     * @param useDynamicColors Использовать динамические цвета
     */
    suspend fun setUseDynamicColors(useDynamicColors: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS_KEY] = useDynamicColors
        }
    }

    /**
     * Flow для отслеживания выбранной темы.
     * При первом запуске возвращает значение по умолчанию (SYSTEM).
     */
    val theme: Flow<AppTheme> =
        context.dataStore.data
            .map { preferences ->
                @Suppress("SwallowedException")
                preferences[PreferencesKeys.THEME_KEY]?.let { themeName ->
                    try {
                        AppTheme.valueOf(themeName)
                    } catch (e: IllegalArgumentException) {
                        AppTheme.SYSTEM
                    }
                } ?: AppTheme.SYSTEM
            }

    /**
     * Flow для отслеживания настройки использования динамических цветов.
     * При первом запуске возвращает значение по умолчанию (true).
     */
    val useDynamicColors: Flow<Boolean> =
        context.dataStore.data
            .map { preferences ->
                preferences[PreferencesKeys.USE_DYNAMIC_COLORS_KEY] ?: true
            }
}
