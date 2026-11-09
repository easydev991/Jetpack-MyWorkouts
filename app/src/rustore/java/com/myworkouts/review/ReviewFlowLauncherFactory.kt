package com.myworkouts.review

import android.content.Context
import com.myworkouts.domain.review.ReviewFlowLauncher
import ru.rustore.sdk.review.RuStoreReviewManagerFactory

/**
 * Фабрика лаунчера review-флоу для flavor `rustore` (tasks 4.1, заменяет
 * заглушку 1.2): публичный FQN [ReviewFlowLauncherFactory] и сигнатура
 * [create] совпадают с github-фабрикой (design D1), wiring в DI (задача
 * 6.1) выбирает реализацию flavor-ом без условной логики. Возвращаемый
 * тип — доменный интерфейс [ReviewFlowLauncher], не конкретный класс.
 *
 * Создает лаунчер на RuStore Review SDK: менеджер строится
 * [RuStoreReviewManagerFactory.create], обёртка [RuStoreReviewFlowLauncher]
 * реализует доменный контракт.
 */
object ReviewFlowLauncherFactory {
    /**
     * Создаёт лаунчер review-флоу для переданного контекста.
     */
    fun create(context: Context): ReviewFlowLauncher =
        RuStoreReviewFlowLauncher(RuStoreReviewManagerFactory.create(context))
}
