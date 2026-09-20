package com.myworkouts.analytics

/**
 * Экраны приложения для событий screen_view. Имена enum-констант —
 * wire-значения (стабильные строки в PascalCase по маршрутам
 * `Screen.kt`, спека «Событийная модель аналитики»).
 */
enum class AppScreen {
    MainScreen,
    ExercisesScreen,
    MoreScreen,
    AppearanceScreen,
    AppDataScreen,
    ArchivedExercisesScreen,
    EntryCreateScreen,
    EntrySettingsScreen,
    ExerciseCreateScreen
}

/**
 * Типы пользовательских действий для событий user_action. Имена
 * enum-констант — wire-значения (стабильные строки UPPER_SNAKE_CASE,
 * канон — design D10).
 */
enum class UserActionType {
    CREATE,
    EDIT,
    DELETE,
    ARCHIVE,
    UNARCHIVE,
    CREATE_BACKUP,
    RESTORE_BACKUP,
    DELETE_ALL_DATA,
    OPEN_ENTRY_CREATE,
    OPEN_EXERCISE_CREATE,
    SELECT_THEME,
    TOGGLE_DYNAMIC_COLORS,
    TOGGLE_EXERCISES_TAB,
    TOGGLE_ENTRY_FIELD_VISIBILITY,
    ENABLE_ALL_ENTRY_FIELDS,
    SEND_FEEDBACK
}

/**
 * Операции для событий app_error. Имена enum-констант — wire-значения
 * (стабильные строки UPPER_SNAKE_CASE, канон — design D9).
 */
enum class AppErrorOperation {
    CREATE_BACKUP,
    RESTORE_BACKUP,
    DELETE_ALL_DATA,
    DELETE_EXERCISE
}

/**
 * События аналитики (спека «Событийная модель аналитики»): ровно три
 * типа — просмотр экрана, пользовательское действие, ошибка приложения.
 */
sealed interface AnalyticsEvent {
    /**
     * Просмотр экрана (breadcrumbs навигации, design D3).
     *
     * @param screen экран, который просматривается
     */
    data class ScreenView(
        val screen: AppScreen
    ) : AnalyticsEvent

    /**
     * Пользовательское действие (design D10).
     *
     * @param action тип действия
     * @param value опциональное значение (SELECT_THEME — light/dark/system,
     * TOGGLE_DYNAMIC_COLORS — on/off, TOGGLE_EXERCISES_TAB — show/hide)
     */
    data class UserAction(
        val action: UserActionType,
        val value: String? = null
    ) : AnalyticsEvent

    /**
     * Ошибка приложения (design D8: итоговая ошибка операции).
     *
     * @param operation операция, в которой произошла ошибка
     * @param throwable исходное исключение
     */
    data class AppError(
        val operation: AppErrorOperation,
        val throwable: Throwable
    ) : AnalyticsEvent
}
