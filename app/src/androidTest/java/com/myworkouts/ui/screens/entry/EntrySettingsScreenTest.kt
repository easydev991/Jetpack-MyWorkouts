package com.myworkouts.ui.screens.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.EntryField
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные UI-тесты для [EntrySettingsScreenContent] через plain state
 * без ViewModel (entry-settings-screen, §8.1; образец —
 * `AppearanceScreenTest`; логика VM покрыта JVM-тестами
 * `EntrySettingsViewModelTest`).
 *
 * Проверяют состав экрана (5 тогглов + подзаголовок + TopAppBar),
 * условную кнопку «Включить все», инверсию Switch через колбэк и
 * однократный вызов `onEnableAll`.
 */
@RunWith(AndroidJUnit4::class)
class EntrySettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Управляемое состояние видимости для setContent (правило 14: один setContent). */
    private lateinit var visibility: MutableMap<EntryField, Boolean>

    private var hasAnyDisabled: Boolean = false

    /** Последний вызов [EntrySettingsScreenState.onToggleField]: поле → новое значение. */
    private var toggledField: Pair<EntryField, Boolean>? = null

    /** Счётчик вызовов [EntrySettingsScreenState.onEnableAll]. */
    private var enableAllCalls: Int = 0

    @Before
    fun setUp() {
        visibility = mutableMapOf(*EntryField.entries.map { it to true }.toTypedArray())
        hasAnyDisabled = false
        toggledField = null
        enableAllCalls = 0
    }

    /**
     * Рендерит контент экрана с текущим тестовым состоянием. Изменения
     * [visibility]/[hasAnyDisabled] внутри первого setContent вызывают
     * рекомпозицию (образец — `EntryEditArchivedExerciseTest`).
     */
    private fun setContent() {
        composeTestRule.setContent {
            EntrySettingsScreenContent(
                state =
                    EntrySettingsScreenState(
                        visibility = visibility.toMap(),
                        hasAnyDisabled = hasAnyDisabled,
                        onToggleField = { field, visible ->
                            toggledField = field to visible
                            visibility[field] = visible
                            hasAnyDisabled = visibility.any { !it.value }
                        },
                        onEnableAll = {
                            enableAllCalls++
                            EntryField.entries.forEach { visibility[it] = true }
                            hasAnyDisabled = false
                        },
                        onBackClick = {}
                    )
            )
        }
    }

    @Test
    fun content_when_opened_then_shows_five_toggles_subtitle_and_top_bar() {
        // Given
        setContent()

        // Then — заголовок, кнопка «назад» и подзаголовок отображены
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_settings_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_settings_subtitle))
            .assertIsDisplayed()

        // Then — пять названий полей
        listOf(
            R.string.entry_visibility_sets_label,
            R.string.entry_visibility_weight_label,
            R.string.entry_visibility_date_label,
            R.string.entry_visibility_time_label,
            R.string.entry_visibility_comment_label
        ).forEach { resId ->
            composeTestRule
                .onNodeWithText(context.getString(resId))
                .assertIsDisplayed()
        }

        // Then — ровно пять Switch
        composeTestRule
            .onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(EntryField.entries.size)
    }

    @Test
    fun content_when_hints_defined_then_sets_date_time_show_hints() {
        // Given
        setContent()

        // Then — подсказки у SETS/DATE/TIME отображены
        listOf(
            R.string.entry_visibility_sets_hint,
            R.string.entry_visibility_date_hint,
            R.string.entry_visibility_time_hint
        ).forEach { resId ->
            composeTestRule
                .onNodeWithText(context.getString(resId))
                .assertIsDisplayed()
        }
    }

    @Test
    fun content_when_all_enabled_then_enable_all_button_does_not_exist() {
        // Given — все поля включены
        setContent()

        // Then — кнопки «Включить все» нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_visibility_enable_all))
            .assertCountEquals(0)
    }

    @Test
    fun content_when_field_disabled_then_enable_all_button_displayed() {
        // Given — «Комментарий» выключен
        visibility[EntryField.COMMENT] = false
        hasAnyDisabled = true

        // When
        setContent()

        // Then — кнопка «Включить все» — последний элемент списка;
        // в компактном вьюпорте она за границей экрана — скроллим до неё
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(
                hasText(context.getString(R.string.entry_visibility_enable_all))
            )
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_visibility_enable_all))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_switch_tapped_then_calls_on_toggle_field_with_inverted_state() {
        // Given — «Доп. вес» включён
        setContent()
        assertEquals(
            "Пре-условие: WEIGHT включён",
            true,
            visibility[EntryField.WEIGHT]
        )

        // When — тап по Switch поля WEIGHT (второй по порядку enum)
        composeTestRule
            .onAllNodes(isToggleable(), useUnmergedTree = true)[EntryField.entries.indexOf(EntryField.WEIGHT)]
            .performClick()

        // Then — колбэк вызван с инвертированным значением
        assertEquals(
            "Тап по включённому Switch вызывает onToggleField(WEIGHT, false)",
            EntryField.WEIGHT to false,
            toggledField
        )
    }

    @Test
    fun content_when_enable_all_clicked_then_calls_on_enable_all_exactly_once() {
        // Given — «Комментарий» выключен, кнопка видна
        visibility[EntryField.COMMENT] = false
        hasAnyDisabled = true
        setContent()

        // When — тап «Включить все» (кнопка — последний элемент списка,
        // в компактном вьюпорте требует скролла)
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(
                hasText(context.getString(R.string.entry_visibility_enable_all))
            )
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_visibility_enable_all))
            .performClick()

        // Then — ровно один вызов колбэка
        assertEquals(
            "Тап «Включить все» вызывает onEnableAll ровно один раз",
            1,
            enableAllCalls
        )
    }
}
