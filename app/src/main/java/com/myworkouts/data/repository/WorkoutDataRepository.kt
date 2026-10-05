package com.myworkouts.data.repository

import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
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
 * `TooManyFunctions` подавлен осознанно: 17 методов — точечные операции
 * экранов + снимки и массовые операции управления данными
 * (app-data-screen, exercises-archive); единый контракт вместо
 * интерфейсов под каждый экран.
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
     * Архивация/восстановление упражнения каталога (exercises-archive,
     * design Decision 3): `isArchived = true` — в архив, `false` — вернуть.
     * Отсутствующий id — `Result.failure(IllegalStateException("Упражнение
     * не найдено"))` (не идемпотентно, в отличие от [deleteEntry]); прочие
     * ошибки БД — generic `Result.failure`. Доступно и для стандартных, и
     * для кастомных упражнений.
     */
    suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit>

    /**
     * Реактивный список архивных упражнений (exercises-archive, spec
     * «Реактивный список архивных упражнений»); сортировка — та же, что в
     * [exercises]. Обновляется при архивации/восстановлении без ручного
     * refresh.
     */
    fun archivedExercises(): Flow<List<Exercise>>

    /**
     * Реактивный признак «в архиве есть упражнения или категории» —
     * условная видимость кнопки архива в `TopAppBar` каталога
     * (exercises-archive design Decision 2; custom-categories, spec
     * «Кнопка архива в TopAppBar»).
     */
    fun hasArchived(): Flow<Boolean>

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
     * Реактивный список записей с данными упражнений каталога
     * (overview-screen, design Decision 1): единый вход расчётов трёх
     * вкладок обзора (статистика, рекорды, активность). JOIN и сортировка
     * по дате убыванием — на стороне SQL, как в [entries].
     */
    fun entriesWithExercise(): Flow<List<EntryWithExercise>>

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
     * Разовый снимок истории записей одного упражнения, новые первыми
     * (entry-prefilled-reps-and-duration, design D2): сортировка
     * `date DESC, created_at DESC` — на стороне SQL. Пустая история —
     * `success(emptyList())`, не failure. Источник предзаполнения
     * полей экрана записи в режиме создания.
     */
    suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>>

    /**
     * Создаёт кастомную категорию с сгенерированным цветом
     * (custom-categories, spec «Создание категории с генерацией цвета»).
     * Имя — непустое после `trim()` и уникальное по
     * `name.trim().lowercase()` среди всех категорий (активных и
     * архивных); пустое или дублирующее — `Result.failure`.
     * Созданная категория: `isCustom = true`, `isArchived = false`.
     */
    suspend fun addCategory(name: String): Result<Category>

    /**
     * Переименовывает кастомную категорию одной транзакцией
     * (custom-categories, design Decision 1): обновляет
     * `categories.name` и `category` всех ссылающихся упражнений —
     * активных и архивных. Стандартная категория
     * ([StandardCategoryDeletionException]), пустое или дублирующее имя,
     * отсутствующий id — `Result.failure`; при любой ошибке транзакция
     * откатывается целиком.
     */
    suspend fun renameCategory(
        id: Long,
        newName: String
    ): Result<Unit>

    /**
     * Удаляет категорию: только кастомную (`isCustom = true`) и только
     * если на неё не ссылается ни одно упражнение (custom-categories,
     * spec «Удаление категории»). Ошибки — типизированными исключениями
     * в `Result.failure` ([StandardCategoryDeletionException],
     * [CategoryInUseDeletionException]) — по образцу [deleteExercise].
     */
    suspend fun deleteCategory(id: Long): Result<Unit>

    /**
     * Архивация/восстановление категории (custom-categories, spec
     * «Архивация и восстановление категории») — по образцу [setArchived]:
     * `isArchived = true` — в архив, `false` — вернуть. Доступно и для
     * стандартных, и для кастомных; упражнения категории не меняются.
     * Отсутствующий id — `Result.failure`.
     */
    suspend fun setCategoryArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit>

    /**
     * Реактивный список активных категорий (`isArchived = false`),
     * стандартных и кастомных; сортировка `is_custom DESC, created_at
     * DESC, name COLLATE NOCASE`. Источник вкладки «Категории» и
     * дропдауна выбора категории (custom-categories, spec «Реактивные
     * списки и счётчики категорий»).
     */
    fun categories(): Flow<List<Category>>

    /**
     * Реактивный список архивных категорий; сортировка — та же, что в
     * [categories]. Источник вкладки «Категории» архива
     * (custom-categories, spec «Вкладка „Категории“ в архиве»).
     */
    fun archivedCategories(): Flow<List<Category>>

    /**
     * Реактивный набор имён категорий, на которые ссылается хотя бы одно
     * упражнение (активное или архивное) — пре-чек удаления категории
     * (custom-categories, design Decision 4): VM открывает алерт только
     * для свободного имени; исключение из репозитория после
     * подтверждения — safety net.
     */
    fun categoryNamesWithLinkedExercises(): Flow<Set<String>>

    /**
     * Разовый снимок всех категорий — активных и архивных — по образцу
     * [getAllExercises]. Для экспорта резервной копии и дедупликации
     * импорта (custom-categories, spec «Снимок всех категорий»).
     */
    suspend fun getAllCategories(): Result<List<Category>>

    /**
     * Транзакционная вставка подготовленных use-case'ом данных
     * (app-data-screen, design Decision 6; custom-categories, design
     * Decision 9): дедупликация и валидация — задача use-case,
     * репозиторий отвечает только за хранение. Внутри одной транзакции
     * вставляет категории, затем упражнения (разрешая `exerciseName`
     * в id — вставленные упражнения одиночными insert с возвратом id;
     * неразрешимое имя — защита от бага use-case), затем записи.
     * При любой ошибке — откат без частичных вставок.
     */
    suspend fun importData(
        categories: List<Category>,
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
