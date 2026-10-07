package com.myworkouts.screenshots

import android.graphics.Bitmap
import android.os.Environment
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.myworkouts.MainActivity
import com.myworkouts.MyWorkoutsApplication
import com.myworkouts.R
import com.myworkouts.domain.model.BackupEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Сценарии скриншотов маркетплейса (навык `kotlin-ui-testing`,
 * `references/screenshot-tests.md`): главный экран,
 * создание записи, обзор. Один `@Test` на сценарий — у каждого своя
 * фикстура (импорт в начале теста), общий `@After` чистит БД.
 *
 * UI-локаль выставляется СНАРУЖИ теста, до instrumentation-запуска:
 * `adb shell cmd locale set-app-locales com.myworkouts --user 0
 * --locales <tag>`. Тест только читает тег локали из runner-аргументов —
 * для выбора русских/английских комментариев фикстуры — и ассертит
 * локале-зависимую строку ресурсов, доказывая, что пер-апп локаль
 * применилась к UI, а не только к фикстуре.
 *
 * Данные — через продакшен-репозиторий процесса приложения
 * (`MyWorkoutsApplication.repository`, канон
 * `MainScreenDetailCardRecreateTest`): один инстанс Room = один
 * `InvalidationTracker` — реактивный Flow `MainViewModel.uiState` видит
 * вставку без перезапуска. Упражнения фикстуры — только стандартные 13:
 * `deleteAllData()` восстанавливает каталог, `importData` зовётся с
 * `exercises = emptyList()`, имена резолвятся `getString` в локали
 * прогона и матчатся каталогом по `name.trim().lowercase()`.
 *
 * Capture — `UiDevice.takeScreenshot()`: рисует framebuffer целиком
 * (нав-/статусбар включительно), в отличие от `captureToImage()`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** Канон из `MainScreenDetailCardRecreateTest`: targetContext — это
     * `:app`-package, у него и забирает файлы `adb pull`. */
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Тег локали из runner-аргументов (`-P…locale=<tag>`), дефолт ru-RU. */
    private lateinit var localeTag: String

    @Before
    fun setup() {
        localeTag = InstrumentationRegistry.getArguments().getString("locale") ?: "ru-RU"
        val app = ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()
        // Оба вызова — Result<Unit>: игнорировать нельзя — упавший setup
        // даст зелёный тест на пустой БД (канон —
        // RoomWorkoutDataRepositoryDataManagementTest).
        runBlocking {
            assertTrue(app.repository.deleteAllData().isSuccess)
        }
    }

    /**
     * Уборка: demo-записи не должны оставаться в production-БД
     * приложения на девайсе — иначе последующий `make android-test`
     * краснеет (живые интеграционные тесты не ждут чужих данных в
     * списке; проверено: без уборки падают
     * `EntryListLiveUpdateIntegrationTest`). Паттерн — `@After
     * tearDown`: `deleteAllData`
     * восстанавливает стандартный набор, состояние БД — как после
     * ручного «удалить все данные».
     */
    @After
    fun tearDown(): Unit =
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()
            assertTrue("Не удалось очистить demo-данные", app.repository.deleteAllData().isSuccess)
        }

    @Test
    fun mainScreen_whenBaseFixtureImported_thenWritesWorkoutsListPng() {
        // Given — фикстура трёх дней импортирована в стандартный каталог
        importFixture(baseEntries())

        // When — якорь отрисовки: верхняя запись списка (d-1, выполнена
        // последней в свой день — сортировка `date DESC, created_at DESC`)
        waitFor(hasText(context.getString(R.string.exercise_squats)))

        // Then — пер-апп локаль применилась к ресурсам приложения
        // (onFirst: строка встречается дважды — TopAppBar и вкладка
        // bottom-bar), capture и валидный PNG
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.workouts_title))
            .onFirst()
            .assertIsDisplayed()
        captureAndValidate("1-workouts-list.png")
    }

    @Test
    fun entryCreateForm_whenFilled_thenWritesCreateEntryPng() {
        // Given — фикстура Сценария 1 (у «Отжиманий» есть история d-3:
        // предзаполнение из истории подставит reps, заменим вводом)
        importFixture(baseEntries())
        val comment = weightedEntryComment()

        // When — FAB → заполненная форма
        openFilledCreateForm(comment)

        // Then — гасим IME (performTextReplacement фокусирует поля и
        // поднимает клавиатуру — она перекрывает низ формы), доскролливаем
        // комментарий в кадр (форма — Column(verticalScroll), семь полей)
        hideIme()
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(context.getString(R.string.entry_field_comment)))
        composeTestRule.waitForIdle()
        captureAndValidate("2-create-new-entry.png")
    }

    @Test
    fun mainScreen_whenEntrySaved_thenWritesNewEntrySavedPng() {
        // Given — заполненная форма (фикстура и заполнение — Сценарий 2)
        importFixture(baseEntries())
        val comment = weightedEntryComment()
        openFilledCreateForm(comment)

        // When — «Сохранить»: экран создания закрывается только кнопкой
        // (`viewModel.save(onSaved = onBackClick)`), никаких pressBack
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_save))
            .performClick()

        // Ждём главный экран (FAB снова в дереве)
        waitFor(hasContentDescription(context.getString(R.string.add_workout)))

        // Then — новая запись сегодняшняя (`createdAt = now`) — первая в
        // группе «Сегодня»; скролл к началу списка (заголовок «Сегодня» —
        // item 0), строка с комментарием видна под заголовком
        hideIme()
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToIndex(0)
        composeTestRule.waitForIdle()
        composeTestRule
            .onAllNodesWithText(comment)
            .onFirst()
            .assertIsDisplayed()
        captureAndValidate("3-new-entry-saved.png")
    }

    @Test
    fun overviewStats_whenWeekPeriodSelected_thenWritesWeekStatsPng() {
        // Given — фикстура с записью сегодня (данные за «Неделю» не пустые)
        importFixture(overviewEntries())

        // When — donut в TopAppBar → Обзор; вкладка «Статистика» кликается
        // явно: lastOverviewTab переживает deleteAllData() в DataStore
        openOverview()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_tab_stats))
            .performClick()

        // Период «Неделя»: клик идемпотентен (неделя — дефолт), но кадр
        // не должен зависеть от дефолта
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_period_week))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — непустое состояние статистики
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_stats_empty))
            .assertDoesNotExist()
        captureAndValidate("4-week-stats.png")
    }

    @Test
    fun overviewRecords_whenPerSetModeDefault_thenWritesRecordsPng() {
        // Given — фикстура с записью сегодня
        importFixture(overviewEntries())

        // When — вкладка «Рекорды»; режим «За подход» — дефолт
        // (`RecordsMode.PER_SET`), второй сегмент не трогаем
        openOverview()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_tab_records))
            .performClick()
        composeTestRule.waitForIdle()

        // Then — непустое состояние рекордов
        composeTestRule
            .onNodeWithText(context.getString(R.string.records_empty))
            .assertDoesNotExist()
        captureAndValidate("5-records-per-set.png")
    }

    @Test
    fun overviewActivity_whenMonthPeriodDefault_thenWritesActivityPng() {
        // Given — фикстура с записью сегодня
        importFixture(overviewEntries())

        // When — вкладка «Активность»; период не переключаем — дефолт
        // `ActivityPeriod.MONTH` в имени PNG не отражён
        openOverview()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_tab_activity))
            .performClick()

        // Then — якорь: легенда хитмапа (надёжнее текстов карточки серии —
        // те собираются из plurals и зависят от streakState)
        composeTestRule
            .onNodeWithText(context.getString(R.string.activity_less))
            .assertIsDisplayed()
        captureAndValidate("6-activity-screen.png")
    }

    /** Donut в TopAppBar главного экрана → экран «Обзор». */
    private fun openOverview() {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.a11y_open_overview))
            .performClick()
        waitFor(hasText(context.getString(R.string.overview_title)))
    }

    /** Комментарий новой записи в паре ru/en — общий для сценариев 2–3. */
    private fun weightedEntryComment(): String = localizedComment("пробую с утяжелением", "trying it weighted")

    /**
     * FAB → форма создания → «Отжимания» / 12 повторов / 5 кг /
     * [comment]. Подходы (`setsText = "1"`) и дата/время не трогаем —
     * дефолты годятся для кадра. IME оставляем открытым — сценарии
     * гасят его сами в нужный момент.
     */
    private fun openFilledCreateForm(comment: String) {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.add_workout))
            .performClick()
        waitFor(hasText(context.getString(R.string.entry_create_title)))

        // Упражнение «Отжимания»: onNodeWithText ищет точное совпадение —
        // не путается с «Отжимания на коленях» / «на брусьях»
        selectRepsExercise(context.getString(R.string.exercise_pushups))

        // Повторы 12, вес 5, комментарий — ввод по label поля
        // (канон EntryCreateScreenTest)
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_reps)))
            .performTextReplacement("12")
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_weight)))
            .performTextReplacement("5")
        composeTestRule
            .onNode(hasText(context.getString(R.string.entry_field_comment)))
            .performTextReplacement(comment)
    }

    private fun hideIme() {
        composeTestRule.activity
            .getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(
                composeTestRule.activity.window.decorView.windowToken,
                0
            )
        composeTestRule.waitForIdle()
    }

    /** Ждёт появления в дереве узла по [matcher] (5 с — дефолтный таймаут). */
    private fun waitFor(matcher: SemanticsMatcher) {
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Выбирает упражнение [optionText] в дропдауме и ждёт появления поля
     * повторов (признак применившегося выбора). Опций в меню 13,
     * «Отжимания» ниже первого экрана: клик по невидимому пункту
     * применился через раз (ru — да, en — нет), поэтому пункт
     * доскролливается в открытом меню (последний scrollable на экране —
     * попап) и кликается видимым.
     */
    private fun selectRepsExercise(optionText: String) {
        composeTestRule
            .onNodeWithText(context.getString(R.string.entry_field_exercise))
            .performClick()
        // Меню открыто: в дереве видны его пункты («Выпады» — среди
        // первых, виден без скролла)
        waitFor(hasText(context.getString(R.string.exercise_lunges)))
        composeTestRule
            .onAllNodes(hasScrollAction())
            .onLast()
            .performScrollToNode(hasText(optionText))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(optionText).performClick()
        waitFor(hasText(context.getString(R.string.entry_field_reps)))
    }

    /** Импорт фикстуры сценария: каталог уже стандартный (`deleteAllData`
     * в `@Before`), импортировать упражнения нечего. */
    private fun importFixture(entries: List<BackupEntry>) {
        val app = ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()
        runBlocking {
            val imported =
                app.repository.importData(
                    categories = emptyList(),
                    exercises = emptyList(),
                    entries = entries
                )
            assertTrue("Импорт фикстуры не удался: $imported", imported.isSuccess)
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Записи Сценария 1 (`1-workouts-list.png`): три дня, 9 записей
     * в порядке выполнения. Все записи собственного
     * веса (`weightKg = null`); брусья — две отдельные записи по
     * 1 подходу, планка — запись на время.
     */
    private fun baseEntries(): List<BackupEntry> =
        listOf(
            fixtureEntry(0, R.string.exercise_lunges, daysAgo = 1, sets = 2, reps = 20),
            fixtureEntry(1, R.string.exercise_squats, daysAgo = 1, sets = 4, reps = 15),
            fixtureEntry(2, R.string.exercise_pullups, daysAgo = 2, sets = 2, reps = 6),
            fixtureEntry(3, R.string.exercise_australian_pullups, daysAgo = 2, sets = 2, reps = 12),
            fixtureEntry(4, R.string.exercise_crunches, daysAgo = 2, sets = 1, reps = 30),
            fixtureEntry(
                5,
                R.string.exercise_plank,
                daysAgo = 2,
                sets = 1,
                durationSec = 45,
                comment = localizedComment("добиваю пресс", "finishing off the core")
            ),
            fixtureEntry(
                6,
                R.string.exercise_pushups,
                daysAgo = 3,
                sets = 1,
                reps = 20,
                comment =
                    localizedComment(
                        "новый рекорд, мощно",
                        "new personal record, felt strong"
                    )
            ),
            fixtureEntry(7, R.string.exercise_bars, daysAgo = 3, sets = 1, reps = 4),
            fixtureEntry(8, R.string.exercise_bars, daysAgo = 3, sets = 1, reps = 4)
        )

    /**
     * Фикстура сценариев 4–6: `baseEntries` + запись сегодня (Отжимания,
     * 1 × 12, 5 кг). Запись сегодня нужна именно в фикстуре, а не
     * «создаётся в UI»: `tearDown` каждого теста делает
     * `deleteAllData()`, записи сценариев 2–3 не переживают свой тест —
     * без неё обзор снимался бы с пустыми состояниями.
     */
    private fun overviewEntries(): List<BackupEntry> =
        baseEntries() +
            fixtureEntry(
                9,
                R.string.exercise_pushups,
                daysAgo = 0,
                sets = 1,
                reps = 12,
                weightKg = 5.0,
                comment = weightedEntryComment()
            )

    /**
     * Запись фикстуры. `date` — начало календарного дня N дней назад
     * (`atStartOfDay` — сдвиг DST не уводит запись на соседний день);
     * `createdAt` строго растёт по позиции в списке фикстуры — внутри
     * дня записи идут обратно-хронологически, последняя выполненная
     * вверху группы.
     */
    private fun fixtureEntry(
        index: Int,
        exerciseNameRes: Int,
        daysAgo: Long,
        sets: Int,
        reps: Int? = null,
        weightKg: Double? = null,
        durationSec: Int? = null,
        comment: String? = null
    ): BackupEntry =
        BackupEntry(
            exerciseName = context.getString(exerciseNameRes),
            date = daysAgoMillis(daysAgo),
            sets = sets,
            reps = reps,
            weightKg = weightKg,
            durationSec = durationSec,
            comment = comment,
            createdAt = daysAgoMillis(1) + index * 60_000L
        )

    private fun daysAgoMillis(days: Long): Long =
        LocalDate
            .now()
            .minusDays(days)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun localizedComment(
        ru: String,
        en: String
    ): String = if (localeTag.startsWith("en")) en else ru

    /**
     * Capture экрана целиком и запись валидного PNG в app-specific
     * external storage — оттуда его забирает `make screenshots`.
     *
     * Перед кадром переждываем системный тост IME «Changed the keyboard
     * language…»: его публикует Gboard, подтягивая пер-апп локаль при
     * старте activity (`sleep 5` после `set-app-locales` его не
     * накрывает), и ложится на bottom-bar в кадре. Обнаружить его из
     * теста нельзя: в дереве доступности и `dumpsys window` окна тоста
     * нет (проверено: `UiDevice.wait(Until.gone(…))` отрабатывает
     * мгновенно). Поэтому переждываем гарантированный срок жизни: тост
     * публикуется не позже ~2 с после старта activity и живёт
     * LENGTH_LONG ≈3,5 с — 5 с после якоря отрисовки покрывают его
     * с запасом. Задержка — часть сценария, не ожидание UI (канон
     * сценарийного `delay` — `ExerciseCreateLiveUpdateIntegrationTest`:
     * `SubscriptionTimeoutRule` + `delay(300L)`).
     */
    private fun captureAndValidate(fileName: String) {
        runBlocking { delay(5_000) }
        val bitmap =
            checkNotNull(
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot()
            ) { "UiDevice.takeScreenshot() вернул null — framebuffer недоступен" }
        val dir =
            File(
                checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)) {
                    "getExternalFilesDir(Pictures) вернул null — external storage недоступен"
                },
                "screenshots/$localeTag"
            )
        dir.mkdirs()
        val file = File(dir, fileName)
        file.outputStream().use { out ->
            // compress возвращает false при ошибке записи — обрезанный
            // или пустой файл мимо него не пройдёт
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "PNG не записан: $file"
            }
        }
    }
}
