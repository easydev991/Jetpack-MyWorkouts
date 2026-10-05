package com.myworkouts.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.R
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Instrumented-тест для [standardExercises].
 *
 * Спецификация «Локализация при prepopulate»:
 * `standardExercises(context)` под локалями `ru` и `en` возвращает
 * названия, равные строкам из `values/strings.xml` и `values-en/strings.xml`.
 */
@RunWith(AndroidJUnit4::class)
class StandardExercisesLocalizationTest {
    private val ru by lazy {
        localizedContext("ru")
    }
    private val en by lazy {
        localizedContext("en")
    }

    private fun localizedContext(locale: String): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = base.resources.configuration
        @Suppress("DEPRECATION")
        config.setLocale(Locale(locale))
        return base.createConfigurationContext(config)
    }

    @Test
    fun standardExercises_ru_resolvesAll13RussianNames() {
        // When
        val exercises = standardExercises(ru)

        // Then
        assertEquals(13, exercises.size)
        // Все имена непустые
        assertEquals(true, exercises.all { it.name.isNotBlank() })
    }

    @Test
    fun standardExercises_en_resolvesAll13EnglishNames() {
        // When
        val exercises = standardExercises(en)

        // Then
        assertEquals(13, exercises.size)
        assertEquals(true, exercises.all { it.name.isNotBlank() })
    }

    @Test
    fun standardExercises_ru_namesMatchValuesStrings() {
        // Given — список ресурсов
        val ctx = ru
        val res = ctx.resources
        val expectedNames =
            listOf(
                res.getString(R.string.exercise_pullups),
                res.getString(R.string.exercise_australian_pullups),
                res.getString(R.string.exercise_squats),
                res.getString(R.string.exercise_pushups),
                res.getString(R.string.exercise_pushups_knees),
                res.getString(R.string.exercise_lunges),
                res.getString(R.string.exercise_bars),
                res.getString(R.string.exercise_plank),
                res.getString(R.string.exercise_crunches),
                res.getString(R.string.exercise_hanging_leg_raises),
                res.getString(R.string.exercise_pistol_squats),
                res.getString(R.string.exercise_running),
                res.getString(R.string.exercise_jump_rope)
            )

        // When
        val actual = standardExercises(ctx).map { it.name }

        // Then — порядок и состав совпадают с контрактом design.md Decision 4
        assertEquals(expectedNames, actual)
    }

    @Test
    fun standardExercises_en_namesMatchValuesEnStrings() {
        // Given
        val ctx = en
        val res = ctx.resources
        val expectedNames =
            listOf(
                res.getString(R.string.exercise_pullups),
                res.getString(R.string.exercise_australian_pullups),
                res.getString(R.string.exercise_squats),
                res.getString(R.string.exercise_pushups),
                res.getString(R.string.exercise_pushups_knees),
                res.getString(R.string.exercise_lunges),
                res.getString(R.string.exercise_bars),
                res.getString(R.string.exercise_plank),
                res.getString(R.string.exercise_crunches),
                res.getString(R.string.exercise_hanging_leg_raises),
                res.getString(R.string.exercise_pistol_squats),
                res.getString(R.string.exercise_running),
                res.getString(R.string.exercise_jump_rope)
            )

        // When
        val actual = standardExercises(ctx).map { it.name }

        // Then
        assertEquals(expectedNames, actual)
    }

    @Test
    fun standardExercises_ru_and_en_returnDifferentStrings() {
        // When
        val ruNames = standardExercises(ru).map { it.name }
        val enNames = standardExercises(en).map { it.name }

        // Then — список длин одинаков, но значения расходятся
        assertEquals(ruNames.size, enNames.size)
        assertNotEquals(ruNames, enNames)
    }

    @Test
    fun standardExercises_categoryAssignmentIsStable_acrossLocales() {
        // When
        val ruExercises = standardExercises(ru)
        val enExercises = standardExercises(en)

        // Then — категории и порядок не зависят от локали
        assertEquals(
            ruExercises.map { it.category },
            enExercises.map { it.category }
        )
    }

    @Test
    fun standardExercises_categoriesCoverAllExpectedValues() {
        // When
        val categories = standardExercises(ru).map { it.category }

        // Then — все 5 категорий представлены, каждая как минимум один раз
        assertEquals(true, categories.contains("PUSH"))
        assertEquals(true, categories.contains("PULL"))
        assertEquals(true, categories.contains("LEGS"))
        assertEquals(true, categories.contains("CORE"))
        assertEquals(true, categories.contains("CARDIO"))
    }

    @Test
    fun standardExercises_measuredBy_timeForRunningPlankAndJumpRope() {
        // Given — бег, планка и скакалка на время, остальные 10 на повторы
        val timeNames =
            setOf(
                ru.getString(R.string.exercise_running),
                ru.getString(R.string.exercise_plank),
                ru.getString(R.string.exercise_jump_rope)
            )

        // When
        val exercises = standardExercises(ru)

        // Then
        val actualTimeNames =
            exercises
                .filter { it.measuredBy == ExerciseMeasuredBy.TIME }
                .map { it.name }
                .toSet()
        assertEquals(timeNames, actualTimeNames)
        assertEquals(10, exercises.count { it.measuredBy == ExerciseMeasuredBy.REPS })
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun standardExercises_under_default_context_alsoProduces13Items() {
        // Sanity-check: под локалью по умолчанию (без переопределения)
        // функция тоже возвращает 13 упражнений — иначе первый запуск на
        // устройстве с любой не-en/ru локалью уронил бы callback.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val exercises = standardExercises(ctx)
        assertEquals(13, exercises.size)
    }
}
