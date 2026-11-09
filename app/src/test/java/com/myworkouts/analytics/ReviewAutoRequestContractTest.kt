package com.myworkouts.analytics

import com.myworkouts.domain.review.ReviewErrorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Контрактные тесты события авто-запроса review-флоу (tasks 5.1, design D9):
 * фиксируют wire-контракт [AnalyticsEvent.ReviewAutoRequest], а не
 * содержательную логику — полный набор wire-значений [ReviewAutoRequestOutcome],
 * наличие типа ошибки только у исхода ERROR и прокидку события через порт
 * [AnalyticsProvider] на fake-провайдере. Firebase-ветка провайдера на JVM
 * не тестируется (Bundle/Firebase — Android API, design D9): wire-стабильность
 * прикрыта code review и openspec-циклом; содержательная логика показа
 * тестируется в tasks 7.3.
 */
class ReviewAutoRequestContractTest {
    @Test
    fun outcome_contains_exactly_shown_and_error_wire_values() {
        // Given — имена enum-констант являются wire-значениями параметра
        // outcome события review_auto_request, отдельное строковое поле не
        // вводится (канон — ReviewFlowContractTest.error_type_contains_exactly_six_wire_values)
        val expected =
            listOf(
                "SHOWN",
                "ERROR"
            )
        // When
        val actual = ReviewAutoRequestOutcome.entries.map { it.name }
        // Then — полный набор wire-значений в порядке объявления
        assertEquals(expected, actual)
    }

    @Test
    fun shown_event_logged_without_error_type() {
        // Given — fake-провайдер, копящий события без обращения к Firebase (design D9)
        val provider = FakeAnalyticsProvider()

        // When — событие успешного показа формы логируется через AnalyticsProvider;
        // конструктор вызывается без номера milestone — состав полей события
        // (outcome + errorType) зафиксирован компиляцией
        provider.log(AnalyticsEvent.ReviewAutoRequest(ReviewAutoRequestOutcome.SHOWN))

        // Then — ровно одно событие; исход SHOWN без типа ошибки. When без else
        // компилируется только при исчерпывающем покрытии всех типов
        // AnalyticsEvent — состав sealed-иерархии зафиксирован на компиляции
        val event =
            when (val captured = provider.events.single()) {
                is AnalyticsEvent.ReviewAutoRequest -> captured
                is AnalyticsEvent.AppError,
                is AnalyticsEvent.ScreenView,
                is AnalyticsEvent.UserAction -> throw AssertionError(
                    "Ожидалось событие ReviewAutoRequest, получено ${captured::class.simpleName}"
                )
            }
        assertEquals(ReviewAutoRequestOutcome.SHOWN, event.outcome)
        assertNull(event.errorType)
    }

    @Test
    fun error_event_logged_with_wire_error_type() {
        // Given — fake-провайдер; ошибка запуска SDK-флоу классифицирована
        // доменным типом ReviewErrorType (переиспользуется из domain/review/,
        // одна классификация на домен и аналитику)
        val provider = FakeAnalyticsProvider()
        val expected =
            AnalyticsEvent.ReviewAutoRequest(ReviewAutoRequestOutcome.ERROR, ReviewErrorType.OUTDATED)

        // When
        provider.log(expected)

        // Then — событие прошло через порт и сохранило состав полей
        // (равенство data class: outcome + errorType, без дополнительных полей)
        assertEquals(listOf(expected), provider.events)
        val event =
            when (val captured = provider.events.single()) {
                is AnalyticsEvent.ReviewAutoRequest -> captured
                is AnalyticsEvent.AppError,
                is AnalyticsEvent.ScreenView,
                is AnalyticsEvent.UserAction -> throw AssertionError(
                    "Ожидалось событие ReviewAutoRequest, получено ${captured::class.simpleName}"
                )
            }
        assertEquals(ReviewErrorType.OUTDATED, event.errorType)
    }
}
