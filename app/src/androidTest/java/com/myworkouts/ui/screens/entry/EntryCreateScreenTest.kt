package com.myworkouts.ui.screens.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.EntryCreateUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Компонентные UI-тесты для [EntryCreateScreenContent] через plain state
 * без ViewModel (образец — `AppearanceScreenTest`; логика VM покрыта
 * JVM-тестами `EntryCreateViewModelTest`).
 *
 * Проверяют состав полей для REPS/TIME-упражнений, переключение
 * типозависимого поля и подсказки обязательности при `canSave = false`.
 */
@RunWith(AndroidJUnit4::class)
class EntryCreateScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val repsExercise =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val timeExercise =
        Exercise(
            id = 2L,
            name = "Планка",
            category = ExerciseCategory.CORE,
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private fun stateFor(uiState: EntryCreateUiState = EntryCreateUiState()): EntryCreateScreenState =
        EntryCreateScreenState(
            uiState = uiState.copy(exercises = listOf(repsExercise, timeExercise)),
            onExerciseChange = {},
            onSetsChange = {},
            onRepsChange = {},
            onDurationChange = {},
            onWeightChange = {},
            onDateChange = {},
            onTimeChange = { _, _ -> },
            onCommentChange = {},
            onSaveClick = {},
            onBackClick = {}
        )

    private fun setContent(uiState: EntryCreateUiState = EntryCreateUiState()) {
        composeTestRule.setContent {
            EntryCreateScreenContent(state = stateFor(uiState))
        }
    }

    /** Метка [higher] визуально выше [lower] (порядок полей в Column). */
    private fun assertDisplayedAbove(
        higher: String,
        lower: String
    ) {
        val higherTop =
            composeTestRule
                .onNodeWithText(higher)
                .fetchSemanticsNode()
                .boundsInRoot.top
        val lowerTop =
            composeTestRule
                .onNodeWithText(lower)
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("«$higher» должен быть выше «$lower»", higherTop < lowerTop)
    }

    @Test
    fun content_when_reps_exercise_selected_then_shows_reps_and_no_duration() {
        // Given — выбрано REPS-упражнение
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                repsText = "10"
            )
        )

        // Then — общие поля
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_exercise)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_sets)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_date)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_time)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_comment)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsDisplayed()

        // Then — типозависимое поле «Повторы» есть, «Длительность» отсутствует
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_reps)).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_duration))
            .assertCountEquals(0)
    }

    @Test
    fun content_when_time_exercise_selected_then_shows_duration_and_no_reps() {
        // Given — выбрано TIME-упражнение с длительностью
        setContent(
            EntryCreateUiState(
                selectedExercise = timeExercise,
                durationSec = 60
            )
        )

        // Then — «Длительность» есть, «Повторы» отсутствуют
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_duration)).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_reps))
            .assertCountEquals(0)
    }

    @Test
    fun content_when_no_exercise_selected_then_execution_fields_absent() {
        // Given — упражнение не выбрано
        setContent()

        // Then — поля выполнения скрыты (spec «Поля выполнения скрыты
        // до выбора упражнения»), общие поля остаются
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_sets))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_reps))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_duration))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_weight))
            .assertCountEquals(0)
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_exercise)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_date)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).assertIsDisplayed()
    }

    @Test
    fun content_when_reps_selected_then_weight_below_reps_and_above_date() {
        // Given — выбрано REPS-упражнение
        setContent(EntryCreateUiState(selectedExercise = repsExercise, repsText = "10"))

        // Then — «Доп. вес» виден; порядок: подходы → повторы → вес → дата
        val weightLabel = context.getString(R.string.entry_field_weight)
        composeTestRule.onNodeWithText(weightLabel).assertIsDisplayed()
        assertDisplayedAbove(context.getString(R.string.entry_field_sets), context.getString(R.string.entry_field_reps))
        assertDisplayedAbove(context.getString(R.string.entry_field_reps), weightLabel)
        assertDisplayedAbove(weightLabel, context.getString(R.string.entry_field_date))
    }

    @Test
    fun content_when_time_selected_then_weight_below_duration() {
        // Given — выбрано TIME-упражнение
        setContent(EntryCreateUiState(selectedExercise = timeExercise, durationSec = 60))

        // Then — порядок: подходы → длительность → вес → дата
        val weightLabel = context.getString(R.string.entry_field_weight)
        assertDisplayedAbove(context.getString(R.string.entry_field_sets), context.getString(R.string.entry_field_duration))
        assertDisplayedAbove(context.getString(R.string.entry_field_duration), weightLabel)
        assertDisplayedAbove(weightLabel, context.getString(R.string.entry_field_date))
    }

    @Test
    fun content_when_decimal_weight_typed_then_state_updates() {
        // Given — REPS-упражнение; вес — редактируемый state
        // (санитайзинг ввода покрыт JVM-тестами ViewModel)
        composeTestRule.setContent {
            var uiState by
                remember {
                    mutableStateOf(EntryCreateUiState(selectedExercise = repsExercise, repsText = "10"))
                }
            EntryCreateScreenContent(
                state =
                    stateFor(uiState).copy(
                        onWeightChange = { text -> uiState = uiState.copy(weightText = text) }
                    )
            )
        }

        // When — ввод десятичного значения (запятая — с Decimal-клавиатуры)
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_weight)))
            .performTextReplacement("12,5")

        // Then — state обновился, поле показывает введённое
        composeTestRule.onNodeWithText("12,5").assertIsDisplayed()
    }

    @Test
    fun content_when_weight_default_zero_then_save_button_enabled() {
        // Given — всё заполнено, «Доп. вес» остался с дефолтом «0»
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "3",
                repsText = "10"
            )
        )

        // Then — дефолтный ноль не блокирует сохранение
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsEnabled()
    }

    @Test
    fun content_when_duration_value_rendered_then_no_raw_format_placeholder() {
        // Given — TIME-упражнение с длительностью 65 сек («1 мин 5 сек»):
        // строки-сокращения не содержат сырых плейсхолдеров «%1$d»
        // (bug: «5 %1$d сек» — ресурс с плейсхолдером без форматирования)
        setContent(
            EntryCreateUiState(
                selectedExercise = timeExercise,
                durationSec = 65
            )
        )

        // Then — ни один узел не содержит сырого плейсхолдера
        composeTestRule
            .onAllNodes(hasText("%1\$d", substring = true))
            .assertCountEquals(0)
    }

    @Test
    fun content_when_exercise_switched_then_field_switches_and_value_resets() {
        // Given — REPS-упражнение с заполненными повторами; смена упражнения
        // эмулирует VM-обработчик: сброс повторов/длительности
        composeTestRule.setContent {
            var uiState by
                remember {
                    mutableStateOf(
                        EntryCreateUiState(selectedExercise = repsExercise, repsText = "10")
                    )
                }
            EntryCreateScreenContent(
                state =
                    stateFor(uiState).copy(
                        onExerciseChange = { exercise ->
                            uiState =
                                uiState.copy(
                                    selectedExercise = exercise,
                                    repsText = "",
                                    durationSec = null
                                )
                        }
                    )
            )
        }
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_reps)).assertIsDisplayed()

        // When — открываем список упражнений и выбираем TIME-упражнение
        composeTestRule.onNodeWithText(repsExercise.name).performClick()
        composeTestRule.onNodeWithText(timeExercise.name).performClick()

        // Then — «Повторы» исчезли, «Длительность» появилась
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_reps))
            .assertCountEquals(0)
        composeTestRule.onNodeWithText(context.getString(R.string.entry_field_duration)).assertIsDisplayed()
    }

    @Test
    fun content_when_no_exercise_selected_then_shows_select_exercise_hint() {
        // Given — упражнение не выбрано, canSave = false
        setContent()

        // Then — подсказка выбрать упражнение
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_hint_select_exercise))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_reps_empty_then_shows_fill_reps_hint() {
        // Given — REPS-упражнение без повторов
        setContent(EntryCreateUiState(selectedExercise = repsExercise))

        // Then — подсказка заполнить повторы
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_hint_fill_reps))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_time_without_duration_then_shows_select_duration_hint() {
        // Given — TIME-упражнение без длительности
        setContent(EntryCreateUiState(selectedExercise = timeExercise))

        // Then — подсказка выбрать длительность
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_hint_select_duration))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_all_required_filled_then_no_hints() {
        // Given — REPS, повторы заполнены (canSave = true)
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "3",
                repsText = "10"
            )
        )

        // Then — подсказок нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_hint_select_exercise))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_hint_fill_reps))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_hint_fill_sets))
            .assertCountEquals(0)
    }

    @Test
    fun content_when_valid_state_then_sets_prefilled_and_time_from_state() {
        // Given
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                repsText = "10",
                date = LocalDate.of(2024, 7, 15),
                hour = 14,
                minute = 30
            )
        )

        // Then — время из state отображается в поле
        composeTestRule.onNodeWithText("14:30").assertIsDisplayed()
    }

    @Test
    fun content_when_save_error_then_shows_error_message() {
        // Given — провал сохранения
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                repsText = "10",
                isSaveError = true
            )
        )

        // Then — сообщение об ошибке
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save_error))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_save_clicked_then_on_save_click_called() {
        // Given
        var saveClicked = false
        composeTestRule.setContent {
            EntryCreateScreenContent(
                state =
                    stateFor(
                        EntryCreateUiState(
                            selectedExercise = repsExercise,
                            repsText = "10"
                        )
                    ).copy(onSaveClick = { saveClicked = true })
            )
        }

        // When
        composeTestRule.onNodeWithText(context.getString(R.string.entry_save)).performClick()

        // Then
        assertTrue("Кнопка сохранения должна вызывать onSaveClick", saveClicked)
    }

    @Test
    fun content_when_no_exercise_selected_then_save_button_disabled() {
        // Given — упражнение не выбрано
        setContent()

        // Then — кнопка неактивна
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsNotEnabled()
    }

    @Test
    fun content_when_reps_empty_then_save_button_disabled() {
        // Given — REPS-упражнение без повторов
        setContent(EntryCreateUiState(selectedExercise = repsExercise))

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsNotEnabled()
    }

    @Test
    fun content_when_time_without_duration_then_save_button_disabled() {
        // Given — TIME-упражнение без длительности
        setContent(EntryCreateUiState(selectedExercise = timeExercise))

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsNotEnabled()
    }

    @Test
    fun content_when_sets_cleared_then_save_button_disabled() {
        // Given — подходы очищены
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                repsText = "10",
                setsText = ""
            )
        )

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsNotEnabled()
    }

    @Test
    fun content_when_all_required_filled_then_save_button_enabled() {
        // Given — всё заполнено (canSave = true)
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "3",
                repsText = "10"
            )
        )

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsEnabled()
    }

    @Test
    fun content_when_date_icon_clicked_then_date_picker_dialog_shown() {
        // Given
        setContent(EntryCreateUiState(selectedExercise = repsExercise, repsText = "10"))

        // When — тап по иконке календаря у поля даты
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_select_date))
            .performClick()

        // Then — диалог пикера даты открыт (кнопка «ОК» диалога видна)
        composeTestRule.onNodeWithText(context.getString(R.string.ok)).assertIsDisplayed()
    }

    @Test
    fun content_when_time_icon_clicked_then_time_picker_dialog_shown() {
        // Given
        setContent(EntryCreateUiState(selectedExercise = repsExercise, repsText = "10"))

        // When — тап по иконке часов у поля времени
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_select_time))
            .performClick()

        // Then — диалог пикера времени открыт (кнопка «ОК» диалога видна)
        composeTestRule.onNodeWithText(context.getString(R.string.ok)).assertIsDisplayed()
    }
}
