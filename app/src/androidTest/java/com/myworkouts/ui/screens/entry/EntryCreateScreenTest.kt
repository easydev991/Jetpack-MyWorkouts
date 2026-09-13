package com.myworkouts.ui.screens.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

        // Then — подсказка заполнить повторы (в нижней панели под кнопкой)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_hint_fill_reps))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_time_without_duration_then_shows_select_duration_hint() {
        // Given — TIME-упражнение без длительности
        setContent(EntryCreateUiState(selectedExercise = timeExercise))

        // Then — подсказка выбрать длительность (в нижней панели под кнопкой)
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

        // Then — сообщение об ошибке (в нижней панели под кнопкой)
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
    fun content_when_date_picker_open_then_future_day_not_selectable() {
        // Given — дата 15 июля 2024, момент открытия — тот же день полдень;
        // onDateChange подключён к state, как это делает ViewModel.
        // repsText = «10» — canSave = true, кнопка сохранения не добавит
        // лишнюю disabled-ноду в подсчёт.
        // (spec «Даты позже дня открытия недоступны в пикере»)
        val openMomentMillis =
            LocalDate
                .of(2024, 7, 15)
                .atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        composeTestRule.setContent {
            var uiState by
                remember {
                    mutableStateOf(
                        EntryCreateUiState(
                            selectedExercise = repsExercise,
                            repsText = "10",
                            date = LocalDate.of(2024, 7, 15),
                            openMomentMillis = openMomentMillis
                        )
                    )
                }
            EntryCreateScreenContent(
                state =
                    stateFor(uiState).copy(
                        onDateChange = { date -> uiState = uiState.copy(date = date) }
                    )
            )
        }

        // When — пикер даты открыт
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_select_date))
            .performClick()

        // Then — дни позже дня открытия (16–31 июля) недоступны: 16
        // disabled-ячеек. Сетка месяца всегда 6×7, дни соседних месяцев —
        // пустые ячейки без семантики (текст дня в M3 1.4 вычищен из
        // семантики, ищем по признаку Disabled).
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .assertCountEquals(16)

        // When — попытка выбрать будущую дату (первая disabled-ячейка —
        // 16 июля) и подтверждение пикера
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .onFirst()
            .performTouchInput { click(center) }
        composeTestRule.onNodeWithText(context.getString(R.string.ok)).performClick()

        // Then — поле даты не изменилось
        val initialDateText =
            LocalDate.of(2024, 7, 15).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        composeTestRule.onNodeWithText(initialDateText).assertIsDisplayed()
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

    @Test
    fun content_when_long_form_filled_then_save_button_visible_without_scroll() {
        // Given — REPS-упражнение, всё заполнено, длинный многострочный
        // комментарий: форма не помещается во viewport
        val longComment =
            List(30) { line -> "Строка $line: достаточно длинный текст комментария" }
                .joinToString(separator = "\n")
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "3",
                repsText = "10",
                weightText = "12.5",
                comment = longComment
            )
        )

        // Then — кнопка «Сохранить» видна без performScrollTo()
        // (после переезда в Scaffold.bottomBar)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_unsaved_changes_and_back_arrow_clicked_then_confirm_dialog_shown() {
        // Given — режим создания с несохранёнными изменениями (комментарий)
        var backClicked = false
        composeTestRule.setContent {
            EntryCreateScreenContent(
                state =
                    stateFor(EntryCreateUiState(comment = "Хорошая техника"))
                        .copy(onBackClick = { backClicked = true })
            )
        }

        // When — тап по стрелке «назад» в TopAppBar
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()

        // Then — диалог подтверждения виден, экран не закрыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertIsDisplayed()
        assertTrue("Диалог показан — экран не должен закрыться сразу", !backClicked)
    }

    @Test
    fun content_when_confirm_dialog_cancel_clicked_then_dialog_hidden_and_content_visible() {
        // Given — диалог подтверждения открыт (есть изменения)
        setContent(EntryCreateUiState(comment = "Хорошая техника"))
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertIsDisplayed()

        // When — «Отмена»
        composeTestRule
            .onNodeWithText(context.getString(R.string.cancel))
            .performClick()

        // Then — диалог скрыт, экран открыт с введёнными данными
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
        composeTestRule.onNodeWithText("Хорошая техника").assertIsDisplayed()
    }

    @Test
    fun content_when_confirm_dialog_close_clicked_then_on_back_click_called() {
        // Given — диалог подтверждения открыт (есть изменения)
        var backClicked = false
        composeTestRule.setContent {
            EntryCreateScreenContent(
                state =
                    stateFor(EntryCreateUiState(comment = "Хорошая техника"))
                        .copy(onBackClick = { backClicked = true })
            )
        }
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertIsDisplayed()

        // When — «Закрыть»
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_confirm_close))
            .performClick()

        // Then — экран закрывается без сохранения
        assertTrue("Подтверждение должно закрывать экран (onBackClick)", backClicked)
    }

    @Test
    fun content_when_unsaved_changes_and_system_back_pressed_then_confirm_dialog_shown() {
        // Given — режим создания с несохранёнными изменениями
        setContent(EntryCreateUiState(comment = "Хорошая техника"))

        // When — системная кнопка/жест «назад»
        Espresso.pressBack()

        // Then — тот же диалог подтверждения, что и для стрелки TopAppBar
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_no_changes_and_back_arrow_clicked_then_on_back_click_without_dialog() {
        // Given — дефолтная форма создания (изменений нет)
        var backClicked = false
        composeTestRule.setContent {
            EntryCreateScreenContent(
                state = stateFor().copy(onBackClick = { backClicked = true })
            )
        }

        // When — тап по стрелке «назад»
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()

        // Then — экран закрылся сразу, без диалога
        assertTrue("Без изменений стрелка должна закрывать экран сразу", backClicked)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
    }

    @Test
    fun content_when_edit_form_changed_valid_and_back_arrow_clicked_then_confirm_dialog_shown() {
        // Given — режим редактирования: форма изменена и валидна
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "3",
                repsText = "10",
                isEditMode = true,
                hasChanges = true
            )
        )

        // When — тап по стрелке «назад»
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()

        // Then — диалог подтверждения
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertIsDisplayed()
    }

    @Test
    fun content_when_edit_form_changed_invalid_and_back_arrow_clicked_then_no_dialog() {
        // Given — режим редактирования: форма изменена, но невалидна
        // (очищены повторы) — canSave = false
        var backClicked = false
        composeTestRule.setContent {
            EntryCreateScreenContent(
                state =
                    stateFor(
                        EntryCreateUiState(
                            selectedExercise = repsExercise,
                            repsText = "",
                            isEditMode = true,
                            hasChanges = true
                        )
                    ).copy(onBackClick = { backClicked = true })
            )
        }

        // When — тап по стрелке «назад»
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()

        // Then — экран закрылся сразу, без диалога
        assertTrue("Невалидная форма закрывается без диалога", backClicked)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
    }

    /**
     * §8.2 exercises-archive: `mutableStateOf<EntryCreateUiState>` с
     * `selectedExercise = X` и `exercises = [X, Y, ...]`; при перерисовке
     * state заменить `exercises = [Y, ...]` (имитация реактивного
     * обновления после архивации, без VM/repo) — X продолжает
     * отображаться как текущий выбор упражнения формы (поле выбора
     * показывает X, в выпадающем списке X отсутствует).
     */
    @Test
    fun content_when_selected_exercise_archived_then_field_keeps_selection_but_dropdown_excludes() {
        // Given — выбрано X, exercises = [X, Y]
        var uiState by mutableStateOf(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                exercises = listOf(repsExercise, timeExercise)
            )
        )
        composeTestRule.setContent {
            EntryCreateScreenContent(
                state =
                    EntryCreateScreenState(
                        uiState = uiState,
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
            )
        }
        composeTestRule.waitForIdle()

        // Sanity: X виден как текущий выбор упражнения формы
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()

        // When — имитация реактивного обновления: X заархивирован,
        // `exercises` обновлён (X отсутствует)
        uiState =
            uiState.copy(
                exercises = listOf(timeExercise)
            )
        composeTestRule.waitForIdle()

        // Then — поле выбора продолжает показывать X как текущее значение
        // формы (UI не падает; выбор сохраняется в `selectedExercise`)
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()

        // Then — открытие выпадающего списка показывает только Y (X
        // отфильтрован)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_field_exercise))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Планка")
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Подтягивания")
            // Один экземпляр — поле выбора (текущее значение),
            // в выпадающем списке нет
            .assertCountEquals(1)
    }

    @Test
    fun content_when_save_clicked_then_no_confirm_dialog() {
        // Given — валидная форма создания с изменениями
        setContent(
            EntryCreateUiState(
                selectedExercise = repsExercise,
                setsText = "3",
                repsText = "10"
            )
        )

        // When — тап по «Сохранить»
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .performClick()

        // Then — путь сохранения не перехватывается: диалога нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
    }
}
