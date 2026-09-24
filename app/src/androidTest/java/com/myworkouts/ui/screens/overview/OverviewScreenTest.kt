package com.myworkouts.ui.screens.overview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.state.OverviewUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные UI-тесты для [OverviewScreenBody] без ViewModel
 * (overview-screen, tasks 7.3; образцы — `EntrySettingsScreenTest`,
 * `MainScreenFabNavigationTest`; логика VM покрыта JVM-тестами
 * `OverviewViewModelTest`).
 *
 * Проверяют: переключение вкладок меняет контент, при единственной
 * видимой вкладке `SecondaryTabRow` скрыт и подзаголовок показывает её
 * название, навигация в настройки и обратно (NavHost-harness).
 */
@RunWith(AndroidJUnit4::class)
class OverviewScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Колбэки-заглушки: тесты переключения вкладок мутируют state сами. */
    private fun stubActions(onTabSelect: (OverviewTab) -> Unit = {}): OverviewScreenActions =
        OverviewScreenActions(
            onTabSelect = onTabSelect,
            onStatsPeriodChange = {},
            onStatsCategorySelect = {},
            onRecordsModeChange = {},
            onActivityPeriodChange = {}
        )

    @Test
    fun tabs_when_records_selected_then_records_content_replaces_stats_content() {
        // Given — экран со всеми вкладками, состояние мутится колбэком
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                var state by remember { mutableStateOf(OverviewUiState()) }
                OverviewScreenBody(
                    state = state,
                    actions = stubActions(onTabSelect = { state = state.copy(selectedTab = it) }),
                    onSettingsClick = {},
                    onBackClick = {}
                )
            }
        }

        // Then — изначально контент «Статистики»: пустой период → подсказка
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_stats_empty))
            .assertIsDisplayed()

        // When — тап по вкладке «Рекорды»
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_tab_records))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — контент сменился: пустой список рекордов, подсказки
        // «Статистики» больше нет
        composeTestRule
            .onNodeWithText(context.getString(R.string.records_empty))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_stats_empty))
            .assertCountEquals(0)
    }

    /**
     * Спец-сценарий одной видимой вкладки (spec «Скрытие таб-бара»):
     * `SecondaryTabRow` не рендерится — других вкладок нет; подзаголовок
     * в шапке показывает название единственной вкладки.
     */
    @Test
    fun tabs_when_single_visible_then_tab_row_hidden_and_subtitle_shows_tab_name() {
        // Given — видима только вкладка «Рекорды»
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OverviewScreenBody(
                        state =
                            OverviewUiState(
                                selectedTab = OverviewTab.RECORDS,
                                visibleTabs = listOf(OverviewTab.RECORDS)
                            ),
                        actions = stubActions(),
                        onSettingsClick = {},
                        onBackClick = {}
                    )
                }
            }
        }

        // Then — подзаголовок «Рекорды» (текст встречается ровно один раз —
        // вкладки в таб-баре нет)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_tab_records))
            .assertCountEquals(1)
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_tab_records))
            .assertIsDisplayed()

        // Then — скрытых вкладок на экране нет вовсе
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_tab_stats))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.overview_tab_activity))
            .assertCountEquals(0)
    }

    /**
     * Навигация в настройки и обратно (NavHost-harness по образцу
     * `MainScreenFabNavigationTest`): тап по шестерёнке открывает маршрут
     * настроек, возврат (системный back) показывает обзор снова.
     */
    @Test
    fun settings_when_gear_clicked_then_navigates_and_back_returns_to_overview() {
        // Given — обзор как стартовый destination + заглушка настроек
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                OverviewScreenWithNavHost(onControllerReady = { navController = it })
            }
        }

        // When — тап по шестерёнке
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.a11y_overview_settings))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — маршрут настроек открыт
        assertEquals(
            "Тап по шестерёнке открывает маршрут настроек обзора",
            Screen.OverviewSettings.route,
            navController.currentDestination?.route
        )

        // When — системный back
        composeTestRule.runOnIdle {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()

        // Then — обзор снова активен (контент вёрстки покрыт тестами
        // OverviewScreenBody выше)
        assertEquals(
            "После возврата активен маршрут обзора",
            Screen.Overview.route,
            navController.currentDestination?.route
        )
    }

    /**
     * Test-harness: [OverviewScreenBody] стартовым destination + заглушка
     * маршрута настроек (реальный экран тестируется в
     * [OverviewSettingsScreenTest]).
     */
    @Composable
    private fun OverviewScreenWithNavHost(onControllerReady: (NavHostController) -> Unit) {
        val navController = rememberNavController()
        onControllerReady(navController)
        NavHost(navController = navController, startDestination = Screen.Overview.route) {
            composable(Screen.Overview.route) {
                OverviewScreenBody(
                    state = OverviewUiState(),
                    actions = stubActions(),
                    onSettingsClick = { navController.navigate(Screen.OverviewSettings.route) },
                    onBackClick = {}
                )
            }
            composable(Screen.OverviewSettings.route) {
                // Заглушка — реальный экран тестируется отдельно.
                Text(text = "Заглушка настроек")
            }
        }
    }
}
