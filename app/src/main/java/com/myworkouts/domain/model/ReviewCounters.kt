package com.myworkouts.domain.model

/**
 * Счётчики review-майлстоунов (add-rustore-in-app-review, design D3).
 *
 * @property createdEntriesCount Сколько записей создано за всё время
 *   (после последнего сброса счётчиков)
 * @property passedMilestones Пороги, для которых review-флоу уже
 *   запускался и был пройден
 * @property pendingMilestone Порог, запланированный к показу, но ещё
 *   не показанный; null — pending нет
 */
data class ReviewCounters(
    val createdEntriesCount: Int = 0,
    val passedMilestones: Set<Int> = emptySet(),
    val pendingMilestone: Int? = null
)
