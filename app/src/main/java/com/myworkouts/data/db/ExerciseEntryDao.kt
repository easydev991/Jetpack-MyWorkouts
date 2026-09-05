package com.myworkouts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    /**
     * Реактивный список записей с JOIN на каталог упражнений.
     * Название упражнения (`exerciseName`) подтягивается из `exercises`,
     * сортировка — по `date DESC` (новые первыми, design Decision 1).
     * Проекция [EntryListItemEntity] — не `@Entity`, схема БД не меняется.
     */
    @Query(
        "SELECT e.id AS id, " +
            "x.name AS exerciseName, " +
            "e.sets AS sets, " +
            "e.reps AS reps, " +
            "e.duration_sec AS durationSec, " +
            "e.weight_kg AS weightKg, " +
            "e.date AS date " +
            "FROM exercise_entries e " +
            "JOIN exercises x ON x.id = e.exercise_id " +
            "ORDER BY e.date DESC"
    )
    fun observeEntries(): Flow<List<EntryListItemEntity>>

    /** Удаляет запись по идентификатору. Отсутствующий id — успех без эффекта. */
    @Query("DELETE FROM exercise_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
