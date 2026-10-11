package com.myworkouts.ui.screens.root

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.MainActivity
import com.myworkouts.MyWorkoutsApplication
import com.myworkouts.R
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.domain.model.ExerciseEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Регрессия утечек памяти (LeakCanary) в корневой навигации: циклы по нижним
 * вкладкам и push/pop вторичных экранов.
 *
 * Правило [noLeaksRule] объявлено с `order = 1` (внешнее), [timeout] — с
 * `order = 2`, [composeTestRule] — с `order = 3` (внутреннее): в JUnit 4
 * «rules with a higher value are inner», поэтому проверка утечек выполняется
 * строго после teardown composeTestRule, когда Activity и композиция уже
 * уничтожены — иначе ловить нечего.
 *
 * NavHostController нельзя хранить в поле теста: он удерживает уничтоженную
 * Activity, и LeakCanary справедливо посчитает это утечкой, созданной тестом.
 * RootScreen создаёт контроллер внутри себя, поэтому навигация идёт только
 * через UI-клики, а целевой экран подтверждается видимостью характерных строк;
 * уничтожение Activity отслеживается `ObjectWatcher`'ом LeakCanary напрямую.
 */
@RunWith(AndroidJUnit4::class)
class MemoryLeakRegressionTest {
    @get:Rule(order = 1)
    val noLeaksRule = DetectLeaksAfterTestSuccess()

    @get:Rule(order = 2)
    val timeout = Timeout(LEAK_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    @get:Rule(order = 3)
    val composeTestRule = createAndroidComposeRule(MainActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // process-singleton: тонкая обёртка над одним и тем же DataStore
    private val settings = AppSettingsDataStore.create(context)

    private val tabTexts =
        listOf(
            context.getString(R.string.workouts_title),
            context.getString(R.string.more_title)
        )

    @Before
    fun setUp() {
        // Given — «Упражнения» выключены → 2 вкладки; без явной записи тест
        // может унаследовать состояние от предыдущего прогона
        runBlocking { settings.setShowExercisesTab(false) }
    }

    @After
    fun tearDown() {
        runBlocking { settings.setShowExercisesTab(false) }
    }

    @Test
    fun rootScreen_whenCyclingBottomNavTabs_thenNoLeaks() {
        // Given — приложение открыто на главной вкладке, нижний бар виден
        composeTestRule.waitForIdle()

        // When — циклы по нижним вкладкам
        cycleTabs(tabTexts, CYCLES)

        // Then — после цикла мы на «Ещё»: строка дважды (TopAppBar + лейбл бара)
        assertTabShown(R.string.more_title)
    }

    @Test
    fun rootScreen_whenCyclingThreeBottomNavTabs_thenNoLeaks() {
        // Given — включена вкладка «Упражнения» (по умолчанию выключена
        // классовым setUp; база данных пересоздаётся между прогонами,
        // но DataStore — нет, поэтому задаём явно)
        runBlocking { settings.setShowExercisesTab(true) }
        composeTestRule.waitForIdle()

        // When — циклы по трём нижним вкладкам (каждая посещается CYCLES раз);
        // порядок заканчивается «Ещё» — под финальный assert
        val threeTabTexts =
            listOf(context.getString(R.string.exercises_title)) + tabTexts
        cycleTabs(threeTabTexts, CYCLES)

        // Then — после цикла мы на «Ещё»: строка дважды (TopAppBar + лейбл бара)
        assertTabShown(R.string.more_title)
    }

    @Test
    fun rootScreen_whenPushPopEntryCreate_thenNoLeaks() {
        // Given — приложение открыто на главной вкладке
        composeTestRule.waitForIdle()

        // When — FAB → EntryCreate
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.add_workout))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — экран создания открыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_create_title))
            .assertIsDisplayed()

        // When — системный back
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Then — вернулись на главный: «Тренировки» дважды
        // (TopAppBar + лейбл вкладки в нижнем баре)
        assertTabShown(R.string.workouts_title)
    }

    @Test
    fun rootScreen_whenPushPopOverviewWithTabsAndSettings_thenNoLeaks() {
        // Given — в базе есть хотя бы одна запись: кнопка «Обзор» в шапке
        // главного экрана видима только при непустом списке (спека
        // overview-screen). Сидируем через production-репозиторий.
        val application = composeTestRule.activity.application as MyWorkoutsApplication
        val exercise =
            runBlocking {
                application.repository
                    .exercises("")
                    .first()
                    .first()
            }
        runBlocking {
            application.repository.addEntry(
                ExerciseEntry(
                    id = 0L,
                    exerciseId = exercise.id,
                    workoutId = null,
                    date = System.currentTimeMillis(),
                    sets = 3,
                    reps = 10,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis()
                )
            )
        }
        composeTestRule.waitForIdle()

        // When — кнопка «Обзор» в шапке главного экрана
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.a11y_open_overview))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — экран «Обзор» открыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_title))
            .assertIsDisplayed()

        // When — циклы переключения внутренних вкладок обзора
        // (Статистика/Рекорды/Активность): пересоздание контента вкладок
        val overviewTabTexts =
            listOf(
                R.string.overview_tab_stats,
                R.string.overview_tab_records,
                R.string.overview_tab_activity
            ).map { context.getString(it) }
        cycleTabs(overviewTabTexts, OVERVIEW_TAB_CYCLES)

        // When — шестерёнка настроек в шапке обзора
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.a11y_overview_settings))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — экран настроек обзора открыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_settings_title))
            .assertIsDisplayed()

        // When — back: настройки → обзор → главный
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_title))
            .assertIsDisplayed()
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Then — вернулись на главный
        assertTabShown(R.string.workouts_title)
    }

    @Test
    fun rootScreen_whenPushPopAppearanceAndAppData_thenNoLeaks() {
        // Given — приложение открыто на вкладке «Ещё»
        composeTestRule.waitForIdle()
        clickNode(context.getString(R.string.more_title))

        // When — ряд «Оформление приложения»
        clickNode(context.getString(R.string.appearance_title))

        // Then — экран оформления открыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.appearance_title))
            .assertIsDisplayed()

        // When — back на «Ещё», затем ряд «Данные приложения»
        Espresso.pressBack()
        composeTestRule.waitForIdle()
        clickNode(context.getString(R.string.app_data))

        // Then — экран данных открыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_data))
            .assertIsDisplayed()

        // When — back на «Ещё»
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Then — вернулись на «Ещё»: строка дважды (TopAppBar + лейбл бара)
        assertTabShown(R.string.more_title)
    }

    /** Клик по кликабельной ноде с текстом и ожидание простоя композиции. */
    private fun clickNode(text: String) {
        // hasText + hasClickAction: строка есть и в TopAppBar (не кликабельный),
        // и в NavigationBarItem/ряде настроек (кликабельный) — без фильтра по
        // click-action найдутся 2 ноды
        composeTestRule
            .onNode(hasText(text) and hasClickAction())
            .performClick()
        composeTestRule.waitForIdle()
    }

    /** [cycles] полных проходов по списку кликабельных строк. */
    private fun cycleTabs(
        texts: List<String>,
        cycles: Int
    ) {
        repeat(cycles) {
            texts.forEach { text -> clickNode(text) }
        }
    }

    /** Строка таба видима дважды: TopAppBar + лейбл вкладки в нижнем баре. */
    private fun assertTabShown(resId: Int) {
        composeTestRule
            .onAllNodesWithText(context.getString(resId))
            .assertCountEquals(2)
    }

    private companion object {
        const val LEAK_CHECK_TIMEOUT_SECONDS = 300L
        const val CYCLES = 15
        const val OVERVIEW_TAB_CYCLES = 5
    }
}
