package com.myworkouts.ui.screens.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.state.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-тесты для [MainScreenContent] без ViewModel (workout-entries-list,
 * tasks 5.1/5.3). Логика VM покрыта `MainViewModelTest` в `app/src/test/`.
 *
 * Проверяет:
 * - рендер строк списка с названием и значением (5.1);
 * - пустое состояние при `entries = emptyList()` (5.1);
 * - long-press → меню «Удалить» → алерт подтверждения → отмена/подтверждение (5.3).
 *
 * Позиция DropdownMenu относительно точки касания покрывается ручной
 * проверкой на эмуляторе (задача 5.5): `offset` меню не имеет семантики
 * точной привязки в Compose UI Test.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val repsItem =
        EntryListItem(
            id = 1L,
            exerciseName = "Подтягивания",
            sets = 4,
            reps = 10,
            durationSec = null,
            date = 1_721_000_000_000L
        )

    private val timeItem =
        EntryListItem(
            id = 2L,
            exerciseName = "Планка",
            sets = 1,
            reps = null,
            durationSec = 20,
            date = 1_721_100_000_000L
        )

    /**
     * Универсальный state для тестов: VM-слой здесь не нужен — тесты
     * подают готовые `MainUiState.Success` и проверяют рендер/колбэки.
     */
    private fun setContent(state: MainUiState) {
        composeTestRule.setContent {
            MainScreenContent(
                state =
                    MainScreenState(
                        uiState = state,
                        onAddEntryClick = {},
                        onRequestDelete = {},
                        onContextMenuItemChange = {},
                        onConfirmDelete = {},
                        onCancelDelete = {}
                    )
            )
        }
    }

    @Test
    fun content_when_entries_then_shows_each_row_with_name_and_value() {
        // Given — две записи: REPS (4×10) и TIME (1×20)
        setContent(
            MainUiState.Success(
                entries = listOf(repsItem, timeItem)
            )
        )

        // Then — заголовок TopAppBar локализован (spec «MainScreen
        // renders with localized title»)
        composeTestRule
            .onNodeWithText(context.getString(R.string.workouts_title))
            .assertIsDisplayed()

        // Then — обе строки отображаются с названием упражнения
        composeTestRule.onNodeWithText(repsItem.exerciseName).assertIsDisplayed()
        composeTestRule.onNodeWithText(timeItem.exerciseName).assertIsDisplayed()

        // Then — значения формата «4 х 10» и «20» (RU-разделитель)
        composeTestRule
            .onNodeWithText(
                context.getString(R.string.entry_value_separator).let { sep ->
                    "${repsItem.sets}$sep${repsItem.reps}"
                }
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("${timeItem.durationSec}")
            .assertIsDisplayed()

        // Then — заглушка пустого состояния не видна
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_empty))
            .assertCountEquals(0)
    }

    @Test
    fun content_when_empty_then_shows_placeholder_and_no_rows() {
        // Given — пустой список
        setContent(
            MainUiState.Success(
                entries = emptyList()
            )
        )

        // Then — заглушка с текстом «Добавьте первую запись»
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_empty))
            .assertIsDisplayed()

        // Then — названий упражнений из списка нет
        composeTestRule
            .onAllNodesWithText(repsItem.exerciseName)
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(timeItem.exerciseName)
            .assertCountEquals(0)
    }

    /**
     * Полный сценарий long-press: открытие меню, переход в алерт,
     * отмена (запись остаётся), повторный переход в алерт, подтверждение
     * (onRequestDelete вызван, onConfirmDelete вызван).
     *
     * Меню/алерт управляются локально в Composable — тест держит свой
     * `mutableStateOf` для эмуляции поведения `MainScreen` (VM-обёртки),
     * который на этом уровне уже не тестируется (есть [MainScreenFabNavigationTest]).
     */
    @Test
    fun content_long_press_open_menu_then_delete_opens_alert_then_cancel_keeps_then_confirm_calls_delete() {
        // Given — экран с одной записью и локальным состоянием меню/диалога,
        // как в реальном `MainScreen` (вызывающий код склеивает стейт-машину).
        val pendingDeletes = mutableListOf<Long>()
        var menuChanges = 0
        var confirmedCount = 0
        var cancelledCount = 0
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ContextMenuState?>(null) }
            var pendingId by remember { mutableStateOf<Long?>(null) }
            val stateValue = pendingId
            val currentState =
                MainUiState.Success(
                    entries = listOf(repsItem),
                    pendingDeleteId = stateValue
                )
            MainScreenContent(
                state =
                    MainScreenState(
                        uiState = currentState,
                        contextMenu = menu,
                        onAddEntryClick = {},
                        onContextMenuItemChange = {
                            menuChanges++
                            menu = it
                        },
                        onRequestDelete = { item ->
                            menu = null
                            pendingDeletes += item.id
                            pendingId = item.id
                        },
                        onConfirmDelete = {
                            confirmedCount++
                            pendingId = null
                        },
                        onCancelDelete = {
                            cancelledCount++
                            pendingId = null
                        }
                    )
            )
        }

        // Then — сначала нет ни меню, ни диалога
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_delete))
            .assertCountEquals(0)

        // When — long-press по первой строке с упражнением
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — колбэк long-press был вызван
        assertEquals(
            "onContextMenuItemChange должен быть вызван один раз",
            1,
            menuChanges
        )

        // Then — меню «Удалить» появилось
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .assertExists()

        // When — тап по пункту «Удалить» в меню
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт подтверждения открыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertExists()
        composeTestRule
            .onNodeWithText(
                context
                    .getString(
                        R.string.entry_list_delete_dialog_message,
                        repsItem.exerciseName
                    )
            ).assertExists()
        assertEquals(
            "onRequestDelete должен быть вызван один раз при клике «Удалить» в меню",
            1,
            pendingDeletes.size
        )
        assertEquals(
            "onRequestDelete должен быть вызван с id записи",
            listOf(repsItem.id),
            pendingDeletes
        )

        // When — отмена в алерте
        composeTestRule
            .onNodeWithText(context.getString(R.string.cancel))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт закрыт
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertCountEquals(0)
        assertEquals(
            "cancelDelete должен быть вызван один раз",
            1,
            cancelledCount
        )
        assertEquals(
            "confirmDelete не должен быть вызван после отмены",
            0,
            confirmedCount
        )

        // When — повторный long-press и подтверждение удаления
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertExists()
        // В алерте также есть пункт с текстом «Удалить» (confirm-кнопка):
        // он уже отображается — кликаем по нему.
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — confirmDelete вызван, алерт закрыт
        assertEquals(
            "confirmDelete должен быть вызван один раз при подтверждении",
            1,
            confirmedCount
        )
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertCountEquals(0)
        assertTrue(
            "onRequestDelete должен быть вызван дважды (отмена + подтверждение)",
            pendingDeletes.size == 2
        )
    }

    /**
     * Spec «Дополнительный вес в строке записи»: запись с весом 5.0
     * показывает вторую строку — метку «Дополнительный вес» слева и
     * значение «5 кг» справа (целое — без дробной части).
     */
    @Test
    fun row_when_weight_then_shows_weight_line() {
        // Given — запись с доп. весом 5.0 кг
        setContent(
            MainUiState.Success(
                entries = listOf(repsItem.copy(weightKg = 5.0))
            )
        )

        // Then — метка строки веса отображается
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_weight_label))
            .assertIsDisplayed()

        // Then — значение «5 кг» (целое, без дробной части)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_weight_value, "5"))
            .assertIsDisplayed()
    }

    /**
     * Spec «Нулевой или отсутствующий вес скрывает строку веса»:
     * записи с `weightKg = null` и `weightKg = 0.0` не показывают
     * строку веса — вид строки как до изменения.
     */
    @Test
    fun row_when_no_weight_then_hides_weight_line() {
        // Given — записи без веса (null) и с нулевым весом (0.0)
        setContent(
            MainUiState.Success(
                entries =
                    listOf(
                        repsItem.copy(weightKg = null),
                        timeItem.copy(id = 3L, weightKg = 0.0)
                    )
            )
        )

        // Then — строки списка отображаются
        composeTestRule.onNodeWithText(repsItem.exerciseName).assertIsDisplayed()
        composeTestRule.onNodeWithText(timeItem.exerciseName).assertIsDisplayed()

        // Then — метки и значения строки веса нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_weight_label))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_weight_value, "0"))
            .assertCountEquals(0)
    }

    /**
     * Строка записи визуально выделяется, пока открыто контекстное меню
     * (design Decision 5, обновлённая часть про `surfaceVariant`), и
     * выделение снимается при закрытии меню. Источник истины —
     * семантика `SemanticsProperties.Selected` (стандартный канал
     * accessibility Compose).
     */
    @Test
    fun content_long_press_then_row_selected_semantic_is_true_then_dismiss_restores_false() {
        // Given — две записи и локальный `contextMenu`, как в реальном
        // `MainScreen`: VM-обёртка управляет состоянием, но проверяем
        // сам `MainScreenContent` с пробрасыванием тестового state.
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ContextMenuState?>(null) }
            MainScreenContent(
                state =
                    MainScreenState(
                        uiState =
                            MainUiState.Success(
                                entries = listOf(repsItem, timeItem)
                            ),
                        contextMenu = menu,
                        onAddEntryClick = {},
                        onContextMenuItemChange = { menu = it },
                        // «Удалить» в меню → запрос удаления, который
                        // сразу сбрасывает меню (имитация VM-обёртки:
                        // в реальности `MainScreen` дополнительно ставит
                        // `pendingDeleteId`, но это уже не зона проверки
                        // семантики выделения).
                        onRequestDelete = { menu = null },
                        onConfirmDelete = {},
                        onCancelDelete = {}
                    )
            )
        }

        // Then — изначально обе строки не выделены
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
            )
        composeTestRule
            .onNodeWithText(timeItem.exerciseName)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
            )

        // When — long-press по первой строке
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — первая строка выделена, вторая — нет
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
            )
        composeTestRule
            .onNodeWithText(timeItem.exerciseName)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
            )

        // When — закрытие меню выбором пункта «Удалить»
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()

        // Then — обе строки снова не выделены (даже если дальше открылся
        // алерт, семантика выделения строки снимается при скрытии меню)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
            )
        composeTestRule
            .onNodeWithText(timeItem.exerciseName)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
            )
    }
}
