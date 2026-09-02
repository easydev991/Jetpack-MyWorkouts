package com.myworkouts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * DAO записей о выполнении.
 *
 * `@Update` обновляет сущность как есть: `modifiedAt` DAO не вычисляет —
 * репозиторий выставляет его через `data class.copy()` до вызова (design.md
 * Decision 5, tasks 3.2/5.3). FK на `exercises.id` определён в entity,
 * Room выбрасывает SQLite-исключение при попытке вставить запись с
 * несуществующим `exerciseId` — это спецификация сценария «Ссылка на
 * несуществующее упражнение», который ловится репозиторием через try/catch.
 */
@Dao
interface ExerciseEntryDao {
    /** Вставляет новую запись. FK-конфликт бросает SQLite-исключение. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ExerciseEntryEntity): Long

    /** Обновляет существующую запись по `id`. */
    @Update
    suspend fun update(entity: ExerciseEntryEntity)

    /** Возвращает запись по идентификатору или `null`, если не найдена. */
    @Query("SELECT * FROM exercise_entries WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntryEntity?
}
