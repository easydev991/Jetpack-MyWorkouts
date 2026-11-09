package com.myworkouts.review

import com.myworkouts.domain.review.ReviewErrorType
import com.myworkouts.domain.review.ReviewFlowLauncher
import com.myworkouts.domain.review.ReviewFlowOutcome

/**
 * No-op реализация [ReviewFlowLauncher] для flavor `github` (tasks 4.2).
 *
 * В github-сборке review-функциональность отсутствует (нет магазина
 * с нативной формой оценки), поэтому лаунчер никогда не вызывается
 * wiring-ом: гейт `isReviewEnabled = false` не пропускает кодовые пути
 * до запуска review-флоу. Вызов — защитный сценарий: возвращается
 * доменный исход [ReviewFlowOutcome.Error] с [ReviewErrorType.UNKNOWN]
 * вместо выброса исключения, чтобы вызывающий код не падал.
 *
 * Реализация доменного контракта без SDK-типов: никаких импортов
 * `ru.rustore.*` (компиляция github-варианта без SDK — проверка
 * изоляции, verify 4.2) и android-типов.
 */
class GithubNoOpReviewLauncher : ReviewFlowLauncher {
    override suspend fun launch(): ReviewFlowOutcome = ReviewFlowOutcome.Error(ReviewErrorType.UNKNOWN)
}
