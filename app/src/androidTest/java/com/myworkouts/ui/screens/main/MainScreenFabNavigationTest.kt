package com.myworkouts.ui.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.navigation.Screen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Навигационный тест FAB главного экрана: тап по FAB открывает маршрут
 * `entry_create` (NavHost-обёртка по образцу `MoreScreenTest`).
 */
@RunWith(AndroidJUnit4::class)
class MainScreenFabNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Test-Harness: [MainScreen] стартовым destination + заглушка маршрута
     * создания записи — реальный экран тестируется отдельно
     * (`EntryCreateScreenTest`).
     */
    @Composable
    private fun MainScreenWithNavHost(onControllerReady: (NavHostController) -> Unit) {
        val navController = rememberNavController()
        onControllerReady(navController)
        NavHost(navController = navController, startDestination = Screen.Main.route) {
            composable(Screen.Main.route) {
                MainScreen(
                    onAddEntryClick = { navController.navigate(Screen.EntryCreate.route) }
                )
            }
            composable(Screen.EntryCreate.route) {
                // Заглушка — реальный экран тестируется отдельно.
            }
        }
    }

    @Test
    fun main_screen_when_fab_clicked_then_navigates_to_entry_create_route() {
        // Given
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            MainScreenWithNavHost(onControllerReady = { navController = it })
        }

        // When — тап по FAB (contentDescription «Добавить тренировку»)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.add_workout))
            .performClick()

        // Then — ждём, пока NavController обработает навигацию
        composeTestRule.waitForIdle()
        val actualRoute = navController.currentDestination?.route
        assertTrue(
            "Ожидался переход на маршрут \"${Screen.EntryCreate.route}\", " +
                "но текущий маршрут — \"$actualRoute\"",
            actualRoute == Screen.EntryCreate.route
        )
    }
}
