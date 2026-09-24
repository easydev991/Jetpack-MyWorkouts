package com.myworkouts.ui.screens.overview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.ui.state.OverviewSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные UI-тесты для [OverviewSettingsScreenBody] без ViewModel
 * (overview-screen, tasks 7.4; образец — `EntrySettingsScreenTest`;
 * логика VM, включая guard последней вкладки, покрыта JVM-тестами
 * `OverviewSettingsViewModelTest`).
 *
 * Проверяют: состав экрана (3 тоггла + подзаголовок + TopAppBar),
 * условную кнопку «Включить все», вызов `onTabToggle` с инвертированным
 * значением и UI-инвариант guard'а — когда колбэк (как VM) игнорирует
 * выключение последней включённой вкладки, Switch остаётся включённым.
 */
@RunWith(AndroidJUnit4::class)
class OverviewSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Управляемое состояние видимости для setContent (правило 14). */
    private lateinit var visibility: MutableMap<OverviewTab, Boolean>

    /** Последний вызов [OverviewSettingsScreenBody] `onTabToggle`. */
    private var toggledTab: Pair<OverviewTab, Boolean>? = null

    @Before
    fun setUp() {
        visibility =
            mutableMapOf(*OverviewTab.entries.map { it to true }.toTypedArray())
        toggledTab = null
    }

    /**
     * Рендерит контент экрана с текущим тестовым состоянием. Изменения
     * [visibility] внутри первого setContent вызывают рекомпозицию
     * (образец — `EntryEditArchivedExerciseTest`). [applyGuard] = `true`
     * имитирует guard VM: колбэк не применяет выключение последней
     * включённой вкладки.
     */
    private fun setContent(applyGuard: Boolean = false) {
        composeTestRule.setContent {
            // remember обязателен: без него рекомпозиция пересоздаёт
            // State с исходным значением (см. OverviewScreenTest)
            var state by remember { mutableStateOf(OverviewSettingsUiState(visibility = visibility.toMap())) }
            OverviewSettingsScreenBody(
                state = state,
                onTabToggle = { tab, visible ->
                    toggledTab = tab to visible
                    if (!(applyGuard && !visible && state.visibility.values.count { it } == 1)) {
                        visibility[tab] = visible
                        state = state.copy(visibility = visibility.toMap())
                    }
                },
                onEnableAll = {
                    OverviewTab.entries.forEach { visibility[it] = true }
                    state = state.copy(visibility = visibility.toMap())
                },
                onBackClick = {}
            )
        }
    }

    @Test
    fun content_when_opened_then_shows_three_toggles_subtitle_and_top_bar() {
        // Given
        setContent()

        // Then — заголовок (общий с EntrySettings), «назад» и подзаголовок
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_settings_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_settings_subtitle))
            .assertIsDisplayed()

        // Then — три названия вкладок
        listOf(
            R.string.overview_tab_stats,
            R.string.overview_tab_records,
            R.string.overview_tab_activity
        ).forEach { resId ->
            composeTestRule
                .onNodeWithText(context.getString(resId))
                .assertIsDisplayed()
        }

        // Then — ровно три Switch
        composeTestRule
            .onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(OverviewTab.entries.size)
    }

    @Test
    fun content_when_all_enabled_then_enable_all_button_does_not_exist() {
        // Given — все вкладки включены
        setContent()

        // Then — кнопки «Включить все» нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_visibility_enable_all))
            .assertCountEquals(0)
    }

    @Test
    fun content_when_tab_disabled_then_enable_all_button_displayed() {
        // Given — «Рекорды» выключена
        visibility[OverviewTab.RECORDS] = false
        setContent()

        // Then — кнопка «Включить все» отображена (onNodeWithText
        // сам падает, если совпадение не ровно одно)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_visibility_enable_all))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_switch_tapped_then_calls_on_tab_toggle_with_inverted_state() {
        // Given — все вкладки включены
        setContent()

        // When — тап по Switch вкладки «Рекорды» (вторая по порядку enum)
        composeTestRule
            .onAllNodes(isToggleable(), useUnmergedTree = true)[OverviewTab.entries.indexOf(OverviewTab.RECORDS)]
            .performClick()
        composeTestRule.waitForIdle()

        // Then — колбэк вызван с инвертированным значением
        assertEquals(
            "Тап по включённому Switch вызывает onTabToggle(RECORDS, false)",
            OverviewTab.RECORDS to false,
            toggledTab
        )

        // Then — состояние применилось: Switch выключен
        composeTestRule
            .onAllNodes(isToggleable(), useUnmergedTree = true)[OverviewTab.entries.indexOf(OverviewTab.RECORDS)]
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    /**
     * Тап по Switch последней включённой вкладки передаёт в VM
     * `onTabToggle(STATS, false)`; VM-имитация (guard) состояние не
     * меняет. Само правило guard'а покрыто JVM-тестом
     * `OverviewSettingsViewModelTest.setTabVisible_when_last_visible_tab_then_guard_ignores`.
     */
    @Test
    fun content_when_guard_ignores_last_tab_then_switch_stays_on() {
        // Given — включена только «Статистика», колбэк-имитация guard'а VM
        visibility[OverviewTab.RECORDS] = false
        visibility[OverviewTab.ACTIVITY] = false
        setContent(applyGuard = true)

        // When — тап по Switch единственной включённой вкладки
        composeTestRule
            .onAllNodes(isToggleable(), useUnmergedTree = true)[OverviewTab.entries.indexOf(OverviewTab.STATS)]
            .performClick()
        composeTestRule.waitForIdle()

        // Then — колбэк получил запрос на выключение
        assertEquals(
            "Guard-тап передаёт onTabToggle(STATS, false) в VM",
            OverviewTab.STATS to false,
            toggledTab
        )
    }
}
