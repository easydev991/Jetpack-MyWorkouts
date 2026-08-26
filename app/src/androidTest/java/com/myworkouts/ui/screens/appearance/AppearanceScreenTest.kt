package com.myworkouts.ui.screens.appearance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Параметризованные UI-тесты для [AppearanceScreenContent].
 *
 * Прогоняются для каждой [AppTheme] (LIGHT, DARK, SYSTEM), чтобы убедиться,
 * что выбранная тема отображается как selected, а клик по другой радиокнопке
 * вызывает соответствующий колбэк.
 *
 * Статические тесты (без параметров) вынесены в [AppearanceScreenStaticTest],
 * чтобы не прогонять их ×3 для каждой [AppTheme].
 */
@RunWith(Parameterized::class)
class AppearanceScreenTest(
    private val theme: AppTheme
) {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Локализованный label радиокнопки для текущей [theme]. */
    private fun expectedLabel(): String =
        context.getString(
            when (theme) {
                AppTheme.LIGHT -> R.string.light
                AppTheme.DARK -> R.string.dark
                AppTheme.SYSTEM -> R.string.system
            }
        )

    /**
     * Обёртка над `setContent` для параметризованных тестов.
     *
     * @param initialTheme тема, передаваемая в `AppearanceScreenState.theme`.
     * По умолчанию — параметризованный [theme] (для selection-теста).
     * Для click-теста передаётся отличная от [theme] тема, чтобы целевая
     * радиокнопка была enabled (selected radio отключён:
     * `enabled = theme != AppTheme.X`).
     */
    private fun setContent(
        initialTheme: AppTheme = theme,
        onThemeChange: (AppTheme) -> Unit = {},
        onBackClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            AppearanceScreenContent(
                state =
                    AppearanceScreenState(
                        theme = initialTheme,
                        useDynamicColors = true,
                        onThemeChange = onThemeChange,
                        onDynamicColorsChange = {},
                        onBackClick = onBackClick
                    )
            )
        }
    }

    /**
     * Matcher, матчащий узел, у которого среди потомков есть Text с заданным
     * [label]. Используется в unmerged-дереве для нахождения Selectable Row
     * (Text-узел сам не несёт `Selected` — его несёт родительский Row).
     */
    private fun hasTextChild(label: String): SemanticsMatcher = hasAnyChild(hasText(label))

    /** Matcher для Selectable Row с заданным [label], который сейчас выбран. */
    private fun selectedRowWithLabel(label: String): SemanticsMatcher = hasTextChild(label).and(isSelectedSemantics())

    /** Matcher: узел имеет `Selected = true` semantic. */
    private fun isSelectedSemantics(): SemanticsMatcher =
        SemanticsMatcher("isSelected=true") {
            it.config.contains(SemanticsProperties.Selected) && it.config[SemanticsProperties.Selected]
        }

    @Test
    fun appearance_screen_theme_radio_button_selected() {
        // When
        setContent()

        // Then - все три радиокнопки отображены
        composeTestRule
            .onNodeWithText(context.getString(R.string.light))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.dark))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.system))
            .assertIsDisplayed()

        // Then - ровно одна из трёх Selectable Row выбрана.
        // Text-узел сам по себе не несёт Selected (его ставит
        // Modifier.selectable на родительском Row); ищем Row в unmerged-дереве
        // через наличие Text-потомка с соответствующим label.
        composeTestRule
            .onAllNodes(
                selectedRowWithLabel(context.getString(R.string.light))
                    .or(selectedRowWithLabel(context.getString(R.string.dark)))
                    .or(selectedRowWithLabel(context.getString(R.string.system))),
                useUnmergedTree = true
            ).assertCountEquals(1)
    }

    @Test
    fun appearance_screen_clicks_theme_radio_button_calls_on_theme_change() {
        // Given - стартуем с темы, отличной от целевой: selected radio
        // отключён (enabled = theme != AppTheme.X),
        // и клик по нему ничего не делает.
        val initialTheme =
            when (theme) {
                AppTheme.LIGHT -> AppTheme.DARK
                AppTheme.DARK -> AppTheme.LIGHT
                AppTheme.SYSTEM -> AppTheme.LIGHT
            }
        var captured: AppTheme? = null
        setContent(initialTheme = initialTheme, onThemeChange = { captured = it })

        // When
        composeTestRule
            .onNodeWithText(expectedLabel())
            .performClick()

        // Then
        assert(captured == theme) { "Ожидалась тема $theme, но был вызван колбэк с $captured" }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "[{0}]")
        fun themes(): List<AppTheme> = AppTheme.entries.toList()
    }
}
