package com.myworkouts.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.myworkouts.data.db.CategoryDao
import com.myworkouts.data.db.CategoryEntity
import com.myworkouts.data.db.ExerciseDao
import com.myworkouts.data.db.ExerciseEntryDao
import com.myworkouts.data.db.ExerciseEntryEntity
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.db.standardCategories
import com.myworkouts.data.db.toDomain
import com.myworkouts.data.db.toEntity
import com.myworkouts.data.db.toEntryWithExercise
import com.myworkouts.data.db.toListItem
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.random.Random

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
 * `TooManyFunctions` подавлен осознанно: методы — единый контракт
 * [WorkoutDataRepository] (app-data-screen, exercises-archive,
 * custom-categories); дробить реализацию на части хуже подавления.
 */
@Suppress("TooManyFunctions")
class RoomWorkoutDataRepository(
    private val database: WorkoutDatabase,
    private val standardExercises: List<StandardExercise>
) : WorkoutDataRepository {
    private val exerciseDao: ExerciseDao = database.exerciseDao()
    private val exerciseEntryDao: ExerciseEntryDao = database.exerciseEntryDao()
    private val categoryDao: CategoryDao = database.categoryDao()

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
     * [deleteExercise]); архивация последнего активного →
     * `Result.failure(LastActiveExerciseException())` — guard, чтобы
     * активный каталог не опустел (prevent-from-archiving-all, design
     * Decision 3; restore не ограничен); прочие ошибки БД — generic
     * `Result.failure`.
     */
    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> =
        runCatching {
            val entity =
                exerciseDao.getById(id) ?: error("Упражнение не найдено")
            if (isArchived && !entity.isArchived && exerciseDao.activeCount() == 1) {
                throw LastActiveExerciseException()
            }
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
     * Реактивный признак «в архиве есть упражнения или категории»
     * (exercises-archive, design Decision 2 + custom-categories, spec
     * «Кнопка архива в TopAppBar») — combine двух существующих
     * счётчиков DAO: интерфейсу не нужен третий SQL-запрос.
     */
    override fun hasArchived(): Flow<Boolean> =
        combine(
            exerciseDao.observeArchivedCount(),
            categoryDao.observeArchivedCount()
        ) { exercises, categories -> exercises > 0 || categories > 0 }

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
     * `EntryListItemWithExerciseEntity.toListItem()` (workout-entries-list,
     * design Decision 2); источник — единый широкий запрос
     * [ExerciseEntryDao.observeEntryRows].
     */
    override fun entries(): Flow<List<EntryListItem>> =
        exerciseEntryDao
            .observeEntryRows()
            .map { rows -> rows.map { it.toListItem() } }

    /**
     * Реактивный список записей для расчётов обзора (overview-screen,
     * design Decision 1): тот же широкий JOIN-запрос
     * [ExerciseEntryDao.observeEntryRows], маппинг — extension
     * `EntryListItemWithExerciseEntity.toEntryWithExercise()`.
     */
    override fun entriesWithExercise(): Flow<List<EntryWithExercise>> =
        exerciseEntryDao
            .observeEntryRows()
            .map { rows -> rows.map { it.toEntryWithExercise() } }

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
     * - последнее активное (`!isArchived && activeCount() == 1`,
     *   pre-check через [ExerciseDao.activeCount]) → `Result.failure(
     *   LastActiveExerciseException())` — удаление опустошило бы
     *   активный каталог (prevent-from-archiving-all, design Decision 3);
     *   архивное упражнение удаляется свободно — не снижает число
     *   активных.
     * - прочие исключения — generic `Result.failure`.
     */
    @Suppress("ReturnCount")
    override suspend fun deleteExercise(id: Long): Result<Unit> {
        // 4 ранних return — сами бизнес-правила (нет id / стандартное /
        // есть записи / последний активный); разворачивать их во
        // вложенные условия хуже (ponytail).
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
        if (!exercise.isArchived && exerciseDao.activeCount() == 1) {
            return Result.failure(LastActiveExerciseException())
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

    /**
     * Разовый снимок истории записей упражнения (design D2): упорядоченный
     * запрос [ExerciseEntryDao.historyForExercise], маппинг — `toDomain()`.
     * Пустая история — `success(emptyList())`; ошибки БД — generic
     * `Result.failure`.
     */
    override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> =
        runCatching {
            exerciseEntryDao.historyForExercise(exerciseId).map { it.toDomain() }
        }

    /**
     * Создаёт кастомную категорию (custom-categories, design Decision 3;
     * tasks `custom-categories-color-change` 1.2). Проверки до записи в БД:
     * непустое после `trim()` имя и уникальность по
     * `name.trim().lowercase()` среди всех категорий (снимок
     * [CategoryDao.getAll] — активные и архивные); unique-индекс БД —
     * запасная страховка по точному совпадению. Цвет передаётся готовым
     * из формы категории и сохраняется как есть — операция не
     * генерирует цвет (спека `category-data` «Создание категории с
     * цветом из формы»).
     */
    override suspend fun addCategory(
        name: String,
        colorArgb: Int
    ): Result<Category> =
        runCatching {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) {
                error("Имя категории не может быть пустым")
            }
            val existingCategories = categoryDao.getAll()
            val normalizedName = trimmedName.lowercase()
            if (existingCategories.any { existing ->
                    existing.name.trim().lowercase() == normalizedName
                }
            ) {
                error("Категория «$trimmedName» уже существует")
            }
            val entity =
                CategoryEntity(
                    name = trimmedName,
                    colorArgb = colorArgb,
                    isCustom = true,
                    isArchived = false,
                    createdAt = System.currentTimeMillis()
                )
            entity.copy(id = categoryDao.insert(entity)).toDomain()
        }

    /**
     * Обновляет имя и цвет кастомной категории одной транзакцией
     * (tasks `custom-categories-color-change` 1.4): guard `isCustom`,
     * проверки имени — как в [addCategory] (дубль исключает саму
     * обновляемую категорию). Внутри транзакции:
     * [CategoryDao.updateNameAndColor] +
     * [ExerciseDao.updateCategoryName] — активные и архивные упражнения
     * одним UPDATE без фильтра архива, каскад выполняется безусловно
     * (при неизменном имени — практический no-op). Стандартная
     * категория — [StandardCategoryDeletionException]; при любой ошибке
     * транзакция откатывается.
     */
    override suspend fun updateCategory(
        id: Long,
        newName: String,
        colorArgb: Int
    ): Result<Unit> =
        runCatching {
            database.withTransaction {
                val category =
                    categoryDao.getById(id)
                        ?: error("Категория не найдена")
                if (!category.isCustom) {
                    throw StandardCategoryDeletionException()
                }
                val trimmedName = newName.trim()
                if (trimmedName.isEmpty()) {
                    error("Имя категории не может быть пустым")
                }
                val normalizedName = trimmedName.lowercase()
                val hasDuplicate =
                    categoryDao
                        .getAll()
                        .any { existing ->
                            existing.id != id &&
                                existing.name.trim().lowercase() == normalizedName
                        }
                if (hasDuplicate) {
                    error("Категория «$trimmedName» уже существует")
                }
                categoryDao.updateNameAndColor(id, trimmedName, colorArgb)
                exerciseDao.updateCategoryName(category.name, trimmedName)
            }
        }

    /**
     * Удаляет категорию — pre-check внутри транзакции (custom-categories,
     * design Decision 4): отсутствующий id, стандартная категория,
     * ссылающиеся упражнения (активные или архивные) — типизированные
     * ошибки; VM маппит их в sealed `DeleteCategoryError`. Ссылок по
     * имени без FK нет — целостность держит порядок проверок в
     * транзакции. Последняя активная (pre-check через
     * [CategoryDao.activeCount]) →
     * `Result.failure(LastActiveCategoryException())` — удаление
     * опустошило бы активный список (prevent-from-archiving-all, design
     * Decision 3); архивная категория удаляется свободно — не снижает
     * число активных.
     */
    override suspend fun deleteCategory(id: Long): Result<Unit> =
        runCatching {
            database.withTransaction {
                val category =
                    categoryDao.getById(id)
                        ?: error("Категория не найдена")
                if (!category.isCustom) {
                    throw StandardCategoryDeletionException()
                }
                if (exerciseDao.countByCategoryName(category.name) > 0) {
                    throw CategoryInUseDeletionException()
                }
                if (!category.isArchived && categoryDao.activeCount() == 1) {
                    throw LastActiveCategoryException()
                }
                categoryDao.deleteById(id)
            }
        }

    /**
     * Архивация/восстановление категории (custom-categories, spec
     * «Архивация и восстановление категории»): один SQL round-trip через
     * [CategoryDao.updateArchived] — паттерн [setArchived]. Упражнения
     * категории не трогаются; отсутствующий id —
     * `Result.failure(IllegalStateException("Категория не найдена"))` —
     * race между сбором списка и подтверждением; архивация последней
     * активной → `Result.failure(LastActiveCategoryException())` —
     * guard, чтобы активный список не опустел (prevent-from-archiving-all,
     * design Decision 3; restore не ограничен).
     */
    override suspend fun setCategoryArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> =
        runCatching {
            val entity =
                categoryDao.getById(id) ?: error("Категория не найдена")
            if (isArchived && !entity.isArchived && categoryDao.activeCount() == 1) {
                throw LastActiveCategoryException()
            }
            if (categoryDao.updateArchived(id, isArchived) == 0) {
                error("Категория не найдена")
            }
        }

    /** Реактивный список активных категорий — маппинг [CategoryEntity.toDomain]. */
    override fun categories(): Flow<List<Category>> =
        categoryDao
            .observeCategories()
            .map { entities -> entities.map { it.toDomain() } }

    /** Реактивный список архивных категорий — тот же контракт, что в [categories]. */
    override fun archivedCategories(): Flow<List<Category>> =
        categoryDao
            .observeArchivedCategories()
            .map { entities -> entities.map { it.toDomain() } }

    /**
     * Реактивный набор имён категорий с упражнениями (custom-categories,
     * design Decision 4): `SELECT DISTINCT` схлопывает дубли множеством.
     */
    override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> =
        exerciseDao
            .observeCategoryNamesInUse()
            .map { names -> names.toSet() }

    /** Разовый снимок всех категорий — по образцу [getAllExercises]. */
    override suspend fun getAllCategories(): Result<List<Category>> =
        runCatching {
            categoryDao.getAll().map { it.toDomain() }
        }

    override suspend fun importData(
        categories: List<Category>,
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> =
        runCatching {
            database.withTransaction {
                val now = System.currentTimeMillis()
                // 1. Категории (custom-categories, design D9): вставка до
                // упражнений — их категории резолвятся именем. `createdAt` —
                // момент импорта: в файле его нет, сортировка списков — по
                // нему.
                categories.forEach { category ->
                    categoryDao.insert(category.toEntity(createdAt = now))
                }
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
                categoryDao.deleteAll()
                // Восстановление стандартного набора тем же маппингом, что
                // и prepopulate: isCustom = false, честный createdAt = now;
                // у категорий — единый now на набор (стабильная сортировка).
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
                standardCategories.forEach { standard ->
                    categoryDao.insert(
                        CategoryEntity(
                            name = standard.name,
                            colorArgb = standard.colorArgb,
                            isCustom = false,
                            isArchived = false,
                            createdAt = now
                        )
                    )
                }
            }
        }
}

/** Полное кольцо hue в модели HSV (custom-categories, design Decision 3). */
private const val DEGREES_IN_HUE_RING = 360f

/**
 * Минимальный угловой отступ hue новой категории от существующих
 * (custom-categories, design Decision 3): гарантирован, пока наибольший
 * зазор кольца не меньше `2 × MIN_HUE_OFFSET`.
 */
private const val MIN_HUE_OFFSET = 15f

/** Фиксированные насыщенность/яркость — визуально согласованы с палитрой категорий. */
private const val CATEGORY_COLOR_SATURATION = 0.55f
private const val CATEGORY_COLOR_VALUE = 0.55f

/** Число компонент HSV-массива `android.graphics.Color` (hue, saturation, value). */
private const val HSV_COMPONENTS = 3

/**
 * Выбирает hue для новой категории на hue-кольце (custom-categories,
 * design Decision 3) — чистая функция, детерминизм через injectable
 * [random] для JVM-тестов.
 *
 * Наибольший зазор кольца `gap ≥ 2 × MIN_HUE_OFFSET` (30°) — случайный
 * hue внутри центрального участка зазора, до обоих соседних hue остаётся
 * ≥ 15°; все зазоры меньше 30° — центр наибольшего зазора (отступ может
 * нарушаться — повторы допустимы). Гарантия отступа держит до 12
 * категорий: максимальный зазор ≥ среднего `360/n` ≥ 30°.
 *
 * @param existingHues hue существующих категорий (активных и архивных)
 * @param random injectable источник случайности
 * @return hue новой категории в градусах
 */
internal fun pickCategoryHue(
    existingHues: List<Float>,
    random: Random
): Float {
    if (existingHues.isEmpty()) {
        return random.nextFloat() * DEGREES_IN_HUE_RING
    }
    val sortedHues = existingHues.map { it.mod(DEGREES_IN_HUE_RING) }.sorted()
    var largestGap = -1f
    var gapStart = 0f
    for (index in sortedHues.indices) {
        val start = sortedHues[index]
        val end =
            if (index == sortedHues.lastIndex) {
                sortedHues[0] + DEGREES_IN_HUE_RING
            } else {
                sortedHues[index + 1]
            }
        val gap = end - start
        if (gap > largestGap) {
            largestGap = gap
            gapStart = start
        }
    }
    return if (largestGap >= 2 * MIN_HUE_OFFSET) {
        gapStart + MIN_HUE_OFFSET + random.nextFloat() * (largestGap - 2 * MIN_HUE_OFFSET)
    } else {
        gapStart + largestGap / 2f
    }
}

/**
 * Генерирует ARGB-цвет категории по правилам custom-categories (design
 * Decision 3 архивного change `custom-categories`): существующие цвета →
 * hue через `Color.colorToHSV` (единая модель HSV во всех расчётах),
 * hue — [pickCategoryHue], S/V фиксированные. `internal` — для инжекта
 * в `CategoryCreateViewModel` как генератора цвета пикера
 * (custom-categories-color-change, design Decision 1); операции создания
 * цвет больше не генерируют — `android.graphics.Color` на JVM
 * недоступен, поэтому VM-тесты подменяют генератор lambda-стабом.
 *
 * @param existingColors ARGB-цвета существующих категорий
 * @param random injectable источник случайности
 * @return ARGB-цвет новой категории
 */
internal fun generateCategoryColor(
    existingColors: List<Int>,
    random: Random
): Int {
    val hsv = FloatArray(HSV_COMPONENTS)
    val hues =
        existingColors.map { color ->
            android.graphics.Color.colorToHSV(color, hsv)
            hsv[0]
        }
    val hue = pickCategoryHue(hues, random)
    return android.graphics.Color.HSVToColor(
        floatArrayOf(hue, CATEGORY_COLOR_SATURATION, CATEGORY_COLOR_VALUE)
    )
}
