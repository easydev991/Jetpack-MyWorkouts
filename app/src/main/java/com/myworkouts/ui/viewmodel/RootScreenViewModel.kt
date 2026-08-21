package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.myworkouts.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RootScreenViewModel : ViewModel() {
    private val _currentTab: MutableStateFlow<Screen> = MutableStateFlow(Screen.Main)
    val currentTab: StateFlow<Screen> = _currentTab.asStateFlow()

    fun switchTab(tab: Screen) {
        _currentTab.value = tab
    }
}
