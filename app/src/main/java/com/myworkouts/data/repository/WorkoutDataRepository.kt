package com.myworkouts.data.repository

import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
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
 *
 * `TooManyFunctions` подавлен осознанно: 13 методов — точечные операции
 * экранов + снимки и массовые операции управления данными
 * (app-data-screen); единый контракт вместо интерфейсов под каждый экран.
 */
@Suppress("TooManyFunctions")
interface WorkoutDataRepository {
    /**
     * Возвращает реактивный список упражнений каталога, отфильтрованный
     * по подстроке названия (без учёта регистра) и отсортированный
     * по убыванию кастомности, затем по убыванию `createdAt`, затем
     * по `name` без учёта регистра. Пустая подстрока возвращает весь каталог.
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
     * Возвращает упражнение по идентификатору или `null`, если не найдено
     * (edit-delete-exercise, tasks §1.3). Используется для prefill-а формы
     * `ExerciseCreateScreen` в edit-режиме; семантика «не найдено» —
     * `Result.success(null)` (не failure), чтобы VM различала «ошибка
     * слоя данных» и «запись отсутствует» (race при открытии).
     */
    suspend fun getExercise(id: Long): Result<Exercise?>

    /**
     * Реактивный список id упражнений, на которые ссылаются записи
     * (edit-delete-exercise, tasks §1.5, design Decision 8). Префикс FK
     * для pre-check перед удалением: `requestDelete` смотрит `value`
     * синхронно, без async-обвязки на тапе. Реактивность обновляет
     * снекбар при добавлении/удалении записи в другой табе — но текущая
     * навигация скрывает bottom-bar на дочерних экранах, так что
     * визуальный эффект только при возврате.
     */
    fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>>

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

    /**
     * Реактивный список всех записей о выполнении с названием упражнения
     * (workout-entries-list, spec «Реактивный список записей»). Сортировка
     * по дате убыванием — на стороне SQL. Актуализируется при изменениях
     * таблицы записей.
     */
    fun entries(): Flow<List<EntryListItem>>

    /**
     * Разовый снимок всего каталога упражнений — стандартных и
     * пользовательских (app-data-screen, design Decision 6). Отдельный
     * метод вместо `exercises("").first()`: `exercises(query)` —
     * UI-поисковый `Flow` с LIKE-фильтром, разовый снимок через него —
     * неявная семантика. Для экспорта и дедупликации импорта.
     */
    suspend fun getAllExercises(): Result<List<Exercise>>

    /**
     * Разовый снимок всех записей о выполнении (app-data-screen, design
     * Decision 6) — для экспорта и дедупликации импорта.
     */
    suspend fun getAllEntries(): Result<List<ExerciseEntry>>

    /**
     * Транзакционная вставка подготовленных use-case'ом данных
     * (app-data-screen, design Decision 6): дедупликация и валидация —
     * задача use-case, репозиторий отвечает только за хранение. Внутри
     * той же транзакции разрешает `exerciseName` в id (вставленные
     * упражнения — одиночными insert с возвратом id); неразрешимое имя —
     * защита от бага use-case. При любой ошибке — откат без частичных
     * вставок.
     */
    suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit>

    /**
     * Массовый сброс (app-data-screen, design Decision 6): удаляет все
     * записи и все упражнения одной транзакцией и восстанавливает
     * стандартный набор (`isCustom = false`). Единственное намеренное
     * исключение из правила «стандартное упражнение нельзя удалить»:
     * работает на уровне DAO внутри транзакции, а не через
     * [deleteExercise].
     */
    suspend fun deleteAllData(): Result<Unit>
}
