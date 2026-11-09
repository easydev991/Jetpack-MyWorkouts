package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.ReviewCounters

/**
 * Решение [ReviewMilestoneDecider] по авто-запросу оценки приложения.
 */
sealed interface ReviewMilestoneDecision {
    /**
     * Review-флоу нужно запланировать на пороге [milestone].
     */
    data class Plan(
        val milestone: Int
    ) : ReviewMilestoneDecision

    /**
     * Авто-запрос не требуется.
     */
    data object None : ReviewMilestoneDecision
}

/**
 * Чистый решатель review-майлстоунов (add-rustore-in-app-review, design
 * D2): по счётчикам определяет, нужно ли запланировать авто-запрос
 * оценки приложения. Достигнутым считается порог, для которого
 * `createdEntriesCount >= порог`; из достигнутых выбирается наивысший
 * непройденный. Решения нет, если авто-запрос в этой сессии уже
 * выполнялся ([hasSessionAutoRequestRun]) или есть непоказанный pending:
 * pending не перезаписывается — промежуточный milestone остаётся
 * непройденным и подхватывается после показа текущего.
 */
class ReviewMilestoneDecider {
    /**
     * Определяет решение по счётчикам [counters] и флагу сессии.
     */
    fun decide(
        counters: ReviewCounters,
        hasSessionAutoRequestRun: Boolean
    ): ReviewMilestoneDecision {
        val blockedBySessionOrPending =
            hasSessionAutoRequestRun || counters.pendingMilestone != null
        if (blockedBySessionOrPending) {
            return ReviewMilestoneDecision.None
        }
        val milestone =
            MILESTONE_THRESHOLDS
                .filter { counters.createdEntriesCount >= it }
                .filterNot { it in counters.passedMilestones }
                .maxOrNull()
        return if (milestone == null) {
            ReviewMilestoneDecision.None
        } else {
            ReviewMilestoneDecision.Plan(milestone)
        }
    }

    companion object {
        /** Пороги авто-запроса по числу созданных записей. */
        val MILESTONE_THRESHOLDS = listOf(1, 5, 15)
    }
}
