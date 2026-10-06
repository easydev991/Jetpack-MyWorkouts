package com.myworkouts.ui.screens.exercises

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.navigation.Screen
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.CategoryCreateViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-тесты пикера цвета [CategoryColorPicker] на реальном
 * [CategoryCreateScreen] (custom-categories-color-change, tasks 3.3):
 * реальная VM поверх Room in-memory с реальными
 * [WorkoutDatabase] / [RoomWorkoutDataRepository], детерминированный
 * генератор цвета (цвета из очереди — стартовая генерация create и
 * последующие refresh). Логика VM покрыта `CategoryCreateViewModelTest`
 * в `app/src/test/`.
 *
 * Проверяет: пикер виден в create- и edit-режимах, refresh
 * перерисовывает кружок, сохранение в edit-режиме пишет в БД имя И цвет
 * (updateCategory, одна транзакция), BackHandler-подтверждение закрытия
 * (системный back с изменениями — диалог, пустая форма — закрытие без
 * диалога; канон `ExerciseCreateScreenTest`).
 */
@RunWith(AndroidJUnit4::class)
class CategoryCreateScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        repository =
            RoomWorkoutDataRepository(database = database, standardExercises = emptyList())
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Открывает экран на реальной VM с детерминированным генератором:
     * цвета отдаются по очереди из [generatedColors] при каждом вызове
     * (первый — стартовая генерация create-режима, последующие —
     * refresh); в edit-режиме генератор не вызывается до refresh.
     */
    private fun setScreen(
        categoryId: Long,
        generatedColors: List<Int>,
        onBackClick: () -> Unit = {}
    ) {
        val nextColors = ArrayDeque(generatedColors)
        val viewModel =
            CategoryCreateViewModel(
                repository = repository,
                categoryId = categoryId,
                analyticsProvider = NoopAnalyticsProvider(),
                categoryColorGenerator = { nextColors.removeFirst() }
            )
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                CategoryCreateScreen(viewModel = viewModel, onBackClick = onBackClick)
            }
        }
    }

    /** Категория в БД — id вставленной записи. */
    private fun addCategoryToDatabase(
        name: String,
        colorArgb: Int
    ): Long =
        runBlocking {
            repository.addCategory(name, colorArgb).getOrThrow().id
        }

    /** Цвет центрального пикселя кружка пикера — текущая заливка. */
    private fun assertCircleColor(expectedColor: Int) {
        val bitmap =
            composeTestRule
                .onNodeWithContentDescription(context.getString(R.string.category_color_current))
                .captureToImage()
                .asAndroidBitmap()
        val centerColor = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertEquals("Кружок пикера должен быть залит ожидаемым цветом", expectedColor, centerColor)
    }

    /** Create-режим: пикер виден, кружок залит стартовым сгенерированным цветом. */
    @Test
    fun content_when_create_mode_then_picker_shown_with_generated_color() {
        // Given — create-режим, генератор отдаёт фиксированный цвет
        setScreen(
            categoryId = Screen.CategoryCreate.NO_CATEGORY_ID,
            generatedColors = listOf(COLOR_FIRST)
        )
        composeTestRule.waitForIdle()

        // Then — заголовок, кнопка refresh и кружок отображены;
        // кружок залит сгенерированным цветом
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_field_color))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.category_color_regenerate))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.category_color_current))
            .assertIsDisplayed()
        assertCircleColor(COLOR_FIRST)
    }

    /** Edit-режим: пикер виден, кружок показывает исходный цвет категории. */
    @Test
    fun content_when_edit_mode_then_circle_shows_original_color() {
        // Given — категория в БД, edit-режим (генератор до refresh не вызывается)
        val categoryId = addCategoryToDatabase(name = "Плечи", colorArgb = COLOR_ORIGINAL)
        setScreen(categoryId = categoryId, generatedColors = emptyList())

        // Then — пикер виден, кружок — исходный цвет категории
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_field_color))
            .assertIsDisplayed()
        assertCircleColor(COLOR_ORIGINAL)
    }

    /** Тап по refresh перерисовывает кружок новым цветом. */
    @Test
    fun content_when_refresh_clicked_then_circle_redrawn_with_new_color() {
        // Given — create-режим; генератор: старт COLOR_FIRST, refresh COLOR_SECOND
        setScreen(
            categoryId = Screen.CategoryCreate.NO_CATEGORY_ID,
            generatedColors = listOf(COLOR_FIRST, COLOR_SECOND)
        )
        composeTestRule.waitForIdle()
        assertCircleColor(COLOR_FIRST)

        // When — тап по кнопке регенерации
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.category_color_regenerate))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — кружок перерисован новым цветом
        assertCircleColor(COLOR_SECOND)
    }

    /**
     * Сохранение в edit-режиме обновляет в БД имя И цвет — одна операция
     * [com.myworkouts.data.repository.WorkoutDataRepository.updateCategory].
     */
    @Test
    fun screen_when_save_in_edit_mode_then_updates_name_and_color_in_database() {
        // Given — категория в БД, edit-режим; refresh отдаст COLOR_REGENERATED
        val categoryId = addCategoryToDatabase(name = "Плечи", colorArgb = COLOR_ORIGINAL)
        setScreen(categoryId = categoryId, generatedColors = listOf(COLOR_REGENERATED))
        composeTestRule.waitForIdle()

        // When — ввод имени (полная замена: поле предзаполнено в edit),
        // регенерация цвета, сохранение
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_field_name))
            .performTextReplacement("Грудь")
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.category_color_regenerate))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — в БД обновлены имя и цвет (в базе одна категория)
        val saved = runBlocking { repository.categories().first().single() }
        assertEquals("Имя должно обновиться в БД", "Грудь", saved.name)
        assertEquals("Цвет должен обновиться в БД", COLOR_REGENERATED, saved.colorArgb)
        assertEquals("Сохраняется отредактированная категория", categoryId, saved.id)
    }

    /**
     * Системный back при несохранённых изменениях (введённое имя)
     * показывает диалог подтверждения закрытия
     * (spec-сценарий «Системный back с введённым именем — диалог»).
     */
    @Test
    fun content_when_system_back_pressed_with_changes_then_confirm_dialog_shown() {
        // Given — create-режим, введено имя (hasChanges == true)
        setScreen(
            categoryId = Screen.CategoryCreate.NO_CATEGORY_ID,
            generatedColors = listOf(COLOR_FIRST)
        )
        composeTestRule
            .onNodeWithText(context.getString(R.string.category_field_name))
            .performTextReplacement("Грусть")
        composeTestRule.waitForIdle()

        // When — системная кнопка/жест «назад»
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        // Then — тот же диалог подтверждения, что и для стрелки TopAppBar
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertIsDisplayed()
    }

    /**
     * Пустая форма закрывается без диалога: тап по стрелке TopAppBar
     * (та же точка входа `attemptClose`, что у системного back — при
     * выключенном BackHandler канон `ExerciseCreateScreenTest`)
     * (spec-сценарий «Пустая форма закрывается без диалога»).
     */
    @Test
    fun content_when_no_changes_and_back_arrow_clicked_then_closes_without_dialog() {
        // Given — create-форма не тронута (hasChanges == false)
        var backClickedCount = 0
        setScreen(
            categoryId = Screen.CategoryCreate.NO_CATEGORY_ID,
            generatedColors = listOf(COLOR_FIRST),
            onBackClick = { backClickedCount++ }
        )
        composeTestRule.waitForIdle()

        // When — тап по стрелке «назад»
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.back))
            .performClick()

        // Then — экран закрылся сразу, без диалога
        assertEquals("Без изменений стрелка должна закрывать экран сразу", 1, backClickedCount)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close_dialog_title))
            .assertCountEquals(0)
    }

    private companion object {
        const val COLOR_FIRST = 0xFF2196F3.toInt()
        const val COLOR_SECOND = 0xFFF44336.toInt()
        const val COLOR_ORIGINAL = 0xFF4CAF50.toInt()
        const val COLOR_REGENERATED = 0xFF9C27B0.toInt()
    }
}
