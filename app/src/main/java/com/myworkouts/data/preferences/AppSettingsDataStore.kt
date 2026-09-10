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

/** DataStore-ключ для выбранной темы. */
private val THEME_KEY = stringPreferencesKey("theme")

/** DataStore-ключ для переключателя динамических цветов. */
private val USE_DYNAMIC_COLORS_KEY = booleanPreferencesKey("use_dynamic_colors")

/** DataStore-ключ для видимости вкладки «Упражнения» в нижней навигации. */
private val SHOW_EXERCISES_TAB_KEY = booleanPreferencesKey("show_exercises_tab")

/**
 * Реализация DataStore для настроек оформления приложения.
 *
 * @param context контекст приложения (должен быть applicationContext —
 * иначе DataStore ругнётся на утечку при уничтожении Activity)
 *
 * @implNote class, не MyWorkoutsApplication (одна зависимость — лишняя
 * сущность). Хранит 3 ключа (theme/use_dynamic_colors/show_exercises_tab).
 * Новые ключи — точечно по фичам, имя файла appearance_prefs НЕ менять
 * (миграция DataStore). Если появится 2-я зависимость от DataStore —
 * вынести в MyWorkoutsApplication.
 */
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
            preferences[THEME_KEY] = theme.name
        }
    }

    /**
     * Сохраняет настройку использования динамических цветов.
     *
     * @param useDynamicColors Использовать динамические цвета
     */
    suspend fun setUseDynamicColors(useDynamicColors: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_DYNAMIC_COLORS_KEY] = useDynamicColors
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
                preferences[THEME_KEY]?.let { themeName ->
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
                preferences[USE_DYNAMIC_COLORS_KEY] ?: true
            }

    /**
     * Сохраняет настройку видимости вкладки «Упражнения».
     *
     * @param show Показывать вкладку «Упражнения» в нижней навигации
     */
    suspend fun setShowExercisesTab(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_EXERCISES_TAB_KEY] = show
        }
    }

    /**
     * Flow для отслеживания видимости вкладки «Упражнения».
     * При первом запуске возвращает значение по умолчанию (false) —
     * каталог нужен не всем, кому нужен — включит тоггл на «Ещё».
     */
    val showExercisesTab: Flow<Boolean> =
        context.dataStore.data
            .map { preferences ->
                preferences[SHOW_EXERCISES_TAB_KEY] ?: false
            }
}
