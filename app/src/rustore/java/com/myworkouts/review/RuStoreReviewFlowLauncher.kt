package com.myworkouts.review

import com.myworkouts.domain.review.ReviewErrorType
import com.myworkouts.domain.review.ReviewFlowLauncher
import com.myworkouts.domain.review.ReviewFlowOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.rustore.sdk.core.exception.RuStoreException
import ru.rustore.sdk.core.exception.RuStoreNotInstalledException
import ru.rustore.sdk.core.exception.RuStoreOutdatedException
import ru.rustore.sdk.core.exception.RuStoreUserUnauthorizedException
import ru.rustore.sdk.review.RuStoreReviewManager
import ru.rustore.sdk.review.errors.RuStoreRequestLimitReached
import ru.rustore.sdk.review.errors.RuStoreReviewExists

/**
 * SDK-лаунчер review-флоу для flavor `rustore` (tasks 4.1) — реализация
 * доменного контракта [ReviewFlowLauncher] поверх RuStore Review SDK
 * (версия SDK подключается flavor-зависимостью, design D7).
 *
 * Флоу двухфазный: подготовка [RuStoreReviewManager.requestReviewFlow]
 * (получение `ReviewInfo`, ~5 минут жизни) и показ
 * [RuStoreReviewManager.launchReviewFlow]. Любая ошибка иерархии
 * [RuStoreException] маппится в доменный [ReviewErrorType] и возвращается
 * значением [ReviewFlowOutcome.Error], а не выбросом; классификация
 * сознательно не шире шести значений — неименованные подтипы иерархии
 * (бан-ветки, `RuStoreInvalidReviewInfo`) уходят в UNKNOWN (design D2).
 */
class RuStoreReviewFlowLauncher(
    private val manager: RuStoreReviewManager
) : ReviewFlowLauncher {
    /**
     * Запускает двухфазный флоу и дожидается его завершения.
     *
     * Ошибка на любой фазе прерывает флоу (вторая фаза не выполняется) и
     * возвращается как [ReviewFlowOutcome.Error] — включая runtime-исключения
     * SDK вне иерархии [RuStoreException] (маппятся в UNKNOWN); только
     * [CancellationException] пробрасывается дальше (кооперативная отмена).
     *
     * Весь SDK-флоу выполняется вне главного потока: `Task.await()` SDK
     * блокирует поток (CountDownLatch), а доставку результата SDK выполняет
     * через main-looper — вызов на main-диспетчере = взаимная блокировка
     * и ANR (урок эталона, design Risks).
     */
    override suspend fun launch(): ReviewFlowOutcome =
        withContext(Dispatchers.IO) {
            try {
                val reviewInfo = manager.requestReviewFlow().await()
                manager.launchReviewFlow(reviewInfo).await()
                ReviewFlowOutcome.Shown
            } catch (e: RuStoreException) {
                ReviewFlowOutcome.Error(e.toReviewErrorType())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Не-RuStoreException (runtime-сбои SDK вне иерархии, напр.
                // IllegalStateException при отвале binding) — тоже значение
                // исхода, а не выброс: фича «тихая», краш ей противоречит.
                ReviewFlowOutcome.Error(ReviewErrorType.UNKNOWN)
            }
        }

    /**
     * Маппит ошибку SDK в доменную классификацию [ReviewErrorType].
     *
     * Порядок веток: именованные подтипы → их константы, остальное
     * (включая бан-ветки и прочие неименованные подтипы) — UNKNOWN;
     * без ветки else неименованные подтипы ушли бы исключением.
     */
    private fun RuStoreException.toReviewErrorType(): ReviewErrorType =
        when (this) {
            is RuStoreNotInstalledException -> ReviewErrorType.NOT_INSTALLED
            is RuStoreOutdatedException -> ReviewErrorType.OUTDATED
            is RuStoreUserUnauthorizedException -> ReviewErrorType.USER_UNAUTHORIZED
            is RuStoreRequestLimitReached -> ReviewErrorType.REQUEST_LIMIT_REACHED
            is RuStoreReviewExists -> ReviewErrorType.REVIEW_EXISTS
            else -> ReviewErrorType.UNKNOWN
        }
}
