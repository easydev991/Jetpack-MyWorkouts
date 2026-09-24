package com.myworkouts.ui.viewmodel

import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.ui.state.OverviewSettingsUiState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit-тесты для [OverviewSettingsViewModel] (overview-screen, §4.1).
 *
 * DataStore — `mockk(relaxed = true)` + stub
 * `every { overviewTabVisibility } returns MutableStateFlow(...)`
 * (паттерн [EntrySettingsViewModelTest]); аналитика —
 * [FakeAnalyticsProvider].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewSettingsViewModelTest {
    private lateinit var mockDataStore: AppSettingsDataStore
    private val fakeAnalytics = FakeAnalyticsProvider()
    private lateinit var testDispatcher: TestDispatcher

    /** Stub Flow видимости вкладок, управляемый тестом. */
    private lateinit var visibilityFlow: MutableStateFlow<Map<OverviewTab, Boolean>>

    @BeforeEach
    fun setUp() {
        mockDataStore = mockk(relaxed = true)
        visibilityFlow = MutableStateFlow(OverviewTab.entries.associateWith { true })
        every { mockDataStore.overviewTabVisibility } returns visibilityFlow
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun ui_state_when_defaults_then_all_tabs_visible() =
        runTest(testDispatcher) {
            // Given/When
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertEquals(
                OverviewSettingsUiState(visibility = OverviewTab.entries.associateWith { true }),
                state,
                "Дефолт: все вкладки видимы"
            )
            assertEquals(false, state.hasAnyDisabled, "hasAnyDisabled = false при всех включённых")
        }

    @Test
    fun setTabVisible_records_false_then_logs_toggle_off_and_writes() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.setTabVisible(OverviewTab.RECORDS, false)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockDataStore.setOverviewTabVisible(OverviewTab.RECORDS, false) }
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(
                        UserActionType.TOGGLE_OVERVIEW_TAB_VISIBILITY,
                        "records_off"
                    )
                ),
                fakeAnalytics.events,
                "Toggle логирует один UserAction(TOGGLE_OVERVIEW_TAB_VISIBILITY, records_off)"
            )
        }

    @Test
    fun setTabVisible_records_true_then_logs_toggle_on() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.setTabVisible(OverviewTab.RECORDS, true)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(
                        UserActionType.TOGGLE_OVERVIEW_TAB_VISIBILITY,
                        "records_on"
                    )
                ),
                fakeAnalytics.events,
                "Toggle логирует UserAction(TOGGLE_OVERVIEW_TAB_VISIBILITY, records_on)"
            )
        }

    @Test
    fun setTabVisible_when_last_visible_tab_then_guard_ignores() =
        runTest(testDispatcher) {
            // Given — включена только «Статистика» (spec «Выключение
            // последней вкладки игнорируется»)
            visibilityFlow.value =
                OverviewTab.entries.associateWith { true } +
                mapOf(OverviewTab.RECORDS to false, OverviewTab.ACTIVITY to false)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.setTabVisible(OverviewTab.STATS, false)
            advanceUntilIdle()

            // Then — ни записи в DataStore, ни события
            coVerify(exactly = 0) { mockDataStore.setOverviewTabVisible(any(), any()) }
            assertEquals(
                emptyList<AnalyticsEvent>(),
                fakeAnalytics.events,
                "Guard: выключение последней вкладки не логирует событие"
            )
        }

    @Test
    fun enableAll_then_single_event_and_single_write() =
        runTest(testDispatcher) {
            // Given — две вкладки выключены
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.enableAll()
            advanceUntilIdle()

            // Then — ровно один общий ивент, без отдельных toggle-ивентов
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.ENABLE_ALL_OVERVIEW_TABS)),
                fakeAnalytics.events,
                "enableAll логирует один ENABLE_ALL_OVERVIEW_TABS без value и без toggle-ивентов"
            )
            coVerify(exactly = 1) { mockDataStore.setAllOverviewTabsVisible() }
        }

    @Test
    fun ui_state_when_flow_emits_sequentially_then_final_state_reflects_last() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — последовательные emissions Flow (две скрыты → одна)
            visibilityFlow.value =
                OverviewTab.entries.associateWith { true } +
                mapOf(OverviewTab.RECORDS to false, OverviewTab.ACTIVITY to false)
            advanceUntilIdle()
            visibilityFlow.value =
                OverviewTab.entries.associateWith { true } +
                mapOf(OverviewTab.ACTIVITY to false)
            advanceUntilIdle()

            // Then — финальный state отражает последнюю emission
            val state = viewModel.uiState.value
            assertEquals(false, state.visibility[OverviewTab.ACTIVITY], "ACTIVITY скрыта")
            assertEquals(true, state.visibility[OverviewTab.RECORDS], "RECORDS снова видна")
            assertEquals(true, state.hasAnyDisabled, "Осталась одна выключенная")
        }

    /** VM с подпиской на uiState через backgroundScope (запускает WhileSubscribed). */
    private fun createViewModel(): OverviewSettingsViewModel = OverviewSettingsViewModel(mockDataStore, fakeAnalytics)
}
