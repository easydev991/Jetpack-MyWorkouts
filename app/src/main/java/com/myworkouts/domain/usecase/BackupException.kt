package com.myworkouts.domain.usecase

/**
 * Типизированная причина сбоя операции с резервной копией.
 *
 * Пользовательский текст выбирается по значению enum'а в UI (маппинг на
 * `AppDataResult`); русское `message` исключения — только для лога
 * (app-data-screen, design Decision 3).
 */
enum class BackupError {
    /** Сбой записи файла резервной копии. */
    EXPORT,

    /** Файл нечитаем или несовместим с форматом. */
    INVALID_FILE
}

/**
 * Ошибка операции с резервной копией: [error] — типизированная причина
 * для UI, `message` — русское техническое сообщение для лога.
 */
class BackupException(
    message: String,
    val error: BackupError
) : Exception(message)
