package com.myworkouts.domain.usecase

/**
 * Режим пикера рекордов (overview-screen, design Decision 4): лучший
 * отдельный подход (`reps ?: durationSec`) или лучшая суточная сумма
 * `sets × value` по записям одного упражнения.
 */
enum class RecordsMode {
    PER_SET,
    PER_DAY
}
