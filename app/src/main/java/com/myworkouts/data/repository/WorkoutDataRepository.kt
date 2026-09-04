package com.myworkouts.data.repository

import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий данных тренировок: каталог упражнений и записи о выполнении.
 *
 * Интерфейс над [RoomWorkoutDataRepository] — единственная реализация;
 * нужен для fake-репозитория на `MutableStateFlow` в JVM-тестах ViewModel
 * (design.md Decision 1 change `entry-create-screen`). API реактивный там,
 * где нужен список (`Flow`), и `suspend` + `Result` для точечных операций.
 */
interface WorkoutDataRepository {
    /**
     * Возвращает реактивный список упражнений каталога, отфильтрованный
     * по подстроке названия (без учёта регистра) и отсортированный по имени.
     * Пустая подстрока возвращает весь каталог.
     */
    fun exercises(query: String): Flow<List<Exercise>>

    /**
     * Вставляет новое упражнение в каталог. Возвращает id вставленной записи
     * каталога в `Result.success`.
     */
    suspend fun addExercise(exercise: Exercise): Result<Long>

    /** Обновляет существующее упражнение. */
    suspend fun updateExercise(exercise: Exercise): Result<Unit>

    /**
     * Вставляет новую запись о выполнении с валидацией [EntryValidator].
     */
    suspend fun addEntry(entry: ExerciseEntry): Result<Long>

    /** Обновляет существующую запись с валидацией [EntryValidator]. */
    suspend fun updateEntry(entry: ExerciseEntry): Result<Unit>

    /** Возвращает запись по идентификатору или `null`. */
    suspend fun getEntry(id: Long): Result<ExerciseEntry?>

    /** Удаляет запись по идентификатору. Отсутствующий id — успех без эффекта. */
    suspend fun deleteEntry(id: Long): Result<Unit>

    /**
     * Удаляет упражнение из каталога: отсутствующий id, стандартное
     * упражнение и FK-нарушение — `Result.failure`.
     */
    suspend fun deleteExercise(id: Long): Result<Unit>
}
