package com.myworkouts.domain.crash

/**
 * Fake-репортер сбоев для JVM-тестов: копит вызовы [logException] в
 * журнале в порядке поступления — тесты проверяют запись сбоя без
 * обращения к Firebase (по образцу FakeAnalyticsProvider).
 */
class FakeCrashReporter : CrashReporter {
    /** Запись журнала: исключение и контекст операции. */
    data class Entry(
        val throwable: Throwable,
        val message: String?
    )

    /** Вызовы [logException] в порядке поступления. */
    val logged = mutableListOf<Entry>()

    override fun logException(
        throwable: Throwable,
        message: String?
    ) {
        logged.add(Entry(throwable, message))
    }
}
