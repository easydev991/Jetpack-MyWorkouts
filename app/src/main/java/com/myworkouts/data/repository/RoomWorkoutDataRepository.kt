package com.myworkouts.data.repository

import com.myworkouts.data.db.ExerciseDao
import com.myworkouts.data.db.ExerciseEntryDao
import com.myworkouts.data.db.toDomain
import com.myworkouts.data.db.toEntity
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Репозиторий данных тренировок: каталог упражнений и записи о выполнении.
 *
 * Concrete-класс — design.md Decision 5: интерфейс появится в следующем
 * change вместе с первым ViewModel-тестом, которому нужен fake-репозиторий
 * на `MutableStateFlow`. API построен под будущий экран создания/редактирования
 * записи: список каталога реактивный (`Flow`), точечные операции — `suspend`
 * с `Result` для обработки ошибок БД.
 *
 * @property exerciseDao DAO каталога упражнений
 * @property exerciseEntryDao DAO записей о выполнении
 */
class RoomWorkoutDataRepository(
    private val exerciseDao: ExerciseDao,
    private val exerciseEntryDao: ExerciseEntryDao
) {
    /**
     * Возвращает реактивный список упражнений каталога, отфильтрованный
     * по подстроке названия (без учёта регистра) и отсортированный по имени.
     * Пустая подстрока возвращает весь каталог.
     */
    fun exercises(query: String): Flow<List<Exercise>> =
        exerciseDao
            .observeExercises(query)
            .map { entities -> entities.map { it.toDomain() } }

    /**
     * Вставляет новое упражнение в каталог. Возвращает id вставленной записи
     * каталога в `Result.success`. Для редактирования существующего упражнения
     * используйте [updateExercise].
     */
    suspend fun addExercise(exercise: Exercise): Result<Long> = runCatching { exerciseDao.insert(exercise.toEntity()) }

    /** Обновляет существующее упражнение. */
    suspend fun updateExercise(exercise: Exercise): Result<Unit> {
        val entity = exercise.toEntity()
        return runCatching { exerciseDao.update(entity) }
    }

    /**
     * Вставляет новую запись о выполнении. Перед записью прогоняет
     * [EntryValidator]; ошибки валидации и FK (несуществующее
     * `exerciseId`) возвращаются в `Result.failure` без записи в БД.
     */
    suspend fun addEntry(entry: ExerciseEntry): Result<Long> {
        val validation = EntryValidator.validate(entry)
        if (validation.isFailure) {
            return Result.failure(
                checkNotNull(validation.exceptionOrNull()) {
                    "Result.isFailure=true, but exceptionOrNull is null"
                }
            )
        }
        return runCatching {
            exerciseEntryDao.insert(entry.toEntity())
        }
    }

    /**
     * Обновляет существующую запись. Перед записью прогоняет
     * [EntryValidator] и проставляет `modifiedAt = now` (`createdAt`
     * не трогается — это гарантирует spec-сценарий «Изменение подходов
     * и повторов»). `createdAt` берётся из переданной записи, не
     * перезаписывается.
     */
    suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> {
        val validation = EntryValidator.validate(entry)
        if (validation.isFailure) {
            return Result.failure(
                checkNotNull(validation.exceptionOrNull()) {
                    "Result.isFailure=true, but exceptionOrNull is null"
                }
            )
        }
        return runCatching {
            val withModifiedAt = entry.copy(modifiedAt = System.currentTimeMillis())
            exerciseEntryDao.update(withModifiedAt.toEntity())
        }
    }

    /** Возвращает запись по идентификатору или `null`. */
    suspend fun getEntry(id: Long): Result<ExerciseEntry?> = runCatching { exerciseEntryDao.getById(id)?.toDomain() }
}
