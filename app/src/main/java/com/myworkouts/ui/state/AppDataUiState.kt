package com.myworkouts.ui.state

/**
 * Одноразовый результат операции управления данными (app-data-screen,
 * design Decision 7): успехи и обобщённые ошибки для маппинга в Toast.
 */
enum class AppDataResult {
    /** Резервная копия создана. */
    EXPORTED,

    /** Данные восстановлены из копии. */
    RESTORED,

    /** Все данные удалены. */
    DELETED,

    /** Ошибка записи файла копии. */
    ERROR_EXPORT,

    /** Файл копии не прошёл проверку формата. */
    ERROR_INVALID_FILE,

    /** Ошибка вставки данных при восстановлении. */
    ERROR_IMPORT,

    /** Ошибка удаления данных. */
    ERROR_DELETE
}

/**
 * UI State экрана «Данные приложения» (app-data-screen, design Decision 7).
 *
 * Обычный data class по правилу проекта (AGENTS.md): взаимоисключающих
 * визуальных состояний нет — Loading-состояния не существует (Room-Flow
 * реактивный), ошибки — одноразовые события, а не состояние экрана.
 *
 * @property hasData есть данные пользователя — записи или хотя бы одно
 *   пользовательское упражнение; стандартный набор данными пользователя
 *   не считается
 * @property isBusy идёт любая операция — все кнопки экрана заблокированы
 * @property result одноразовый результат операции для Toast; `null` —
 *   нечего показывать; экран сбрасывает его после показа через
 *   `clearResult()`
 */
data class AppDataUiState(
    val hasData: Boolean = false,
    val isBusy: Boolean = false,
    val result: AppDataResult? = null
)
