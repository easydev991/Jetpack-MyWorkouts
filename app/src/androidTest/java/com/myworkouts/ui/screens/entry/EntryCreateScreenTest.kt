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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.EntryField
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.icons.EXERCISE_ICON_TEST_TAG
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.state.EntryCreateUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.EntryCreateViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
            category = "PULL",
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val timeExercise =
        Exercise(
            id = 2L,
            name = "Планка",
            category = "CORE",
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private fun stateFor(
        uiState: EntryCreateUiState = EntryCreateUiState(),
        // Точный каталог вместо фикстур по умолчанию (тесты каталога
        // с иконками); null — подставить стандартные фикстуры
        exercises: List<Exercise>? = null,
        onOpenSettings: () -> Unit = {}
    ): EntryCreateScreenState =
        EntryCreateScreenState(
            uiState = uiState.copy(exercises = exercises ?: listOf(repsExercise, timeExercise)),
            onExerciseChange = {},
            onSetsChange = {},
            onRepsChange = {},
            onDurationChange = {},
            onWeightChange = {},
            onDateChange = {},
            onTimeChange = { _, _ -> },
            onCommentChange = {},
            onSaveClick = {},
            onOpenSettings = onOpenSettings,
            onBackClick = {}
        )

    private fun setContent(
        uiState: EntryCreateUiState = EntryCreateUiState(),
        exercises: List<Exercise>? = null
    ) {
        composeTestRule.setContent {
            EntryCreateScreenContent(state = stateFor(uiState, exercises))
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
        assertDisplayedAbove(
            context.getString(R.string.entry_field_sets),
            context.getString(R.string.entry_field_duration)
        )
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
                        onOpenSettings = {},
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

    /**
     * Spec «Иконка упражнения в списке выбора упражнения»
     * (exercise-icon-display): опция с известным номером иконки
     * показывает иконку в `leadingIcon`; опция с `iconNumber = null` —
     * только текст (слот иконки не занимает места). Тема-вариант
     * (тёмная тема) покрыт эталонно в `MainScreenTest`: рендер — общий
     * `ExerciseIcon` (design Decision 6).
     */
    @Test
    fun dropdown_when_options_with_and_without_icon_then_only_iconed_options_show_icons() {
        // Given — два упражнения с иконками и кастомное без иконки
        val noIconExercise =
            Exercise(
                id = 100L,
                name = "Жим",
                category = "PUSH",
                isCustom = true,
                comment = null,
                createdAt = 0L,
                measuredBy = ExerciseMeasuredBy.REPS,
                iconNumber = null
            )
        setContent(
            exercises =
                listOf(
                    repsExercise.copy(iconNumber = ExerciseIcons.PULLUPS),
                    timeExercise.copy(iconNumber = ExerciseIcons.PUSHUPS),
                    noIconExercise
                )
        )

        // Sanity — до открытия списка иконок нет: они только в опциях
        composeTestRule
            .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(0)

        // When — открытие выпадающего списка
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_field_exercise))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — все три опции в списке (третья может быть за нижним
        // краем экрана — проверяем существование, не видимость)
        composeTestRule.onNodeWithText("Подтягивания").assertIsDisplayed()
        composeTestRule.onNodeWithText("Планка").assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Жим")
            .assertCountEquals(1)

        // Then — иконки ровно у двух опций: «Жим» без `iconNumber` —
        // только текст
        composeTestRule
            .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(2)
    }

    /**
     * Сценарий «Архивное упражнение записи сохраняет иконку»
     * (exercise-icon-display): в режиме редактирования архивное исходное
     * упражнение, присутствующее в списке опций, показывает свою иконку
     * на общих правилах — иконка хранится на упражнении и не зависит от
     * архивного состояния.
     */
    @Test
    fun dropdown_when_edit_mode_archived_source_in_options_then_option_keeps_icon() {
        // Given — режим редактирования; архивное исходное упражнение —
        // в списке опций (spec: «и оно присутствует в списке опций»);
        // обе опции с иконками — архивное рендерится на общих правилах
        val archivedSource =
            repsExercise.copy(isArchived = true, iconNumber = ExerciseIcons.PULLUPS)
        setContent(
            uiState =
                EntryCreateUiState(
                    selectedExercise = archivedSource,
                    isEditMode = true
                ),
            exercises =
                listOf(
                    archivedSource,
                    timeExercise.copy(iconNumber = ExerciseIcons.PUSHUPS)
                )
        )

        // When — открытие выпадающего списка
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_field_exercise))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — обе опции в списке, поле показывает исходное
        composeTestRule
            .onAllNodesWithText("Подтягивания")
            // Поле выбора (текущее значение) + опция списка
            .assertCountEquals(2)
        composeTestRule.onNodeWithText("Планка").assertIsDisplayed()

        // Then — иконка архивного исходного упражнения на месте
        // (ровно две: опции «Подтягивания» и «Планка»)
        composeTestRule
            .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(2)
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

    // ---------- entry-settings-screen: кнопка настроек и скрытие полей ----------

    /** Кнопка настроек в TopAppBar: тап вызывает колбэк навигации (§8.2). */
    @Test
    fun content_when_settings_button_clicked_then_calls_on_open_settings() {
        // Given
        var openSettingsCalled = false
        composeTestRule.setContent {
            EntryCreateScreenContent(
                state = stateFor(onOpenSettings = { openSettingsCalled = true })
            )
        }

        // When — тап по IconButton «Настроить экран»
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_settings_content_description))
            .performClick()

        // Then
        assertTrue("Тап по настройкам вызывает колбэк навигации", openSettingsCalled)
    }

    /** Скрытое поле «Доп. вес» не рендерится, остальные — по правилам. */
    @Test
    fun content_when_weight_field_hidden_then_weight_field_not_rendered() {
        // Given — упражнение выбрано, поле веса скрыто настройкой
        setContent(
            EntryCreateUiState(selectedExercise = repsExercise, isWeightFieldVisible = false)
        )

        // Then
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_weight))
            .assertCountEquals(0)
    }

    /** Нон-регресс: при видимой настройке поле «Доп. вес» отображается. */
    @Test
    fun content_when_weight_field_visible_then_weight_field_rendered() {
        // Given — упражнение выбрано, видимость веса — дефолт (включена)
        setContent(EntryCreateUiState(selectedExercise = repsExercise))

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_field_weight))
            .assertIsDisplayed()
    }

    // ---------- entry-prefilled-reps-and-duration: предзаполнение из истории ----------

    /** In-memory БД харнесса предзаполнения; создаётся лениво, закрывается в [tearDown]. */
    private var prefillDatabase: WorkoutDatabase? = null

    /** Закрывает БД харнесса и сбрасывает видимость веса (DataStore-синглтон
     * живёт весь инструментальный процесс — скрытие не должно утекать
     * в другие тесты и классы). */
    @After
    fun tearDown() {
        runBlocking {
            AppSettingsDataStore.create(context).setFieldVisible(EntryField.WEIGHT, true)
        }
        prefillDatabase?.close()
        prefillDatabase = null
    }

    /** Room in-memory без стандартных упражнений — каталог только из сида. */
    private fun setupPrefillRepository(): RoomWorkoutDataRepository {
        val database =
            Room
                .inMemoryDatabaseBuilder(context.applicationContext, WorkoutDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        prefillDatabase = database
        return RoomWorkoutDataRepository(database, standardExercises = emptyList())
    }

    /** Миллисы «[days] дней назад» — записи различаются датами, порядок строгий. */
    private fun daysAgoMillis(days: Long): Long = System.currentTimeMillis() - days * 86_400_000L

    /** Запись истории с фиксированными подходами (2) — не пересекаются
     * с сохраняемой в тестах (3). */
    private fun historyEntry(
        exerciseId: Long,
        date: Long,
        reps: Int?,
        weightKg: Double?,
        createdAt: Long,
        durationSec: Int? = null
    ): ExerciseEntry =
        ExerciseEntry(
            id = 0L,
            exerciseId = exerciseId,
            workoutId = null,
            date = date,
            sets = 2,
            reps = reps,
            weightKg = weightKg,
            durationSec = durationSec,
            comment = null,
            createdAt = createdAt,
            modifiedAt = createdAt
        )

    /** Каталожное упражнение харнесса: значимы имя, тип измерения и порядок создания. */
    private fun exercise(
        name: String,
        category: String,
        measuredBy: ExerciseMeasuredBy,
        createdAt: Long
    ): Exercise =
        Exercise(
            id = 0L,
            name = name,
            category = category,
            isCustom = true,
            comment = null,
            createdAt = createdAt,
            measuredBy = measuredBy
        )

    /**
     * Сид каталога и истории: «Жим лёжа» — 4 записи, 3 с весом (строгое
     * большинство), последняя (12 повторов, вчера) без веса → вес берётся
     * из записи позавчера (25.0); «Отжимания» — без записей; «Планка» —
     * одна запись с длительностью 90 секунд.
     */
    private fun seedHistory(repository: RoomWorkoutDataRepository): Unit =
        runBlocking {
            val repsWithHistoryId =
                repository
                    .addExercise(exercise("Жим лёжа", "PUSH", ExerciseMeasuredBy.REPS, createdAt = 1L))
                    .getOrThrow()
            repository
                .addExercise(exercise("Отжимания", "PUSH", ExerciseMeasuredBy.REPS, createdAt = 2L))
                .getOrThrow()
            val timeId =
                repository
                    .addExercise(exercise("Планка", "CORE", ExerciseMeasuredBy.TIME, createdAt = 3L))
                    .getOrThrow()
            val add: suspend (ExerciseEntry) -> Unit = { repository.addEntry(it).getOrThrow() }
            add(historyEntry(repsWithHistoryId, daysAgoMillis(4), reps = 6, weightKg = 15.0, createdAt = 1L))
            add(historyEntry(repsWithHistoryId, daysAgoMillis(3), reps = 8, weightKg = 20.0, createdAt = 2L))
            add(historyEntry(repsWithHistoryId, daysAgoMillis(2), reps = 10, weightKg = 25.0, createdAt = 3L))
            add(historyEntry(repsWithHistoryId, daysAgoMillis(1), reps = 12, weightKg = null, createdAt = 4L))
            add(historyEntry(timeId, daysAgoMillis(1), reps = null, weightKg = null, durationSec = 90, createdAt = 5L))
        }

    /**
     * Открывает экран создания с реальной [EntryCreateViewModel] поверх
     * [repository] (образец — `EntryEditArchivedExerciseTest`): сквозная
     * проверка «выбор в UI → предзаполнение в полях → сохранение в БД»;
     * правила расчёта покрыты JVM-тестами `EntryCreateViewModelTest`.
     */
    private fun setPrefillContent(
        repository: RoomWorkoutDataRepository,
        dataStore: AppSettingsDataStore,
        onBackClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                EntryCreateScreen(
                    viewModel =
                        EntryCreateViewModel(
                            repository = repository,
                            nowProvider = { System.currentTimeMillis() },
                            entryId = Screen.EntryCreate.NO_ENTRY_ID,
                            dataStore = dataStore,
                            analyticsProvider = NoopAnalyticsProvider()
                        ),
                    onBackClick = onBackClick,
                    onOpenSettings = {}
                )
            }
        }
    }

    /**
     * Открывает дропдаун с пунктом [fieldTextRes] и выбирает [optionText].
     * Клик повторяется, пока пункт не появится: попап композируется кадром
     * позже клика (образец — `EntryEditArchivedExerciseTest`).
     */
    private fun selectDropdownOption(
        fieldTextRes: Int,
        optionText: String
    ) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            val optionExists =
                composeTestRule.onAllNodesWithText(optionText).fetchSemanticsNodes().isNotEmpty()
            if (!optionExists) {
                composeTestRule
                    .onNodeWithText(context.getString(fieldTextRes))
                    .performClick()
            }
            optionExists
        }
        composeTestRule.onNodeWithText(optionText).performClick()
    }

    /** Ждёт появления узла с текстом [text] (предзаполнение асинхронно). */
    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Ждёт появления узла с текстом [text] и проверяет, что он отображается. */
    private fun waitForDisplayed(text: String) {
        awaitText(text)
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    /** Выбор REPS-упражнения с историей предзаполняет повторы последней
     * записи и вес последней записи с положительным весом. */
    @Test
    fun prefill_when_reps_exercise_with_history_selected_then_reps_and_weight_prefilled() {
        // Given — «Жим лёжа»: 4 записи, 3 с весом; последняя (12 повторов)
        // без веса → вес из записи позавчера (25.0)
        val repository = setupPrefillRepository()
        seedHistory(repository)
        val dataStore = AppSettingsDataStore.create(context)
        runBlocking { dataStore.setFieldVisible(EntryField.WEIGHT, true) }
        setPrefillContent(repository, dataStore)

        // When — выбор упражнения с историей в дропдауне
        selectDropdownOption(R.string.entry_field_exercise, "Жим лёжа")

        // Then — повторы последней записи и вес последней записи с весом
        waitForDisplayed("12")
        composeTestRule.onNodeWithText("25.0").assertIsDisplayed()
    }

    /** Выбор TIME-упражнения с историей предзаполняет длительность последней
     * записи; вес без большинства остаётся нулевым. */
    @Test
    fun prefill_when_time_exercise_with_history_selected_then_duration_prefilled() {
        // Given — «Планка»: единственная запись с длительностью 90 секунд
        val repository = setupPrefillRepository()
        seedHistory(repository)
        val dataStore = AppSettingsDataStore.create(context)
        runBlocking { dataStore.setFieldVisible(EntryField.WEIGHT, true) }
        setPrefillContent(repository, dataStore)

        // When — выбор TIME-упражнения с историей
        selectDropdownOption(R.string.entry_field_exercise, "Планка")

        // Then — длительность последней записи в поле, плейсхолдер исчез;
        // 0 записей с весом из 1 → «Доп. вес» остаётся «0»
        waitForDisplayed("1 мин 30 сек")
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_duration_not_selected))
            .assertCountEquals(0)
        composeTestRule.onNodeWithText("0").assertIsDisplayed()
    }

    /** Выбор упражнения без записей оставляет типозависимое поле пустым,
     * вес — нулевым, сохранение — недоступным. */
    @Test
    fun prefill_when_exercise_without_history_selected_then_fields_stay_empty() {
        // Given — «Отжимания» без единой записи
        val repository = setupPrefillRepository()
        seedHistory(repository)
        val dataStore = AppSettingsDataStore.create(context)
        runBlocking { dataStore.setFieldVisible(EntryField.WEIGHT, true) }
        setPrefillContent(repository, dataStore)

        // When — выбор упражнения без истории
        selectDropdownOption(R.string.entry_field_exercise, "Отжимания")
        composeTestRule.waitForIdle()

        // Then — повторы пустые (подсказка), вес «0», сохранение неактивно
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_hint_fill_reps))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("0").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .assertIsNotEnabled()
    }

    /** Скрытое настройкой поле «Доп. вес» не отображается, но предзаполняется
     * на общих правилах и попадает в сохранённую запись (spec-сценарий
     * «Скрытое поле веса предзаполняется на общих правилах»). */
    @Test
    fun prefill_when_weight_field_hidden_then_not_rendered_but_saved_with_prefilled_weight() {
        // Given — поле веса скрыто настройкой; история «Жима» — 3 из 4 с весом
        val repository = setupPrefillRepository()
        seedHistory(repository)
        val dataStore = AppSettingsDataStore.create(context)
        runBlocking { dataStore.setFieldVisible(EntryField.WEIGHT, false) }
        var backClicked = false
        setPrefillContent(repository, dataStore) { backClicked = true }

        // When — выбор упражнения с историей
        selectDropdownOption(R.string.entry_field_exercise, "Жим лёжа")
        awaitText("12")

        // Then — поле веса не отображается, хотя предзаполнено в состоянии
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_field_weight))
            .assertCountEquals(0)

        // When — подходы заполнены, запись сохранена; клик повторяется, пока
        // запись не появится в БД (инъекция может уйти в IME-анимацию —
        // образец `awaitSaved` из `EntryEditArchivedExerciseTest`)
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_sets)))
            .performTextReplacement("3")
        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            val saved =
                backClicked ||
                    runBlocking {
                        repository.getAllEntries().getOrThrow().any { it.sets == 3 }
                    }
            if (!saved) {
                composeTestRule
                    .onNodeWithText(context.getString(R.string.entry_save))
                    .performClick()
            }
            saved
        }

        // Then — запись получила предзаполненные значения скрытых полей
        val saved =
            checkNotNull(
                runBlocking {
                    repository.getAllEntries().getOrThrow().firstOrNull { it.sets == 3 }
                }
            ) {
                "Сохранённая запись должна появиться в БД"
            }
        assertEquals(
            "Скрытый вес предзаполнился и сохранился",
            25.0,
            checkNotNull(saved.weightKg),
            0.0
        )
        assertEquals("Повторы взялись из предзаполнения", 12, saved.reps)
        assertTrue("После сохранения экран закрывается", backClicked)
    }
}
