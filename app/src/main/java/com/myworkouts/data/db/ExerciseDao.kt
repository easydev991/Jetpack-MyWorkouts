package com.myworkouts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO каталога упражнений.
 *
 * Поиск и сортировка — в SQL (`ORDER BY name COLLATE NOCASE`,
 * `LIKE '%' || :query || '%' COLLATE NOCASE`), см. design.md Decision 5:
 * фильтрация в Kotlin отклонена как лишняя работа на UI-потоке.
 * Спецсимволы LIKE (`%`, `_`) в пользовательском вводе не экранируются —
 * YAGNI для каталога из 13 названий; экранирование добавится миграцией
 * при росте каталога.
 */
@Dao
interface ExerciseDao {
    /**
     * Возвращает каталог, отфильтрованный по подстроке названия и
     * отсортированный по имени без учёта регистра. Пустая подстрока
     * возвращает весь каталог.
     */
    @Query(
        "SELECT * FROM exercises " +
            "WHERE name LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY name COLLATE NOCASE"
    )
    fun observeExercises(query: String): Flow<List<ExerciseEntity>>

    /**
     * Вставляет новое упражнение. Конфликт по `id` завершится ошибкой
     * (`ABORT`); на практике не достигается — Room генерирует id сам.
     *
     * @return id вставленной строки
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ExerciseEntity): Long

    /** Обновляет существующее упражнение. */
    @Update
    suspend fun update(entity: ExerciseEntity)

    /** Возвращает упражнение по идентификатору или `null`, если не найдено. */
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?
}
