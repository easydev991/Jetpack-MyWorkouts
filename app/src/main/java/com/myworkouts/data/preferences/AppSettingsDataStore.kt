package com.myworkouts.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myworkouts.domain.model.AppTheme
import com.myworkouts.domain.model.EntryField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore-ключ для выбранной темы. */
private val THEME_KEY = stringPreferencesKey("theme")

/** DataStore-ключ для переключателя динамических цветов. */
private val USE_DYNAMIC_COLORS_KEY = booleanPreferencesKey("use_dynamic_colors")

/** DataStore-ключ для видимости вкладки «Упражнения» в нижней навигации. */
private val SHOW_EXERCISES_TAB_KEY = booleanPreferencesKey("show_exercises_tab")

/** DataStore-ключ для глобального opt-out диалога подтверждения архивации. */
private val DONT_SHOW_ARCHIVE_DIALOG_KEY = booleanPreferencesKey("dont_show_archive_dialog")

/**
 * Реализация DataStore для настроек оформления приложения.
 *
 * @param dataStore Базовый [DataStore] для `appearance_prefs` (singleton
 *   per file — обеспечивается `preferencesDataStore` делегатом в
 *   [Companion.create] для production; в тестах инжектится через
 *   `PreferenceDataStoreFactory.create(produceFile = ...)`).
 *
 * @implNote class, не MyWorkoutsApplication (одна зависимость — лишняя
 * сущность). Хранит 4 ключа (theme/use_dynamic_colors/show_exercises_tab/
 * dont_show_archive_dialog). Новые ключи — точечно по фичам, имя файла
 * appearance_prefs НЕ менять (миграция DataStore). Если появится 2-я
 * зависимость от DataStore — вынести в MyWorkoutsApplication.
 */
class AppSettingsDataStore(
    private val dataStore: DataStore<Preferences>
) {
    /**
     * Сохраняет выбранную тему приложения.
     *
     * @param theme Выбранная тема
     */
    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }

    /**
     * Сохраняет настройку использования динамических цветов.
     *
     * @param useDynamicColors Использовать динамические цвета
     */
    suspend fun setUseDynamicColors(useDynamicColors: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_DYNAMIC_COLORS_KEY] = useDynamicColors
        }
    }

    /**
     * Flow для отслеживания выбранной темы.
     * При первом запуске возвращает значение по умолчанию (SYSTEM).
     */
    val theme: Flow<AppTheme> =
        dataStore.data
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
        dataStore.data
            .map { preferences ->
                preferences[USE_DYNAMIC_COLORS_KEY] ?: true
            }

    /**
     * Сохраняет настройку видимости вкладки «Упражнения».
     *
     * @param show Показывать вкладку «Упражнения» в нижней навигации
     */
    suspend fun setShowExercisesTab(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_EXERCISES_TAB_KEY] = show
        }
    }

    /**
     * Flow для отслеживания видимости вкладки «Упражнения».
     * При первом запуске возвращает значение по умолчанию (false) —
     * каталог нужен не всем, кому нужен — включит тоггл на «Ещё».
     */
    val showExercisesTab: Flow<Boolean> =
        dataStore.data
            .map { preferences ->
                preferences[SHOW_EXERCISES_TAB_KEY] ?: false
            }

    /**
     * Сохраняет настройку глобального opt-out диалога подтверждения
     * архивации упражнения. При `true` архивация выполняется сразу
     * без диалога; при `false` (по умолчанию) — диалог показывается.
     *
     * @param value Скрывать ли диалог архивации в будущем
     */
    suspend fun setDontShowArchiveDialog(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[DONT_SHOW_ARCHIVE_DIALOG_KEY] = value
        }
    }

    /**
     * Flow для отслеживания настройки скрытия диалога архивации.
     * При первом запуске возвращает значение по умолчанию (`false`)
     * — обратная совместимость: пользователь видит диалог как раньше,
     * пока сам не поставит галочку.
     */
    val dontShowArchiveDialog: Flow<Boolean> =
        dataStore.data
            .map { preferences ->
                preferences[DONT_SHOW_ARCHIVE_DIALOG_KEY] ?: false
            }

    /**
     * Сохраняет видимость опционального поля формы записи.
     *
     * @param field Поле формы ([EntryField])
     * @param visible Показывать ли поле на экране записи
     */
    suspend fun setFieldVisible(
        field: EntryField,
        visible: Boolean
    ) {
        dataStore.edit { preferences ->
            preferences[fieldKey(field)] = visible
        }
    }

    /**
     * Flow карты видимости опциональных полей формы записи: одно
     * чтение `data` для всех ключей (entry-settings-screen, design D2 —
     * не combine пяти Flow-ов). Отсутствие ключа = поле отображается
     * (дефолт `true`), миграция не нужна.
     */
    val entryFieldVisibility: Flow<Map<EntryField, Boolean>> =
        dataStore.data
            .map { preferences ->
                EntryField.entries.associateWith { field -> preferences[fieldKey(field)] ?: true }
            }

    /**
     * Включает все опциональные поля формы записи одной записью:
     * один `edit` — одна перезапись файла и одно emission
     * [entryFieldVisibility] (ревью `enableAll`).
     */
    suspend fun setAllFieldsVisible() {
        dataStore.edit { preferences ->
            EntryField.entries.forEach { field ->
                preferences[fieldKey(field)] = true
            }
        }
    }

    companion object {
        /**
         * DataStore-ключ видимости поля: деривируется из имени enum-константы
         * (`SETS` → `show_sets_field`) — новое поле enum не требует новых
         * методов хранилища (design D2).
         */
        private fun fieldKey(field: EntryField): Preferences.Key<Boolean> =
            booleanPreferencesKey("show_${field.name.lowercase()}_field")

        /**
         * Singleton-per-file [DataStore] в файле `appearance_prefs` —
         * повторные обращения возвращают один и тот же инстанс (иначе
         * DataStore бросил бы `IllegalStateException`).
         */
        private val Context.preferencesDataStoreSingleton: DataStore<Preferences>
            by preferencesDataStore(name = "appearance_prefs")

        /**
         * Создаёт [AppSettingsDataStore] поверх [Context] с правильным
         * именем файла. Для тестов — прямой конструктор с
         * `PreferenceDataStoreFactory.create(produceFile = ...)`,
         * минуя singleton-делегат (нужен temp-файл).
         */
        fun create(context: Context): AppSettingsDataStore = AppSettingsDataStore(context.preferencesDataStoreSingleton)
    }
}
