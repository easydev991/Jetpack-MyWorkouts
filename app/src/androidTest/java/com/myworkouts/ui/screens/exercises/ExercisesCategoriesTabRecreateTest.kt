package com.myworkouts.ui.screens.exercises

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.MainActivity
import com.myworkouts.R
import com.myworkouts.data.preferences.AppSettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционный сценарий «выбранная вкладка переживает поворот»
 * (custom-categories, tasks 5.5; spec `exercises-screen` «Выбранная
 * вкладка переживает поворот»): вкладка живёт в
 * [com.myworkouts.ui.viewmodel.ExercisesViewModel]
 * — после пересоздания activity активна «Категории».
 *
 * Использует реальную [MainActivity] (а не `ComponentActivity`),
 * потому что `scenario.recreate()` восстанавливает Compose-иерархию
 * только через `MainActivity.onCreate` → `setContent`; с голым
 * `ComponentActivity` дерево после recreate пустеет — «No compose
 * hierarchies found» (gotcha задокументирован в
 * `MainScreenDetailCardRecreateTest`). Вкладка «Упражнения» включается
 * через продакшен-DataStore (RootScreen реагирует реактивно; сброс
 * в `finally`).
 */
@RunWith(AndroidJUnit4::class)
class ExercisesCategoriesTabRecreateTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun selectedTab_whenActivityRecreated_thenPersistsInViewModel() {
        val dataStore = AppSettingsDataStore.create(context)
        runBlocking { dataStore.setShowExercisesTab(true) }
        try {
            // Given — нижняя навигация показывает вкладку «Упражнения»
            // (реакция RootScreen на DataStore-флаг), переходим на неё
            composeTestRule.waitUntil(5_000) {
                composeTestRule
                    .onAllNodesWithText(context.getString(R.string.exercises_title))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule
                .onNode(
                    hasText(context.getString(R.string.exercises_title)) and hasClickAction()
                ).performClick()
            composeTestRule.waitForIdle()

            // When — выбор вкладки «Категории» и пересоздание activity
            composeTestRule
                .onNode(
                    hasText(context.getString(R.string.exercises_tab_categories)) and hasClickAction()
                ).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.activityRule.scenario.recreate()
            composeTestRule.waitForIdle()

            // Then — после пересоздания активна вкладка «Категории»
            composeTestRule
                .onNodeWithText(context.getString(R.string.exercises_tab_categories))
                .assertIsSelected()
            composeTestRule
                .onNodeWithText(context.getString(R.string.exercise_category_push))
                .assertIsDisplayed()
        } finally {
            runBlocking { dataStore.setShowExercisesTab(false) }
        }
    }
}
