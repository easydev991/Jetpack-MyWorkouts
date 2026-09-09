package com.myworkouts.ui.screens.appdata

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные тесты для [AppDataScreenContent] (app-data-screen).
 *
 * SAF-пикеры (экспорт/восстановление) в androidTest не открываются —
 * по клику проверяется только семантика кнопок (видимость/
 * кликабельность); диалог удаления — собственный AlertDialog экрана.
 * Стилизация кнопки удаления (цвет ошибки) проверяется preview и
 * ручной проверкой (design Decision 8).
 */
@RunWith(AndroidJUnit4::class)
class AppDataScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setContent(
        hasData: Boolean,
        isBusy: Boolean = false,
        onDeleteAll: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            AppDataScreenContent(
                state =
                    AppDataScreenState(
                        hasData = hasData,
                        isBusy = isBusy,
                        onExportRequest = {},
                        onRestoreRequest = {},
                        onDeleteAll = onDeleteAll,
                        onBackClick = {}
                    )
            )
        }
    }

    /** Проверяет, что заголовок «Данные приложения» отображается. */
    @Test
    fun app_data_screen_when_displayed_then_shows_title() {
        // Given
        setContent(hasData = true)

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_data))
            .assertIsDisplayed()
    }

    /**
     * Спека app-data-management «Нет данных — кнопки скрыты»: без
     * записей и пользовательских упражнений экспорт и удаление скрыты,
     * восстановление остаётся видимым.
     */
    @Test
    fun app_data_screen_when_no_data_then_export_and_delete_hidden() {
        // Given
        setContent(hasData = false)

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.restore_from_backup))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.create_backup))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(context.getString(R.string.delete_all_data))
            .assertDoesNotExist()
    }

    /**
     * Спека app-data-management «Только пользовательские упражнения —
     * экспорт доступен»: при наличии данных кнопка экспорта видима.
     */
    @Test
    fun app_data_screen_when_has_data_then_export_visible() {
        // Given
        setContent(hasData = true)

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.create_backup))
            .assertIsDisplayed()
    }

    /** Диалог удаления открывается по клику и закрывается «Отменой». */
    @Test
    fun app_data_screen_when_delete_cancelled_then_dialog_dismissed() {
        // Given
        setContent(hasData = true)

        // When — открыли диалог
        composeTestRule
            .onNodeWithText(context.getString(R.string.delete_all_data))
            .performClick()

        // Then — текст диалога виден
        composeTestRule
            .onNodeWithText(context.getString(R.string.delete_all_dialog_text))
            .assertIsDisplayed()

        // When — отменили
        composeTestRule
            .onNodeWithText(context.getString(R.string.cancel))
            .performClick()

        // Then — диалог закрыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.delete_all_dialog_text))
            .assertDoesNotExist()
    }

    /** Подтверждение диалога вызывает удаление всех данных. */
    @Test
    fun app_data_screen_when_delete_confirmed_then_on_delete_all_called() {
        // Given
        var deleteAllCalled = false
        setContent(hasData = true, onDeleteAll = { deleteAllCalled = true })

        // When
        composeTestRule
            .onNodeWithText(context.getString(R.string.delete_all_data))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.delete))
            .performClick()

        // Then
        assertTrue("Подтверждение диалога должно вызвать удаление данных", deleteAllCalled)
    }
}
