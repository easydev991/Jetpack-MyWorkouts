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
import com.myworkouts.domain.model.Category
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.state.CategoryCreateUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel экрана категории: создание (FAB вкладки «Категории»,
 * `categoryId == -1`) и переименование существующей (`categoryId != -1`).
 *
 * Источник существующих имён — реактивное объединение активных и архивных
 * категорий репозитория: дубликат, созданный в другой сессии, ловится без
 * пересоздания экрана (spec «Валидация уникальности имени категории»).
 * В edit-режиме категория загружается по id из первой эмиссии того же
 * потока. Логика ввода и нормализация (`trim()` — единая точка [save]) —
 * здесь; UI — «глупый» рендер [CategoryCreateUiState].
 *
 * @property repository Репозиторий данных тренировок
 * @property analyticsProvider Провайдер аналитики: user_action на сохранении
 *   (CREATE_CATEGORY / EDIT_CATEGORY по режиму, design custom-categories D11)
 * @param categoryId Id редактируемой категории;
 * [Screen.CategoryCreate.NO_CATEGORY_ID] — режим создания
 */
class CategoryCreateViewModel(
    private val repository: WorkoutDataRepository,
    categoryId: Long = Screen.CategoryCreate.NO_CATEGORY_ID,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    /**
     * Загруженная категория edit-режима: `null` — режим создания или
     * категория не найдена (в UiState не попадает).
     */
    private var originalCategory: Category? = null

    /** Снимок всех известных категорий для проверки уникальности имени. */
    private var knownCategories: List<Category> = emptyList()

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
                    mutableUiState.update { it.copy(name = found.name) }
                }
                // Пересчёт hasChanges/isDuplicateName по актуальному списку
                updateState { it }
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
     * Сохраняет форму: в create-режиме — [WorkoutDataRepository.addCategory]
     * с `trim()`-именем (цвет генерирует репозиторий), в edit-режиме —
     * [WorkoutDataRepository.renameCategory] по id оригинала (транзакция
     * обновляет и упражнения). Успех → [onSaved] (навигация назад), провал →
     * [CategoryCreateUiState.isSaveError], экран остаётся открытым; причина
     * провала rename не различается — общий текст ошибки.
     */
    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        if (!state.canSave) return
        val original = originalCategory
        val name = state.name?.trim().orEmpty()
        // CREATE_CATEGORY/EDIT_CATEGORY — по наличию оригинала; на вызове
        // операции (design D10, канон ExerciseCreateViewModel.save)
        val action = if (original != null) UserActionType.EDIT_CATEGORY else UserActionType.CREATE_CATEGORY
        analyticsProvider.log(AnalyticsEvent.UserAction(action))
        viewModelScope.launch {
            val result =
                if (original != null) {
                    repository.renameCategory(original.id, name)
                } else {
                    repository.addCategory(name)
                }
            result
                .onSuccess { onSaved() }
                .onFailure { mutableUiState.update { it.copy(isSaveError = true) } }
        }
    }

    /**
     * Единая точка мутации состояния: после трансформации пересчитывает
     * `hasChanges` (сравнением `trim()`-имени с оригиналом, без
     * `lowercase()` — он только в проверке уникальности) и
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
                hasChanges = original != null && draftName != original.name.trim(),
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
