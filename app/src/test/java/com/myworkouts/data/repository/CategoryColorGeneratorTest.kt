package com.myworkouts.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * JVM-тесты чистой функции выбора hue [pickCategoryHue] для генерации
 * цвета новой категории (custom-categories, design Decision 3):
 * минимальный отступ 15° от существующих hue, пока наибольший зазор
 * hue-кольца не меньше 30°; детерминизм — через injectable `Random`.
 */
class CategoryColorGeneratorTest {
    /** Угловое расстояние по кольцу hue: `min(|a−b|, 360−|a−b|)`. */
    private fun hueDistance(
        first: Float,
        second: Float
    ): Float {
        val direct = abs(first - second)
        return min(direct, 360f - direct)
    }

    @Test
    fun pick_category_hue_when_no_existing_hues_then_takes_hue_from_random() {
        // When
        val hue = pickCategoryHue(emptyList(), Random(42))

        // Then: пустой набор — hue целиком из random (детерминированно)
        val expected = Random(42).nextFloat() * 360f
        assertEquals(expected, hue, 0f, "Пустой набор — hue из random")
    }

    @Test
    fun pick_category_hue_when_single_existing_then_offset_at_least_15_degrees() {
        // When
        val hue = pickCategoryHue(listOf(0f), Random(42))

        // Then
        val distance = hueDistance(hue, 0f)
        assertTrue(distance >= 15f, "Отступ от единственного hue ≥ 15°, было $distance°")
    }

    @Test
    fun pick_category_hue_when_gap_larger_than_30_then_offset_from_both_neighbors() {
        // When: зазоры 180° и 180° — случайный hue внутри центрального
        // участка наибольшего зазора
        val hue = pickCategoryHue(listOf(0f, 180f), Random(42))

        // Then
        val distanceToLeft = hueDistance(hue, 0f)
        val distanceToRight = hueDistance(hue, 180f)
        assertTrue(distanceToLeft >= 15f, "Отступ слева ≥ 15°, было $distanceToLeft°")
        assertTrue(distanceToRight >= 15f, "Отступ справа ≥ 15°, было $distanceToRight°")
    }

    @Test
    fun pick_category_hue_when_gap_exactly_30_then_exact_offset_without_random() {
        // Given: 12 hue через 30° — все зазоры ровно на границе, случайной
        // «свободной» части нет
        val evenlySpacedTwelve = List(12) { index -> index * 30f }

        // When
        val hue = pickCategoryHue(evenlySpacedTwelve, Random(7))

        // Then: ровно gapStart + 15° — random не участвует
        assertEquals(15f, hue, 0f, "Зазор ровно 30° — hue = gapStart + 15°")
    }

    @Test
    fun pick_category_hue_when_all_gaps_below_30_then_center_of_largest_gap() {
        // Given: 30 hue через 12° — все зазоры меньше 30°
        val evenlySpacedThirty = List(30) { index -> index * 12f }

        // When
        val hue = pickCategoryHue(evenlySpacedThirty, Random(7))

        // Then: центр наибольшего (первого) зазора; отступ может
        // нарушаться — повторы допустимы (spec «Цвет устойчиво различим»)
        assertEquals(6f, hue, 0f, "Все зазоры 12° — центр наибольшего зазора")
    }

    @Test
    fun pick_category_hue_when_duplicate_hues_then_uses_ring_gap() {
        // When: дублирующийся hue — на кольце один зазор 360°
        val hue = pickCategoryHue(listOf(100f, 100f), Random(42))

        // Then
        val distance = hueDistance(hue.mod(360f), 100f)
        assertTrue(distance >= 15f, "Дублирующийся hue — зазор кольца 360°, отступ ≥ 15°, было $distance°")
    }

    @Test
    fun pick_category_hue_when_creating_up_to_12_categories_then_offset_holds_every_step() {
        // Given: hue пяти стандартных категорий (приблизительно) —
        // сценарий «Цвет устойчиво различим»: суммарно ≤ 12 категорий
        var hues = listOf(0f, 217f, 150f, 291f, 46f)
        val random = Random(1234)

        // When: создаём ещё 7 категорий (итого 12)
        repeat(7) { iteration ->
            val hue = pickCategoryHue(hues, random)

            // Then: отступ ≥ 15° от всех существующих
            val minDistance = hues.minOf { neighbor -> hueDistance(hue, neighbor) }
            assertTrue(
                minDistance >= 15f,
                "Итерация $iteration: минимальный отступ $minDistance° < 15°"
            )
            hues = hues + hue
        }
    }
}
