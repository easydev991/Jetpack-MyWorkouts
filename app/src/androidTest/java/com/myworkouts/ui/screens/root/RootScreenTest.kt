package com.myworkouts.ui.screens.root

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.MainActivity
import com.myworkouts.R
import com.myworkouts.navigation.BottomTabs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility-тесты нижней навигации (accessibility-semantics-fixes,
 * tasks 3.3/3.4).
 *
 * Полный стек через реальную `MainActivity` (образец —
 * `EntryListLiveUpdateIntegrationTest`): нижний бар рендерится только
 * внутри приватного `AppBottomBar` → `RootScreen`, изолированный
 * компонентный рендер невозможен без дублирования internals.
 *
 * Проверяет: иконка `NavigationBarItem` не дублирует название вкладки
 * через `contentDescription` — название читается один раз из лейбла
 * (design D6).
 */
@RunWith(AndroidJUnit4::class)
class RootScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule(MainActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bottom_bar_when_rendered_then_tab_icons_have_no_content_description() {
        // Given — приложение открыто на главной вкладке, нижний бар виден
        composeTestRule.waitForIdle()

        // Then — нижний бар отрисован: лейбл «Ещё» виден (этот текст
        // живёт только в баре — экран «Ещё» на старте не открыт)
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_title))
            .assertExists()

        // Then — ни одна вкладка не дублирует название через
        // contentDescription иконки (скрытая вкладка даёт 0 автоматически)
        BottomTabs.forEach { tab ->
            composeTestRule
                .onAllNodesWithContentDescription(context.getString(tab.titleResId))
                .assertCountEquals(0)
        }
    }
}
