package com.myworkouts.domain.model

/**
 * Запись о выполнении упражнения.
 *
 * MVP: каждая запись — одиночная (`workoutId == null`), привязка к тренировке
 * появится в будущих изменениях отдельной миграцией БД. Ввод «4 подхода
 * по 10 повторов с весом 5 кг» хранится одной записью (`sets=4, reps=10,
 * weightKg=5.0`), а не отдельной строкой на подход. Текстовые подписи
 * («4×10», «Жим 3×8 по 12.5 кг») вычисляются из полей при отрисовке
 * и в БД не хранятся.
 *
 * @property id Идентификатор записи (`0` — для новой, БД присвоит свой)
 * @property exerciseId Ссылка на упражнение каталога ([Exercise.id])
 * @property workoutId Ссылка на тренировку; в MVP всегда `null`
 * @property date Календарная дата выполнения (epoch millis)
 * @property sets Количество подходов (≥ 1)
 * @property reps Опциональное количество повторов в подходе (`null` — упражнения «на время»)
 * @property weightKg Опциональный вес в кг (`null` — собственный вес,
 *   `0.0` — нулевой вес; это разные состояния, см. `EntryValidator`)
 * @property durationSec Опциональная длительность в секундах (для планки и т.п.)
 * @property comment Опциональный комментарий к конкретному выполнению
 * @property createdAt Момент создания записи (epoch millis)
 * @property modifiedAt Момент последнего изменения (epoch millis)
 */
data class ExerciseEntry(
    val id: Long,
    val exerciseId: Long,
    val workoutId: Long?,
    val date: Long,
    val sets: Int,
    val reps: Int?,
    val weightKg: Double?,
    val durationSec: Int?,
    val comment: String?,
    val createdAt: Long,
    val modifiedAt: Long
)
