package com.myworkouts.data.repository

import androidx.room.withTransaction
import com.myworkouts.data.db.CategoryDao
import com.myworkouts.data.db.CategoryEntity
import com.myworkouts.data.db.ExerciseDao
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.domain.model.Category
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * JVM-тесты категорийных операций [RoomWorkoutDataRepository]
 * (custom-categories, этап 2): генерация цвета, CRUD с валидацией имени,
 * транзакционное переименование, пре-чек удаления, архивация и
 * реактивные запросы — делегирование к MockK-DAO.
 *
 * `Room`-транзакция подменяется прямым выполнением блока — реальная БД
 * на JVM не поднимается.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomWorkoutDataRepositoryCategoryTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var repository: RoomWorkoutDataRepository

    /** Цвет, передаваемый в `addCategory` — персистится операцией как есть. */
    private val testColor: Int = 0xFF123456.toInt()

    private val spineCategory =
        CategoryEntity(
            id = 5L,
            name = "Спина",
            colorArgb = 0xFF102030.toInt(),
            isCustom = true,
            isArchived = false,
            createdAt = 1_000L
        )

    @BeforeEach
    fun setUp() {
        database = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        exerciseDao = mockk(relaxed = true)
        every { database.categoryDao() } returns categoryDao
        every { database.exerciseDao() } returns exerciseDao
        repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = emptyList()
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Подмена `withTransaction` (custom-categories, design Decision 1/4:
     * rename и delete выполняются в транзакции): блок выполняется
     * напрямую. Блок ищется по типу `Function1` — позиция аргументов
     * suspend-расширения в MockK (receiver/continuation) не важна.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mockRoomTransaction() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            val block =
                checkNotNull(args.filterIsInstance<Function1<*, *>>().firstOrNull()) {
                    "Ожидался suspend-блок транзакции"
                }
            (block as suspend () -> Any?).invoke()
        }
    }

    /** Пять стандартных категорий как в БД после prepopulate. */
    private fun standardEntities(): List<CategoryEntity> =
        listOf("PUSH", "PULL", "LEGS", "CORE", "CARDIO").mapIndexed { index, name ->
            CategoryEntity(
                id = index + 1L,
                name = name,
                colorArgb = 0xFF000000.toInt() + index,
                isCustom = false,
                isArchived = false,
                createdAt = 1L
            )
        }

    // ---------- addCategory (задачи 2.3/2.4) ----------

    @Test
    fun add_category_when_blank_name_then_failure_without_insert() =
        runTest {
            // When
            val result = repository.addCategory("   ", colorArgb = testColor)

            // Then
            assertTrue(result.isFailure, "Blank-имя отклоняется")
            coVerify(exactly = 0) { categoryDao.getAll() }
            coVerify(exactly = 0) { categoryDao.insert(any()) }
        }

    @Test
    fun add_category_when_duplicate_name_ignoring_case_and_trim_then_failure() =
        runTest {
            // Given: в БД есть «Спина»
            coEvery { categoryDao.getAll() } returns listOf(spineCategory)

            // When: «  СПИНА » — тот же ключ после trim().lowercase()
            val result = repository.addCategory("  СПИНА ", colorArgb = testColor)

            // Then
            assertTrue(result.isFailure, "Дубль по name.trim().lowercase() отклоняется")
            coVerify(exactly = 0) { categoryDao.insert(any()) }
        }

    @Test
    fun add_category_when_success_then_saves_custom_active_category_with_passed_color() =
        runTest {
            // Given
            val inserted = slot<CategoryEntity>()
            coEvery { categoryDao.getAll() } returns standardEntities()
            coEvery { categoryDao.insert(capture(inserted)) } returns 42L

            // When
            val result = repository.addCategory("Плечи", colorArgb = testColor)

            // Then
            assertTrue(result.isSuccess, "Уникальное имя создаётся")
            val created = checkNotNull(result.getOrNull())
            assertEquals(42L, created.id, "В модели — id из вставки")
            assertEquals("Плечи", created.name, "Имя после trim()")
            assertEquals(testColor, created.colorArgb, "Переданный цвет сохраняется как есть")
            assertTrue(created.isCustom, "Создана пользователем — isCustom = true")
            assertFalse(created.isArchived, "Новая категория не в архиве")
            assertEquals("Плечи", inserted.captured.name, "В БД пишется trim()-имя")
            assertEquals(testColor, inserted.captured.colorArgb, "В БД пишется переданный цвет")
            assertTrue(inserted.captured.isCustom, "В БД — isCustom = true")
            assertFalse(inserted.captured.isArchived, "В БД — isArchived = false")
        }

    // ---------- updateCategory (tasks 1.3/1.4) ----------

    @Test
    fun update_category_updates_name_color_and_linked_exercises_in_one_transaction() =
        runTest {
            // Given
            mockRoomTransaction()
            coEvery { categoryDao.getById(5L) } returns spineCategory
            coEvery { categoryDao.getAll() } returns listOf(spineCategory)

            // When
            val result = repository.updateCategory(5L, "Спина и поясница", testColor)

            // Then: обе таблицы обновлены внутри одной транзакции; UPDATE
            // упражнений без фильтра архива накрывает активные и архивные
            assertTrue(result.isSuccess, "Уникальное новое имя обновляется")
            coVerify(exactly = 1) { database.withTransaction(any<suspend () -> Unit>()) }
            coVerify(exactly = 1) { categoryDao.updateNameAndColor(5L, "Спина и поясница", testColor) }
            coVerify(exactly = 1) { exerciseDao.updateCategoryName("Спина", "Спина и поясница") }
        }

    @Test
    fun update_category_when_only_color_changed_then_exercises_keep_their_links() =
        runTest {
            // Given: имя то же, цвет новый
            mockRoomTransaction()
            coEvery { categoryDao.getById(5L) } returns spineCategory
            coEvery { categoryDao.getAll() } returns listOf(spineCategory)

            // When
            val result = repository.updateCategory(5L, "Спина", testColor)

            // Then: цвет сохранён; каскад безусловен, по тому же имени —
            // практический no-op, ссылки упражнений не меняются
            assertTrue(result.isSuccess, "Смена только цвета — успех")
            coVerify(exactly = 1) { categoryDao.updateNameAndColor(5L, "Спина", testColor) }
            coVerify(exactly = 1) { exerciseDao.updateCategoryName("Спина", "Спина") }
        }

    @Test
    fun update_category_when_standard_then_standard_category_deletion_exception() =
        runTest {
            // Given
            mockRoomTransaction()
            coEvery { categoryDao.getById(1L) } returns
                spineCategory.copy(id = 1L, name = "PUSH", isCustom = false)

            // When
            val result = repository.updateCategory(1L, "Новое имя", testColor)

            // Then
            val exception = checkNotNull(result.exceptionOrNull())
            assertTrue(
                exception is StandardCategoryDeletionException,
                "Стандартная категория не обновляется тем же типом ошибки, что и удаление"
            )
            coVerify(exactly = 0) { categoryDao.updateNameAndColor(any(), any(), any()) }
            coVerify(exactly = 0) { exerciseDao.updateCategoryName(any(), any()) }
        }

    @Test
    fun update_category_when_duplicate_new_name_then_failure_and_no_changes() =
        runTest {
            // Given: другая категория «Грудь»
            mockRoomTransaction()
            coEvery { categoryDao.getById(5L) } returns spineCategory
            coEvery { categoryDao.getAll() } returns
                listOf(
                    spineCategory,
                    spineCategory.copy(id = 6L, name = "Грудь", createdAt = 2_000L)
                )

            // When
            val result = repository.updateCategory(5L, "грудь", testColor)

            // Then: транзакция откатывается — ни категория, ни упражнения
            assertTrue(result.isFailure, "Дубль имени отклоняется")
            coVerify(exactly = 0) { categoryDao.updateNameAndColor(any(), any(), any()) }
            coVerify(exactly = 0) { exerciseDao.updateCategoryName(any(), any()) }
        }

    @Test
    fun update_category_when_unknown_id_then_failure() =
        runTest {
            // Given
            mockRoomTransaction()
            coEvery { categoryDao.getById(99L) } returns null

            // When
            val result = repository.updateCategory(99L, "Что-то", testColor)

            // Then
            assertTrue(result.isFailure, "Несуществующая категория — ошибка")
            coVerify(exactly = 0) { categoryDao.updateNameAndColor(any(), any(), any()) }
        }

    @Test
    fun update_category_when_blank_new_name_then_failure_and_no_changes() =
        runTest {
            // Given
            mockRoomTransaction()
            coEvery { categoryDao.getById(5L) } returns spineCategory

            // When
            val result = repository.updateCategory(5L, "   ", testColor)

            // Then: новое имя проходит те же проверки, что при создании
            assertTrue(result.isFailure, "Blank-имя отклоняется")
            coVerify(exactly = 0) { categoryDao.updateNameAndColor(any(), any(), any()) }
            coVerify(exactly = 0) { exerciseDao.updateCategoryName(any(), any()) }
        }

    @Test
    fun update_category_when_same_normalized_name_of_self_then_success() =
        runTest {
            // Given: дубль исключает саму обновляемую категорию
            mockRoomTransaction()
            coEvery { categoryDao.getById(5L) } returns spineCategory
            coEvery { categoryDao.getAll() } returns listOf(spineCategory)

            // When
            val result = repository.updateCategory(5L, "  спина ", testColor)

            // Then
            assertTrue(result.isSuccess, "Своё же имя (после trim/lowercase) — не дубль")
            coVerify(exactly = 1) { categoryDao.updateNameAndColor(5L, "спина", testColor) }
        }

    // ---------- deleteCategory (задачи 2.7/2.8) ----------

    @Test
    fun delete_category_when_unused_custom_then_success() =
        runTest {
            // Given
            mockRoomTransaction()
            coEvery { categoryDao.getById(5L) } returns spineCategory
            coEvery { exerciseDao.countByCategoryName("Спина") } returns 0

            // When
            val result = repository.deleteCategory(5L)

            // Then
            assertTrue(result.isSuccess, "Кастомная категория без упражнений удаляется")
            coVerify(exactly = 1) { categoryDao.deleteById(5L) }
        }

    @Test
    fun delete_category_when_used_by_exercises_then_in_use_exception_and_not_deleted() =
        runTest {
            // Given: хотя бы одно ссылающееся упражнение
            mockRoomTransaction()
            coEvery { categoryDao.getById(5L) } returns spineCategory
            coEvery { exerciseDao.countByCategoryName("Спина") } returns 2

            // When
            val result = repository.deleteCategory(5L)

            // Then
            val exception = checkNotNull(result.exceptionOrNull())
            assertTrue(
                exception is CategoryInUseDeletionException,
                "Категория с упражнениями неудаляема"
            )
            coVerify(exactly = 0) { categoryDao.deleteById(any()) }
        }

    @Test
    fun delete_category_when_standard_then_standard_category_deletion_exception() =
        runTest {
            // Given
            mockRoomTransaction()
            coEvery { categoryDao.getById(1L) } returns
                spineCategory.copy(id = 1L, name = "PUSH", isCustom = false)

            // When
            val result = repository.deleteCategory(1L)

            // Then
            val exception = checkNotNull(result.exceptionOrNull())
            assertTrue(
                exception is StandardCategoryDeletionException,
                "Стандартная категория неудаляема"
            )
            coVerify(exactly = 0) { categoryDao.deleteById(any()) }
            coVerify(exactly = 0) { exerciseDao.countByCategoryName(any()) }
        }

    @Test
    fun delete_category_when_unknown_id_then_failure() =
        runTest {
            // Given
            mockRoomTransaction()
            coEvery { categoryDao.getById(99L) } returns null

            // When
            val result = repository.deleteCategory(99L)

            // Then
            assertTrue(result.isFailure, "Несуществующая категория — ошибка")
            coVerify(exactly = 0) { categoryDao.deleteById(any()) }
        }

    // ---------- setCategoryArchived (задачи 2.7/2.8) ----------

    @Test
    fun set_category_archived_when_existing_then_delegates_to_dao() =
        runTest {
            // Given
            coEvery { categoryDao.updateArchived(3L, true) } returns 1

            // When
            val result = repository.setCategoryArchived(3L, true)

            // Then
            assertTrue(result.isSuccess, "Архивация стандартной и кастомной категории — успех")
            coVerify(exactly = 1) { categoryDao.updateArchived(3L, true) }
        }

    @Test
    fun set_category_archived_when_restore_then_delegates_to_dao() =
        runTest {
            // Given
            coEvery { categoryDao.updateArchived(3L, false) } returns 1

            // When
            val result = repository.setCategoryArchived(3L, false)

            // Then
            assertTrue(result.isSuccess, "Восстановление — успех")
            coVerify(exactly = 1) { categoryDao.updateArchived(3L, false) }
        }

    @Test
    fun set_category_archived_when_unknown_id_then_failure() =
        runTest {
            // Given: DAO вернул 0 изменённых строк
            coEvery { categoryDao.updateArchived(999L, true) } returns 0

            // When
            val result = repository.setCategoryArchived(999L, true)

            // Then
            assertTrue(result.isFailure, "Несуществующий id — failure (race сбора списка и подтверждения)")
        }

    // ---------- Реактивные запросы и снимок (задача 2.9) ----------

    @Test
    fun categories_maps_dao_entities_to_domain() =
        runTest {
            // Given
            every { categoryDao.observeCategories() } returns flowOf(listOf(spineCategory))

            // When
            val categories = repository.categories().first()

            // Then
            assertEquals(
                listOf(
                    Category(
                        id = 5L,
                        name = "Спина",
                        colorArgb = 0xFF102030.toInt(),
                        isCustom = true,
                        isArchived = false
                    )
                ),
                categories,
                "Активные категории маппятся в домен"
            )
        }

    @Test
    fun archived_categories_maps_dao_entities_to_domain() =
        runTest {
            // Given
            val archived = spineCategory.copy(isArchived = true)
            every { categoryDao.observeArchivedCategories() } returns flowOf(listOf(archived))

            // When
            val categories = repository.archivedCategories().first()

            // Then
            assertTrue(categories.all { it.isArchived }, "Архивный список — только архивные")
            assertEquals(5L, categories.first().id, "Маппинг в домен сохраняет поля")
        }

    @Test
    fun hasArchived_reflects_archived_categories_count_from_dao() =
        runTest {
            // Given — счётчики DAO: архивных категорий 2, упражнений 0
            every { categoryDao.observeArchivedCount() } returns flowOf(2)
            every { exerciseDao.observeArchivedCount() } returns flowOf(0)

            // When + Then
            assertTrue(
                repository.hasArchived().first(),
                "hasArchived — реактивный признак архивных категорий"
            )
        }

    @Test
    fun category_names_with_linked_exercises_maps_list_to_set() =
        runTest {
            // Given: дубль имени из двух упражнений схлопывается множеством
            every { exerciseDao.observeCategoryNamesInUse() } returns
                flowOf(listOf("PUSH", "PULL", "PUSH"))

            // When + Then
            assertEquals(
                setOf("PUSH", "PULL"),
                repository.categoryNamesWithLinkedExercises().first(),
                "Набор имён категорий с упражнениями — без дублей"
            )
        }

    @Test
    fun get_all_categories_returns_snapshot_of_all() =
        runTest {
            // Given: активная и архивная категории
            coEvery { categoryDao.getAll() } returns
                listOf(
                    spineCategory,
                    spineCategory.copy(id = 1L, name = "PUSH", isCustom = false, isArchived = true)
                )

            // When
            val result = repository.getAllCategories()

            // Then
            val categories = checkNotNull(result.getOrNull())
            assertEquals(2, categories.size, "Снимок включает активные и архивные")
            assertTrue(categories.any { it.isArchived }, "Архивные в снимке помечены isArchived")
        }
}
