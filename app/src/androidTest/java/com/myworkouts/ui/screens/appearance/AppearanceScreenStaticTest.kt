package com.myworkouts.ui.screens.appearance

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Статические UI-тесты для [AppearanceScreenContent] (без параметров).
 *
 * Вынесены из [AppearanceScreenTest], чтобы не прогонять их ×3 для каждой
 * [com.myworkouts.domain.model.AppTheme] — параметризация оправдана только
 * для тестов с зависимостью от темы.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceScreenStaticTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Обёртка над `setContent` для статических тестов.
     */
    private fun setContent(
        theme: com.myworkouts.domain.model.AppTheme = com.myworkouts.domain.model.AppTheme.SYSTEM,
        useDynamicColors: Boolean = true,
        isDynamicColorsAvailable: Boolean = true,
        onDynamicColorsChange: (Boolean) -> Unit = {}
    ) {
        composeTestRule.setContent {
            AppearanceScreenContent(
                state =
                    AppearanceScreenState(
                        theme = theme,
                        useDynamicColors = useDynamicColors,
                        onThemeChange = {},
                        onDynamicColorsChange = onDynamicColorsChange,
                        isDynamicColorsAvailable = isDynamicColorsAvailable,
                        onBackClick = {}
                    )
            )
        }
    }

    @Test
    fun appearance_screen_displays_app_bar_with_back_button() {
        // When
        setContent()

        // Then - заголовок TopAppBar отображается
        composeTestRule
            .onNodeWithText(context.getString(R.string.appearance_title))
            .assertIsDisplayed()

        // Then - кнопка «Назад» с contentDescription отображается
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
    }

    @Test
    fun appearance_screen_displays_app_theme_section() {
        // When
        setContent()

        // Then - заголовок секции темы отображается
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_theme))
            .assertIsDisplayed()

        // Then - все три радиокнопки отображаются
        composeTestRule
            .onNodeWithText(context.getString(R.string.light))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.dark))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.system))
            .assertIsDisplayed()
    }

    @Test
    fun appearance_screen_displays_dynamic_colors_section_when_available() {
        // Given
        setContent(isDynamicColorsAvailable = true)

        // Then - заголовок секции динамических цветов отображается
        composeTestRule
            .onNodeWithText(context.getString(R.string.dynamic_colors))
            .assertIsDisplayed()

        // Then - лейбл переключателя отображается
        composeTestRule
            .onNodeWithText(context.getString(R.string.use_dynamic_colors))
            .assertIsDisplayed()

        // Then - ровно один Switch в дереве (ищем по isToggleable — M3 Switch задаёт ToggleableState)
        composeTestRule
            .onAllNodes(isToggleable())
            .assertCountEquals(1)
    }

    @Test
    fun appearance_screen_hides_dynamic_colors_section_when_not_available() {
        // Given - динамические цвета недоступны (например, Android < 12)
        setContent(isDynamicColorsAvailable = false)

        // Then - заголовок секции динамических цветов НЕ рендерится
        composeTestRule
            .onNodeWithText(context.getString(R.string.dynamic_colors))
            .assertDoesNotExist()

        // Then - лейбл переключателя НЕ рендерится
        composeTestRule
            .onNodeWithText(context.getString(R.string.use_dynamic_colors))
            .assertDoesNotExist()

        // Then - Switch в дереве отсутствует
        composeTestRule
            .onAllNodes(isToggleable())
            .assertCountEquals(0)
    }

    @Test
    fun appearance_screen_switch_dynamic_colors_with_use_dynamic_colors_true_changes_to_false() {
        // Given - динамические цвета включены
        var captured: Boolean? = null
        setContent(useDynamicColors = true, onDynamicColorsChange = { captured = it })

        // When - кликаем по Switch
        composeTestRule
            .onNode(isToggleable())
            .performClick()

        // Then - колбэк вызван с инвертированным значением
        assertEquals(false, captured)
    }

    @Test
    fun appearance_screen_switch_dynamic_colors_with_use_dynamic_colors_false_changes_to_true() {
        // Given - динамические цвета выключены
        var captured: Boolean? = null
        setContent(useDynamicColors = false, onDynamicColorsChange = { captured = it })

        // When - кликаем по Switch
        composeTestRule
            .onNode(isToggleable())
            .performClick()

        // Then - колбэк вызван с инвертированным значением
        assertEquals(true, captured)
    }
}
