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
 * Поиск и сортировка — в SQL (`ORDER BY is_custom DESC, created_at DESC,
 * name COLLATE NOCASE`, `LIKE '%' || :query || '%' COLLATE NOCASE`), см.
 * design.md Decision 5: фильтрация в Kotlin отклонена как лишняя работа
 * на UI-потоке.
 * Спецсимволы LIKE (`%`, `_`) в пользовательском вводе не экранируются —
 * YAGNI для каталога из 13 названий; экранирование добавится миграцией
 * при росте каталога.
 */
@Dao
interface ExerciseDao {
    /**
     * Возвращает активный каталог (только `is_archived = 0`), отфильтрованный
     * по подстроке названия (без учёта регистра) и отсортированный по
     * убыванию кастомности, затем по убыванию `createdAt`, затем по `name`
     * без учёта регистра. Пустая подстрока возвращает весь активный каталог.
     * Архивные упражнения — только через [observeArchivedExercises]
     * (exercises-archive).
     */
    @Query(
        "SELECT * FROM exercises " +
            "WHERE is_archived = 0 " +
            "AND name LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY is_custom DESC, created_at DESC, name COLLATE NOCASE"
    )
    fun observeExercises(query: String): Flow<List<ExerciseEntity>>

    /**
     * Реактивный список архивных упражнений (`is_archived = 1`),
     * отсортированный по тому же контракту, что и [observeExercises]
     * (exercises-archive, design Decision 6: сортировка едина для обоих
     * экранов).
     */
    @Query(
        "SELECT * FROM exercises " +
            "WHERE is_archived = 1 " +
            "ORDER BY is_custom DESC, created_at DESC, name COLLATE NOCASE"
    )
    fun observeArchivedExercises(): Flow<List<ExerciseEntity>>

    /**
     * Реактивный счётчик архивных упражнений (exercises-archive, design
     * Decision 2): источник условной видимости кнопки архива в `TopAppBar`.
     * Запрос лёгкий; индекс на `is_archived` не нужен, пока таблица мала.
     */
    @Query("SELECT COUNT(*) FROM exercises WHERE is_archived = 1")
    fun observeArchivedCount(): Flow<Int>

    /**
     * Non-reactive счётчик активных упражнений (prevent-from-archiving-all,
     * design Decision 3): guard «последний активный» в репозитории —
     * одноразовая проверка перед записью, Flow не нужен (по образцу
     * non-reactive pre-check'ов репозитория).
     */
    @Query("SELECT COUNT(*) FROM exercises WHERE is_archived = 0")
    suspend fun activeCount(): Int

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

    /**
     * Проставляет флаг архивации упражнения за один SQL-запрос
     * (exercises-archive): один round-trip вместо read-modify-write.
     *
     * @return число изменённых строк — `0` если id отсутствует
     */
    @Query("UPDATE exercises SET is_archived = :isArchived WHERE id = :id")
    suspend fun updateArchived(
        id: Long,
        isArchived: Boolean
    ): Int

    /** Возвращает упражнение по идентификатору или `null`, если не найдено. */
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    /** Удаляет упражнение по идентификатору. Отсутствующий id — успех без эффекта. */
    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Переименовывает категорию во всём каталоге упражнений — активных
     * и архивных (custom-categories, design Decision 1): без FK
     * целостность имён держит транзакция вместе с
     * [CategoryDao.updateNameAndColor].
     */
    @Query("UPDATE exercises SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(
        oldName: String,
        newName: String
    )

    /**
     * Число упражнений — активных и архивных — ссылающихся на категорию
     * `name`: pre-check удаления внутри транзакции (custom-categories,
     * design Decision 4).
     */
    @Query("SELECT COUNT(*) FROM exercises WHERE category = :name")
    suspend fun countByCategoryName(name: String): Int

    /**
     * Реактивный список различных имён категорий, на которые ссылается
     * хотя бы одно упражнение (активное или архивное) — пре-чек удаления
     * категории (custom-categories, design Decision 4): VM держит
     * подписку и открывает алерт только для свободного имени.
     */
    @Query("SELECT DISTINCT category FROM exercises")
    fun observeCategoryNamesInUse(): Flow<List<String>>

    /**
     * Разовый снимок всего каталога без поискового фильтра и сортировки —
     * для экспорта и дедупликации импорта (app-data-screen, design
     * Decision 6).
     */
    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    /**
     * Удаляет все упражнения каталога. Только внутри транзакции
     * `deleteAllData` (app-data-screen, design Decision 6): FK не даст
     * выполниться, пока в БД есть записи.
     */
    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}
