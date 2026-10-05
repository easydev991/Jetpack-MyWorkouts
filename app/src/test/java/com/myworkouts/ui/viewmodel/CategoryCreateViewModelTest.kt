package com.myworkouts.ui.viewmodel

import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Fake [WorkoutDataRepository] для JVM-тестов [CategoryCreateViewModel]:
 * реактивные списки категорий — настраиваемые `MutableStateFlow`,
 * `addCategory`/`renameCategory` записывают аргументы и возвращают
 * настраиваемый результат, остальные операции — заглушки.
 */
private class FakeCategoryRepository : WorkoutDataRepository {
    val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

    val archivedCategoriesFlow = MutableStateFlow<List<Category>>(emptyList())

    /** Имена, переданные в `addCategory`, в порядке вызовов. */
    val addedCategoryNames = mutableListOf<String>()

    /** Пары (id, newName), переданные в `renameCategory`, в порядке вызовов. */
    val renamedCategories = mutableListOf<Pair<Long, String>>()

    /** Настраиваемый результат `addCategory`. */
    var addCategoryResult: Result<Category> =
        Result.success(Category(id = 1L, name = "", colorArgb = 0, isCustom = true))

    /** Настраиваемый результат `renameCategory`. */
    var renameCategoryResult: Result<Unit> = Result.success(Unit)

    override fun categories(): Flow<List<Category>> = categoriesFlow

    override fun archivedCategories(): Flow<List<Category>> = archivedCategoriesFlow

    override suspend fun addCategory(name: String): Result<Category> {
        addedCategoryNames.add(name)
        return addCategoryResult
    }

    override suspend fun renameCategory(
        id: Long,
        newName: String
    ): Result<Unit> {
        renamedCategories.add(id to newName)
        return renameCategoryResult
    }

    override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

    override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

    override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> = Result.success(Unit)

    override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(emptyList())

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun deleteCategory(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun setCategoryArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> = Result.success(Unit)

    override fun hasArchived(): Flow<Boolean> = MutableStateFlow(false)

    override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = MutableStateFlow(emptySet())

    override suspend fun getAllCategories(): Result<List<Category>> =
        Result.success(categoriesFlow.value + archivedCategoriesFlow.value)

    override suspend fun importData(
        categories: List<Category>,
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)
}

/**
 * Unit-тесты для [CategoryCreateViewModel].
 *
 * Подписка на `uiState` — через `backgroundScope` (запускает
 * WhileSubscribed), значения читаются синхронно через `.value` после
 * `advanceUntilIdle` (образец — `ExerciseCreateViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryCreateViewModelTest {
    private lateinit var testDispatcher: TestDispatcher

    private val repository = FakeCategoryRepository()

    private val analyticsProvider = FakeAnalyticsProvider()

    private fun createViewModel(categoryId: Long = Screen.CategoryCreate.NO_CATEGORY_ID): CategoryCreateViewModel =
        CategoryCreateViewModel(repository, categoryId, analyticsProvider)

    private fun category(
        id: Long,
        name: String,
        isArchived: Boolean = false
    ): Category =
        Category(
            id = id,
            name = name,
            colorArgb = 0xFF3F51B5.toInt(),
            isCustom = true,
            isArchived = isArchived
        )

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // canSave в create-режиме

    /** Уникальное непустое имя — canSave true (spec «Валидная форма — canSave = true»). */
    @Test
    fun create_when_unique_name_then_can_save_true() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onNameChange("Спина")
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.canSave, "Уникальное имя — canSave true")
        }

    /**
     * Дубль по `name.trim().lowercase()` блокирует сохранение
     * (spec-сценарий «Дубликат блокирует сохранение с подсказкой»).
     */
    @Test
    fun create_when_duplicate_name_ignoring_case_and_spaces_then_can_save_false() =
        runTest(testDispatcher) {
            // Given — в БД есть «Спина»
            repository.categoriesFlow.value = listOf(category(id = 1L, name = "Спина"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — ввод с пробелом и в другом регистре
            viewModel.onNameChange("СПИНА ")
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isDuplicateName, "Дубль должен определяться")
            assertFalse(viewModel.uiState.value.canSave, "Дубль должен блокировать сохранение")
        }

    /** Пробелы — не валидное имя (spec-сценарий «Пробелы не считаются валидным именем»). */
    @Test
    fun create_when_blank_name_then_can_save_false() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When
            viewModel.onNameChange("   ")
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.canSave, "Blank-имя не проходит валидацию")
        }

    // hasUnsavedChanges в create-режиме

    /** Пустая create-форма не блокирует закрытие; ввод имени — блокирует. */
    @Test
    fun create_has_unsaved_changes_follows_name_input() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — нетронутая форма
            assertFalse(viewModel.uiState.value.hasUnsavedChanges, "Пустая форма — нет изменений")

            // When — blank-ввод
            viewModel.onNameChange("   ")
            advanceUntilIdle()

            // Then — blank по-прежнему «нет изменений»
            assertFalse(
                viewModel.uiState.value.hasUnsavedChanges,
                "Blank-имя не считается несохранённым изменением"
            )

            // When — валидный ввод
            viewModel.onNameChange("Спина")
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.hasUnsavedChanges, "Введённое имя блокирует закрытие")
        }

    // Сохранение в create-режиме

    /**
     * Успешное сохранение передаёт `trim()`-имя в репозиторий, зовёт
     * `onSaved()` ровно один раз и логирует user_action CREATE_CATEGORY
     * (spec-сценарий «Успешное создание закрывает экран», design D11).
     */
    @Test
    fun create_save_success_passes_trimmed_name_and_calls_on_saved_once() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("  Спина  ")
            advanceUntilIdle()
            var savedCount = 0

            // When
            viewModel.save { savedCount++ }
            advanceUntilIdle()

            // Then
            assertEquals(listOf("Спина"), repository.addedCategoryNames, "Имя передаётся с trim()")
            assertEquals(1, savedCount, "onSaved вызывается ровно один раз")
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.CREATE_CATEGORY)),
                analyticsProvider.events,
                "Создание категории — user_action CREATE_CATEGORY (design D11)"
            )
        }

    /** Тап при canSave = false — no-op (spec-сценарий «Тап при canSave = false»). */
    @Test
    fun create_save_when_can_save_false_then_repository_and_on_saved_not_called() =
        runTest(testDispatcher) {
            // Given — пустая форма
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            var savedCount = 0

            // When
            viewModel.save { savedCount++ }
            advanceUntilIdle()

            // Then
            assertTrue(repository.addedCategoryNames.isEmpty(), "Репозиторий не вызывается")
            assertEquals(0, savedCount, "onSaved не вызывается")
        }

    /**
     * Провал сохранения — isSaveError true, экран остаётся открытым;
     * user_action логируется до корутины, поэтому есть и при провале
     * (design D11).
     */
    @Test
    fun create_save_failure_then_is_save_error_and_stays_open() =
        runTest(testDispatcher) {
            // Given
            repository.addCategoryResult = Result.failure(IllegalStateException("дубль"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Спина")
            advanceUntilIdle()
            var savedCount = 0

            // When
            viewModel.save { savedCount++ }
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isSaveError, "Провал сохранения должен ставит флаг")
            assertEquals(0, savedCount, "При провале onSaved не вызывается")
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.CREATE_CATEGORY)),
                analyticsProvider.events,
                "User_action логируется до корутины — есть и при провале (design D11)"
            )
        }

    /** Ввод после неуспешного сохранения скрывает ошибку (spec-сценарий). */
    @Test
    fun create_on_name_change_after_failure_resets_save_error() =
        runTest(testDispatcher) {
            // Given — неуспешное сохранение
            repository.addCategoryResult = Result.failure(IllegalStateException("дубль"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Спина")
            advanceUntilIdle()
            viewModel.save { }
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isSaveError)

            // When
            viewModel.onNameChange("Спина2")
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isSaveError, "Ввод сбрасывает ошибку сохранения")
        }

    /** Категория, созданная в другой сессии, ловится реактивно (spec «Валидация уникальности»). */
    @Test
    fun create_when_category_appears_reactively_then_duplicate_detected() =
        runTest(testDispatcher) {
            // Given — список пуст, имя валидно
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Спина")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canSave)

            // When — в другой сессии создали «Спина»
            repository.categoriesFlow.value = listOf(category(id = 2L, name = "Спина"))
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isDuplicateName, "Реактивный дубль должен ловиться")
            assertFalse(viewModel.uiState.value.canSave)
        }

    // Edit-режим: загрузка и предзаполнение

    /** Edit-режим предзаполняет имя; форма без изменений — canSave false (spec-сценарий). */
    @Test
    fun edit_when_opened_then_name_prefilled_and_can_save_false() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            val viewModel = createViewModel(categoryId = 7L)

            // When
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertEquals("Плечи", viewModel.uiState.value.name, "Поле предзаполнено")
            assertTrue(viewModel.uiState.value.isEditMode)
            assertFalse(viewModel.uiState.value.hasChanges, "Без правок hasChanges false")
            assertFalse(viewModel.uiState.value.canSave, "Edit без изменений — canSave false")
            assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        }

    /** Категория из архивного потока тоже доступна для предзаполнения (активные + архивные). */
    @Test
    fun edit_when_category_in_archive_flow_then_prefilled_from_it() =
        runTest(testDispatcher) {
            // Given
            repository.archivedCategoriesFlow.value =
                listOf(category(id = 7L, name = "Плечи", isArchived = true))
            val viewModel = createViewModel(categoryId = 7L)

            // When
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertEquals("Плечи", viewModel.uiState.value.name, "Архивные участвуют в источнике")
            assertFalse(viewModel.uiState.value.isCategoryMissing)
        }

    /** Собственное имя в edit-режиме — не дубликат (spec-сценарий «Собственное имя не дубликат»). */
    @Test
    fun edit_when_own_name_then_not_duplicate_and_has_changes_false() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value =
                listOf(category(id = 7L, name = "Плечи"), category(id = 1L, name = "Спина"))
            val viewModel = createViewModel(categoryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — повторный ввод собственного имени
            viewModel.onNameChange("Плечи")
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isDuplicateName, "Само-сравнение исключено")
            assertFalse(viewModel.uiState.value.hasChanges, "trim()-сравнение без lowercase()")
        }

    /** Имя другой существующей категории в edit-режиме — дубликат. */
    @Test
    fun edit_when_name_of_other_category_then_duplicate_blocks_save() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value =
                listOf(category(id = 7L, name = "Плечи"), category(id = 1L, name = "Спина"))
            val viewModel = createViewModel(categoryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onNameChange("Спина")
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isDuplicateName)
            assertFalse(viewModel.uiState.value.canSave)
        }

    /** Изменение имени — hasChanges true и canSave true. */
    @Test
    fun edit_when_name_changed_then_has_changes_true_and_can_save_true() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            val viewModel = createViewModel(categoryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onNameChange("Плечи большие")
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.hasChanges)
            assertTrue(viewModel.uiState.value.canSave)
            assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        }

    /**
     * Успешное сохранение вызывает renameCategory с id оригинала,
     * popBackStack-колбэк и логирует user_action EDIT_CATEGORY
     * (design D11).
     */
    @Test
    fun edit_save_calls_rename_with_original_id_and_calls_on_saved() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            val viewModel = createViewModel(categoryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onNameChange("Дельты")
            advanceUntilIdle()
            var savedCount = 0

            // When
            viewModel.save { savedCount++ }
            advanceUntilIdle()

            // Then
            assertEquals(listOf(7L to "Дельты"), repository.renamedCategories, "Rename по id оригинала")
            assertEquals(1, savedCount, "onSaved вызывается ровно один раз")
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.EDIT_CATEGORY)),
                analyticsProvider.events,
                "Переименование категории — user_action EDIT_CATEGORY (design D11)"
            )
        }

    /** Провал rename — isSaveError, экран открыт (spec «Неуспешное сохранение»). */
    @Test
    fun edit_save_failure_shows_save_error_and_stays_open() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            repository.renameCategoryResult = Result.failure(IllegalStateException("ошибка БД"))
            val viewModel = createViewModel(categoryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onNameChange("Дельты")
            advanceUntilIdle()
            var savedCount = 0

            // When
            viewModel.save { savedCount++ }
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isSaveError)
            assertEquals(0, savedCount)
        }

    /** Категория не найдена — isCategoryMissing, сохранение недоступно (spec-сценарий). */
    @Test
    fun edit_when_category_missing_then_is_category_missing_true() =
        runTest(testDispatcher) {
            // Given — пустые потоки (категория удалена в другой сессии)
            val viewModel = createViewModel(categoryId = 42L)

            // When
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isCategoryMissing)
            assertFalse(viewModel.uiState.value.canSave, "Сохранение недостижимо disabled")
            assertTrue(repository.renamedCategories.isEmpty())
        }
}
