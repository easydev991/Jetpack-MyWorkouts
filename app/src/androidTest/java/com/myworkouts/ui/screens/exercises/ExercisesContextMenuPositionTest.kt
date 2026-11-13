package com.myworkouts.ui.screens.exercises

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.crash.NoopCrashReporter
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.ExercisesViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Позиция long-press контекстного меню у нижней границы списка
 * (exercises-screen, «Жест и позиция меню»: меню SHALL располагаться
 * в точке нажатия).
 *
 * Регрессия: при long-press последней видимой строки каталога первый
 * кандидат `DropdownMenuPositionProvider` («верх меню к низу якоря +
 * offset») не проходит проверку влезания в popup-область, лестница
 * проваливается в кандидаты «низ/центр меню к верху якоря + offset» —
 * они трактуют отрицательный `offset` относительно ВЕРХА якоря, и меню
 * улетает под TopAppBar. Меню стандартного упражнения (один пункт
 * «Архивировать») низкое — кандидаты-фолбэки проходят margin-проверку;
 * поэтому репро требует именно нижнюю строку переполненного списка.
 *
 * Ассерт: верх меню в нижней половине окна. У обрезанной нижней строки
 * семантические bounds = видимая часть (крошечные), поэтому привязка
 * к границам строки была бы хрупкой; регрессия «меню у TopAppBar» даёт
 * разрыв в тысячи px и надёжно валит ассерт.
 */
@RunWith(AndroidJUnit4::class)
class ExercisesContextMenuPositionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Хостинг-паддинги над/под [ExercisesScreen] в тесте — имитация
     * RootScreen-хрома продакшена. Верхний: без хрома над списком
     * смещение якоря при регрессии «двойной учёт» мало и маскируется
     * флипом лестницы M3; с хромом первая строка уходит вниз и
     * смещённый якорь детерминированно не проходит margin-проверку.
     * Нижний: зазор контейнера до popup-области (в продакшене —
     * NavigationBar), иначе первому кандидату размещения «верх меню
     * к низу якоря» не хватает места и лестница уходит в фолбэки.
     */
    private val topChromePadding = 72.dp

    /** Нижний хостинг-паддинг — высота NavigationBar продакшена. */
    private val bottomBarPadding = 104.dp

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository

    /**
     * Двенадцать стандартных упражнений — каталог заведомо переполняет
     * вьюпорт (видимо ~9 строк), нижняя строка прижата к нижней границе.
     * Все стандартные (isCustom = false) → меню из одного пункта
     * «Архивировать» — низкое, именно оно проваливалось в фолбэк-кандидаты.
     */
    private val standardExercises =
        (1..12).map { i ->
            StandardExercise(name = "Тестовое упражнение %02d".format(i), category = "TEST")
        }

    @Before
    fun setUp() {
        // createInMemory (не «голый» inMemoryDatabaseBuilder): только он
        // вешает seed-callback с prepopulate стандартного набора — иначе
        // каталог пуст и тесту не на чем открывать меню.
        database =
            WorkoutDatabase.createInMemory(
                ApplicationProvider.getApplicationContext(),
                standardExercises
            )
        repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = standardExercises
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Регистрирует [ExercisesScreen] с реальной VM на Room in-memory. */
    private fun setUpScreen() {
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                // Хостинг-отступы воспроизводят продакшен (RootScreen:
                // статус-бар + TopAppBar над контентом, NavigationBar под
                // ним). Без верхнего полоса регрессии сжимается до ~3dp;
                // без нижнего контейнер упирается в popup-область и
                // «меню в точке нажатия» недостижимо геометрически —
                // лестница уходит в верхние фолбэки и после фикса.
                Box(
                    Modifier.fillMaxSize().padding(
                        top = topChromePadding,
                        bottom = bottomBarPadding
                    )
                ) {
                    ExercisesScreen(
                        viewModel =
                            viewModel(
                                factory =
                                    ExercisesViewModel.factory(
                                        repository,
                                        AppSettingsDataStore.create(context),
                                        NoopAnalyticsProvider(),
                                        NoopCrashReporter()
                                    )
                            ),
                        onAddExerciseClick = {},
                        onAddCategoryClick = {},
                        onArchivedExercisesClick = {},
                        analyticsProvider = NoopAnalyticsProvider()
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun context_menu_when_long_press_last_visible_row_then_menu_near_row_not_at_top() {
        setUpScreen()

        // Given — нижняя полностью видимая строка каталога (merging-дерево:
        // onAllNodesWithText находит merged-узел строки с её bounds).
        // Первая эмиссия Room-Flow асинхронна — ждём рендер списка.
        val names = standardExercises.map { it.name }
        composeTestRule.waitUntil(10_000) {
            names.any { name ->
                composeTestRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeTestRule.waitForIdle()
        val windowHeight =
            composeTestRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInWindow.height
        val lastRow =
            names
                .mapNotNull { name ->
                    composeTestRule
                        .onAllNodesWithText(name)
                        .fetchSemanticsNodes()
                        .firstOrNull()
                        ?.let { name to it }
                }.filter { (_, node) ->
                    // Lookahead-узлы LazyColumn: нулевые bounds и top за
                    // нижней границей окна — в выборку не годятся
                    val bounds = node.boundsInWindow
                    bounds.height > 0 &&
                        bounds.top >= 0 &&
                        bounds.bottom <= windowHeight
                }.maxByOrNull { (_, node) -> node.positionInWindow.y }
        checkNotNull(lastRow) { "Ни одной полностью видимой строки каталога не найдено" }
        val lastName = lastRow.first
        val rowNode = lastRow.second

        // When — long-press по нижней строке, у её нижнего края (90%
        // высоты): центр строки попадает на границу «лестничного» band'а
        // DropdownMenuPositionProvider и тест нестабилен, край строки —
        // глубоко в полосе бага
        composeTestRule
            .onNodeWithText(lastName)
            .performTouchInput { longClick(percentOffset(0.5f, 0.9f)) }
        composeTestRule.waitForIdle()

        // Then — меню открыто (семантика popup-контента живёт в
        // координатах popup-окна, поэтому абсолютную позицию пункта
        // снимаем из accessibility-дерева через UiAutomation)
        val archiveText = context.getString(R.string.exercise_list_menu_archive)
        composeTestRule
            .onNodeWithText(archiveText)
            .assertExists()
        composeTestRule.waitForIdle()

        // Then — меню у нажатой строки, а не у верхней границы экрана:
        // верх пункта меню в нижней половине окна. Граница «нижняя
        // половина» устойчива к вёрстке, а регрессия «меню у TopAppBar»
        // даёт разрыв в тысячи px и надёжно валит ассерт
        val menuTop = findTextBoundsInScreen(archiveText).top
        val rowTop = rowNode.positionInWindow.y
        assertTrue(
            "Меню (top=$menuTop) должно открываться в нижней половине окна " +
                "(высота $windowHeight) у нажатой строки (top=$rowTop), " +
                "а не у верхней границы экрана",
            menuTop >= windowHeight / 2
        )
    }

    /**
     * Меню у точки нажатия на ВЕРХНЕЙ строке списка: верх пункта меню
     * совпадает с точкой long-press в пределах двух высот строки (первый
     * кандидат лестницы M3 «верх меню к якорю»). Регрессия-двойной-учёт:
     * place якоря в координатах корня внутри узла списка смещает меню
     * вниз ровно на смещение списка в окне (сотни px) — ассерт её ловит.
     * Верхняя строка обязательна: у средних/нижних смещённый вниз якорь
     * не влезает вниз экрана, лестница флипает меню низом к точке
     * нажатия и сдвиг МАСКИРУЕТСЯ (нажатие внизу случайно выглядит
     * правильно даже на сломанном коде).
     */
    @Test
    fun context_menu_when_long_press_first_row_then_menu_at_press_point() {
        setUpScreen()

        val names = standardExercises.map { it.name }
        composeTestRule.waitUntil(10_000) {
            names.any { name ->
                composeTestRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeTestRule.waitForIdle()
        val windowHeight =
            composeTestRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInWindow.height

        // ПЕРВАЯ полностью видимая строка (верх списка)
        val visibleRows =
            names.mapNotNull { name ->
                composeTestRule
                    .onAllNodesWithText(name)
                    .fetchSemanticsNodes()
                    .firstOrNull { node ->
                        val bounds = node.boundsInWindow
                        bounds.height > 0 && bounds.top >= 0 && bounds.bottom <= windowHeight
                    }?.let { name to it }
            }
        check(visibleRows.isNotEmpty()) { "Ни одной полностью видимой строки каталога" }
        // Верхняя по Y (не по порядку items): LazyListState — rememberSaveable
        // и переживает пересоздание Activity между тестами класса, стартовый
        // скролл не гарантирован
        val (firstName, firstRow) = visibleRows.minBy { it.second.positionInWindow.y }
        val pressY = firstRow.positionInWindow.y + firstRow.boundsInWindow.height * 0.9f

        // When — long-press по верхней строке
        composeTestRule
            .onNodeWithText(firstName)
            .performTouchInput { longClick(percentOffset(0.5f, 0.9f)) }
        composeTestRule.waitForIdle()

        // Then — меню открыто
        val archiveText = context.getString(R.string.exercise_list_menu_archive)
        composeTestRule
            .onNodeWithText(archiveText)
            .assertExists()
        composeTestRule.waitForIdle()

        // Then — верх меню у точки нажатия (не смещён вниз на высоту
        // хрома над списком, не улетел вверх)
        val menuTop = findTextBoundsInScreen(archiveText).top
        val rowHeight = firstRow.boundsInWindow.height
        assertTrue(
            "Верх меню (top=$menuTop) должен совпадать с точкой нажатия " +
                "(pressY=$pressY, строка $firstName) в пределах двух высот строки",
            kotlin.math.abs(menuTop - pressY) <= 2 * rowHeight
        )
    }

    /**
     * Ищет узел с текстом [text] во всех окнах (popup меню — отдельное
     * окно) и возвращает его bounds в экранных координатах.
     */
    private fun findTextBoundsInScreen(text: String): Rect {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Флаг интерактивных окон: без него automation.windows отдаёт не
        // все окна, а popup меню — отдельное окно
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        // Popup-окно прикрепляется к accessibility-дереву с лагом в
        // кадр-два после compose-композиции — опрашиваем с таймаутом
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            val roots =
                buildList {
                    automation.rootInActiveWindow?.let(::add)
                    automation.windows.forEach { window -> window.root?.let(::add) }
                }

            // Обход дерева вручную: findAccessibilityNodeInfosByText на
            // этом дереве стабильно не находит текст
            fun walk(node: AccessibilityNodeInfo): Rect? {
                if (node.text?.toString() == text) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty) return bounds
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child -> walk(child) }?.let { return it }
                }
                return null
            }
            roots.firstNotNullOfOrNull(::walk)?.let { return it }
            Thread.sleep(100)
        }
        error("Узел с текстом \"$text\" не найден в accessibility-дереве")
    }
}
