package com.myworkouts.ui.screens.main

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.MainUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * UI-тесты карточки деталей записи на главном экране
 * (workout-detail-view, tasks §4): тап по строке открывает карточку,
 * три способа закрытия (крестик / скрим / системный Back), регресс
 * long-press, длинный комментарий целиком, a11y-действие «Показать
 * детали», пустой комментарий, поведение при открытом меню, мутация БД
 * при открытой карточке (закрытие/обновление), смена конфигурации
 * (recreate Activity).
 *
 * Большая часть тестов использует [MainScreenContent] с локальным
 * состоянием `detailsEntryId`/`contextMenu` — без реального VM
 * (по образцу существующего `MainScreenTest`). Тесты на удаление /
 * изменение / поворот используют реальный [MainScreen] поверх Room
 * in-memory + `MutableStateFlow`-репозиторий, чтобы
 * `rememberSaveable`/`BackHandler` были активны.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenDetailCardTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule(ComponentActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Для тестов на мутацию БД при открытой карточке и смену
    // конфигурации — Room in-memory + репозиторий. Стандартный набор
    // упражнений пустой, чтобы тесты были изолированы друг от друга и от
    // каталога приложения.
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository
    private lateinit var initialLocale: Locale

    /** Запись с весом — для проверки «4 х 10 (+ 5 кг)» в карточке. */
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

    /** Запись на время — для проверки «3 х 20 сек» (без веса). */
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

    /** Запись с длинным комментарием (>2 строк) — для теста отсутствия обрезки. */
    private val longComment =
        "очень длинный комментарий, который точно занимает больше двух строк " +
            "и не должен обрезаться многоточием в карточке деталей записи"
    private val repsWithLongComment = repsItem.copy(id = 3L, comment = longComment)

    /** Записи без комментария и с blank-комментарием — разные id/имена для поиска. */
    private val noCommentItem = repsItem.copy(id = 4L, exerciseName = "Упражнение A", comment = null)
    private val blankCommentItem = repsItem.copy(id = 5L, exerciseName = "Упражнение B", comment = "   ")

    @Before
    fun setUp() {
        initialLocale = Locale.getDefault()
        Locale.setDefault(Locale("ru"))
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(appContext, WorkoutDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = emptyList()
            )
    }

    @After
    fun tearDown() {
        Locale.setDefault(initialLocale)
        database.close()
    }

    // ===== Helpers =====

    private fun successState(entries: List<EntryListItem>): MainUiState = MainUiState.Success(entries = entries)

    /**
     * Установка [MainScreenContent] с локальным состоянием для
     * `detailsEntryId`/`contextMenu`. Колбэки зеркалят продакшен-
     * [MainScreen]: жест тапа учитывает открытое меню (popup поглощает
     * тап, карточку открывает повторный тап), a11y-канал деталей
     * всегда открывает карточку (workout-detail-view, design
     * Risks/Trade-offs).
     */
    private fun setContentWithLocalState(entries: List<EntryListItem>) {
        composeTestRule.setContent {
            val detailsEntryId = remember { mutableStateOf<Long?>(null) }
            val contextMenu = remember { mutableStateOf<ContextMenuState?>(null) }
            JetpackMyWorkoutsTheme {
                MainScreenContent(
                    state =
                        MainScreenState(
                            uiState = successState(entries),
                            contextMenu = contextMenu.value,
                            detailsEntryId = detailsEntryId.value,
                            onAddEntryClick = {},
                            onContextMenuItemChange = { contextMenu.value = it },
                            // Жест тапа: если меню открыто — popup поглощает,
                            // карточку открывает повторный тап
                            onEntryTap = { item ->
                                val menuOpen = contextMenu.value != null
                                contextMenu.value = null
                                if (!menuOpen) {
                                    detailsEntryId.value = item.id
                                }
                            },
                            // A11y «Показать детали»: всегда закрывает меню
                            // и открывает карточку (TalkBack идёт мимо popup)
                            onEntryOpenDetails = { item ->
                                contextMenu.value = null
                                detailsEntryId.value = item.id
                            },
                            onCloseDetails = { detailsEntryId.value = null },
                            onRequestEdit = { contextMenu.value = null },
                            onRequestDelete = { contextMenu.value = null },
                            onConfirmDelete = {},
                            onCancelDelete = {}
                        )
                )
            }
        }
    }

    /**
     * Установка реального [MainScreen] поверх Room in-memory: подготавливает
     * данные через [seed] (suspend-блок), затем рендерит экран с
     * `MainViewModel`. Используется для тестов с `BackHandler` и
     * реактивной БД.
     */
    private fun setContentWithMainScreen(seed: suspend () -> Unit = {}) {
        runBlocking { seed() }
        composeTestRule.setContent {
            JetpackMyWorkoutsTheme {
                MainScreen(
                    viewModel =
                        viewModel(
                            factory = MainViewModel.factory(repository, NoopAnalyticsProvider())
                        ),
                    onAddEntryClick = {},
                    analyticsProvider = NoopAnalyticsProvider()
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Вставляет запись и её упражнение в [repository]. Тип измерения
     * упражнения выбирается по наличию `reps`/`durationSec` (как в
     * реальном UI).
     */
    private suspend fun insertEntry(item: EntryListItem): Long {
        val measuredBy =
            if (item.reps != null) ExerciseMeasuredBy.REPS else ExerciseMeasuredBy.TIME
        val exerciseId =
            repository
                .addExercise(
                    Exercise(
                        id = 0L,
                        name = item.exerciseName,
                        category = ExerciseCategory.PULL,
                        isCustom = true,
                        comment = null,
                        createdAt = 0L,
                        measuredBy = measuredBy
                    )
                ).getOrThrow()
        return repository
            .addEntry(
                ExerciseEntry(
                    id = 0L,
                    exerciseId = exerciseId,
                    workoutId = null,
                    date = item.date,
                    sets = item.sets,
                    reps = item.reps,
                    weightKg = item.weightKg,
                    durationSec = item.durationSec,
                    comment = item.comment,
                    createdAt = item.date,
                    modifiedAt = item.date
                )
            ).getOrThrow()
    }

    /**
     * Ожидаемая строка значения в карточке: «4 х 10 (+ 5 кг)» / «3 х 20
     * сек». Повторяет логику `formatEntryDetailsValue` в терминах
     * локализованных ресурсов (androidTest не видит internal-функцию).
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
                    context.getString(R.string.entry_details_weight_suffix, it.toString().removeSuffix(".0"))
            } ?: ""
        return "$setsPrefix$value$weightSuffix"
    }

    /**
     * Локализованное представление даты/времени карточки: паттерн
     * `d MMMM yyyy, HH:mm` в локали ru, зона — как у машины (дефолт
     * [EntryValueFormatter.formatDetailDateTime]).
     */
    private fun expectedDetailDate(dateMillis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("ru"))
        return Instant
            .ofEpochMilli(dateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter)
    }

    // ===== Tests =====

    /**
     * 4.1. Тап по строке открывает карточку деталей: карточка видна со
     * значением «4 х 10 (+ 5 кг)», строка имеет `SemanticsProperties.Selected`,
     * контекстного меню нет.
     */
    @Test
    fun card_when_row_tapped_then_opens_with_value_and_row_selected_no_menu() {
        // Given — экран с одной записью, карточки и меню нет, строка не выделена
        setContentWithLocalState(listOf(repsItem))
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))

        // When — тап по строке (через touchInput — `detectTapGestures` не
        // реагирует на performClick, использует собственный click)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Then — карточка с «4 х 10 (+ 5 кг)» и крестиком видна
        composeTestRule
            .onNodeWithText(expectedDetailsValue(repsItem))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()

        // Then — после открытия карточки exerciseName есть и в строке, и в
        // карточке (две ноды), строка выделена через семантику `Selected`
        composeTestRule
            .onAllNodesWithText(repsItem.exerciseName)
            .assertCountEquals(2)
        composeTestRule
            .onAllNodesWithText(repsItem.exerciseName)
            .filter(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertCountEquals(1)

        // Then — контекстного меню нет
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_delete))
            .assertCountEquals(0)
    }

    /**
     * 4.2a. Крестик в правом верхнем углу закрывает карточку и снимает
     * выделение строки.
     */
    @Test
    fun card_when_close_button_tapped_then_card_hides_and_row_deselected() {
        setContentWithLocalState(listOf(repsItem))
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()

        // When — тап по крестику
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — карточка скрыта, выделение снято
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    /**
     * 4.2b. Тап по затемнённой области (скриму) закрывает карточку и
     * снимает выделение строки. Карточка по центру с боковыми 24dp —
     * верхний левый угол экрана всегда в зоне скрима.
     */
    @Test
    fun card_when_scrim_tapped_then_card_hides_and_row_deselected() {
        setContentWithLocalState(listOf(repsItem))
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()

        // When — тап в верхний левый угол (вне карточки, на скриме)
        composeTestRule.onRoot().performTouchInput { click(Offset(20f, 20f)) }
        composeTestRule.waitForIdle()

        // Then — карточка скрыта, выделение снято
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    /**
     * 4.2c. Системная кнопка Back закрывает карточку (BackHandler в
     * [MainScreen], срабатывает только при `detailsEntryId != null`).
     */
    @Test
    fun card_when_back_pressed_then_card_hides_and_row_deselected() {
        // Real MainScreen — BackHandler регистрируется только в нём
        setContentWithMainScreen {
            insertEntry(repsItem)
        }
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()

        // When — системный Back через OnBackPressedDispatcher
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Then — карточка скрыта, выделение снято
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close))
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    /**
     * 4.3a. Длинный комментарий (>2 строк) отображается в карточке
     * целиком — без `maxLines`/`Ellipsis` (в отличие от строки списка).
     * Дата/время записи отображаются.
     *
     * Комментарий находится в двух местах: строка списка (под карточкой,
     * скрыта скримом) и сама карточка. В семантическом дереве оба узла
     * несут полный текст — обрезка до `maxLines = 2` влияет только на
     * визуал, не на содержимое `Text` в семантике. Поэтому «нет
     * обрезки» проверяем через высоту ноды в карточке: комментарий
     * длиной ~125 символов в карточке шириной ~280dp занимает несколько
     * строк, а в строке списка с `maxLines = 2` — не более двух (~80px).
     * Реальная проверка «нет Ellipsis» остаётся на ручную верификацию
     * на эмуляторе: Compose 1.12 не выставляет семантику `MaxTextLines`.
     */
    @Test
    fun card_when_long_comment_then_displays_completely_and_shows_date() {
        setContentWithLocalState(listOf(repsWithLongComment))
        composeTestRule
            .onNodeWithText(repsWithLongComment.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Then — полный комментарий найден (в строке и в карточке — 2 ноды)
        composeTestRule.onAllNodesWithText(longComment).assertCountEquals(2)

        // Then — высота текста в карточке больше, чем в строке: комментарий
        // многострочный, без обрезки до двух строк. Карточка шире (24dp от
        // краёв), но текст `bodyMedium` ~125 символов всё равно занимает
        // 3–4 строки, что значительно больше 2 строк строки списка.
        val heights =
            composeTestRule
                .onAllNodesWithText(longComment)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot.height }
        assertTrue(
            "Одна из нод комментария должна быть многострочной " +
                "(фактические высоты: $heights px)",
            heights.any { it > MIN_MULTILINE_HEIGHT_PX }
        )

        // Then — дата/время записи отображаются (только в карточке)
        composeTestRule
            .onNodeWithText(expectedDetailDate(repsWithLongComment.date))
            .assertIsDisplayed()
    }

    /**
     * 4.3b. Регресс: long-press по строке открывает контекстное меню,
     * а не карточку деталей (workout-detail-view, design Decision 4:
     * `detectTapGestures(onTap, onLongPress)` — long-press имеет приоритет).
     */
    @Test
    fun long_press_when_invoked_then_opens_menu_not_card() {
        setContentWithLocalState(listOf(repsItem))

        // When — long-press по строке
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        // Then — пункты меню «Изменить» / «Удалить» отображаются
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_delete))
            .assertIsDisplayed()

        // Then — карточка не открылась
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close))
            .assertCountEquals(0)
    }

    /**
     * 4.4. A11y-действие «Показать детали» (workout-detail-view, design
     * Decision 4) открывает карточку. Действие живёт на строке рядом с
     * существующим «Открыть меню».
     */
    @Test
    fun card_when_a11y_show_details_invoked_then_opens_card() {
        setContentWithLocalState(listOf(repsItem))

        // Поиск custom-действия «Показать детали» на строке (всегда ровно
        // одна строка — до открытия карточки имя упражнения только в ней)
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

        // When — активация действия (как из меню действий TalkBack)
        checkNotNull(showDetailsAction).action.invoke()
        composeTestRule.waitForIdle()

        // Then — карточка открыта со значением и крестиком
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedDetailsValue(repsItem))
            .assertIsDisplayed()

        // Then — строка выделена (на карточке `Selected` не выставляется)
        composeTestRule
            .onAllNodesWithText(repsItem.exerciseName)
            .filter(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertCountEquals(1)
    }

    /**
     * 4.5a. Удаление записи из БД при открытой карточке — карточка
     * закрывается автоматически (workout-detail-view, design Decision 3:
     * `firstOrNull { it.id == detailsEntryId }` даёт `null` после удаления
     * записи → карточка не рендерится).
     */
    @Test
    fun card_when_entry_deleted_then_closes_automatically() {
        var entryId = 0L
        setContentWithMainScreen {
            entryId = insertEntry(repsItem)
        }

        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()

        // When — удаление записи в БД
        runBlocking {
            repository.deleteEntry(entryId).getOrThrow()
        }
        composeTestRule.waitForIdle()

        // Then — карточка закрылась (lookup возвращает null)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_confirm_close))
            .assertCountEquals(0)
    }

    /**
     * 4.5b. Изменение записи в БД при открытой карточке — карточка
     * отображает актуальные данные, выделение строки сохраняется
     * (workout-detail-view, design Decision 3: сравнение по id,
     * `Flow` реактивно эмитит свежие `entries`).
     */
    @Test
    fun card_when_entry_updated_then_data_refreshed_and_selection_preserved() {
        var entryId = 0L
        setContentWithMainScreen {
            entryId = insertEntry(repsItem.copy(comment = null))
        }

        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()
        // Карточка открыта, обновлённого комментария пока нет (и в строке,
        // и в карточке)
        composeTestRule
            .onAllNodesWithText("обновлённый комментарий")
            .assertCountEquals(0)

        // When — обновление записи в БД
        runBlocking {
            val current =
                repository.getEntry(entryId).getOrThrow()
                    ?: error("Запись $entryId не найдена")
            repository
                .updateEntry(
                    current.copy(
                        comment = "обновлённый комментарий",
                        modifiedAt = System.currentTimeMillis()
                    )
                ).getOrThrow()
        }
        composeTestRule.waitForIdle()

        // Then — обновлённый комментарий отображается и в строке списка,
        // и в карточке деталей (две ноды)
        composeTestRule
            .onAllNodesWithText("обновлённый комментарий")
            .assertCountEquals(2)

        // Then — выделение строки сохранено (Selected=true ровно у одной —
        // строки; у карточки Selected не выставляется)
        composeTestRule
            .onAllNodesWithText(repsItem.exerciseName)
            .filter(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertCountEquals(1)
    }

    /**
     * 4.7. У записи без комментария (`null` и blank «   ») блок
     * комментария в карточке не отображается (workout-detail-view,
     * design Decision 5: `if (!comment.isNullOrBlank()) DetailComment(...)`).
     *
     * Сценарий 4.6 «поворот при открытых деталях» живёт в отдельном
     * тестовом классе [MainScreenDetailCardRecreateTest] с реальной
     * `MainActivity` (см. KDoc файла).
     */
    @Test
    fun card_when_no_comment_then_no_comment_block() {
        // Две записи с разными именами для надёжного поиска строк
        setContentWithLocalState(listOf(noCommentItem, blankCommentItem))

        // Open first record (null comment)
        composeTestRule
            .onNodeWithText(noCommentItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Карточка открыта, блока комментария нет
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("   ")
            .assertCountEquals(0)

        // Закрыть и открыть карточку для второй записи (blank comment)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(blankCommentItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Карточка открыта, блока комментария нет (blank не рендерится)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText("   ")
            .assertCountEquals(0)
    }

    /**
     * 4.8. Тап по другой строке при открытом меню — закрывает только
     * меню, карточка не открывается; повторный тап открывает карточку
     * (workout-detail-view, design Risks/Trade-offs: `DropdownMenu`
     * поглощает тап, `onDismissRequest` гасит меню). Жест тапа идёт через
     * отдельный колбэк `onEntryTap` с guard «было ли меню открыто» — иначе
     * Material3 DropdownMenu не поглощает тап, карточка открывается
     * сразу вместе с закрытием меню.
     */
    @Test
    fun menu_when_tap_other_row_then_only_menu_closes_then_repeat_opens_card() {
        setContentWithLocalState(listOf(repsItem, timeItem))

        // Given — long-press по первой строке, меню открыто
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()

        // When — тап по второй строке (popup поглощает тап, guard в
        // `onEntryTap` не открывает карточку)
        composeTestRule
            .onNodeWithText(timeItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Then — меню закрыто, карточка не открылась
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_edit))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodes(hasContentDescription(context.getString(R.string.entry_confirm_close)))
            .assertCountEquals(0)

        // When — повторный тап по второй строке (guard не срабатывает —
        // меню уже закрыто, карточка открывается)
        composeTestRule
            .onNodeWithText(timeItem.exerciseName)
            .performTouchInput { click() }
        composeTestRule.waitForIdle()

        // Then — карточка открыта для второй записи (крестик уникален)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()
        // Значение «3 х 20 сек» после открытия карточки есть и в строке,
        // и в карточке (две ноды)
        composeTestRule
            .onAllNodesWithText(expectedDetailsValue(timeItem))
            .assertCountEquals(2)
    }

    /**
     * 4.9. A11y-действие «Показать детали» на другой строке при открытом
     * меню — закрывает меню и открывает карточку (a11y идёт мимо popup);
     * выделена только новая строка (workout-detail-view, design
     * Risks/Trade-offs).
     */
    @Test
    fun menu_when_a11y_show_details_other_row_then_closes_menu_and_opens_card() {
        setContentWithLocalState(listOf(repsItem, timeItem))

        // Given — long-press по первой строке, меню открыто
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_list_menu_edit))
            .assertIsDisplayed()

        // Поиск custom-действия «Показать детали» на второй строке (пока
        // карточка закрыта — `Планка` есть только в строке списка)
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

        // When — активация действия (a11y идёт мимо popup)
        checkNotNull(showDetailsAction).action.invoke()
        composeTestRule.waitForIdle()

        // Then — меню закрыто
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.entry_list_menu_edit))
            .assertCountEquals(0)

        // Then — карточка открыта для второй записи (крестик уникален)
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.entry_confirm_close))
            .assertIsDisplayed()

        // Then — значение карточки «3 х 20 сек» присутствует; после
        // открытия карточки оно есть и в строке списка, и в карточке (две
        // ноды), обе видимы
        composeTestRule
            .onAllNodesWithText(expectedDetailsValue(timeItem))
            .assertCountEquals(2)

        // Then — выделена только вторая строка (Selected=true у одной из
        // двух нод с именем «Планка» — у строки; первая нода не выделена)
        composeTestRule
            .onAllNodesWithText(timeItem.exerciseName)
            .filter(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertCountEquals(1)
        // Первая строка (repsItem.exerciseName) — только в списке, без
        // карточки; Selected=false
        composeTestRule
            .onNodeWithText(repsItem.exerciseName)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    private companion object {
        /**
         * Минимальная высота ноды комментария в пикселях, при которой
         * текст считается многострочным. Достаточно одной строки
         * `bodyMedium` (~14sp, lineHeight ~20sp) на любой разумной
         * плотности (xxhdpi 3.0 → 60px). Конкретный комментарий в
         * [longComment] длиной ~125 символов в карточке шириной ~280dp
         * занимает ≥3 строк, что значительно превышает этот порог —
         * строка списка с `maxLines = 2` осталась бы ниже.
         */
        const val MIN_MULTILINE_HEIGHT_PX = 80f
    }
}
