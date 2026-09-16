package com.myworkouts.analytics

/**
 * Fake-провайдер аналитики для JVM-тестов (design D9): копит события
 * в списке в порядке поступления — тесты проверяют [AnalyticsEvent.AppError]
 * в failure-сценариях и [AnalyticsEvent.UserAction] на вызовах операций
 * без обращения к Firebase.
 */
class FakeAnalyticsProvider : AnalyticsProvider {
    /** События в порядке логирования. */
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events.add(event)
    }
}
