package com.myworkouts.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit-тесты pure-функции [visibleTabs] (exercises-tab, task 6.1,
 * design Decision 7): покрытие обоих состояний нижнего бара (2/3
 * вкладки) без androidTest с реальным DataStore.
 */
class VisibleTabsTest {
    @Test
    fun visible_tabs_when_setting_off_then_exercises_hidden() {
        // Given/When — настройка выключена (default)
        val tabs = visibleTabs(showExercisesTab = false)

        // Then — «Тренировки» и «Ещё», порядок сохранён
        assertEquals(
            listOf(Screen.Main.route, Screen.More.route),
            tabs.map { it.route },
            "При выключенной настройке вкладка Exercises скрыта, порядок прежний"
        )
    }

    @Test
    fun visible_tabs_when_setting_on_then_exercises_between_main_and_more() {
        // Given/When — настройка включена
        val tabs = visibleTabs(showExercisesTab = true)

        // Then — Exercises между Main и More
        assertEquals(
            listOf(Screen.Main.route, Screen.Exercises.route, Screen.More.route),
            tabs.map { it.route },
            "При включенной настройке все три вкладки видны, Exercises между Main и More"
        )
    }
}
