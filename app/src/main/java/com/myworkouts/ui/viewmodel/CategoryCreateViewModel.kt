package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.data.repository.generateCategoryColor
import com.myworkouts.domain.model.Category
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.state.CategoryCreateUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel экрана категории: создание (FAB вкладки «Категории»,
 * `categoryId == -1`) и обновление существующей (`categoryId != -1`).
 *
 * Источник существующих имён — реактивное объединение активных и архивных
 * категорий репозитория: дубликат, созданный в другой сессии, ловится без
 * пересоздания экрана (spec «Валидация уникальности имени категории»).
 * В edit-режиме категория загружается по id из первой эмиссии того же
 * потока. Логика ввода и нормализация (`trim()` — единая точка [save]) —
 * здесь; UI — «глупый» рендер [CategoryCreateUiState].
 *
 * Цвет (custom-categories-color-change): в create стартовый цвет
 * генерируется при первой эмиссии списка категорий, в edit кружок
 * предзаполняется исходным `colorArgb`; [onRegenerateColorClick]
 * регенерирует по правилам спеки `category-data` от цветов всех известных
 * категорий. Стартовая линия цвета хранится приватно — в UiState только
 * текущее значение.
 *
 * @property repository Репозиторий данных тренировок
 * @property analyticsProvider Провайдер аналитики: user_action на сохранении
 *   (CREATE_CATEGORY / EDIT_CATEGORY по режиму, design custom-categories D11)
 * @param categoryId Id редактируемой категории;
 * [Screen.CategoryCreate.NO_CATEGORY_ID] — режим создания
 * @param categoryColorGenerator Генератор цвета от цветов известных
 *   категорий (design custom-categories-color-change Decision 1); default —
 *   [generateCategoryColor] со случайным `Random`, тесты инжектят
 *   одноаргументную заглушку
 */
class CategoryCreateViewModel(
    private val repository: WorkoutDataRepository,
    categoryId: Long = Screen.CategoryCreate.NO_CATEGORY_ID,
    private val analyticsProvider: AnalyticsProvider,
    private val categoryColorGenerator: (List<Int>) -> Int =
        { generateCategoryColor(it, Random.Default) }
) : ViewModel() {
    /**
     * Загруженная категория edit-режима: `null` — режим создания или
     * категория не найдена (в UiState не попадает).
     */
    private var originalCategory: Category? = null

    /**
     * Стартовая линия цвета для `hasChanges`: первый сгенерированный цвет
     * в create / исходный `colorArgb` категории в edit
     * (design custom-categories-color-change Decision 3).
     */
    private var originalColorArgb: Int? = null

    /** Снимок всех известных категорий для проверки уникальности имени. */
    private var knownCategories: List<Category> = emptyList()

    /**
     * Идёт сохранение: эмиссии списка категорий не пересчитывают
     * derived-флагы — иначе переэмиссия Room с только что созданной
     * категорией помечает её же имя дублем (вспышка ошибки под полем
     * до закрытия экрана).
     */
    private var isSaving = false

    private val mutableUiState =
        MutableStateFlow(
            CategoryCreateUiState(isEditMode = categoryId != Screen.CategoryCreate.NO_CATEGORY_ID)
        )

    init {
        viewModelScope.launch {
            combine(
                repository.categories(),
                repository.archivedCategories()
            ) { active, archived -> active + archived }.collect { categories ->
                knownCategories = categories
                if (categoryId != Screen.CategoryCreate.NO_CATEGORY_ID && originalCategory == null) {
                    val found = categories.find { it.id == categoryId }
                    if (found == null) {
                        // Категории нет — подсказка не нужна, кнопка недостижимо disabled
                        mutableUiState.update { it.copy(isCategoryMissing = true) }
                        return@collect
                    }
                    originalCategory = found
                    originalColorArgb = found.colorArgb
                    mutableUiState.update { it.copy(name = found.name, colorArgb = found.colorArgb) }
                } else if (categoryId == Screen.CategoryCreate.NO_CATEGORY_ID &&
                    mutableUiState.value.colorArgb == null
                ) {
                    // Create: стартовый цвет — при первой эмиссии (Room Flow
                    // эмитит мгновенно — до интеракции пользователя); вход —
                    // цвета всех известных категорий
                    val generated = generateColor()
                    originalColorArgb = generated
                    mutableUiState.update { it.copy(colorArgb = generated) }
                }
                // Пересчёт hasChanges/isDuplicateName по актуальному списку;
                // во время сохранения пропускается — эмиссия с только что
                // созданной категорией иначе помечает её имя дублем
                if (!isSaving) updateState { it }
            }
        }
    }

    /**
     * UI State экрана: состояние ввода формы. Каждый колбэк ввода сбрасывает
     * `isSaveError`, [save] читает [uiState] — экран и сохранение видят одно
     * состояние. Upstream — собственный `MutableStateFlow` (за ним нет
     * холодного источника), поэтому `asStateFlow()` без sharing-слоя:
     * `.value` всегда свежий, в отличие от `stateIn(WhileSubscribed)`.
     */
    val uiState: StateFlow<CategoryCreateUiState> = mutableUiState.asStateFlow()

    /**
     * Ввод названия; сбрасывает ошибку сохранения. Перепроверка дубликата
     * выполняется по актуальному списку имён в [updateState].
     */
    fun onNameChange(text: String) =
        updateState {
            it.copy(name = text, isSaveError = false)
        }

    /**
     * Регенерация цвета кружка (custom-categories-color-change): новый
     * цвет от цветов всех известных категорий — активных и архивных
     * (`knownCategories`), правила — спека `category-data`. Сбрасывает
     * ошибку сохранения (паттерн `onXChange`).
     */
    fun onRegenerateColorClick() =
        updateState {
            it.copy(
                colorArgb = generateColor(),
                isSaveError = false
            )
        }

    /** Генерирует цвет от цветов всех известных категорий (правила — спека `category-data`). */
    private fun generateColor(): Int = categoryColorGenerator(knownCategories.map { it.colorArgb })

    /**
     * Сохраняет форму: в create-режиме — [WorkoutDataRepository.addCategory]
     * с `trim()`-именем и цветом из пикера, в edit-режиме —
     * [WorkoutDataRepository.updateCategory] по id оригинала с актуальным
     * цветом (транзакция обновляет и упражнения). Если цвет ещё не
     * сгенерирован (имя введено раньше первой эмиссии — практически
     * исключено), генерируется на месте теми же правилами
     * (design custom-categories-color-change Decision 3). Успех → [onSaved]
     * (навигация назад), провал → [CategoryCreateUiState.isSaveError],
     * экран остаётся открытым; причина провала не различается — общий
     * текст ошибки. На время сохранения реактивный пересчёт производных
     * флагов приостанавливается: переэмиссия списка с только что
     * созданной категорией не помечает её имя дублем.
     */
    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        if (!state.canSave) return
        isSaving = true
        val original = originalCategory
        val name = state.name?.trim().orEmpty()
        val colorArgb = state.colorArgb ?: generateColor()
        // CREATE_CATEGORY/EDIT_CATEGORY — по наличию оригинала; на вызове
        // операции (design D10, канон ExerciseCreateViewModel.save)
        val action = if (original != null) UserActionType.EDIT_CATEGORY else UserActionType.CREATE_CATEGORY
        analyticsProvider.log(AnalyticsEvent.UserAction(action))
        viewModelScope.launch {
            val result =
                if (original != null) {
                    repository.updateCategory(original.id, name, colorArgb)
                } else {
                    repository.addCategory(name, colorArgb)
                }
            result
                .onSuccess { onSaved() }
                .onFailure {
                    // Провал — экран остаётся открытым, реактивный пересчёт
                    // дублей восстанавливается (spec «Валидация уникальности»)
                    isSaving = false
                    mutableUiState.update { it.copy(isSaveError = true) }
                }
        }
    }

    /**
     * Единая точка мутации состояния: после трансформации пересчитывает
     * `hasChanges` (сравнением `trim()`-имени со стартовой линией, без
     * `lowercase()` — он только в проверке уникальности, ИЛИ цвета
     * по значению — custom-categories-color-change, design Decision 3) и
     * `isDuplicateName` по актуальному списку известных категорий,
     * исключая саму редактируемую.
     */
    private fun updateState(transform: (CategoryCreateUiState) -> CategoryCreateUiState) {
        mutableUiState.update { state ->
            val next = transform(state)
            val original = originalCategory
            val draftName = next.name?.trim().orEmpty()
            val normalizedName = next.name?.trim()?.lowercase()
            next.copy(
                hasChanges =
                    draftName != original?.name?.trim().orEmpty() ||
                        next.colorArgb != originalColorArgb,
                isDuplicateName =
                    normalizedName != null &&
                        knownCategories.any { existing ->
                            existing.id != original?.id &&
                                existing.name.trim().lowercase() == normalizedName
                        }
            )
        }
    }

    companion object {
        /**
         * Фабрика для manual DI: создаётся в `composable`-блоке
         * `RootScreen.AppNavHost`, сам экран фабрику не строит.
         *
         * @param repository Репозиторий данных тренировок
         * @param categoryId Id редактируемой категории;
         * [Screen.CategoryCreate.NO_CATEGORY_ID] — режим создания
         * @param analyticsProvider Провайдер аналитики
         * @return Factory для создания ViewModel
         */
        fun factory(
            repository: WorkoutDataRepository,
            categoryId: Long = Screen.CategoryCreate.NO_CATEGORY_ID,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CategoryCreateViewModel(repository, categoryId, analyticsProvider)
                }
            }
    }
}
