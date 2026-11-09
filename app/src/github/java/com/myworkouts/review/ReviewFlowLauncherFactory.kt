package com.myworkouts.review

import android.content.Context
import com.myworkouts.domain.review.ReviewFlowLauncher

/**
 * Фабрика лаунчера review-флоу для flavor `github` (tasks 4.2, заменяет
 * заглушку 1.2): публичный FQN [ReviewFlowLauncherFactory] и сигнатура
 * [create] совпадают с rustore-фабрикой (design D1), wiring в DI (задача
 * 6.1) выбирает реализацию flavor-ом без условной логики. Возвращаемый
 * тип — доменный интерфейс [ReviewFlowLauncher], не конкретный класс.
 *
 * В github-сборке review-функциональность отсутствует, поэтому фабрика
 * выдаёт [GithubNoOpReviewLauncher] — no-op реализацию доменного
 * контракта. SDK-зависимостей у flavor нет в принципе (никаких
 * `ru.rustore.*`); контекст принимается только для совпадения сигнатуры
 * с rustore-вариантом.
 */
object ReviewFlowLauncherFactory {
    /**
     * Создаёт лаунчер review-флоу для github-сборки.
     */
    fun create(context: Context): ReviewFlowLauncher = GithubNoOpReviewLauncher()
}
