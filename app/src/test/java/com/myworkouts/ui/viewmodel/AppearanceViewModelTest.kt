package com.myworkouts.ui.viewmodel

import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.domain.model.AppTheme
import com.myworkouts.ui.state.AppearanceUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit-тесты для [AppearanceViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private lateinit var mockDataStore: AppSettingsDataStore
    private val fakeAnalytics = FakeAnalyticsProvider()
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        mockDataStore = mockk(relaxed = true)
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @ParameterizedTest(name = "[{0}]")
    @EnumSource(AppTheme::class)
    fun update_theme_should_update_all_themes(theme: AppTheme) =
        runTest(testDispatcher) {
            // Given
            coEvery { mockDataStore.setTheme(any()) } coAnswers { }
            val viewModel = AppearanceViewModel(mockDataStore, fakeAnalytics)

            // When
            viewModel.updateTheme(theme)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockDataStore.setTheme(theme) }
        }

    @Test
    fun update_dynamic_colors_should_save_true_to_data_store() =
        runTest(testDispatcher) {
            // Given
            coEvery { mockDataStore.setUseDynamicColors(any()) } coAnswers { }
            val viewModel = AppearanceViewModel(mockDataStore, fakeAnalytics)

            // When
            viewModel.updateDynamicColors(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockDataStore.setUseDynamicColors(true) }
        }

    @Test
    fun update_dynamic_colors_should_save_false_to_data_store() =
        runTest(testDispatcher) {
            // Given
            coEvery { mockDataStore.setUseDynamicColors(any()) } coAnswers { }
            val viewModel = AppearanceViewModel(mockDataStore, fakeAnalytics)

            // When
            viewModel.updateDynamicColors(false)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockDataStore.setUseDynamicColors(false) }
        }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("themeAndDynamicColorsPairs")
    fun ui_state_should_emit_combined_theme_and_dynamic_colors(
        theme: AppTheme,
        useDynamicColors: Boolean
    ) = runTest(testDispatcher) {
        // Given
        every { mockDataStore.theme } returns flowOf(theme)
        every { mockDataStore.useDynamicColors } returns flowOf(useDynamicColors)
        val viewModel = AppearanceViewModel(mockDataStore, fakeAnalytics)

        // When — подписка через backgroundScope запускает WhileSubscribed,
        // advanceUntilIdle дожидается эмиссии upstream; после этого читаем .value
        // (synchronous). Это эквивалент .first() для StateFlow с WhileSubscribed:
        // .first() вернул бы initialValue, потому что StateFlow сначала отдаёт
        // текущее значение, а upstream успевает эмитнуть только потом.
        backgroundScope.launch { viewModel.uiState.collect { /* trigger subscription */ } }
        advanceUntilIdle()
        val actual = viewModel.uiState.value

        // Then
        assertEquals(
            AppearanceUiState(theme = theme, useDynamicColors = useDynamicColors),
            actual
        )
    }

    /**
     * `updateTheme` эмитит `UserAction(SELECT_THEME)` со value —
     * wire-имя темы в lowercase: light/dark/system (design D10).
     */
    @ParameterizedTest(name = "[{0}]")
    @EnumSource(AppTheme::class)
    fun update_theme_logs_user_action_select_theme_with_value(theme: AppTheme) =
        runTest(testDispatcher) {
            // Given
            coEvery { mockDataStore.setTheme(any()) } coAnswers { }
            val viewModel = AppearanceViewModel(mockDataStore, fakeAnalytics)

            // When
            viewModel.updateTheme(theme)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(
                        UserActionType.SELECT_THEME,
                        theme.name.lowercase()
                    )
                ),
                fakeAnalytics.events,
                "updateTheme логирует UserAction(SELECT_THEME, light/dark/system)"
            )
        }

    /**
     * `updateDynamicColors` эмитит `UserAction(TOGGLE_DYNAMIC_COLORS)`
     * со value on/off (design D10).
     */
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(booleans = [true, false])
    fun update_dynamic_colors_logs_user_action_toggle_with_value(enabled: Boolean) =
        runTest(testDispatcher) {
            // Given
            coEvery { mockDataStore.setUseDynamicColors(any()) } coAnswers { }
            val viewModel = AppearanceViewModel(mockDataStore, fakeAnalytics)

            // When
            viewModel.updateDynamicColors(enabled)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(
                        UserActionType.TOGGLE_DYNAMIC_COLORS,
                        if (enabled) "on" else "off"
                    )
                ),
                fakeAnalytics.events,
                "updateDynamicColors логирует UserAction(TOGGLE_DYNAMIC_COLORS, on/off)"
            )
        }

    companion object {
        @JvmStatic
        fun themeAndDynamicColorsPairs(): List<Arguments> =
            listOf(
                Arguments.of(AppTheme.LIGHT, true),
                Arguments.of(AppTheme.DARK, false),
                Arguments.of(AppTheme.SYSTEM, true)
            )
    }
}
