package com.myworkouts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO категорий упражнений (custom-categories, design Decision 2).
 *
 * Поиск и сортировка — в SQL, как в [ExerciseDao]: единый контракт
 * `is_custom DESC, created_at DESC, name COLLATE NOCASE` для активных
 * и архивных списков.
 */
@Dao
interface CategoryDao {
    /**
     * Реактивный список активных категорий (`is_archived = 0`),
     * отсортированный по убыванию кастомности, затем по убыванию
     * `createdAt`, затем по `name` без учёта регистра. Архивные —
     * только через [observeArchivedCategories].
     */
    @Query(
        "SELECT * FROM categories " +
            "WHERE is_archived = 0 " +
            "ORDER BY is_custom DESC, created_at DESC, name COLLATE NOCASE"
    )
    fun observeCategories(): Flow<List<CategoryEntity>>

    /**
     * Реактивный список архивных категорий (`is_archived = 1`),
     * отсортированный по тому же контракту, что и [observeCategories].
     */
    @Query(
        "SELECT * FROM categories " +
            "WHERE is_archived = 1 " +
            "ORDER BY is_custom DESC, created_at DESC, name COLLATE NOCASE"
    )
    fun observeArchivedCategories(): Flow<List<CategoryEntity>>

    /**
     * Реактивный счётчик архивных категорий — источник условной
     * видимости вкладки «Категории» в архиве (по образцу
     * [ExerciseDao.observeArchivedCount]).
     */
    @Query("SELECT COUNT(*) FROM categories WHERE is_archived = 1")
    fun observeArchivedCount(): Flow<Int>

    /**
     * Вставляет новую категорию. Конфликт unique-индекса `name`
     * (точное совпадение) завершится ошибкой (`ABORT`);
     * регистронезависимый дедуп выполняется в репозитории до вставки.
     *
     * @return id вставленной строки
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity): Long

    /** Возвращает категорию по идентификатору или `null`, если не найдено. */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    /**
     * Разовый снимок всех категорий — активных и архивных
     * (custom-categories, spec «Снимок всех категорий»): валидация
     * уникальности имени и экспорт. Сортировка — тот же контракт, что
     * в [observeCategories].
     */
    @Query(
        "SELECT * FROM categories " +
            "ORDER BY is_custom DESC, created_at DESC, name COLLATE NOCASE"
    )
    suspend fun getAll(): List<CategoryEntity>

    /**
     * Проставляет флаг архивации категории за один SQL-запрос — по
     * образцу [ExerciseDao.updateArchived]: один round-trip вместо
     * read-modify-write. Упражнения категории не трогаются.
     *
     * @return число изменённых строк — `0` если id отсутствует
     */
    @Query("UPDATE categories SET is_archived = :isArchived WHERE id = :id")
    suspend fun updateArchived(
        id: Long,
        isArchived: Boolean
    ): Int

    /**
     * Обновляет имя и цвет категории одним запросом. Только внутри
     * транзакции `updateCategory` вместе с обновлением
     * `exercises.category` — целостность имён без FK держит транзакция
     * (custom-categories, design Decision 1).
     */
    @Query("UPDATE categories SET name = :name, color_argb = :colorArgb WHERE id = :id")
    suspend fun updateNameAndColor(
        id: Long,
        name: String,
        colorArgb: Int
    )

    /**
     * Удаляет категорию по идентификатору. Только после pre-check
     * `isCustom` и отсутствия упражнений внутри транзакции
     * (custom-categories, design Decision 4).
     */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Удаляет все категории. Только внутри транзакции `deleteAllData`
     * с последующим пересевом стандартного набора.
     */
    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
