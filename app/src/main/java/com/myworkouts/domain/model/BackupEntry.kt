package com.myworkouts.domain.model

/**
 * Запись из файла резервной копии: [ExerciseEntry] без `exerciseId` и
 * `modifiedAt`, связь с упражнением — по имени (app-data-screen, design
 * Decision 6). id строк БД непереносимы между устройствами, поэтому в файле
 * их нет; `modifiedAt` восстановленная запись получает моментом импорта.
 *
 * Ровно поля элемента `entries` файла резервной копии (design Decision 2).
 *
 * @property exerciseName имя упражнения — ключ связи с каталогом
 * @property date дата выполнения, миллисекунды эпохи
 * @property sets количество подходов
 * @property reps повторы (для упражнений, измеряемых повторами)
 * @property weightKg вес, кг; `0.0` и `null` — разные значения
 * @property durationSec длительность, секунды (для измеряемых временем)
 * @property comment произвольный комментарий
 * @property createdAt момент создания записи, миллисекунды эпохи
 */
data class BackupEntry(
    val exerciseName: String,
    val date: Long,
    val sets: Int,
    val reps: Int?,
    val weightKg: Double?,
    val durationSec: Int?,
    val comment: String?,
    val createdAt: Long
)
