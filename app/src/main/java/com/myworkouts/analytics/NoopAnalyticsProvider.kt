package com.myworkouts.analytics

/**
 * Noop-провайдер для debug и тестовых окружений: не выполняет никаких
 * действий при логировании событий.
 */
class NoopAnalyticsProvider : AnalyticsProvider {
    override fun log(event: AnalyticsEvent) = Unit
}
