package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.ReviewCounters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit-тесты [ReviewMilestoneDecider.decide] (add-rustore-in-app-review,
 * tasks 2.1–2.2, design D2): пороги 1/5/15 по числу созданных записей,
 * выбор — наивысший достигнутый непройденный; флаг сессии и непоказанный
 * pending дают решение «не запрашивать».
 */
class ReviewMilestoneDeciderTest {
    private val decider = ReviewMilestoneDecider()

    /** Счётчики с краткими дефолтами: [created] записей, [passed] пройдено, [pending] в ожидании. */
    private fun counters(
        created: Int,
        passed: Set<Int> = emptySet(),
        pending: Int? = null
    ): ReviewCounters =
        ReviewCounters(
            createdEntriesCount = created,
            passedMilestones = passed,
            pendingMilestone = pending
        )

    @Test
    fun first_entry_plans_milestone_1() {
        // Given — первая созданная запись, ничего не пройдено, pending нет
        val input = counters(created = 1)

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — milestone 1 планируется на первой записи
        assertEquals(ReviewMilestoneDecision.Plan(1), result, "Первая запись — Plan(1)")
    }

    @Test
    fun milestone_5_planned_at_threshold() {
        // Given — пять записей, milestone 1 уже пройден
        val input = counters(created = 5, passed = setOf(1))

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — milestone 5 планируется на пороге
        assertEquals(ReviewMilestoneDecision.Plan(5), result, "Порог 5 — Plan(5)")
    }

    @Test
    fun milestone_15_planned_at_threshold() {
        // Given — пятнадцать записей, milestones 1 и 5 уже пройдены
        val input = counters(created = 15, passed = setOf(1, 5))

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — milestone 15 планируется на пороге
        assertEquals(ReviewMilestoneDecision.Plan(15), result, "Порог 15 — Plan(15)")
    }

    @Test
    fun highest_reached_unpassed_milestone_is_chosen() {
        // Given — двадцать записей, milestones 1 и 5 пройдены (15 достигнут, но не пройден)
        val input = counters(created = 20, passed = setOf(1, 5))

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — выбирается наивысший достигнутый непройденный
        assertEquals(ReviewMilestoneDecision.Plan(15), result, "Наивысший достигнутый непройденный — 15")
    }

    @Test
    fun between_thresholds_gives_none() {
        // Given — три записи, milestone 1 пройден, до порога 5 не дошло
        val input = counters(created = 3, passed = setOf(1))

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — решения между порогами нет
        assertEquals(ReviewMilestoneDecision.None, result, "Между порогами решений нет")
    }

    @Test
    fun session_flag_true_gives_none() {
        // Given — авто-запрос в этой сессии уже выполнялся (флаг holder'а сессии)
        val input = counters(created = 1)

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = true)

        // Then — не более одного авто-запроса за сессию
        assertEquals(ReviewMilestoneDecision.None, result, "Флаг сессии — решений нет")
    }

    @Test
    fun pending_milestone_blocks_decision_and_is_not_overwritten() {
        // Given — pending=1 ещё не показан, счётчик скакнул к 15 (D2: порядок 1→15→5)
        val input = counters(created = 15, pending = 1)

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — решение «не запрашивать»: pending не перезаписывается,
        // промежуточный milestone остаётся непройденным
        assertEquals(ReviewMilestoneDecision.None, result, "Непоказанный pending — решений нет")
    }

    @Test
    fun passed_milestone_is_not_repeated() {
        // Given — порог 5 достигнут, но уже пройден (авто-запрос уже был)
        val input = counters(created = 5, passed = setOf(1, 5))

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — пройденный milestone не повторяется
        assertEquals(ReviewMilestoneDecision.None, result, "Пройденный milestone не повторяется")
    }

    @Test
    fun all_milestones_passed_gives_none() {
        // Given — все пороги пройдены
        val input = counters(created = 100, passed = setOf(1, 5, 15))

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — после всех порогов решений нет
        assertEquals(ReviewMilestoneDecision.None, result, "Все milestones пройдены — решений нет")
    }

    @Test
    fun counters_reset_returns_to_milestone_1() {
        // Given — сброс счётчиков из флоу «Удалить все данные» (task 3.3):
        // счётчик записей и пройденные milestones обнулены, pending снят
        val input = counters(created = 1)

        // When
        val result = decider.decide(input, hasSessionAutoRequestRun = false)

        // Then — снова планируется milestone 1
        assertEquals(ReviewMilestoneDecision.Plan(1), result, "Сброс счётчиков возвращает к milestone 1")
    }
}
