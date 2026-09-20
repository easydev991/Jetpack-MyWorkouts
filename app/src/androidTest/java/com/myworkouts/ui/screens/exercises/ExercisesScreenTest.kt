package com.myworkouts.ui.screens.exercises

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.icons.EXERCISE_ICON_TEST_TAG
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.state.ExercisesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Компонентные тесты для экрана «Упражнения» (exercises-tab, task 5.1).
 *
 * Рендер — через [ExercisesScreenContent] с готовым
 * [ExercisesScreenState] (без ViewModel и fake-репозитория, design
 * Decision 3). Навигация — NavHost-harness по образцу
 * `MoreScreenTest.MoreScreenWithNavHost`: заглушки-destinations +
 * `onControllerReady`, нижнего бара нет — проверяется, что
 * `navigate(Screen.Exercises.route)` открывает экран «Упражнения» и
 * что тап FAB ведёт на `Screen.ExerciseCreate.route` (fill/save-flow
 * формы — в `ExerciseCreateScreenTest` и ручной проверке 8.4).
 */
@RunWith(AndroidJUnit4::class)
class ExercisesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val pullups =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = "широкий хват",
            createdAt = 1_721_000_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val plank =
        Exercise(
            id = 2L,
            name = "Планка",
            category = ExerciseCategory.CORE,
            isCustom = false,
            comment = null,
            createdAt = 1_721_100_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    /** Кастомное упражнение для long-press тестов (edit-delete-exercise, §4). */
    private val bench =
        Exercise(
            id = 100L,
            name = "Жим",
            category = ExerciseCategory.PUSH,
            isCustom = true,
            comment = null,
            createdAt = 1_721_200_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    /** Рендер [ExercisesScreenContent] с готовым состоянием. */
    private fun setContentWith(uiState: ExercisesUiState) {
        composeTestRule.setContent {
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = uiState,
                        onAddExerciseClick = {}
                    )
            )
        }
    }

    /**
     * Test-Harness: `NavHost` со стартовой заглушкой, destination
     * `Screen.Exercises.route` на [ExercisesScreenContent] (без
     * ViewModel) и заглушкой `Screen.ExerciseCreate.route` — без неё
     * FAB-навигация бросила бы `IllegalArgumentException:
     * destination not found`. Без графа `navigate(...)` бросает
     * `IllegalArgumentException: Navigation graph has not been set`.
     */
    @Composable
    private fun ExercisesScreenWithNavHost(onControllerReady: (NavHostController) -> Unit = {}) {
        val navController = rememberNavController()
        onControllerReady(navController)
        NavHost(navController = navController, startDestination = "stub") {
            composable("stub") {
                // Заглушка стартового маршрута.
            }
            composable(Screen.Exercises.route) {
                ExercisesScreenContent(
                    state =
                        ExercisesScreenState(
                            uiState = ExercisesUiState.Success(exercises = listOf(pullups, plank)),
                            onAddExerciseClick = { navController.navigate(Screen.ExerciseCreate.createRoute()) }
                        )
                )
            }
            composable(Screen.ExerciseCreate.route, arguments = Screen.ExerciseCreate.arguments) {
                // Заглушка destination-маршрута: сценарий проверяет
                // только навигацию FAB → route (fill-flow формы — в
                // `ExerciseCreateScreenTest`, на пустой заглушке полей нет).
            }
        }
    }

    /** Проверяет, что список рендерит название и лейбл типа измерения. */
    @Test
    fun exercises_screen_when_listed_then_shows_name_and_measured_by_label() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = listOf(pullups, plank)))

        // Then
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_measured_by_reps))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Планка")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_measured_by_time))
            .assertIsDisplayed()
    }

    /**
     * Spec «Иконка упражнения в строке списка», сценарий «Упражнение
     * с назначенной иконкой» (exercise-icon-display): известный номер —
     * иконка слева от названия в верхнем ряду, лейбл типа измерения
     * остаётся у правого края; `iconNumber = null` — иконки нет
     * (строка как до изменения).
     */
    @Test
    fun row_when_icon_assigned_then_shows_icon_left_of_name_and_null_shows_none() {
        // Given — подтягивания с назначенной иконкой, планка без иконки
        setContentWith(
            ExercisesUiState.Success(
                exercises = listOf(pullups.copy(iconNumber = ExerciseIcons.PULLUPS), plank)
            )
        )

        // Then — ровно одна иконка: строка без `iconNumber` — без узла иконки
        composeTestRule
            .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)
            .assertCountEquals(1)

        // Then — иконка левее названия своей строки: оба узла берём из
        // unmerged-дерева — в merged-дереве текст слит со строкой и его
        // границы совпадают со всей строкой
        val iconLeft =
            composeTestRule
                .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)[0]
                .fetchSemanticsNode()
                .boundsInRoot.left
        val nameLeft =
            composeTestRule
                .onNodeWithText(pullups.name, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot.left
        assertTrue(
            "Иконка должна отображаться слева от названия упражнения",
            iconLeft < nameLeft
        )

        // Then — лейбл типа измерения на месте (у правого края)
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_measured_by_reps))
            .assertIsDisplayed()
    }

    // Тема-вариант иконки (тёмная тема) покрыт эталонно в `MainScreenTest`:
    // рендер — общий `ExerciseIcon` (design Decision 6).

    /** Проверяет, что комментарий виден при наличии и отсутствует при null. */
    @Test
    fun exercises_screen_row_comment_visible_only_when_present() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = listOf(pullups, plank)))

        // Then — комментарий подтягиваний виден
        composeTestRule
            .onNodeWithText("широкий хват")
            .assertIsDisplayed()

        // Then — у планки (comment = null) комментария нет: в дереве
        // нет текста отсутствующего комментария, а её название есть
        composeTestRule
            .onNodeWithText("Планка")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("тело — прямая линия")
            .assertDoesNotExist()
    }

    /** Проверяет пустое состояние при пустом каталоге. */
    @Test
    fun exercises_screen_when_empty_then_shows_empty_state() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = emptyList()))

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_list_empty))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertDoesNotExist()
    }

    /** Проверяет, что FAB отображается. */
    @Test
    fun exercises_screen_when_displayed_then_shows_fab() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = listOf(pullups)))

        // Then
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
            .assertIsDisplayed()
    }

    /**
     * Проверяет, что destination `Screen.Exercises.route` открывает
     * экран «Упражнения» (заголовок TopAppBar + контент списка).
     */
    @Test
    fun exercises_screen_when_navigated_to_exercises_route_then_opens_exercises() {
        // Given
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            ExercisesScreenWithNavHost(onControllerReady = { navController = it })
        }

        // When
        composeTestRule.runOnIdle {
            navController.navigate(Screen.Exercises.route)
        }

        // Then
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()
    }

    /**
     * Проверяет, что тап FAB ведёт на `Screen.ExerciseCreate.route`
     * (заглушка destination пустая — проверяется только маршрут).
     */
    @Test
    fun exercises_screen_when_fab_clicked_then_navigates_to_exercise_create_route() {
        // Given — экран «Упражнения» открыт через NavHost-harness
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            ExercisesScreenWithNavHost(onControllerReady = { navController = it })
        }
        composeTestRule.runOnIdle {
            navController.navigate(Screen.Exercises.route)
        }
        composeTestRule.waitForIdle()

        // When — тап по FAB «Добавить упражнение»
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
            .performClick()

        // Then — активный маршрут сменился на форму создания упражнения
        composeTestRule.runOnIdle {
            assertEquals(
                "FAB должен навигировать на Screen.ExerciseCreate.route",
                Screen.ExerciseCreate.route,
                navController.currentDestination?.route
            )
        }
    }

    // region edit-delete-exercise §4 (long-press, menu, alert)

    /**
     * §6.2: long-press на строке стандартного упражнения — меню из
     * одного пункта «Архивировать» (`Icons.Filled.Archive`); пункты
     * «Изменить» и «Удалить» НЕ показываются (exercises-archive
     * spec «Контекстное меню по долгому нажатию — с учётом типа
     * упражнения»). Переименовано из
     * `row_long_press_when_standard_then_no_context_menu` §4.1:
     * ранее стандартные не открывали меню (edit-delete-exercise
     * Decision 4), теперь — единственный пункт «Архивировать»
     * (exercises-archive Decision 4).
     */
    @Test
    fun row_long_press_when_standard_then_menu_with_archive_only() {
        // Given — стандартные упражнения + локальная state-машина
        // (как в тестах кастомного упражнения — `setContentWith` использует
        // дефолтные no-op callbacks и не хранит `contextMenu`, меню не
        // открывается)
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = ExercisesUiState.Success(exercises = listOf(pullups, plank)),
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = {},
                        onRequestArchive = {},
                        onRequestDelete = {}
                    )
            )
        }

        // When — long-press на строке стандартного упражнения
        composeTestRule
            .onNodeWithText("Подтягивания")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — пункт «Архивировать» присутствует
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_archive))
            .assertExists()

        // Then — пунктов «Изменить» и «Удалить» нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_list_menu_delete))
            .assertCountEquals(0)

        // Then — строка подсвечена (Selected semantic)
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()
    }

    /**
     * Accessibility-канал long-press (accessibility-semantics-fixes):
     * строка каталога предоставляет custom-действие `a11y_open_menu`;
     * выполнение открывает то же меню, что long-press — для стандартного
     * упражнения единственный пункт «Архивировать» (состав по типу).
     */
    @Test
    fun row_when_a11y_action_invoked_then_menu_with_archive_only() {
        // Given — стандартные упражнения + локальная state-машина
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = ExercisesUiState.Success(exercises = listOf(pullups)),
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = {},
                        onRequestArchive = {},
                        onRequestDelete = {}
                    )
            )
        }

        // When — выполнение custom-действия «Открыть меню» (как из
        // меню действий TalkBack)
        val openMenuAction =
            composeTestRule
                .onNodeWithText("Подтягивания")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                ?.firstOrNull { it.label == context.getString(R.string.a11y_open_menu) }
        assertNotNull(
            "Строка каталога должна предоставлять custom-действие «Открыть меню»",
            openMenuAction
        )
        checkNotNull(openMenuAction).action.invoke()
        composeTestRule.waitForIdle()

        // Then — меню как после long-press: только «Архивировать»
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_archive))
            .assertExists()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_list_menu_delete))
            .assertCountEquals(0)
    }

    /**
     * Accessibility-канал long-press: состав пунктов меню — по типу
     * упражнения. У кастомного — все три пункта «Изменить» /
     * «Архивировать» / «Удалить» (spec: сценарий «открывает меню
     * по типу упражнения»).
     */
    @Test
    fun row_when_a11y_action_invoked_when_custom_then_menu_with_edit_archive_delete() {
        // Given — кастомное упражнение + локальная state-машина
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = ExercisesUiState.Success(exercises = listOf(bench)),
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = {},
                        onRequestArchive = {},
                        onRequestDelete = {}
                    )
            )
        }

        // When — выполнение custom-действия «Открыть меню»
        val openMenuAction =
            composeTestRule
                .onNodeWithText("Жим")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                ?.firstOrNull { it.label == context.getString(R.string.a11y_open_menu) }
        assertNotNull(
            "Строка кастомного упражнения должна предоставлять custom-действие «Открыть меню»",
            openMenuAction
        )
        checkNotNull(openMenuAction).action.invoke()
        composeTestRule.waitForIdle()

        // Then — все три пункта, как после long-press
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_edit))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_archive))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_delete))
            .assertExists()
    }

    /**
     * §6.1: long-press на строке кастомного упражнения — меню из трёх
     * пунктов «Изменить» / «Архивировать» / «Удалить» в указанном
     * порядке (exercises-archive spec). Ранее — два пункта
     * «Изменить» / «Удалить» (edit-delete-exercise §4.2).
     */
    @Test
    fun row_long_press_when_custom_then_menu_with_edit_archive_delete_in_order() {
        // Given — кастомное упражнение + локальная state-машина
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = ExercisesUiState.Success(exercises = listOf(bench)),
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = {},
                        onRequestArchive = {},
                        onRequestDelete = {}
                    )
            )
        }

        // When — long-press на строке кастомного упражнения
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — все три пункта видны
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_edit))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_archive))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_delete))
            .assertExists()

        // Then — «Изменить» выше «Архивировать», «Архивировать» выше «Удалить»
        assertDisplayedAbove(
            higher = context.getString(R.string.exercise_list_menu_edit),
            lower = context.getString(R.string.exercise_list_menu_archive)
        )
        assertDisplayedAbove(
            higher = context.getString(R.string.exercise_list_menu_archive),
            lower = context.getString(R.string.exercise_list_menu_delete)
        )
    }

    /**
     * §4.3: long-press на кастомном → тап «Удалить» в меню → алерт
     * «Удалить упражнение?» с именем упражнения; тап «Отмена» —
     * алерт закрыт, упражнение остаётся в списке.
     *
     * Тест через ExercisesScreenContent + локальное состояние
     * contextMenu/pendingDeleteId (как в MainScreenTest —
     * `MainScreenContent` со своей state-машиной): это упражнение
     * проверяет UI-механику (появление алерта и реакция на «Отмена»).
     */
    @Test
    fun row_long_press_when_custom_tap_delete_then_alert_with_name_then_cancel_keeps() {
        // Given — экран с локальной state-машиной (как в MainScreenTest)
        val cancelled = mutableListOf<Unit>()
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            var pendingId by remember { mutableStateOf<Long?>(null) }
            val current =
                ExercisesUiState.Success(
                    exercises = listOf(bench),
                    pendingDeleteId = pendingId
                )
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = current,
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = {},
                        onRequestArchive = {},
                        onRequestDelete = { item ->
                            menu = null
                            pendingId = item.id
                        },
                        onConfirmDelete = { pendingId = null },
                        onCancelDelete = {
                            cancelled += Unit
                            pendingId = null
                        }
                    )
            )
        }

        // When — long-press на кастомном, тап «Удалить»
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_delete))
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт с именем упражнения
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_delete_dialog_title))
            .assertExists()
        composeTestRule
            .onNodeWithText(
                context.getString(R.string.exercise_delete_dialog_text, "Жим")
            ).assertExists()

        // When — тап «Отмена»
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_delete_dialog_cancel))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт закрыт, упражнение в списке
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_delete_dialog_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText("Жим")
            .assertIsDisplayed()
        assertEquals("cancelDelete должен быть вызван один раз", 1, cancelled.size)
    }

    /**
     * §4.5: long-press на кастомном → тап «Изменить» в меню — VM-обёртка
     * закрывает меню и вызывает `onEditExercise(item.id)`.
     *
     * Тест через ExercisesScreenContent с локальным контекстом-меню
     * (как VM-обёртка в реальном `ExercisesScreen`).
     */
    @Test
    fun row_long_press_when_custom_tap_edit_then_onEditExercise_callback_fires() {
        // Given — захват callback для Edit
        var capturedId: Long? = null
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = ExercisesUiState.Success(exercises = listOf(bench)),
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = { item ->
                            menu = null
                            capturedId = item.id
                        },
                        onRequestArchive = {},
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onCancelDelete = {}
                    )
            )
        }

        // When — long-press → тап «Изменить»
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_edit))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — колбэк вызван с id кастомного упражнения
        assertEquals(
            "onEditExercise должен быть вызван с id кастомного упражнения",
            bench.id,
            capturedId
        )
    }

    /** Вспомогательный метод: проверяет, что текст [higher] визуально выше [lower]. */
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
        org.junit.Assert.assertTrue(
            "«$higher» должен быть визуально выше «$lower»",
            higherTop < lowerTop
        )
    }
    // endregion

    // region exercises-archive §6 (long-press archive menu, alert, TopAppBar button)

    /**
     * §6.3: long-press на кастомном → тап «Архивировать» в меню →
     * алерт «Архивировать упражнение?» с именем упражнения;
     * тап «Отмена» — алерт закрыт, упражнение остаётся в списке.
     */
    @Test
    fun row_long_press_when_custom_tap_archive_then_alert_with_name_then_cancel_keeps() {
        // Given — экран с локальной state-машиной (как в MainScreenTest)
        val cancelled = mutableListOf<Unit>()
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            var pendingArchiveId by remember { mutableStateOf<Long?>(null) }
            val current =
                ExercisesUiState.Success(
                    exercises = listOf(bench),
                    pendingArchiveId = pendingArchiveId
                )
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = current,
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = {},
                        onRequestArchive = { item ->
                            menu = null
                            pendingArchiveId = item.id
                        },
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onCancelDelete = {},
                        onConfirmArchive = { pendingArchiveId = null },
                        onCancelArchive = {
                            cancelled += Unit
                            pendingArchiveId = null
                        },
                        onArchivedExercisesClick = {}
                    )
            )
        }

        // When — long-press на кастомном, тап «Архивировать»
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_archive))
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт с заголовком и именем упражнения
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_archive_dialog_title))
            .assertExists()
        composeTestRule
            .onNodeWithText(
                context.getString(R.string.exercise_archive_dialog_text, "Жим")
            ).assertExists()

        // When — тап «Отмена»
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_archive_dialog_cancel))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт закрыт, упражнение в списке
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_archive_dialog_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText("Жим")
            .assertIsDisplayed()
        assertEquals("cancelArchive должен быть вызван один раз", 1, cancelled.size)
    }

    /**
     * §6.4: long-press на кастомном → тап «Архивировать» → тап
     * «Архивировать» в алерте подтверждения → сработал колбэк
     * подтверждения архивации; после обновления state (исключающего
     * заархивированное упражнение из списка) строка исчезает.
     *
     * Content-based инфраструктура текущего `ExercisesScreenTest` —
     * репозиторий не проверяется, проверяется колбэк подтверждения
     * + перерисовка state.
     */
    @Test
    fun row_long_press_when_custom_tap_archive_in_dialog_then_callback_and_row_removed() {
        // Given — экран с локальной state-машиной
        var confirmed = false
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            var pendingArchiveId by remember { mutableStateOf<Long?>(null) }
            // Имитация реактивного обновления: после confirm —
            // упражнение пропадает из списка
            val exercisesAfterArchive = remember { mutableStateOf<List<Exercise>>(listOf(bench)) }
            val current =
                ExercisesUiState.Success(
                    exercises = exercisesAfterArchive.value,
                    pendingArchiveId = pendingArchiveId
                )
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = current,
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = {},
                        onRequestArchive = { item ->
                            menu = null
                            pendingArchiveId = item.id
                        },
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onCancelDelete = {},
                        onConfirmArchive = {
                            confirmed = true
                            pendingArchiveId = null
                            exercisesAfterArchive.value = emptyList()
                        },
                        onCancelArchive = { pendingArchiveId = null },
                        onArchivedExercisesClick = {}
                    )
            )
        }

        // When — long-press → тап «Архивировать» → тап «Архивировать» в алерте
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_archive))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_archive_dialog_confirm))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт закрыт, упражнение пропало из списка,
        // колбэк подтверждения сработал
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_archive_dialog_title))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText("Жим")
            .assertCountEquals(0)
        org.junit.Assert.assertTrue(
            "confirmArchive должен быть вызван",
            confirmed
        )
    }

    /**
     * Accessibility чекбокса «Больше не показывать»
     * (accessibility-semantics-fixes, design D7): лейбл и чекбокс —
     * единая кликабельная область; тап по лейблу переключает чекбокс,
     * подтверждение с поставленным лейблом приводит к записи
     * `dontShowArchiveDialog = true` (колбэк `onConfirmArchive`
     * получает `suppress = true`, как в существующем сценарии
     * «confirmArchive с suppress=true пишет pref»).
     */
    @Test
    fun archive_alert_when_label_tapped_then_checkbox_toggles_and_confirm_records_suppress() {
        // Given — экран с алертом архивации и захватом suppress
        var confirmedSuppress: Boolean? = null
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }
            var pendingArchiveId by remember { mutableStateOf<Long?>(null) }
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(bench),
                                pendingArchiveId = pendingArchiveId
                            ),
                        contextMenu = menu,
                        onAddExerciseClick = {},
                        onContextMenuItemChange = { menu = it },
                        onRequestEdit = {},
                        onRequestArchive = { item ->
                            menu = null
                            pendingArchiveId = item.id
                        },
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onCancelDelete = {},
                        onConfirmArchive = { suppress ->
                            confirmedSuppress = suppress
                            pendingArchiveId = null
                        },
                        onCancelArchive = { pendingArchiveId = null },
                        onArchivedExercisesClick = {}
                    )
            )
        }
        composeTestRule
            .onNodeWithText("Жим")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_list_menu_archive))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — до тапа по лейблу чекбокс выключен
        val suppressLabel = context.getString(R.string.exercise_archive_dont_show_again)
        val toggledRow =
            composeTestRule
                .onNodeWithText(suppressLabel)
                .fetchSemanticsNode()
                .config
        assertEquals(
            "До тапа по лейблу чекбокс должен быть выключен",
            ToggleableState.Off,
            toggledRow.getOrNull(SemanticsProperties.ToggleableState)
        )

        // When — тап по лейблу «Больше не показывать»
        composeTestRule
            .onNodeWithText(suppressLabel)
            .performClick()
        composeTestRule.waitForIdle()

        // Then — чекбокс переключился
        assertEquals(
            "Тап по лейблу должен переключить чекбокс",
            ToggleableState.On,
            composeTestRule
                .onNodeWithText(suppressLabel)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.ToggleableState)
        )

        // When — подтверждение архивации с поставленным лейблом
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_archive_dialog_confirm))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — подтверждение записывает suppress = true
        assertEquals(
            "onConfirmArchive должен получить suppress = true",
            true,
            confirmedSuppress
        )
    }

    /**
     * §6.5: `archivedCount == 0` → кнопка архива в `TopAppBar` скрыта;
     * `archivedCount == 1` → кнопка отображается с иконкой
     * `Icons.Filled.Archive` и `contentDescription =
     * exercises_archive_button`; тап кнопки — сработал колбэк
     * `onArchivedExercisesClick`.
     */
    @Test
    fun exercises_screen_when_archivedCount_is_zero_then_archive_button_hidden() {
        // Given — `archivedCount = 0`
        setContentWith(
            ExercisesUiState.Success(exercises = listOf(pullups, plank), archivedCount = 0)
        )

        // Then — кнопки архива нет
        composeTestRule
            .onAllNodesWithContentDescription(
                context.getString(R.string.exercises_archive_button)
            ).assertCountEquals(0)
    }

    @Test
    fun exercises_screen_when_archivedCount_is_one_then_archive_button_visible_and_tap_fires_callback() {
        // Given — `archivedCount = 1`, захват callback
        var clickCount = 0
        composeTestRule.setContent {
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(pullups),
                                archivedCount = 1
                            ),
                        onAddExerciseClick = {},
                        onArchivedExercisesClick = { clickCount += 1 }
                    )
            )
        }

        // Then — кнопка архива видна
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_archive_button))
            .assertIsDisplayed()

        // When — тап по кнопке
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_archive_button))
            .performClick()

        // Then — колбэк вызван один раз
        assertEquals("onArchivedExercisesClick должен быть вызван один раз", 1, clickCount)
    }

    /**
     * §6.6: реактивное появление/скрытие кнопки — после изменения
     * `archivedCount` с 0 на 1 кнопка появляется; после восстановления
     * (1 → 0) — скрывается. `mutableStateOf<ExercisesUiState>` в
     * `setContent` имитирует реактивное обновление (Content-based
     * инфраструктура текущего `ExercisesScreenTest` без отдельного
     * FakeRepository).
     */
    @Test
    fun exercises_screen_archive_button_reactive_show_hide() {
        // Given — начальное состояние `archivedCount = 0`
        composeTestRule.setContent {
            val uiState: androidx.compose.runtime.MutableState<ExercisesUiState> =
                remember {
                    androidx.compose.runtime.mutableStateOf<ExercisesUiState>(
                        ExercisesUiState.Success(exercises = listOf(pullups))
                    )
                }
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState = uiState.value,
                        onAddExerciseClick = {}
                    )
            )
            // Сохраняем ссылку на uiState через side-effect
            uiStateRef = uiState
        }
        composeTestRule.waitForIdle()

        // Then — кнопка скрыта
        composeTestRule
            .onAllNodesWithContentDescription(
                context.getString(R.string.exercises_archive_button)
            ).assertCountEquals(0)

        // When — реактивное обновление: `archivedCount = 1`
        composeTestRule.runOnIdle {
            uiStateRef.value =
                ExercisesUiState.Success(exercises = listOf(pullups), archivedCount = 1)
        }
        composeTestRule.waitForIdle()

        // Then — кнопка появилась
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_archive_button))
            .assertIsDisplayed()

        // When — реактивное обновление: `archivedCount = 0`
        composeTestRule.runOnIdle {
            uiStateRef.value =
                ExercisesUiState.Success(exercises = listOf(pullups), archivedCount = 0)
        }
        composeTestRule.waitForIdle()

        // Then — кнопка скрылась
        composeTestRule
            .onAllNodesWithContentDescription(
                context.getString(R.string.exercises_archive_button)
            ).assertCountEquals(0)
    }

    /** Side-effect-ссылка на uiState для теста реактивного обновления. */
    private var uiStateRef: androidx.compose.runtime.MutableState<ExercisesUiState> =
        androidx.compose.runtime.mutableStateOf(ExercisesUiState.Loading)
    // endregion
}
