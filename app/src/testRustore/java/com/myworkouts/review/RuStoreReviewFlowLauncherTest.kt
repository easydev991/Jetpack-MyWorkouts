package com.myworkouts.review

import com.myworkouts.domain.review.ReviewErrorType
import com.myworkouts.domain.review.ReviewFlowOutcome
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.rustore.sdk.core.exception.RuStoreApplicationBannedException
import ru.rustore.sdk.core.exception.RuStoreException
import ru.rustore.sdk.core.exception.RuStoreNotInstalledException
import ru.rustore.sdk.core.exception.RuStoreOutdatedException
import ru.rustore.sdk.core.exception.RuStoreUserBannedException
import ru.rustore.sdk.core.exception.RuStoreUserUnauthorizedException
import ru.rustore.sdk.core.tasks.Task
import ru.rustore.sdk.review.RuStoreReviewManager
import ru.rustore.sdk.review.errors.RuStoreInvalidReviewInfo
import ru.rustore.sdk.review.errors.RuStoreRequestLimitReached
import ru.rustore.sdk.review.errors.RuStoreReviewExists
import ru.rustore.sdk.review.model.ReviewInfo

/**
 * Тесты SDK-лаунчера review-флоу flavor `rustore` (tasks 4.1).
 *
 * Менеджер RuStore Review SDK подменяется MockK-обёрткой: проверяются порядок
 * фаз (requestReviewFlow → launchReviewFlow(ReviewInfo)), успешный исход и
 * маппинг иерархии `RuStoreException` в доменный enum [ReviewErrorType].
 *
 * `Task.await()` SDK — не suspend, поэтому стабы через `every`, а не `coEvery`.
 * Отличие от эталона (JetpackDays): доменная ошибка — enum, а не sealed
 * `ReviewException`, поэтому исходы сравниваются равенством
 * `ReviewFlowOutcome.Error(ReviewErrorType.X)`; неименованные подтипы
 * иерархии (бан-ветки, `RuStoreInvalidReviewInfo`) уходят в UNKNOWN —
 * классификация сознательно не шире шести значений (design D2).
 */
class RuStoreReviewFlowLauncherTest {
    private val manager: RuStoreReviewManager = mockk()
    private val reviewInfo: ReviewInfo = mockk()

    private fun launcher() = RuStoreReviewFlowLauncher(manager)

    /** Стабит успешную фазу подготовки: requestReviewFlow → ReviewInfo. */
    private fun requestReviewFlowSucceeds() {
        val task: Task<ReviewInfo> = mockk()
        every { task.await() } returns reviewInfo
        every { manager.requestReviewFlow() } returns task
    }

    /** Стабит падение фазы подготовки заданной ошибкой SDK. */
    private fun requestReviewFlowFails(sdkError: RuStoreException) {
        val task: Task<ReviewInfo> = mockk()
        every { task.await() } throws sdkError
        every { manager.requestReviewFlow() } returns task
    }

    /** Стабит успешную фазу показа: launchReviewFlow(ReviewInfo) → Unit. */
    private fun launchReviewFlowSucceeds() {
        val task: Task<Unit> = mockk()
        every { task.await() } returns Unit
        every { manager.launchReviewFlow(reviewInfo) } returns task
    }

    /** Прогон: фаза подготовки падает [sdkError], ожидается маппинг в [expected]. */
    private fun assertSdkMapped(
        sdkError: RuStoreException,
        expected: ReviewErrorType
    ) = runTest {
        // Given — подготовка данных падает ошибкой SDK
        requestReviewFlowFails(sdkError)

        // When
        val outcome = launcher().launch()

        // Then — ошибка возвращается значением исхода, а не выбросом
        assertEquals(ReviewFlowOutcome.Error(expected), outcome)
    }

    @Test
    fun success_flow_returns_shown_and_runs_both_phases_in_order() =
        runTest {
            // Given — обе фазы SDK завершаются успешно
            requestReviewFlowSucceeds()
            launchReviewFlowSucceeds()

            // When
            val outcome = launcher().launch()

            // Then — фазы двухфазного флоу идут по порядку: подготовка → показ
            assertEquals(ReviewFlowOutcome.Shown, outcome)
            verifyOrder {
                manager.requestReviewFlow()
                manager.launchReviewFlow(reviewInfo)
            }
        }

    @Test
    fun phase_one_error_skips_phase_two_and_maps_to_error_type() =
        runTest {
            // Given — подготовка падает RequestLimitReached
            requestReviewFlowFails(RuStoreRequestLimitReached())

            // When
            val outcome = launcher().launch()

            // Then — ошибка первой фазы прерывает флоу: показ не запускается
            assertEquals(ReviewFlowOutcome.Error(ReviewErrorType.REQUEST_LIMIT_REACHED), outcome)
            coVerify(exactly = 0) { manager.launchReviewFlow(any()) }
        }

    @Test
    fun phase_two_error_maps_to_error_type() =
        runTest {
            // Given — подготовка успешна, показ падает ReviewExists
            requestReviewFlowSucceeds()
            val task: Task<Unit> = mockk()
            every { task.await() } throws RuStoreReviewExists()
            every { manager.launchReviewFlow(reviewInfo) } returns task

            // When
            val outcome = launcher().launch()

            // Then
            assertEquals(ReviewFlowOutcome.Error(ReviewErrorType.REVIEW_EXISTS), outcome)
        }

    @Test
    fun not_installed_exception_maps_to_not_installed() =
        assertSdkMapped(RuStoreNotInstalledException(), ReviewErrorType.NOT_INSTALLED)

    @Test
    fun outdated_exception_maps_to_outdated() = assertSdkMapped(RuStoreOutdatedException(), ReviewErrorType.OUTDATED)

    @Test
    fun user_unauthorized_exception_maps_to_user_unauthorized() =
        assertSdkMapped(RuStoreUserUnauthorizedException(), ReviewErrorType.USER_UNAUTHORIZED)

    @Test
    fun request_limit_reached_maps_to_request_limit_reached() =
        assertSdkMapped(RuStoreRequestLimitReached(), ReviewErrorType.REQUEST_LIMIT_REACHED)

    @Test
    fun review_exists_maps_to_review_exists() = assertSdkMapped(RuStoreReviewExists(), ReviewErrorType.REVIEW_EXISTS)

    @Test
    fun application_banned_exception_maps_to_unknown() =
        assertSdkMapped(RuStoreApplicationBannedException(), ReviewErrorType.UNKNOWN)

    @Test
    fun user_banned_exception_maps_to_unknown() = assertSdkMapped(RuStoreUserBannedException(), ReviewErrorType.UNKNOWN)

    @Test
    fun invalid_review_info_maps_to_unknown() = assertSdkMapped(RuStoreInvalidReviewInfo(), ReviewErrorType.UNKNOWN)

    @Test
    fun base_ru_store_exception_maps_to_unknown() =
        assertSdkMapped(RuStoreException("сбой SDK"), ReviewErrorType.UNKNOWN)

    /**
     * Runtime-исключение SDK вне иерархии `RuStoreException` (напр.
     * `IllegalStateException` при отвале binding) возвращается значением
     * исхода UNKNOWN, а не выбросом — фича «тихая», краш ей противоречит.
     */
    @Test
    fun non_rustore_runtime_exception_returns_unknown_outcome() =
        runTest {
            // Given — фаза подготовки падает исключением вне RuStoreException
            val task: Task<ReviewInfo> = mockk()
            every { task.await() } throws IllegalStateException("binding отвалился")
            every { manager.requestReviewFlow() } returns task

            // When
            val outcome = launcher().launch()

            // Then — исключение не выбрасывается
            assertEquals(ReviewFlowOutcome.Error(ReviewErrorType.UNKNOWN), outcome)
            coVerify(exactly = 0) { manager.launchReviewFlow(any()) }
        }
}
