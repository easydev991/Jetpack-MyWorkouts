package com.myworkouts.ui.screens.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.BuildConfig
import com.myworkouts.R
import com.myworkouts.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные тесты для [MoreScreen].
 *
 * Проверяет наличие кнопки «Оформление приложения», корректность
 * отображения версии приложения и переход на маршрут `Screen.Appearance`
 * при клике.
 */
@RunWith(AndroidJUnit4::class)
class MoreScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Test-Harness: оборачивает [MoreScreen] в `NavHost` с двумя
     * destinations — [Screen.More] (стартовый) и [Screen.Appearance]
     * (заглушка). Без графа `navigate(...)` бросает
     * `IllegalArgumentException: Navigation graph has not been set`,
     * потому что production-код вызывает `popUpTo(Screen.More.route)`,
     * что требует зарегистрированного маршрута.
     */
    @Composable
    private fun MoreScreenWithNavHost(onControllerReady: (NavHostController) -> Unit = {}) {
        val navController = rememberNavController()
        onControllerReady(navController)
        NavHost(navController = navController, startDestination = Screen.More.route) {
            composable(Screen.More.route) {
                MoreScreen(navController = navController)
            }
            composable(Screen.Appearance.route) {
                // Заглушка — реальный экран тестируется отдельно.
            }
        }
    }

    /** Проверяет, что кнопка «Оформление приложения» отображается на экране. */
    @Test
    fun more_screen_when_displayed_then_shows_appearance_button() {
        // Given
        composeTestRule.setContent {
            MoreScreenWithNavHost()
        }

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.appearance_title))
            .assertIsDisplayed()
    }

    /** Проверяет, что кнопка «Отправить обратную связь» отображается на экране. */
    @Test
    fun more_screen_when_displayed_then_shows_feedback_send_button() {
        // Given
        composeTestRule.setContent {
            MoreScreenWithNavHost()
        }

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.feedback_send))
            .assertIsDisplayed()
    }

    /**
     * Проверяет, что клик по кнопке «Оформление приложения» переводит
     * навигацию на маршрут [Screen.Appearance].
     */
    @Test
    fun more_screen_when_appearance_button_clicked_then_navigates_to_appearance_route() {
        // Given - NavController создаётся в setContent и сохраняется в
        // lateinit-поле; после click читаем currentDestination.
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            MoreScreenWithNavHost(onControllerReady = { navController = it })
        }

        // When
        composeTestRule
            .onNodeWithText(context.getString(R.string.appearance_title))
            .performClick()

        // Then - ждём, пока NavController обработает навигацию.
        composeTestRule.waitForIdle()
        val actualRoute = navController.currentDestination?.route
        assert(actualRoute == Screen.Appearance.route) {
            "Ожидался переход на маршрут \"${Screen.Appearance.route}\", " +
                "но текущий маршрут — \"$actualRoute\""
        }
    }

    /** Проверяет, что версия приложения отображается внизу экрана. */
    @Test
    fun more_screen_when_displayed_then_shows_app_version() {
        // Given
        composeTestRule.setContent {
            MoreScreenWithNavHost()
        }

        // Then
        val expectedVersionText = BuildConfig.VERSION_NAME
        composeTestRule
            .onNodeWithText(
                context.getString(
                    R.string.app_version,
                    expectedVersionText
                )
            ).assertIsDisplayed()
    }

    /**
     * Регресс-тест: кнопка «Оформление приложения» должна быть
     * горизонтально отцентрирована относительно экрана, а не прижата
     * к левому краю.
     */
    @Test
    fun more_screen_when_displayed_then_appearance_button_is_horizontally_centered() {
        // Given
        composeTestRule.setContent {
            MoreScreenWithNavHost()
        }

        // When
        // getBoundsInRoot() возвращает DpRect, у которого left/right —
        // Dp. Конвертируем через .value во Float для assertEquals.
        val rootBounds = composeTestRule.onRoot().getBoundsInRoot()
        val buttonBounds =
            composeTestRule
                .onNodeWithText(context.getString(R.string.appearance_title))
                .getBoundsInRoot()
        val leftMargin = (buttonBounds.left - rootBounds.left).value
        val rightMargin = (rootBounds.right - buttonBounds.right).value

        // Then — отступы слева и справа равны (±1px на rounding).
        assertEquals(
            "Кнопка должна быть горизонтально отцентрирована: " +
                "leftMargin=$leftMargin, rightMargin=$rightMargin, " +
                "rootWidth=${(rootBounds.right - rootBounds.left).value}, " +
                "buttonWidth=${(buttonBounds.right - buttonBounds.left).value}",
            leftMargin,
            rightMargin,
            1f
        )
    }
}
