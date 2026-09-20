package com.myworkouts.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.myworkouts.data.db.ExerciseDao
import com.myworkouts.data.db.ExerciseEntryDao
import com.myworkouts.data.db.ExerciseEntryEntity
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.db.toDomain
import com.myworkouts.data.db.toEntity
import com.myworkouts.data.db.toListItem
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-реализация [WorkoutDataRepository]: каталог упражнений и записи
 * о выполнении.
 *
 * Вся валидация записей — в [EntryValidator]; сюда приходят уже собранные
 * доменные модели. API построен под экран создания/редактирования записи:
 * список каталога реактивный (`Flow`), точечные операции — `suspend`
 * с `Result` для обработки ошибок БД.
 *
 * @property database база приложения: DAO берутся из неё, массовным
 *   операциям (app-data-screen, design Decision 6) нужна транзакция
 *   `withTransaction` — DAO от другой базы сделали бы её бессмысленной
 * @property standardExercises стандартный набор для восстановления в
 *   [deleteAllData] — тот же список, что передаётся в
 *   [com.myworkouts.data.db.WorkoutDatabase.create]
 *
 * `TooManyFunctions` подавлен осознанно: 17 методов — единый контракт
 * [WorkoutDataRepository] (app-data-screen, exercises-archive); дробить
 * реализацию на части хуже подавления.
 */
@Suppress("TooManyFunctions")
class RoomWorkoutDataRepository(
    private val database: WorkoutDatabase,
    private val standardExercises: List<StandardExercise>
) : WorkoutDataRepository {
    private val exerciseDao: ExerciseDao = database.exerciseDao()
    private val exerciseEntryDao: ExerciseEntryDao = database.exerciseEntryDao()

    /**
     * Возвращает реактивный список упражнений каталога, отфильтрованный
     * по подстроке названия (без учёта регистра) и отсортированный
     * по убыванию кастомности, затем по убыванию `createdAt`, затем
     * по `name` без учёта регистра. Пустая подстрока возвращает весь каталог.
     */
    override fun exercises(query: String): Flow<List<Exercise>> =
        exerciseDao
            .observeExercises(query)
            .map { entities -> entities.map { it.toDomain() } }

    /**
     * Архивация/восстановление упражнения (exercises-archive, design
     * Decision 3): один метод для обоих направлений, один SQL round-trip
     * через [ExerciseDao.updateArchived]. Отсутствующий id →
     * `Result.failure(IllegalStateException("Упражнение не найдено"))` —
     * race между сбором списка и подтверждением алерта (паттерн
     * [deleteExercise]); прочие ошибки БД — generic `Result.failure`.
     */
    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> =
        runCatching {
            if (exerciseDao.updateArchived(id, isArchived) == 0) {
                error("Упражнение не найдено")
            }
        }

    /**
     * Реактивный список архивных упражнений, отсортированный по тому же
     * контракту, что и [exercises] (exercises-archive, design Decision 6).
     */
    override fun archivedExercises(): Flow<List<Exercise>> =
        exerciseDao
            .observeArchivedExercises()
            .map { entities -> entities.map { it.toDomain() } }

    /**
     * Реактивный счётчик архивных упражнений (exercises-archive, design
     * Decision 2) — условная видимость кнопки архива в `TopAppBar`.
     */
    override fun archivedCount(): Flow<Int> = exerciseDao.observeArchivedCount()

    /**
     * Вставляет новое упражнение в каталог. Возвращает id вставленной записи
     * каталога в `Result.success`. Для редактирования существующего упражнения
     * используйте [updateExercise].
     */
    override suspend fun addExercise(exercise: Exercise): Result<Long> =
        runCatching {
            exerciseDao.insert(exercise.toEntity())
        }

    /** Обновляет существующее упражнение. */
    override suspend fun updateExercise(exercise: Exercise): Result<Unit> {
        val entity = exercise.toEntity()
        return runCatching { exerciseDao.update(entity) }
    }

    /**
     * Возвращает упражнение по идентификатору или `null`, если не найдено
     * (edit-delete-exercise, tasks §1.3). `null` — `Result.success(null)`,
     * не failure: VM различает «ошибка слоя данных» и «запись отсутствует
     * при открытии edit-режима» через сам факт null.
     */
    override suspend fun getExercise(id: Long): Result<Exercise?> =
        runCatching {
            exerciseDao.getById(id)?.toDomain()
        }

    /**
     * Реактивный список id упражнений с ненулевым числом записей
     * (edit-delete-exercise, tasks §1.5, design Decision 8). Префикс FK
     * для pre-check перед удалением.
     */
    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> =
        exerciseEntryDao
            .observeExerciseIdsWithLinkedEntries()
            .map { it.toSet() }

    /**
     * Вставляет новую запись о выполнении. Перед записью прогоняет
     * [EntryValidator]; ошибки валидации и несуществующее `exerciseId`
     * возвращаются в `Result.failure` без записи в БД.
     *
     * Упражнение грузится по `exerciseId` до валидации: `measuredBy`
     * упражнения нужен валидатору (workout-entries-list, design Decision 7).
     * Несуществующий id → `Result.failure(IllegalStateException("Упражнение
     * не найдено"))` по образцу `deleteExercise`.
     */
    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> {
        validateWithExercise(entry)?.let { return Result.failure(it) }
        return runCatching { exerciseEntryDao.insert(entry.toEntity()) }
    }

    /**
     * Обновляет существующую запись. Перед записью прогоняет
     * [EntryValidator] (с `measuredBy` упражнения) и проставляет
     * `modifiedAt = now` (`createdAt` не трогается — это гарантирует
     * spec-сценарий «Изменение подходов и повторов»). `createdAt`
     * берётся из переданной записи, не перезаписывается.
     */
    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> {
        validateWithExercise(entry)?.let { return Result.failure(it) }
        return runCatching {
            val withModifiedAt = entry.copy(modifiedAt = System.currentTimeMillis())
            exerciseEntryDao.update(withModifiedAt.toEntity())
        }
    }

    /**
     * Пролог для `addEntry`/`updateEntry`: грузит упражнение по
     * `entry.exerciseId` и прогоняет [EntryValidator]. Возвращает
     * упражнение при успехе или `Throwable` (несуществующий id /
     * ошибка валидации) для пробрасывания в `Result.failure` вызывающим
     * кодом. Выделено, чтобы убрать дубликат между двумя write-операциями.
     */
    @Suppress("ReturnCount")
    private suspend fun validateWithExercise(entry: ExerciseEntry): Throwable? {
        val exercise =
            exerciseDao.getById(entry.exerciseId)?.toDomain()
                ?: return IllegalStateException("Упражнение не найдено")
        val validation = EntryValidator.validate(entry, exercise.measuredBy)
        if (validation.isFailure) {
            return checkNotNull(validation.exceptionOrNull()) {
                "Result.isFailure=true, but exceptionOrNull is null"
            }
        }
        return null
    }

    /** Возвращает запись по идентификатору или `null`. */
    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> =
        runCatching {
            exerciseEntryDao.getById(id)?.toDomain()
        }

    /**
     * Реактивный список записей с JOIN-проекцией на название упражнения.
     * Сортировка — в SQL (`ORDER BY date DESC`), маппинг — extension
     * `EntryListItemEntity.toListItem()` (workout-entries-list, design
     * Decision 2).
     */
    override fun entries(): Flow<List<EntryListItem>> =
        exerciseEntryDao
            .observeEntries()
            .map { projections -> projections.map { it.toListItem() } }

    /**
     * Удаляет запись о выполнении по идентификатору. Отсутствующий id —
     * успех без эффекта (идемпотентность, spec-сценарий «Удаление
     * несуществующей записи»); нештатные ошибки БД — `Result.failure`.
     */
    override suspend fun deleteEntry(id: Long): Result<Unit> = runCatching { exerciseEntryDao.deleteById(id) }

    /**
     * Удаляет упражнение из каталога по идентификатору (edit-delete-exercise,
     * tasks §1.5, design Decision 3, 8):
     * - отсутствующий id → `Result.failure(IllegalStateException(
     *   "Упражнение не найдено"))` (race между сбором списка и
     *   подтверждением алерта — редкий случай в офлайн-приложении);
     *   попадает в `DeleteExerciseError.Other(message)` через VM-маппинг
     *   Decision 3. Операция не идемпотентна, в отличие от [deleteEntry].
     * - стандартное упражнение (`isCustom = false`) →
     *   `Result.failure(StandardExerciseDeletionException())` —
     *   маппится в `DeleteExerciseError.StandardExercise`.
     * - FK-нарушение (у упражнения есть записи) — pre-check через
     *   [ExerciseEntryDao.countByExerciseId] ДО `exerciseDao.delete(...)`,
     *   чтобы не зависеть от парсинга текста SQLite-исключения
     *   (design Decision 8): `Result.failure(
     *   LinkedEntriesDeletionException())` →
     *   `DeleteExerciseError.HasLinkedEntries`.
     *   `SQLiteConstraintException` остаётся как safety net для race
     *   между pre-check и `deleteById` (запись добавлена в другой
     *   сессии) — маппится в тот же `HasLinkedEntries`.
     * - прочие исключения — generic `Result.failure`.
     */
    @Suppress("ReturnCount")
    override suspend fun deleteExercise(id: Long): Result<Unit> {
        // 3 ранних return — сами бизнес-правила (нет id / стандартное /
        // есть записи); разворачивать их во вложенные условия хуже
        // (ponytail).
        val exercise =
            exerciseDao.getById(id)?.toDomain()
                ?: return Result.failure(
                    IllegalStateException("Упражнение не найдено")
                )
        if (!exercise.isCustom) {
            return Result.failure(StandardExerciseDeletionException())
        }
        if (exerciseEntryDao.countByExerciseId(id) > 0) {
            return Result.failure(LinkedEntriesDeletionException())
        }
        return runCatching { exerciseDao.deleteById(id) }.recoverCatching { e ->
            // Safety net для race между pre-check и deleteById:
            // запись добавлена в параллельной сессии — то же сообщение
            // и тот же sealed-вариант, что у pre-check.
            if (e is SQLiteConstraintException) {
                throw LinkedEntriesDeletionException()
            }
            throw e
        }
    }

    override suspend fun getAllExercises(): Result<List<Exercise>> =
        runCatching {
            exerciseDao.getAll().map { it.toDomain() }
        }

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> =
        runCatching {
            exerciseEntryDao.getAll().map { it.toDomain() }
        }

    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> =
        runCatching {
            database.withTransaction {
                // Карта «ключ имени → id» поверх текущего каталога и
                // вставленных упражнений (design Decision 6). Вставка —
                // одиночными insert: нужен возвращённый id для связей.
                val idsByKey =
                    buildMap {
                        exerciseDao.getAll().forEach { entity ->
                            put(entity.name.trim().lowercase(), entity.id)
                        }
                        exercises.forEach { exercise ->
                            val id = exerciseDao.insert(exercise.toEntity())
                            put(exercise.name.trim().lowercase(), id)
                        }
                    }
                val now = System.currentTimeMillis()
                val entities =
                    entries.map { backup ->
                        val exerciseId =
                            idsByKey[backup.exerciseName.trim().lowercase()]
                                // Неразрешимое имя — защита от бага use-case:
                                // он такие записи отфильтровал (Decision 4).
                                ?: error(
                                    "Имя упражнения не разрешено каталогом: " +
                                        backup.exerciseName
                                )
                        ExerciseEntryEntity(
                            exerciseId = exerciseId,
                            workoutId = null,
                            date = backup.date,
                            sets = backup.sets,
                            reps = backup.reps,
                            weightKg = backup.weightKg,
                            durationSec = backup.durationSec,
                            comment = backup.comment,
                            createdAt = backup.createdAt,
                            modifiedAt = now
                        )
                    }
                exerciseEntryDao.insertAll(entities)
            }
        }

    override suspend fun deleteAllData(): Result<Unit> =
        runCatching {
            database.withTransaction {
                exerciseEntryDao.deleteAll()
                exerciseDao.deleteAll()
                // Восстановление стандартного набора тем же маппингом, что
                // и prepopulate: isCustom = false, честный createdAt = now.
                val now = System.currentTimeMillis()
                standardExercises.forEach { standard ->
                    exerciseDao.insert(
                        Exercise(
                            id = 0L,
                            name = standard.name,
                            category = standard.category,
                            isCustom = false,
                            comment = null,
                            createdAt = now,
                            measuredBy = standard.measuredBy,
                            iconNumber = standard.iconNumber
                        ).toEntity()
                    )
                }
            }
        }
}
