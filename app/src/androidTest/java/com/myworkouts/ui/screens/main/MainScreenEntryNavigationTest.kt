package com.myworkouts.ui.screens.main

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.crash.NoopCrashReporter
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.screens.entrydetail.EntryDetailScreen
import com.myworkouts.ui.viewmodel.EntryDetailViewModel
import com.myworkouts.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Навигационные тесты главного экрана и экрана деталей записи
 * (workout-entry-detail-screen, tasks §5.1): переписанный набор
 * `MainScreenDetailCardTest` — тап и a11y-действие «Показать детали»
 * открывают `entry_detail/{entryId}`, системный Back возвращает на
 * главный экран, long-press по-прежнему открывает меню (не экран),
 * удаление с экрана деталей возвращает на главный, актуализация данных
 * экрана при мутации БД, поведение меню при тапе/a11y на другой строке.
 *
 * Выделение строки (`Selected`) больше не проверяется: карточка-попап
 * снесена, `isSelected` выставляется только при открытом меню
 * (workout-detail-view, design Decision 7).
 *
 * Harness — NavHost по образцу [MainScreenEditNavigationTest]:
 * реальный `MainScreen` + `EntryDetailScreen` на fake-репозитории
 * (`MutableStateFlow`), маршруты регистрируются как в `RootScreen`.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenEntryNavigationTest {
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
            weightKg = 5.0,
            comment = null,
            date = 1_721_000_000_000L
        )

    /** Запись на время — вторая строка для сценариев с открытым меню. */
    private val timeItem =
        EntryListItem(
            id = 2L,
            exerciseName = "Планка",
            sets = 3,
            reps = null,
            durationSec = 20,
            weightKg = null,
            comment = null,
            date = 1_721_100_000_000L
        )

    /**
     * Ожидаемая строка значения на экране деталей: «4 х 10 (+ 5 кг)» /
     * «3 х 20 сек» (тот же формат, что и в строке списка).
     */
    private fun expectedDetailsValue(item: EntryListItem): String {
        val value =
            item.reps?.toString()
                ?: "${item.durationSec} ${context.getString(R.string.entry_duration_seconds_short)}"
        val separator = context.getString(R.string.entry_value_separator)
        val setsPrefix = if (item.sets > 1) "${item.sets}$separator" else ""
        val weightSuffix =
            item.weightKg?.takeIf { it > 0.0 }?.let {
                " " +
                    context.getString(
                        R.string.entry_details_weight_suffix,
                        it.toString().removeSuffix(".0")
                    )
            } ?: ""
        return "$setsPrefix$value$weightSuffix"
    }

    // ===== Harness =====

    /**
     * NavHost с реальными [MainScreen] и [EntryDetailScreen] — обёртка
     * повторяет регистрацию `RootScreen`: тап/a11y/«Открыть» ведут на
     * `Screen.EntryDetail.createRoute(entryId)`, Back со деталей —
     * `popBackStack`, «Изменить» не используется в этих сценариях.
     */
    private fun setContentWithNavHost(
        repository: WorkoutDataRepository,
        onControllerReady: (NavHostController) -> Unit
    ) {
        composeTestRule.setContent {
            val navController = rememberNavController()
            onControllerReady(navController)
            NavHost(navController = navController, startDestination = Screen.Main.route) {
                composable(Screen.Main.route) {
                    MainScreen(
                        viewModel =
                            viewModel(
                                factory =
                                    MainViewModel.factory(
                                        repository,
                                        NoopAnalyticsProvider(),
                                        NoopCrashReporter()
                                    )
                            ),
                        onAddEntryClick = {},
                        analyticsProvider = NoopAnalyticsProvider(),
                        onOpenEntryClick = { entryId ->
                            navController.navigate(Screen.EntryDetail.createRoute(entryId))
                        }
                    )
                }
                composable(
                    Screen.EntryDetail.route,
                    arguments = Screen.EntryDetail.arguments
                ) { backStackEntry ->
                    val entryId =
                        backStackEntry.arguments?.getLong(Screen.EntryDetail.ARG_ENTRY_ID) ?: 0L
                    EntryDetailScreen(
                        viewModel =
                            viewModel(
                                factory =
                                    EntryDetailViewModel.factory(
                                        repository,
                                        NoopAnalyticsProvider(),
                                        NoopCrashReporter(),
                                        entryId
                                    )
                            ),
                        onBackClick = { navController.popBackStack() },
                        onEditClick = {}
                    )
                }
            }
        }
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText(context.getString(R.string.entry_list_empty))
                .fetchSemanticsNodes()
                .isNotEmpty() ||
                composeTestRule
                    .onAllNodesWithText(repsItem.exerciseName)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
    }

    /**
     * Fake-репозиторий на [MutableStateFlow]: список записей — реактивный
     * (главный экран и `entryById`-проекция читают один источник), удаление
     * реально убирает запись из списка. Остальные методы — заглушки.
     *
     * [entriesFlow] открыт для мутаций из теста (актуализация данных экрана
     * деталей при изменении записи в БД).
     */
    private class FakeRepository(
        initialEntries: List<EntryListItem>
    ) : WorkoutDataRepository {
        val entriesFlow = MutableStateFlow(initialEntries)

        /** Id записей, переданные в [deleteEntry]. */
        val deletedIds = mutableListOf<Long>()

        /** Мутация записи в БД (аналог `updateEntry` на уровне проекции). */
        fun upsertEntry(item: EntryListItem) {
            entriesFlow.value =
                entriesFlow.value.filterNot { it.id == item.id } + item
        }

        override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

        override fun entries(): Flow<List<EntryListItem>> = entriesFlow

        override fun entryById(id: Long): Flow<EntryListItem?> =
            entriesFlow.map { list -> list.firstOrNull { it.id == id } }

        override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

        override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = error("Не используется в этом тесте")

        override suspend fun addExercise(exercise: Exercise): Result<Long> = error("Не используется в этом тесте")

        override suspend fun updateExercise(exercise: Exercise): Result<Unit> = error("Не используется в этом тесте")

        override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = error("Не используется в этом тесте")

        override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = error("Не используется в этом тесте")

        override suspend fun deleteEntry(id: Long): Result<Unit> {
            deletedIds.add(id)
            entriesFlow.value = entriesFlow.value.filterNot { it.id == id }
            return Result.success(Unit)
        }

        override suspend fun deleteExercise(id: Long): Result<Unit> = error("Не используется в этом тесте")

        override suspend fun getExercise(id: Long): Result<Exercise?> = error("Не используется в этом тесте")

        override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

        override suspend fun getAllExercises(): Result<List<Exercise>> = error("Не используется в этом тесте")

        override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = error("Не используется в этом тесте")

        override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> =
            error("Не используется в этом тесте")

        override suspend fun importData(
            categories: List<Category>,
            exercises: List<Exercise>,
            entries: List<BackupEntry>
        ): Result<Unit> = error("Не используется в этом тесте")

        override suspend fun deleteAllData(): Result<Unit> = error("Не используется в этом тесте")

        override suspend fun setArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

        override fun hasArchived(): Flow<Boolean> = MutableStateFlow(false)

        override suspend fun addCategory(
            name: String,
            colorArgb: Int
        ): Result<Category> =
            Result.success(Category(id = 0L, name = name.trim(), colorArgb = colorArgb, isCustom = true))

        override suspend fun updateCategory(
            id: Long,
            newName: String,
            colorArgb: Int
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteCategory(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun setCategoryArchived(
            id: Long,
            isArchived: Boolean
        ): Result<Unit> = Result.success(Unit)

        override fun categories(): Flow<List<Category>> = MutableStateFlow(emptyList())

        override fun archivedCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())

        override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun getAllCategories(): Result<List<Category>> = Result.success(emptyList())
    }

    // ===== Tests =====

    /**
     * 5.1. Тап по строке открывает экран деталей `entry_detail/{entryId}`:
     * маршрут с аргументом id записи, заголовок «Детали записи», значение
     * «4 х 10 (+ 5 кг)», контекстного меню нет. Проверки `Selected` нет —
     * выделение от деталей устранено (design Decision 7).
     */
    @Test
    fun main_when_row_tapped_then_opens_entry_detail_screen() {
        lateinit var navController: NavHostController
        setContentWithNavHost(
            repository = FakeRepository(listOf(repsItem)),
            onControllerReady = { navController = it }
        )

        // When — тап по строке (detectTapGestures не реагирует на
        // performClick — используется собственный click)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Then — маршрут `entry_detail/{entryId}` с аргументом id записи
        assertEquals(Screen.EntryDetail.route, navController.currentDestination?.route)
        assertEquals(
            repsItem.id,
            checkNotNull(
                navController
                    .currentBackStackEntry
                    ?.arguments
                    ?.getLong(Screen.EntryDetail.ARG_ENTRY_ID)
            )
        )

        // Then — заголовок и значение деталей отображаются
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedDetailsValue(repsItem))
            .assertIsDisplayed()

        // Then — контекстного меню нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_delete))
            .assertCountEquals(0)
    }

    /**
     * 5.1. A11y-действие «Показать детали» (workout-detail-view, design
     * Decision 4) открывает экран деталей.
     */
    @Test
    fun main_when_a11y_show_details_invoked_then_opens_entry_detail_screen() {
        lateinit var navController: NavHostController
        setContentWithNavHost(
            repository = FakeRepository(listOf(repsItem)),
            onControllerReady = { navController = it }
        )

        // Поиск custom-действия «Показать детали» на строке
        val rowNode =
            composeTestRule.onNodeWithText(repsItem.exerciseName).fetchSemanticsNode()
        val showDetailsAction =
            rowNode
                .config
                .getOrNull(SemanticsActions.CustomActions)
                ?.firstOrNull { it.label == context.getString(R.string.a11y_show_details) }
        assertNotNull(
            "Строка должна предоставлять custom-действие «Показать детали»",
            showDetailsAction
        )

        // When — активация действия (как из меню действий TalkBack);
        // navigate() внутри — на главном потоке, иначе гон lifecycle
        composeTestRule.runOnUiThread { checkNotNull(showDetailsAction).action.invoke() }
        composeTestRule.waitForIdle()

        // Then — открыт экран деталей для этой записи
        assertEquals(Screen.EntryDetail.route, navController.currentDestination?.route)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedDetailsValue(repsItem))
            .assertIsDisplayed()
    }

    /**
     * 5.1. Системный Back на экране деталей возвращает на главный экран
     * (канон — `ExerciseCreateScreenTest`
     * .edit_when_no_changes_and_system_back_then_closed_without_dialog).
     */
    @Test
    fun entry_detail_when_back_pressed_then_returns_to_main() {
        lateinit var navController: NavHostController
        setContentWithNavHost(
            repository = FakeRepository(listOf(repsItem)),
            onControllerReady = { navController = it }
        )
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()

        // When — системный Back
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Then — главный экран, строка записи видна
        assertEquals(Screen.Main.route, navController.currentDestination?.route)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assertIsDisplayed()
    }

    /**
     * 5.1. Регресс: long-press по строке открывает контекстное меню
     * («Открыть» / «Изменить» / «Удалить»), а не экран деталей.
     */
    @Test
    fun main_long_press_when_invoked_then_opens_menu_not_detail() {
        lateinit var navController: NavHostController
        setContentWithNavHost(
            repository = FakeRepository(listOf(repsItem)),
            onControllerReady = { navController = it }
        )

        // When — long-press по строке
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — три пункта меню отображаются
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_view))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .assertIsDisplayed()

        // Then — экран деталей не открылся
        assertEquals(Screen.Main.route, navController.currentDestination?.route)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_detail_title))
            .assertCountEquals(0)
    }

    /**
     * 5.1. Пункт «Открыть» контекстного меню открывает экран деталей —
     * тот же канал, что тап (design Decision 7): маршрут `entry_detail/
     * {entryId}` с аргументом id записи, заголовок «Детали записи».
     */
    @Test
    fun main_when_menu_view_clicked_then_opens_entry_detail_screen() {
        lateinit var navController: NavHostController
        setContentWithNavHost(
            repository = FakeRepository(listOf(repsItem)),
            onControllerReady = { navController = it }
        )

        // When — long-press открывает меню, клик по пункту «Открыть»
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_view))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — маршрут `entry_detail/{entryId}` с аргументом id записи
        assertEquals(Screen.EntryDetail.route, navController.currentDestination?.route)
        assertEquals(
            repsItem.id,
            checkNotNull(
                navController
                    .currentBackStackEntry
                    ?.arguments
                    ?.getLong(Screen.EntryDetail.ARG_ENTRY_ID)
            )
        )

        // Then — заголовок деталей отображается
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
    }

    /**
     * 5.1. Удаление с экрана деталей: «Удалить» в TopAppBar → алерт →
     * подтверждение → запись удалена из БД, возврат на главный экран.
     */
    @Test
    fun entry_detail_when_delete_confirmed_then_entry_deleted_and_back_on_main() {
        val repository = FakeRepository(listOf(repsItem))
        lateinit var navController: NavHostController
        setContentWithNavHost(
            repository = repository,
            onControllerReady = { navController = it }
        )
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()

        // When — «Удалить» в TopAppBar → алерт подтверждения
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_delete_dialog_title))
            .assertIsDisplayed()

        // When — подтверждение «Удалить» в алерте
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — запись удалена из БД
        assertEquals(listOf(repsItem.id), repository.deletedIds)

        // Then — возврат на главный экран, список пуст
        assertEquals(Screen.Main.route, navController.currentDestination?.route)
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_empty))
            .assertIsDisplayed()
    }

    /**
     * 5.1. Изменение записи в БД при открытом экране деталей — экран
     * отображает актуальные данные (реактивная проекция `entryById`).
     * Выделения строки нет — деталей-попапа больше не существует
     * (design Decision 7).
     */
    @Test
    fun entry_detail_when_entry_updated_then_data_refreshed() {
        lateinit var navController: NavHostController
        val repository = FakeRepository(listOf(repsItem))
        setContentWithNavHost(
            repository = repository,
            onControllerReady = { navController = it }
        )
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_detail_title))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("обновлённый комментарий")
            .assertCountEquals(0)

        // When — мутация списка (запись с тем же id получает комментарий):
        // `entryById` — производный Flow от того же MutableStateFlow
        repository.upsertEntry(repsItem.copy(comment = "обновлённый комментарий"))
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText("обновлённый комментарий")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Then — актуальные данные на экране деталей
        composeTestRule
            .onNodeWithText("обновлённый комментарий")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedDetailsValue(repsItem))
            .assertIsDisplayed()

        // Then — ни одна нода в дереве не выделена (`Selected` только от меню)
        assertTrue(
            "На экране деталей не должно быть выделенных нод",
            composeTestRule
                .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    /**
     * 5.1. Тап по другой строке при открытом меню — закрывает только меню,
     * экран деталей не открывается (popup поглощает тап, design
     * Risks/Trade-offs).
     */
    @Test
    fun menu_when_tap_other_row_then_only_menu_closes() {
        lateinit var navController: NavHostController
        setContentWithNavHost(
            repository = FakeRepository(listOf(repsItem, timeItem)),
            onControllerReady = { navController = it }
        )

        // Given — long-press по первой строке, меню открыто
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()

        // When — тап по второй строке
        composeTestRule
            .onNodeWithText(timeItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Then — меню закрыто, экран деталей не открылся
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_edit))
            .assertCountEquals(0)
        assertEquals(Screen.Main.route, navController.currentDestination?.route)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_detail_title))
            .assertCountEquals(0)

        // When — повторный тап по второй строке (меню уже закрыто)
        composeTestRule
            .onNodeWithText(timeItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Then — открыт экран деталей второй записи
        assertEquals(Screen.EntryDetail.route, navController.currentDestination?.route)
        assertEquals(
            timeItem.id,
            checkNotNull(
                navController
                    .currentBackStackEntry
                    ?.arguments
                    ?.getLong(Screen.EntryDetail.ARG_ENTRY_ID)
            )
        )
        composeTestRule
            .onNodeWithText(expectedDetailsValue(timeItem))
            .assertIsDisplayed()
    }

    /**
     * 5.1. A11y-действие «Показать детали» на другой строке при открытом
     * меню — закрывает меню и открывает экран деталей (a11y идёт мимо
     * popup).
     */
    @Test
    fun menu_when_a11y_show_details_other_row_then_closes_menu_and_opens_detail() {
        lateinit var navController: NavHostController
        setContentWithNavHost(
            repository = FakeRepository(listOf(repsItem, timeItem)),
            onControllerReady = { navController = it }
        )

        // Given — long-press по первой строке, меню открыто
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()

        // Поиск custom-действия «Показать детали» на второй строке
        val rowNode =
            composeTestRule.onNodeWithText(timeItem.exerciseName).fetchSemanticsNode()
        val showDetailsAction =
            rowNode
                .config
                .getOrNull(SemanticsActions.CustomActions)
                ?.firstOrNull { it.label == context.getString(R.string.a11y_show_details) }
        assertNotNull(
            "Строка должна предоставлять custom-действие «Показать детали»",
            showDetailsAction
        )

        // When — активация действия (a11y идёт мимо popup); navigate()
        // внутри — на главном потоке, иначе гон lifecycle
        composeTestRule.runOnUiThread { checkNotNull(showDetailsAction).action.invoke() }
        composeTestRule.waitForIdle()

        // Then — меню закрыто, открыт экран деталей второй записи
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_edit))
            .assertCountEquals(0)
        assertEquals(Screen.EntryDetail.route, navController.currentDestination?.route)
        assertEquals(
            timeItem.id,
            checkNotNull(
                navController
                    .currentBackStackEntry
                    ?.arguments
                    ?.getLong(Screen.EntryDetail.ARG_ENTRY_ID)
            )
        )
        composeTestRule
            .onNodeWithText(expectedDetailsValue(timeItem))
            .assertIsDisplayed()
    }

    private companion object {
        /** Таймаут ожидания появления нод, мс. */
        const val TIMEOUT_MS: Long = 5_000L
    }
}
