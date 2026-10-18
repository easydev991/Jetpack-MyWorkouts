package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.ui.state.ArchivedExercisesUiState
import com.myworkouts.ui.state.ArchivedTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel экрана «Архив» (exercises-archive + custom-categories,
 * tasks 7.2).
 *
 * Списки приходят реактивно из [WorkoutDataRepository.archivedExercises]
 * и [WorkoutDataRepository.archivedCategories] (НЕ разовые снимки).
 * Благодаря реактивности восстановление упражнения/категории
 * автоматически убирает его из списка без ручного refresh.
 *
 * `selectedTab` хранится в ViewModel (переживает поворот экрана,
 * не персистится); при опустошении выбранной вкладки ViewModel
 * выравнивает её на оставшуюся непустую (spec «Табы „Упражнения“ /
 * „Категории“ и видимость таб-бара»). Счётчики архива отдельно не
 * подписываются — длины списков и есть счётчики (design Decision 8).
 *
 * @property repository Репозиторий данных тренировок
 * @property analyticsProvider Провайдер аналитики
 */
class ArchivedExercisesViewModel(
    private val repository: WorkoutDataRepository,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    /** Выбранная вкладка архива (spec: хранится в ViewModel). */
    private val selectedTab = MutableStateFlow(ArchivedTab.EXERCISES)

    /**
     * UI State экрана: `Loading` до первой эмиссии репозитория, затем
     * `Success` со списками обоих типов и выбранной вкладкой.
     * `WhileSubscribed(defaultSubscriptionTimeoutMs())` — прецедент
     * [MainViewModel] / [ExercisesViewModel]: подписка живёт 5 секунд
     * после отписки, повторные открытия экрана не дёргают SQL.
     */
    val uiState: StateFlow<ArchivedExercisesUiState> =
        combine(
            repository.archivedExercises(),
            repository.archivedCategories(),
            selectedTab
        ) { exercises, categories, tab ->
            // Автовыбор оставшейся непустой вкладки при опустошении
            // выбранной (spec «Табы…»; обе пусты — заглушка, таб не
            // трогаем). Запись обратно в selectedTab — чтобы
            // выравнивание пережило рекомпозицию; повторная эмиссия
            // идемпотентна.
            val aligned =
                when (tab) {
                    ArchivedTab.EXERCISES ->
                        if (exercises.isEmpty() && categories.isNotEmpty()) ArchivedTab.CATEGORIES else tab
                    ArchivedTab.CATEGORIES ->
                        if (categories.isEmpty() && exercises.isNotEmpty()) ArchivedTab.EXERCISES else tab
                }
            selectedTab.value = aligned
            ArchivedExercisesUiState.Success(exercises, categories, aligned)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
            initialValue = ArchivedExercisesUiState.Loading
        )

    /**
     * Выбирает вкладку архива (тап по таб-бару). Аналитика — имя вкладки
     * в `value` (канон [OverviewViewModel.selectTab], custom-categories
     * design D11).
     *
     * @param tab Вкладка «Упражнения» или «Категории»
     */
    fun selectTab(tab: ArchivedTab) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.SELECT_CATEGORIES_TAB, tab.name.lowercase())
        )
        selectedTab.value = tab
    }

    /**
     * Восстанавливает упражнение из архива: вызывает
     * [WorkoutDataRepository.setArchived] с `isArchived = false`.
     * Список обновляется сам реактивно (Flow архива эмитит обновление);
     * ручного refresh не нужно. `unarchive` молча игнорирует
     * `Result.failure` от репозитория — спека «Failure при подтверждении»
     * на этом экране не описана, восстановление инициируется одиночным
     * тапом без алерта, ошибка маловероятна (offline Room).
     *
     * @param id Идентификатор архивного упражнения
     */
    fun unarchive(id: Long) {
        // Действие на вызове операции, до/независимо от исхода (design D10)
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.UNARCHIVE))
        viewModelScope.launch { repository.setArchived(id, false) }
    }

    /**
     * Восстанавливает категорию из архива (custom-categories, spec
     * «Тап „Вернуть“ восстанавливает категорию»):
     * [WorkoutDataRepository.setCategoryArchived] с `isArchived = false`;
     * список обновляется реактивно. Игнорирование `Result.failure` —
     * по образцу [unarchive].
     *
     * @param id Идентификатор архивной категории
     */
    fun unarchiveCategory(id: Long) {
        // Зеркально unarchive упражнения; отдельный тип — под категорию
        // (custom-categories design D11)
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.UNARCHIVE_CATEGORY))
        viewModelScope.launch { repository.setCategoryArchived(id, false) }
    }

    companion object {
        /**
         * Factory для создания [ArchivedExercisesViewModel]. Ручной DI
         * вместо Hilt, по образцу [ExercisesViewModel.factory] /
         * [MainViewModel.factory].
         *
         * @param repository Репозиторий данных тренировок
         * @param analyticsProvider Провайдер аналитики
         * @return Factory для создания ViewModel
         */
        fun factory(
            repository: WorkoutDataRepository,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ArchivedExercisesViewModel(repository, analyticsProvider) }
            }
    }
}
