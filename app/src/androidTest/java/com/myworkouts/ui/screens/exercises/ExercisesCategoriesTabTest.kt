package com.myworkouts.ui.screens.exercises

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.state.ExercisesTab
import com.myworkouts.ui.state.ExercisesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-тесты вкладки «Категории» экрана «Упражнения» (custom-categories,
 * tasks 5.4/5.5): таб-бар «Упражнения / Категории», список и empty-state,
 * контекстное меню по типу категории, алерты удаления/архивации,
 * contentDescription FAB по вкладке, навигация FAB → `CategoryCreate`,
 * переживание поворота выбранной вкладки.
 *
 * Контентные тесты — через [ExercisesScreenContent] с готовым
 * [ExercisesScreenState] (без ViewModel, образец — `ExercisesScreenTest`);
 * переживание поворота вкладки — отдельный класс
 * `ExercisesCategoriesTabRecreateTest` на реальной `MainActivity`
 * (см. задокументированный gotcha recreate + ComponentActivity).
 */
@RunWith(AndroidJUnit4::class)
class ExercisesCategoriesTabTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val pullups =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = "PULL",
            isCustom = false,
            comment = null,
            createdAt = 1_721_000_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    /** Стандартная категория (локализованный label — «Толкающие» в ru). */
    private val push =
        Category(
            id = 1L,
            name = "PUSH",
            colorArgb = 0xFF4E6EF2.toInt(),
            isCustom = false
        )

    /** Кастомная категория (имя как есть). */
    private val shoulders =
        Category(
            id = 2L,
            name = "Плечи",
            colorArgb = 0xFF22AA66.toInt(),
            isCustom = true
        )

    // ---------- Таб-бар и переключение содержимого ----------

    /**
     * Spec «Переключение вкладок меняет содержимое»: по умолчанию
     * активна «Упражнения» (список каталога); тап «Категории» — тело
     * показывает список категорий, `TopAppBar` остаётся «Упражнения»;
     * тап «Упражнения» возвращает каталог.
     */
    @Test
    fun tabs_whenSwitched_thenBodyContentChanges_andTitleStays() {
        // Given
        composeTestRule.setContent {
            var tab by remember { mutableStateOf(ExercisesTab.EXERCISES) }
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(pullups),
                                categories = listOf(push, shoulders),
                                selectedTab = tab
                            ),
                        onTabSelect = { tab = it }
                    )
            )
        }

        // Then — по умолчанию каталог
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()

        // When — тап вкладки «Категории» (matcher с hasClickAction:
        // текст «Упражнения» дублируется в TopAppBar, «Категории» — нет,
        // но для единообразия кликаем по кликабельному узлу таба)
        composeTestRule
            .onNode(hasText(context.getString(R.string.exercises_tab_categories)) and hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()

        // Then — список категорий вместо каталога; заголовок не меняется
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_category_push))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Плечи")
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("Подтягивания")
            .assertCountEquals(0)
        // «Упражнения» — два узла (TopAppBar + вкладка): заголовок
        // остаётся на месте независимо от вкладки (spec).
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercises_title))[0]
            .assertIsDisplayed()

        // When — возврат на «Упражнения» (кликабельный узел таба —
        // текст «Упражнения» есть и в TopAppBar)
        composeTestRule
            .onNode(hasText(context.getString(R.string.exercises_tab_exercises)) and hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()

        // Then
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_category_push))
            .assertCountEquals(0)
    }

    /** Spec «Все категории заархивированы — заглушка»: «Нет категорий». */
    @Test
    fun categoriesTab_whenEmpty_thenShowsEmptyState() {
        // Given
        composeTestRule.setContent {
            ExercisesScreenContent(
                state =
                    ExercisesScreenState(
                        uiState =
                            ExercisesUiState.Success(
                                exercises = listOf(pullups),
                                categories = emptyList(),
                                selectedTab = ExercisesTab.CATEGORIES
                            )
                    )
            )
        }

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.categories_list_empty))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.exercise_category_push))
            .assertCountEquals(0)
    }

    // ---------- Контекстное меню по типу категории ----------

    /** Рендер вкладки категорий с локальной state-машиной контекст-меню. */
    @Composable
    private fun CategoriesTabWithMenuMachine(
        categories: List<Category>,
        onCancelCategoryDelete: () -> Unit = {}
    ) {
        // Локальная state-машина (как в ExercisesScreenTest): меню +
        // pending-подтверждения, имитирующие ViewModel.
        var menu by remember { mutableStateOf<CategoryContextMenuState?>(null) }
        var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
        var pendingArchiveId by remember { mutableStateOf<Long?>(null) }
        ExercisesScreenContent(
            state =
                ExercisesScreenState(
                    uiState =
                        ExercisesUiState.Success(
                            exercises = listOf(pullups),
                            categories = categories,
                            selectedTab = ExercisesTab.CATEGORIES,
                            pendingCategoryDeleteId = pendingDeleteId,
                            pendingCategoryArchiveId = pendingArchiveId
                        ),
                    categoryContextMenu = menu,
                    onCategoryContextMenuItemChange = { menu = it },
                    onRequestCategoryEdit = {},
                    onRequestCategoryArchive = { category ->
                        menu = null
                        pendingArchiveId = category.id
                    },
                    onRequestCategoryDelete = { category ->
                        menu = null
                        pendingDeleteId = category.id
                    },
                    onConfirmCategoryDelete = { pendingDeleteId = null },
                    onCancelCategoryDelete = {
                        onCancelCategoryDelete()
                        pendingDeleteId = null
                    },
                    onConfirmCategoryArchive = { pendingArchiveId = null },
                    onCancelCategoryArchive = { pendingArchiveId = null }
                )
        )
    }

    /**
     * Spec «Long-press стандартной категории — один пункт»: только
     * «Архивировать»; «Изменить» и «Удалить» НЕ показываются.
     */
    @Test
    fun categoryRow_longPress_whenStandard_thenMenuWithArchiveOnly() {
        // Given
        composeTestRule.setContent {
            CategoriesTabWithMenuMachine(categories = listOf(push, shoulders))
        }

        // When — long-press на строке стандартной категории
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_category_push))
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — только «Архивировать»
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_list_menu_archive))
            .assertExists()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_delete))
            .assertCountEquals(0)
    }

    /**
     * Spec «Long-press кастомной категории — три пункта»: «Изменить» /
     * «Архивировать» / «Удалить» в указанном порядке.
     */
    @Test
    fun categoryRow_longPress_whenCustom_thenMenuWithEditArchiveDeleteInOrder() {
        // Given
        composeTestRule.setContent {
            CategoriesTabWithMenuMachine(categories = listOf(push, shoulders))
        }

        // When — long-press на строке кастомной категории
        composeTestRule
            .onNodeWithText("Плечи")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — все три пункта видны и в порядке Изменить → Архивировать → Удалить
        val editTop =
            composeTestRule
                .onNodeWithText(context.getString(R.string.category_list_menu_edit))
                .fetchSemanticsNode()
                .boundsInRoot.top
        val archiveTop =
            composeTestRule
                .onNodeWithText(context.getString(R.string.category_list_menu_archive))
                .fetchSemanticsNode()
                .boundsInRoot.top
        val deleteTop =
            composeTestRule
                .onNodeWithText(context.getString(R.string.category_list_menu_delete))
                .fetchSemanticsNode()
                .boundsInRoot.top
        org.junit.Assert.assertTrue(
            "«Изменить» должен быть выше «Архивировать»",
            editTop < archiveTop
        )
        org.junit.Assert.assertTrue(
            "«Архивировать» должен быть выше «Удалить»",
            archiveTop < deleteTop
        )
    }

    /**
     * Spec «Неиспользуемая категория — алерт с именем»: тап «Удалить»
     * открывает алерт с отображаемым именем; «Отмена» закрывает, строка
     * остаётся.
     */
    @Test
    fun categoryRow_longPress_whenCustom_tapDelete_thenAlertWithName_thenCancelKeeps() {
        // Given
        var cancelled = 0
        composeTestRule.setContent {
            CategoriesTabWithMenuMachine(
                categories = listOf(push, shoulders),
                onCancelCategoryDelete = { cancelled += 1 }
            )
        }
        composeTestRule
            .onNodeWithText("Плечи")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // When — тап «Удалить» в меню
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт с именем категории
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_delete_dialog_title))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_delete_dialog_text, "Плечи"))
            .assertExists()

        // When — тап «Отмена»
        composeTestRule
            .onNodeWithText(context.getString(R.string.cancel))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт закрыт, категория в списке
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_delete_dialog_title))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText("Плечи")
            .assertIsDisplayed()
        assertEquals("cancelCategoryDelete должен быть вызван один раз", 1, cancelled)
    }

    /**
     * Spec «Тап „Архивировать“ открывает алерт с именем категории»:
     * алерт с чекбоксом «Больше не показывать» между текстом и кнопками.
     */
    @Test
    fun categoryRow_longPress_whenCustom_tapArchive_thenAlertWithSuppressCheckbox() {
        // Given
        composeTestRule.setContent {
            CategoriesTabWithMenuMachine(categories = listOf(push, shoulders))
        }
        composeTestRule
            .onNodeWithText("Плечи")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // When — тап «Архивировать» в меню
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_list_menu_archive))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — алерт с заголовком, текстом и чекбоксом подавления
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_archive_dialog_title))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_archive_dialog_text, "Плечи"))
            .assertExists()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_archive_dont_show_again))
            .assertExists()

        // When — тап «Отмена»
        composeTestRule
            .onNodeWithText(context.getString(R.string.cancel))
            .performClick()
        composeTestRule.waitForIdle()

        // Then
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_archive_dialog_title))
            .assertCountEquals(0)
    }

    // ---------- Кнопка архива и FAB ----------

    /**
     * Spec «Кнопка архива в TopAppBar»: архивны только категории —
     * `hasArchived = true` — кнопка отображается с contentDescription
     * `archive_title`; в архиве пусто (`hasArchived = false`) — кнопка
     * скрыта.
     */
    @Test
    fun archiveButton_whenOnlyCategoriesArchived_thenVisible_whenEmptyHidden() {
        // Given — архивны только категории, ни одного упражнения
        // (один setContent: обновление через state-ref — повторный
        // setContent в одном тесте запрещён)
        lateinit var uiStateRef:
            androidx.compose.runtime.MutableState<ExercisesUiState>
        composeTestRule.setContent {
            val uiState =
                remember {
                    mutableStateOf<ExercisesUiState>(
                        ExercisesUiState.Success(
                            exercises = listOf(pullups),
                            categories = listOf(push),
                            hasArchived = true
                        )
                    )
                }
            uiStateRef = uiState
            ExercisesScreenContent(state = ExercisesScreenState(uiState = uiState.value))
        }

        // Then — кнопка с contentDescription archive_title видна
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.archive_title))
            .assertIsDisplayed()

        // When — архив пуст (реактивное обновление)
        composeTestRule.runOnIdle {
            uiStateRef.value =
                ExercisesUiState.Success(
                    exercises = listOf(pullups),
                    hasArchived = false
                )
        }
        composeTestRule.waitForIdle()

        // Then — кнопка скрыта
        composeTestRule
            .onAllNodesWithContentDescription(context.getString(R.string.archive_title))
            .assertCountEquals(0)
    }

    /**
     * Spec «Тап по FAB на вкладке категорий открывает
     * CategoryCreateScreen»: contentDescription FAB меняется на
     * `categories_add_category`, тап навигирует на
     * `Screen.CategoryCreate.route` (create-режим).
     */
    @Test
    fun fab_whenCategoriesTab_thenNavigatesToCategoryCreateRoute() {
        // Given — NavHost-harness с заглушками destination
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Screen.Exercises.route) {
                composable(Screen.Exercises.route) {
                    var tab by remember { mutableStateOf(ExercisesTab.EXERCISES) }
                    ExercisesScreenContent(
                        state =
                            ExercisesScreenState(
                                uiState =
                                    ExercisesUiState.Success(
                                        exercises = listOf(pullups),
                                        categories = listOf(push),
                                        selectedTab = tab
                                    ),
                                onAddExerciseClick = {
                                    navController.navigate(Screen.ExerciseCreate.createRoute())
                                },
                                onAddCategoryClick = {
                                    navController.navigate(Screen.CategoryCreate.createRoute())
                                },
                                onTabSelect = { tab = it }
                            )
                    )
                }
                composable(Screen.ExerciseCreate.route, arguments = Screen.ExerciseCreate.arguments) {
                    // Заглушка destination (fill-flow — в ExerciseCreateScreenTest).
                }
                composable(Screen.CategoryCreate.route, arguments = Screen.CategoryCreate.arguments) {
                    // Заглушка destination (форма категории — задача 6.4).
                }
            }
        }

        // When — выбор вкладки «Категории» и тап по FAB
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_tab_categories))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.categories_add_category))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — активный маршрут — форма категории
        composeTestRule.runOnIdle {
            assertEquals(
                "FAB на вкладке категорий должен навигировать на Screen.CategoryCreate.route",
                Screen.CategoryCreate.route,
                navController.currentDestination?.route
            )
        }
    }

    /**
     * Guard последней активной категории (prevent-from-archiving-all,
     * 6.2): у последней активной КАСТОМНОЙ категории в меню скрыты
     * «Архивировать» и «Удалить» — остаётся только «Изменить».
     */
    @Test
    fun categoryRow_longPress_whenLastActiveCustom_thenMenuWithEditOnly() {
        // Given — единственная (последняя активная) категория кастомная
        composeTestRule.setContent {
            CategoriesTabWithMenuMachine(categories = listOf(shoulders))
        }

        // When — long-press на строке последней активной
        composeTestRule
            .onNodeWithText("Плечи")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — «Изменить» есть, «Архивировать»/«Удалить» нет
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_list_menu_edit))
            .assertExists()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_archive))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_delete))
            .assertCountEquals(0)
    }

    /**
     * Guard последней активной категории (prevent-from-archiving-all,
     * 6.2): у последней активной СТАНДАРТНОЙ состав меню пуст —
     * long-press не открывает меню (ранний return до смены state).
     */
    @Test
    fun categoryRow_longPress_whenLastActiveStandard_thenMenuNotOpened() {
        // Given — единственная (последняя активная) категория стандартная
        composeTestRule.setContent {
            CategoriesTabWithMenuMachine(categories = listOf(push))
        }

        // When — long-press на строке последней активной
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercise_category_push))
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — меню не открылось: ни одного пункта нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_archive))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_delete))
            .assertCountEquals(0)
    }

    /**
     * Guard последней активной категории (prevent-from-archiving-all,
     * 6.2): accessibility-канал — custom-действие «Открыть меню» у
     * последней активной стандартной категории не открывает пустое меню.
     */
    @Test
    fun categoryRow_whenA11yActionInvoked_whenLastActiveStandard_thenMenuNotOpened() {
        // Given — единственная (последняя активная) категория стандартная
        composeTestRule.setContent {
            CategoriesTabWithMenuMachine(categories = listOf(push))
        }

        // When — выполнение custom-действия «Открыть меню»
        val openMenuAction =
            composeTestRule
                .onNodeWithText(context.getString(R.string.exercise_category_push))
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                ?.firstOrNull { it.label == context.getString(R.string.a11y_open_menu) }
        assertNotNull(
            "Строка категории должна предоставлять custom-действие «Открыть меню»",
            openMenuAction
        )
        checkNotNull(openMenuAction).action.invoke()
        composeTestRule.waitForIdle()

        // Then — меню не открылось: ни одного пункта нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_archive))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.category_list_menu_delete))
            .assertCountEquals(0)
    }

    /**
     * Guard последней активной категории (prevent-from-archiving-all,
     * 6.2): «Изменить» у последней активной кастомной категории
     * навигирует на edit-экран (`CategoryCreate` с id) как раньше —
     * скрытие архивационных пунктов не задело edit-канал.
     */
    @Test
    fun categoryRow_longPress_whenLastActiveCustom_tapEdit_thenNavigatesToCategoryEditRoute() {
        // Given — NavHost-harness с заглушкой destination (паттерн FAB-теста);
        // локальная state-машина контекст-меню — как в CategoriesTabWithMenuMachine
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            var menu by remember { mutableStateOf<CategoryContextMenuState?>(null) }
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Screen.Exercises.route) {
                composable(Screen.Exercises.route) {
                    ExercisesScreenContent(
                        state =
                            ExercisesScreenState(
                                uiState =
                                    ExercisesUiState.Success(
                                        exercises = listOf(pullups),
                                        categories = listOf(shoulders),
                                        selectedTab = ExercisesTab.CATEGORIES
                                    ),
                                categoryContextMenu = menu,
                                onCategoryContextMenuItemChange = { menu = it },
                                onAddExerciseClick = {},
                                onAddCategoryClick = {},
                                onRequestCategoryEdit = { category ->
                                    menu = null
                                    navController.navigate(
                                        Screen.CategoryCreate.createRoute(category.id)
                                    )
                                }
                            )
                    )
                }
                composable(Screen.CategoryCreate.route, arguments = Screen.CategoryCreate.arguments) {
                    // Заглушка destination (форма категории — вне сценария).
                }
            }
        }

        // When — long-press на последней активной и тап «Изменить»
        composeTestRule
            .onNodeWithText("Плечи")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_list_menu_edit))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — активный маршрут — edit-экран категории
        composeTestRule.runOnIdle {
            assertEquals(
                "«Изменить» у последней активной категории должен навигировать на Screen.CategoryCreate.route",
                Screen.CategoryCreate.route,
                navController.currentDestination?.route
            )
        }
    }
}
