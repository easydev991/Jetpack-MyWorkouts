package com.myworkouts.ui.screens.overview

import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.StatsPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Демо-данные превью обзорного экрана: непустые и согласованные — иначе
 * превью в IDE рендерят пустые экраны и не показывают ключевые состояния.
 */
class PreviewDemoDataTest {
    @Test
    fun demo_stats_state_when_full_then_has_totals_and_drill_entries() {
        // When
        val state = demoStatsState(period = StatsPeriod.WEEK)
        // Демо кладёт агрегаты в карту окна 0 (актуальное окно)
        val page = state.statsByOffset.getValue(0)

        // Then — донат имеет ненулевую долю (подходы), легенда и drill-down заполнены
        assertTrue(page.categoryTotals.isNotEmpty(), "Категории легенды не должны быть пусты")
        assertTrue(
            page.categoryTotals.sumOf { it.totalSets } > 0,
            "Суммарное число подходов должно быть положительным"
        )
        assertTrue(page.exerciseTotals.isNotEmpty(), "Суммы упражнений не должны быть пусты")
        page.exerciseTotals.forEach { (category, totals) ->
            assertTrue(totals.isNotEmpty(), "У категории $category должны быть упражнения")
        }
        page.drillGroups.forEach { (category, groups) ->
            assertTrue(groups.isNotEmpty(), "У категории $category должны быть дни")
            groups.forEach { group ->
                assertTrue(group.entries.isNotEmpty(), "Записи дня ${group.date} не должны быть пусты")
            }
        }
    }

    @Test
    fun demo_stats_state_when_category_selected_then_drill_groups_exist() {
        // When
        val state = demoStatsState(period = StatsPeriod.WEEK, selected = "PUSH")
        val page = state.statsByOffset.getValue(0)

        // Then
        assertTrue(
            state.selected != null && page.drillGroups[state.selected].orEmpty().isNotEmpty(),
            "У выбранной категории должны быть записи drill-down"
        )
    }

    @Test
    fun demo_records_not_empty() {
        // Then — превью «Рекордов» показывает строки, а не пустое состояние
        assertTrue(demoRecords.isNotEmpty(), "Демо-рекорды не должны быть пусты")
    }

    @Test
    fun demo_heatmap_when_month_then_has_active_days() {
        // When
        val heatmap = demoHeatmap(ActivityPeriod.MONTH)

        // Then — окно заполнено и есть непустые ячейки уровня 1..4
        assertEquals(ActivityPeriod.MONTH.days, heatmap.size)
        assertTrue(heatmap.any { it.level > 0 }, "В хитмапе должны быть активные дни")
    }
}
