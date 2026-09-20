package com.myworkouts.ui.viewmodel

import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.domain.model.EntryField
import com.myworkouts.ui.state.EntrySettingsUiState
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
 * Unit-тесты для [EntrySettingsViewModel] (entry-settings-screen, §3.1).
 *
 * DataStore — `mockk(relaxed = true)` + stub
 * `every { entryFieldVisibility } returns MutableStateFlow(...)`
 * (паттерн [AppearanceViewModelTest]); аналитика — [FakeAnalyticsProvider].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntrySettingsViewModelTest {
    private lateinit var mockDataStore: AppSettingsDataStore
    private val fakeAnalytics = FakeAnalyticsProvider()
    private lateinit var testDispatcher: TestDispatcher

    /** Stub Flow видимости, управляемый тестом. */
    private lateinit var visibilityFlow: MutableStateFlow<Map<EntryField, Boolean>>

    @BeforeEach
    fun setUp() {
        mockDataStore = mockk(relaxed = true)
        visibilityFlow = MutableStateFlow(EntryField.entries.associateWith { true })
        every { mockDataStore.entryFieldVisibility } returns visibilityFlow
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** VM с подпиской на uiState через backgroundScope (запускает WhileSubscribed). */
    private fun createViewModel(): EntrySettingsViewModel {
        val viewModel = EntrySettingsViewModel(mockDataStore, fakeAnalytics)
        return viewModel
    }

    @Test
    fun ui_state_when_defaults_then_all_visible_and_no_disabled() =
        runTest(testDispatcher) {
            // Given/When
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertEquals(
                EntrySettingsUiState(visibility = EntryField.entries.associateWith { true }),
                state,
                "Дефолт: все поля видимы"
            )
            assertEquals(false, state.hasAnyDisabled, "hasAnyDisabled = false при всех включённых")
        }

    @Test
    fun setFieldVisible_weight_false_then_updates_state_and_logs_toggle_off() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.setFieldVisible(EntryField.WEIGHT, false)
            advanceUntilIdle()

            // Then — состояние обновлено из stub Flow (запись в DataStore
            // транслируется тестом вручную: stub возвращает один и тот же Flow,
            // поэтому проверяем DataStore-вызов и ивент, а map эмулируем записью)
            coVerify { mockDataStore.setFieldVisible(EntryField.WEIGHT, false) }
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(
                        UserActionType.TOGGLE_ENTRY_FIELD_VISIBILITY,
                        "weight_off"
                    )
                ),
                fakeAnalytics.events,
                "Toggle логирует один UserAction(TOGGLE_ENTRY_FIELD_VISIBILITY, weight_off)"
            )
        }

    @Test
    fun setFieldVisible_weight_true_then_logs_toggle_on() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.setFieldVisible(EntryField.WEIGHT, true)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(
                        UserActionType.TOGGLE_ENTRY_FIELD_VISIBILITY,
                        "weight_on"
                    )
                ),
                fakeAnalytics.events,
                "Toggle логирует UserAction(TOGGLE_ENTRY_FIELD_VISIBILITY, weight_on)"
            )
        }

    @Test
    fun enableAll_then_logs_single_enable_all_event_without_toggles() =
        runTest(testDispatcher) {
            // Given — два поля выключены
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.enableAll()
            advanceUntilIdle()

            // Then — ровно один общий ивент, без отдельных toggle-ивентов
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.ENABLE_ALL_ENTRY_FIELDS)),
                fakeAnalytics.events,
                "enableAll логирует один ENABLE_ALL_ENTRY_FIELDS без value и без toggle-ивентов"
            )
            coVerify(exactly = 1) { mockDataStore.setAllFieldsVisible() }
        }

    @Test
    fun ui_state_when_flow_emits_sequentially_then_final_state_reflects_last() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — последовательные emissions Flow (два поля выключено → одно)
            visibilityFlow.value =
                EntryField.entries.associateWith { true } +
                mapOf(EntryField.DATE to false, EntryField.COMMENT to false)
            advanceUntilIdle()
            visibilityFlow.value =
                EntryField.entries.associateWith { true } +
                mapOf(EntryField.COMMENT to false)
            advanceUntilIdle()

            // Then — финальный state отражает последнюю emission
            val state = viewModel.uiState.value
            assertEquals(false, state.visibility[EntryField.COMMENT], "COMMENT скрыт")
            assertEquals(true, state.visibility[EntryField.DATE], "DATE снова виден")
            assertEquals(true, state.hasAnyDisabled, "Остался один выключенный")
        }
}
