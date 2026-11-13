package com.myworkouts.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.MyWorkoutsApplication
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.repository.WorkoutDataRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Предзаполнение БД debug-приложения демо-данными для ручных тестов
 * (инфраструктурный тул, не прод-логика и не регрессионный
 * тест). Запуск — только явно: `make seed-demo-db`; стирание демо-данных
 * — кнопкой «Очистить данные» на экране «Ещё».
 *
 * Форма JUnit-теста — вынужденная: AGP 9.5.1 игнорирует кастомные
 * `<instrumentation>` в androidTest-манифесте (merged всегда содержит
 * только AndroidJUnitRunner), поэтому собственная
 * Instrumentation не регистрируется. Guard-аргумент `seedDemo=true`
 * обязателен: без него (обычный `make android-test`) тест мгновенно
 * скипается через [assumeTrue] и БД не трогает; цель `seed-demo-db` в
 * Makefile дополнительно исключает класс из полного прогона (`notClass`).
 *
 * Запуск вручную (не через connected-задачу Gradle! — она делает
 * uninstall APK и стирает данные после прогона):
 * ```
 * adb install -r app/build/outputs/apk/androidTest/github/debug/app-github-debug-androidTest.apk
 * adb shell am instrument -w -e class com.myworkouts.tools.DemoDataSeederTest \
 *   -e seedDemo true -e days 365 -e seed 42 \
 *   com.myworkouts.test/androidx.test.runner.AndroidJUnitRunner
 * adb uninstall com.myworkouts.test
 * ```
 *
 * Генерация:
 * - окно [ARG_DAYS] календарных дней, заканчивающееся сегодня (якорь —
 *   `LocalDate.now()`, как в screenshot-tests);
 * - в каждой полной неделе окна минимум 5 дней с «Приседаниями»
 *   (случайные 5–6 дней недели, seeded RNG), подходов 1–3, значение
 *   за подход строго 10 или 20 повторов;
 * - в остальных днях — с вероятностью 20% пусто, иначе 1–3 случайных
 *   стандартных упражнения (кроме приседаний), подходов 1–3, значение
 *   за подход 5–50: повторы для REPS, секунды для TIME;
 * - вес и комментарии не заполняются, `workoutId = null`.
 *
 * Гварды: БД обязана быть пустой (иначе — отказ с подсказкой про очистку
 * на экране «Ещё»), «Приседания» обязаны найтись в каталоге (поиск по
 * локализованному имени из ресурса; если каталог создан при другой
 * локали — честный отказ).
 *
 * Данные пишутся через production-репозиторий ([MyWorkoutsApplication.repository])
 * в том же процессе, что и приложение (targetPackage androidTest-APK) —
 * совместимость со схемой гарантирована, открытый экран видит вставки
 * живьём через Room InvalidationTracker. Вставки идут через `addEntry`
 * с валидацией [com.myworkouts.domain.repository.EntryValidator] — как
 * пользовательские.
 */
@RunWith(AndroidJUnit4::class)
class DemoDataSeederTest {
    @Test
    fun seedDemoDatabase() {
        // Guard ручного запуска: без явного seedDemo=true (полный
        // make android-test) тест скипается и БД не трогает
        assumeTrue(
            "Тул запускается только через make seed-demo-db (аргумент seedDemo=true)",
            InstrumentationRegistry.getArguments().getString(ARG_SEED_DEMO) == "true"
        )
        val days =
            (InstrumentationRegistry.getArguments().getString(ARG_DAYS)?.toIntOrNull() ?: DEFAULT_DAYS)
                .coerceIn(MIN_DAYS, MAX_DAYS)
        val seed = InstrumentationRegistry.getArguments().getString(ARG_SEED)?.toLongOrNull() ?: DEFAULT_SEED

        runBlocking { seedDatabase(days, seed) }
    }

    /**
     * Заполняет БД демо-данными. Любая ошибка — исключение (тест падает
     * с сообщением гварда в отчёте `am instrument`).
     */
    private suspend fun seedDatabase(
        days: Int,
        seed: Long
    ) {
        val app =
            checkNotNull(
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as? MyWorkoutsApplication
            ) {
                "Application целевого пакета не MyWorkoutsApplication — проверьте targetPackage androidTest-APK"
            }
        val repository = app.repository
        val rng = Random(seed)

        // Guard «БД пуста»: сид поверх живых данных даёт непредсказуемую
        // картину — отказ с подсказкой (очистка — кнопкой на экране «Ещё»)
        val existing =
            checkNotNull(repository.getAllEntries().getOrNull()) {
                "Не удалось прочитать записи БД"
            }
        require(existing.isEmpty()) {
            "БД не пуста (${existing.size} записей) — очистите данные на экране «Ещё» и повторите make seed-demo-db"
        }

        val squats = findSquats(app, repository)
        val others =
            repository
                .getAllExercises()
                .getOrDefault(emptyList())
                .filter { it.id != squats.id && !it.isArchived }
        require(others.isNotEmpty()) { "Каталог упражнений пуст — стандартный набор не prepopulated" }

        val today = LocalDate.now()
        val firstDay = today.minusDays(days.toLong() - 1)
        val allDays = (0 until days).map { firstDay.plusDays(it.toLong()) }
        // Недели от начала окна; хвост (days % 7 дней) — без гарантии
        // приседаний (неделя неполная, правило «минимум 5 в неделю» к ней
        // не применяется), но случайные записи генерируются и в нём —
        // иначе при DAYS=365 хвост-«сегодня» детерминированно пуст
        val weeks = allDays.chunked(DAYS_PER_WEEK)

        for (week in weeks) {
            // 5–6 дней полной недели с приседаниями: «как минимум 5», с
            // небольшой вариативностью; seeded shuffle — один seed даёт
            // один план. В неполной хвостовой неделе приседаний нет
            val squatDays =
                if (week.size >= MIN_SQUAT_DAYS_PER_WEEK) {
                    week.shuffled(rng).take(MIN_SQUAT_DAYS_PER_WEEK + rng.nextInt(2)).toSet()
                } else {
                    emptySet()
                }
            for (day in week) {
                val dayMillis = toEpochMillis(day)
                if (day in squatDays) {
                    repository.addEntry(squatsEntry(squats.id, dayMillis, rng)).getOrThrow()
                }
                // Прочие упражнения: 20% дней без них, иначе 1–3 записи.
                // Приседательный день остаётся непустым независимо от жребия —
                // гарантия «минимум 5 в неделю» сильнее правила пустого дня
                val otherCount =
                    if (rng.nextInt(100) < EMPTY_DAY_PROBABILITY_PERCENT) {
                        0
                    } else {
                        rng.nextInt(MAX_OTHER_PER_DAY) + 1
                    }
                for (exercise in others.shuffled(rng).take(otherCount)) {
                    repository.addEntry(randomEntry(exercise, dayMillis, rng)).getOrThrow()
                }
            }
        }
    }

    /** «Приседания» каталога: поиск по локализованному имени из ресурса. */
    private suspend fun findSquats(
        app: MyWorkoutsApplication,
        repository: WorkoutDataRepository
    ): Exercise {
        // Локаль каталога фиксируется при первом запуске (prepopulate);
        // сравнение с текущим ресурсом даёт отказ при смене локали после
        // создания БД — честный гвард вместо молчаливой генерации без приседаний
        val squatsName = app.getString(R.string.exercise_squats)
        val candidates =
            checkNotNull(repository.getAllExercises().getOrNull()) {
                "Не удалось прочитать каталог упражнений"
            }.filter { it.name == squatsName && !it.isArchived }
        require(candidates.isNotEmpty()) {
            "Упражнение «$squatsName» не найдено в каталоге — проверьте локаль эмулятора (каталог создаётся при первом запуске с локалью устройства)"
        }
        return candidates.first()
    }

    /** Запись приседаний: 1–3 подхода по 10 или 20 повторов. */
    private fun squatsEntry(
        exerciseId: Long,
        dayMillis: Long,
        rng: Random
    ): ExerciseEntry =
        ExerciseEntry(
            id = 0L,
            exerciseId = exerciseId,
            workoutId = null,
            date = dayMillis,
            sets = rng.nextInt(MAX_SETS) + 1,
            reps = SQUAT_REPS_VALUES[rng.nextInt(SQUAT_REPS_VALUES.size)],
            weightKg = null,
            durationSec = null,
            comment = null,
            createdAt = dayMillis,
            modifiedAt = dayMillis
        )

    /**
     * Запись случайного упражнения: значение за подход 5–50 в единицах
     * измерения (повторы для REPS, секунды для TIME), подходов 1–3,
     * вес и комментарий не заполняются.
     */
    private fun randomEntry(
        exercise: Exercise,
        dayMillis: Long,
        rng: Random
    ): ExerciseEntry {
        val value = rng.nextInt(MIN_VALUE_PER_SET, MAX_VALUE_PER_SET + 1)
        val isTime = exercise.measuredBy == ExerciseMeasuredBy.TIME
        return ExerciseEntry(
            id = 0L,
            exerciseId = exercise.id,
            workoutId = null,
            date = dayMillis,
            sets = rng.nextInt(MAX_SETS) + 1,
            reps = if (isTime) null else value,
            weightKg = null,
            durationSec = if (isTime) value else null,
            comment = null,
            createdAt = dayMillis,
            modifiedAt = dayMillis
        )
    }

    /** Миллисы полудня календарного дня в системной зоне (запись днём). */
    private fun toEpochMillis(day: LocalDate): Long =
        day
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private companion object {
        const val ARG_SEED_DEMO = "seedDemo"
        const val ARG_DAYS = "days"
        const val ARG_SEED = "seed"
        const val DEFAULT_DAYS = 365
        const val MIN_DAYS = 7
        const val MAX_DAYS = 3650
        const val DEFAULT_SEED = 42L
        const val DAYS_PER_WEEK = 7
        const val MIN_SQUAT_DAYS_PER_WEEK = 5
        const val EMPTY_DAY_PROBABILITY_PERCENT = 20
        const val MAX_OTHER_PER_DAY = 3
        const val MAX_SETS = 3
        const val MIN_VALUE_PER_SET = 5
        const val MAX_VALUE_PER_SET = 50

        /** Значения за подход приседаний: 10 или 20. */
        val SQUAT_REPS_VALUES = intArrayOf(10, 20)
    }
}
