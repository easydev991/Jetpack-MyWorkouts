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
 * `addCategory`/`updateCategory` записывают аргументы и возвращают
 * настраиваемый результат (успешный `addCategory` дополнительно эмитит
 * созданную категорию в поток — имитация Room), остальные операции —
 * заглушки.
 */
private class FakeCategoryRepository : WorkoutDataRepository {
    val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

    val archivedCategoriesFlow = MutableStateFlow<List<Category>>(emptyList())

    /** Пары (имя, цвет), переданные в `addCategory`, в порядке вызовов. */
    val addedCategories = mutableListOf<Pair<String, Int>>()

    /** Тройки (id, newName, colorArgb), переданные в `updateCategory`, в порядке вызовов. */
    val updatedCategories = mutableListOf<Triple<Long, String, Int>>()

    /** Настраиваемый результат `addCategory`. */
    var addCategoryResult: Result<Category> =
        Result.success(Category(id = 1L, name = "", colorArgb = 0, isCustom = true))

    /** Настраиваемый результат `updateCategory`. */
    var updateCategoryResult: Result<Unit> = Result.success(Unit)

    override fun categories(): Flow<List<Category>> = categoriesFlow

    override fun archivedCategories(): Flow<List<Category>> = archivedCategoriesFlow

    override suspend fun addCategory(
        name: String,
        colorArgb: Int
    ): Result<Category> {
        addedCategories.add(name to colorArgb)
        val result = addCategoryResult
        if (result.isSuccess) {
            // Имитация Room: успешная вставка появляется в потоке категорий
            // (реальный репозиторий переэмитит список через observeCategories)
            val created = result.getOrNull()
            categoriesFlow.value =
                categoriesFlow.value +
                Category(
                    id = created?.id ?: 1L,
                    name = name,
                    colorArgb = colorArgb,
                    isCustom = true
                )
        }
        return result
    }

    override suspend fun updateCategory(
        id: Long,
        newName: String,
        colorArgb: Int
    ): Result<Unit> {
        updatedCategories.add(Triple(id, newName, colorArgb))
        return updateCategoryResult
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

    /** Цвет, возвращаемый stub-генератором; тесты меняют его для сценария «другое значение». */
    private var stubColor = 0xFF112233.toInt()

    /** Входы stub-генератора — цвета известных категорий на момент вызова. */
    private val generatorInputs = mutableListOf<List<Int>>()

    private val stubGenerator: (List<Int>) -> Int = { colors ->
        generatorInputs.add(colors)
        stubColor
    }

    private fun createViewModel(
        categoryId: Long = Screen.CategoryCreate.NO_CATEGORY_ID,
        colorGenerator: (List<Int>) -> Int = stubGenerator
    ): CategoryCreateViewModel = CategoryCreateViewModel(repository, categoryId, analyticsProvider, colorGenerator)

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

    // hasChanges в create-режиме

    /** Пустая create-форма не блокирует закрытие; ввод имени — блокирует. */
    @Test
    fun create_has_unsaved_changes_follows_name_input() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — нетронутая форма
            assertFalse(viewModel.uiState.value.hasChanges, "Пустая форма — нет изменений")

            // When — blank-ввод
            viewModel.onNameChange("   ")
            advanceUntilIdle()

            // Then — blank по-прежнему «нет изменений»
            assertFalse(
                viewModel.uiState.value.hasChanges,
                "Blank-имя не считается несохранённым изменением"
            )

            // When — валидный ввод
            viewModel.onNameChange("Спина")
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.hasChanges, "Введённое имя блокирует закрытие")
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
            assertEquals(
                listOf("Спина"),
                repository.addedCategories.map { it.first },
                "Имя передаётся с trim()"
            )
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
            assertTrue(repository.addedCategories.isEmpty(), "Репозиторий не вызывается")
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

    /**
     * После успешного создания Room переэмитит список уже с новой
     * категорией — пересчёт не помечает её же имя дублем (иначе под полем
     * на кадр вспыхивает `category_error_duplicate` до `popBackStack`).
     */
    @Test
    fun create_save_success_then_emission_of_created_category_does_not_flag_duplicate() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Спина")
            advanceUntilIdle()

            // When — сохранение; fake эмитит созданную категорию (имитация Room)
            viewModel.save { }
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.isDuplicateName,
                "Свежесозданная категория не дубль сама себе"
            )
        }

    /** После провала сохранения реактивный пересчёт дублей восстанавливается (spec «Валидация уникальности»). */
    @Test
    fun create_save_failure_then_reactive_duplicate_detection_restored() =
        runTest(testDispatcher) {
            // Given — неуспешное сохранение уникального имени
            repository.addCategoryResult = Result.failure(IllegalStateException("дубль"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Уникальная")
            advanceUntilIdle()
            viewModel.save { }
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isSaveError)

            // When — в другой сессии создали категорию с тем же именем
            repository.categoriesFlow.value = listOf(category(id = 2L, name = "Уникальная"))
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.isDuplicateName,
                "После провала сохранения реактивный дубль ловится"
            )
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
        }

    /**
     * Успешное сохранение вызывает updateCategory с id оригинала, именем
     * и цветом оригинала, popBackStack-колбэк и логирует user_action
     * EDIT_CATEGORY (design D11).
     */
    @Test
    fun edit_save_calls_update_with_original_id_and_calls_on_saved() =
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
            assertEquals(
                listOf(Triple(7L, "Дельты", 0xFF3F51B5.toInt())),
                repository.updatedCategories,
                "Update по id оригинала с именем и цветом оригинала"
            )
            assertEquals(1, savedCount, "onSaved вызывается ровно один раз")
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.EDIT_CATEGORY)),
                analyticsProvider.events,
                "Сохранение edit — user_action EDIT_CATEGORY (design D11)"
            )
        }

    /** Провал сохранения — isSaveError, экран открыт (spec «Неуспешное сохранение»). */
    @Test
    fun edit_save_failure_shows_save_error_and_stays_open() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            repository.updateCategoryResult = Result.failure(IllegalStateException("ошибка БД"))
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
            assertTrue(repository.updatedCategories.isEmpty())
        }

    // Цвет: стартовая генерация, регенерация и сохранение

    /**
     * Create-режим: стартовый цвет генерируется при первой эмиссии списка
     * категорий и не перегенерируется на повторных эмиссиях
     * (spec «ViewModel, UiState и canSave», design Decision 3).
     */
    @Test
    fun create_on_first_categories_emission_then_start_color_generated() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When — первая эмиссия (пустой список)
            advanceUntilIdle()

            // Then
            assertEquals(
                0xFF112233.toInt(),
                viewModel.uiState.value.colorArgb,
                "Стартовый цвет генерируется при первой эмиссии"
            )
            assertEquals(
                emptyList<Int>(),
                generatorInputs.first(),
                "Генератор получает цвета известных категорий (на старте — пусто)"
            )

            // When — повторная эмиссия (другая сессия добавила категорию)
            repository.categoriesFlow.value = listOf(category(id = 1L, name = "Спина"))
            advanceUntilIdle()

            // Then — стартовый цвет не перегенерируется
            assertEquals(
                0xFF112233.toInt(),
                viewModel.uiState.value.colorArgb,
                "Повторная эмиссия не меняет стартовый цвет"
            )
            assertEquals(1, generatorInputs.size, "Генератор вызывается один раз")
        }

    /**
     * Edit-режим: кружок предзаполняется исходным `colorArgb` категории,
     * генератор на загрузке не вызывается (spec «Edit-режим: загрузка
     * и предзаполнение»).
     */
    @Test
    fun edit_when_opened_then_color_prefilled_from_original_and_generator_not_used() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            val viewModel = createViewModel(categoryId = 7L)

            // When
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            assertEquals(
                0xFF3F51B5.toInt(),
                viewModel.uiState.value.colorArgb,
                "Кружок — исходный цвет категории"
            )
            assertTrue(generatorInputs.isEmpty(), "На загрузке edit-режима генератор не вызывается")
        }

    /**
     * Регенерация: новый цвет от цветов всех известных категорий
     * (активные + архивные) и сброс isSaveError
     * (spec «ViewModel, UiState и canSave»).
     */
    @Test
    fun create_on_regenerate_color_then_new_color_from_known_categories_and_resets_save_error() =
        runTest(testDispatcher) {
            // Given — активная и архивная категории, провал сохранения
            repository.categoriesFlow.value = listOf(category(id = 1L, name = "Спина"))
            repository.archivedCategoriesFlow.value =
                listOf(category(id = 2L, name = "Ноги", isArchived = true))
            repository.addCategoryResult = Result.failure(IllegalStateException("дубль"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onNameChange("Руки")
            viewModel.save { }
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isSaveError)

            // When
            stubColor = 0xFFE91E63.toInt()
            viewModel.onRegenerateColorClick()
            advanceUntilIdle()

            // Then
            assertEquals(
                0xFFE91E63.toInt(),
                viewModel.uiState.value.colorArgb,
                "Регенерация обновляет цвет кружка"
            )
            assertEquals(
                listOf(0xFF3F51B5.toInt(), 0xFF3F51B5.toInt()),
                generatorInputs.last(),
                "Генератор получает цвета всех известных категорий (активные + архивные)"
            )
            assertFalse(viewModel.uiState.value.isSaveError, "Регенерация сбрасывает ошибку сохранения")
        }

    /**
     * Create: регенерация вернула стартовое значение — не изменение
     * (spec-сценарий «Регенерация тем же значением — не изменение»,
     * сравнение строго по значению `colorArgb`).
     */
    @Test
    fun create_when_regenerate_returns_same_color_then_no_changes() =
        runTest(testDispatcher) {
            // Given — стартовый цвет сгенерирован, имя пустое
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            val startColor = viewModel.uiState.value.colorArgb
            assertEquals(0xFF112233.toInt(), startColor)

            // When — регенерация вернула то же значение (stub не менялся)
            viewModel.onRegenerateColorClick()
            advanceUntilIdle()

            // Then
            assertEquals(startColor, viewModel.uiState.value.colorArgb)
            assertFalse(viewModel.uiState.value.hasChanges, "Совпадение по значению — не изменение")
        }

    /**
     * Edit: регенерация вернула исходный цвет категории — не изменение,
     * «Сохранить» остаётся disabled (spec-сценарий «Регенерация тем же
     * значением — не изменение», оба режима).
     */
    @Test
    fun edit_when_regenerate_returns_original_color_then_no_changes_and_can_save_false() =
        runTest(testDispatcher) {
            // Given
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            val viewModel = createViewModel(categoryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — регенерация вернула исходный цвет категории
            stubColor = 0xFF3F51B5.toInt()
            viewModel.onRegenerateColorClick()
            advanceUntilIdle()

            // Then
            assertEquals(0xFF3F51B5.toInt(), viewModel.uiState.value.colorArgb)
            assertFalse(viewModel.uiState.value.hasChanges, "Совпадение по значению — не изменение")
            assertFalse(viewModel.uiState.value.canSave, "Edit без реальных изменений — canSave false")
        }

    /**
     * Edit: изменённый только цвет включает «Сохранить»
     * (spec-сценарий «Изменённый только цвет включает Сохранить в edit»).
     */
    @Test
    fun edit_when_only_color_changed_then_has_changes_true_and_can_save_true() =
        runTest(testDispatcher) {
            // Given — имя не тронуто
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            val viewModel = createViewModel(categoryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — цвет регенерирован в другое значение
            stubColor = 0xFFE91E63.toInt()
            viewModel.onRegenerateColorClick()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.hasChanges, "Изменённый цвет — изменение")
            assertTrue(viewModel.uiState.value.canSave, "Edit: цвет включает «Сохранить»")
        }

    /**
     * Create: регенерированный в другое значение цвет при пустом имени —
     * несохранённое изменение (диалог подтверждения закрытия)
     * (spec-сценарий «Регенерированный цвет в create — несохранённое изменение»).
     */
    @Test
    fun create_when_regenerated_to_other_color_with_empty_name_then_has_unsaved_changes() =
        runTest(testDispatcher) {
            // Given — имя пустое, стартовый цвет сгенерирован
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.hasChanges, "Стартовый цвет — не изменение")

            // When — регенерация в другое значение (stub-генератор вернёт новый цвет)
            stubColor = 0xFFE91E63.toInt()
            viewModel.onRegenerateColorClick()
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.hasChanges,
                "Регенерированный цвет при пустом имени — несохранённое изменение"
            )
        }

    /**
     * Create: сохранение передаёт в `addCategory` цвет из состояния
     * (spec «Сохранение — create и update с popBackStack», scenario
     * «Успешное создание закрывает экран» — цвет из пикера).
     */
    @Test
    fun create_save_passes_picker_color_to_add_category() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onNameChange("Спина")
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(0xFF112233.toInt()),
                repository.addedCategories.map { it.second },
                "addCategory получает цвет из пикера"
            )
        }

    /**
     * Edit: сохранение передаёт в `updateCategory` актуальный цвет пикера,
     * а не исходный цвет категории (spec-сценарий «Успешное редактирование
     * передаёт оба поля»).
     */
    @Test
    fun edit_save_passes_current_picker_color_to_update_category() =
        runTest(testDispatcher) {
            // Given — имя не менялось, цвет регенерирован (canSave только от цвета)
            repository.categoriesFlow.value = listOf(category(id = 7L, name = "Плечи"))
            val viewModel = createViewModel(categoryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            stubColor = 0xFFE91E63.toInt()
            viewModel.onRegenerateColorClick()
            advanceUntilIdle()
            var savedCount = 0

            // When
            viewModel.save { savedCount++ }
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(Triple(7L, "Плечи", 0xFFE91E63.toInt())),
                repository.updatedCategories,
                "updateCategory получает актуальный цвет пикера"
            )
            assertEquals(1, savedCount, "onSaved вызывается ровно один раз")
        }
}
