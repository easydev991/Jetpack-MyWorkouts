package com.myworkouts.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-сущность записи о выполнении упражнения.
 *
 * FK на [ExerciseEntity.id] (`NO ACTION` — SQLite отклонит запись с
 * несуществующим `exerciseId`, см. design.md Decision 3). Колонка `workoutId`
 * присутствует, FK на будущую таблицу `workouts` не объявлен — таблицы ещё
 * нет, она появится отдельной миграцией v1→v2. В MVP `workoutId` всегда
 * `null`, [com.myworkouts.domain.repository.EntryValidator] отклоняет записи
 * с непустым `workoutId`.
 *
 * Индексы:
 * - `exerciseId` — FK + будущие выборки по упражнению;
 * - `date` — будущие агрегаты за периоды (неделя/месяц/год).
 *
 * `modifiedAt` обновляется в репозитории через `data class.copy()` до вызова
 * DAO — DAO не вычисляет время сам (design.md Decision 5).
 */
@Entity(
    tableName = "exercise_entries",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index("exercise_id"),
        Index("date")
    ]
)
data class ExerciseEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    @ColumnInfo(name = "workout_id")
    val workoutId: Long? = null,
    val date: Long,
    val sets: Int,
    val reps: Int? = null,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double? = null,
    @ColumnInfo(name = "duration_sec")
    val durationSec: Int? = null,
    val comment: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long
)
