package com.myworkouts.navigation

import com.myworkouts.analytics.UserActionType

/**
 * Action intent статического шортката лаунчера «Добавить тренировку»
 * (`shortcuts.xml`); совпадает с action в intent-filter MainActivity.
 */
const val ACTION_ADD_WORKOUT = "com.myworkouts.action.ADD_WORKOUT"

/**
 * Определяет, что intent запущен именно шорткатом «Добавить тренировку».
 *
 * @param action action пришедшего intent (может быть null — например,
 * обычный тап по иконке приложения)
 * @return true — intent шортката, false — любой другой источник запуска
 */
fun isAddWorkoutAction(action: String?): Boolean = action == ACTION_ADD_WORKOUT

/**
 * Решение по доставленному intent шортката: открыть экран создания
 * записи — и с каким событием аналитики это залогировать.
 *
 * Проверка action выполняется раньше, в MainActivity
 * ([isAddWorkoutAction]) — сюда intent доходит только шорткатный.
 *
 * @param entryCreateOnStack true, если экран создания записи уже есть
 * в back stack (не только наверху: настройки записи открываются над
 * формой отдельным destination)
 * @return [UserActionType.OPEN_ENTRY_CREATE] — нужно открыть форму;
 * null — навигацию выполнять не нужно (форма уже в стеке)
 */
fun resolve(entryCreateOnStack: Boolean): UserActionType? =
    if (entryCreateOnStack) {
        null
    } else {
        UserActionType.OPEN_ENTRY_CREATE
    }
