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
     * Название упражнения (`exerciseName`) и номер иконки
     * (`iconNumber`, exercise-icon-display) подтягиваются из `exercises`,
     * сортировка — по `date DESC` (новые первыми, design Decision 1).
     * Проекция [EntryListItemEntity] — не `@Entity`, схема БД не меняется.
     * Комментарий записи (`comment`) включён в проекцию
     * (mainscreen-itemrow-comment).
     */
    @Query(
        "SELECT e.id AS id, " +
            "x.name AS exerciseName, " +
            "x.icon_number AS iconNumber, " +
            "e.sets AS sets, " +
            "e.reps AS reps, " +
            "e.duration_sec AS durationSec, " +
            "e.weight_kg AS weightKg, " +
            "e.comment AS comment, " +
            "e.date AS date " +
            "FROM exercise_entries e " +
            "JOIN exercises x ON x.id = e.exercise_id " +
            "ORDER BY e.date DESC"
    )
    fun observeEntries(): Flow<List<EntryListItemEntity>>

    /** Удаляет запись по идентификатору. Отсутствующий id — успех без эффекта. */
    @Query("DELETE FROM exercise_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Разовый снимок всех записей — для экспорта и дедупликации импорта
     * (app-data-screen, design Decision 6).
     */
    @Query("SELECT * FROM exercise_entries")
    suspend fun getAll(): List<ExerciseEntryEntity>

    /**
     * Массовая вставка подготовленных записей внутри транзакции `importData`
     * (app-data-screen, design Decision 6).
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ExerciseEntryEntity>)

    /**
     * Удаляет все записи. Только внутри транзакций массовых операций
     * (app-data-screen, design Decision 6).
     */
    @Query("DELETE FROM exercise_entries")
    suspend fun deleteAll()

    /**
     * Количество записей, ссылающихся на упражнение с `id`
     * (edit-delete-exercise, tasks §1.5, design Decision 8): pre-check
     * FK перед `deleteExercise`, чтобы не зависеть от парсинга текста
     * SQLite-исключения. `0` — можно удалять, `>0` — есть записи.
     */
    @Query("SELECT COUNT(*) FROM exercise_entries WHERE exercise_id = :id")
    suspend fun countByExerciseId(id: Long): Int

    /**
     * Реактивный список id упражнений с ненулевым числом записей
     * (edit-delete-exercise, tasks §1.5, design Decision 8). Источник
     * для `WorkoutDataRepository.exerciseIdsWithLinkedEntries()` —
     * pre-check FK на тапе «Удалить» в каталоге.
     */
    @Query("SELECT exercise_id FROM exercise_entries GROUP BY exercise_id")
    fun observeExerciseIdsWithLinkedEntries(): Flow<List<Long>>
}
