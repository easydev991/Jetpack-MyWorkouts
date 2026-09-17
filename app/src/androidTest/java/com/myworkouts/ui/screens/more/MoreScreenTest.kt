package com.myworkouts.ui.screens.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.navigation.BottomTabs
import com.myworkouts.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные тесты для [MoreScreen].
 *
 * Проверяет наличие кнопки «Оформление приложения», корректность
 * отображения версии приложения, переход на маршрут `Screen.Appearance`
 * при клике и кнопку видимости вкладки «Упражнения» (exercises-tab).
 */
@RunWith(AndroidJUnit4::class)
class MoreScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Test-Harness: оборачивает [MoreScreen] в `NavHost` с двумя
     * destinations — [Screen.More] (стартовый) и [Screen.Appearance]/
     * [Screen.AppData] (заглушки). Без графа `navigate(...)` бросает
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
                MoreScreen(
                    navController = navController,
                    showExercisesTab = false,
                    onShowExercisesTabChange = {},
                    analyticsProvider = NoopAnalyticsProvider()
                )
            }
            composable(Screen.Appearance.route) {
                // Заглушка — реальный экран тестируется отдельно.
            }
            composable(Screen.AppData.route) {
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
     * Проверяет, что кнопка «Данные приложения» (ru) отображается на
     * экране (app-data-screen, спека more-screen).
     */
    @Test
    fun more_screen_when_displayed_then_shows_app_data_button() {
        // Given
        composeTestRule.setContent {
            MoreScreenWithNavHost()
        }

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_data))
            .assertIsDisplayed()
    }

    /**
     * Проверяет, что клик по кнопке «Данные приложения» переводит
     * навигацию на маршрут [Screen.AppData].
     */
    @Test
    fun more_screen_when_app_data_button_clicked_then_navigates_to_app_data_route() {
        // Given
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            MoreScreenWithNavHost(onControllerReady = { navController = it })
        }

        // When
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_data))
            .performClick()

        // Then
        composeTestRule.waitForIdle()
        val actualRoute = navController.currentDestination?.route
        assert(actualRoute == Screen.AppData.route) {
            "Ожидался переход на маршрут \"${Screen.AppData.route}\", " +
                "но текущий маршрут — \"$actualRoute\""
        }
    }

    /**
     * Спека app-data-management «Нижняя панель скрыта» (статическая
     * часть): маршрут `app_data` не входит в нижние табы — RootScreen
     * прячет панель по условию `currentRoute in BottomTabs`. Видимая
     * часть (панель реально исчезает) проверяется вручную.
     */
    @Test
    fun more_screen_app_data_route_is_not_bottom_tab() {
        // Then
        val tabRoutes = BottomTabs.map { it.route }
        assert(Screen.AppData.route !in tabRoutes) {
            "Маршрут \"${Screen.AppData.route}\" не должен быть нижним табом: $tabRoutes"
        }
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

    /**
     * Test-Harness для кнопки видимости вкладки (exercises-tab):
     * [MoreScreen] с параметром `showExercisesTab` и колбэком-получателем.
     * NavHost не нужен — кнопка не навигирует, а `navController` здесь лишь
     * обязательный параметр.
     */
    @Composable
    private fun MoreScreenWithToggle(
        showExercisesTab: Boolean,
        onShowExercisesTabChange: (Boolean) -> Unit = {}
    ) {
        MoreScreen(
            navController = rememberNavController(),
            showExercisesTab = showExercisesTab,
            onShowExercisesTabChange = onShowExercisesTabChange,
            analyticsProvider = NoopAnalyticsProvider()
        )
    }

    /** При скрытой вкладке кнопка показывает «Показать…». */
    @Test
    fun more_screen_when_setting_off_then_shows_show_label() {
        // Given — настройка выключена
        composeTestRule.setContent {
            MoreScreenWithToggle(showExercisesTab = false)
        }

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_exercises_tab_show))
            .assertIsDisplayed()
    }

    /** При показанной вкладке кнопка показывает «Скрыть…». */
    @Test
    fun more_screen_when_setting_on_then_shows_hide_label() {
        // Given — настройка включена
        composeTestRule.setContent {
            MoreScreenWithToggle(showExercisesTab = true)
        }

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_exercises_tab_hide))
            .assertIsDisplayed()
    }

    /** Клик по кнопке вызывает колбэк и меняет надпись. */
    @Test
    fun more_screen_when_toggle_clicked_then_calls_on_show_exercises_tab_change() {
        // Given — stateful-harness: колбэк обновляет состояние, как
        // реальный RootScreen через DataStore
        var showExercisesTab by mutableStateOf(false)
        var changedTo: Boolean? = null
        composeTestRule.setContent {
            MoreScreenWithToggle(
                showExercisesTab = showExercisesTab,
                onShowExercisesTabChange = {
                    showExercisesTab = it
                    changedTo = it
                }
            )
        }

        // When
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_exercises_tab_show))
            .performClick()

        // Then
        composeTestRule.waitForIdle()
        assertEquals(
            "Тап по кнопке при showExercisesTab = false вызывает " +
                "onShowExercisesTabChange(true)",
            true,
            changedTo
        )
        // После обновления состояния надпись сменилась на «Скрыть…»
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_exercises_tab_hide))
            .assertIsDisplayed()
    }
}
