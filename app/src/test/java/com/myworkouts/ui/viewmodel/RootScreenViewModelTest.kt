package com.myworkouts.ui.viewmodel

import com.myworkouts.navigation.Screen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootScreenViewModelTest {
    @Test
    fun when_view_model_created_then_default_tab_is_main() {
        // Given
        val viewModel = RootScreenViewModel()

        // When
        val current = viewModel.currentTab.value

        // Then
        assertEquals(Screen.Main, current, "Стартовая вкладка должна быть Main")
    }

    @Test
    fun when_switch_to_more_then_current_tab_is_more() {
        // Given
        val viewModel = RootScreenViewModel()

        // When
        viewModel.switchTab(Screen.More)

        // Then
        assertEquals(Screen.More, viewModel.currentTab.value, "После switchTab(More) должна быть вкладка More")
    }

    @Test
    fun when_switch_back_to_main_from_more_then_current_tab_is_main() {
        // Given
        val viewModel = RootScreenViewModel()
        viewModel.switchTab(Screen.More)

        // When
        viewModel.switchTab(Screen.Main)

        // Then
        assertEquals(Screen.Main, viewModel.currentTab.value, "После switchTab(Main) должна быть вкладка Main")
    }
}
