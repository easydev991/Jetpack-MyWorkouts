package com.myworkouts.analytics

/**
 * Точка поставки событий аналитики. Реализации: Firebase (release) и
 * Noop (debug/тесты) — выбор в MainActivity по BuildConfig.DEBUG
 * (design D4); в ViewModel инъекция параметром для перехвата в тестах
 * (спека «Перехват событий в тестах»).
 */
interface AnalyticsProvider {
    /**
     * Логирует событие аналитики.
     *
     * @param event событие для логирования
     */
    fun log(event: AnalyticsEvent)
}
