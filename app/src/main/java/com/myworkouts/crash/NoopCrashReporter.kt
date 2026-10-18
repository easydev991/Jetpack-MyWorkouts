package com.myworkouts.crash

import com.myworkouts.domain.crash.CrashReporter

/**
 * Noop-реализация [CrashReporter] для debug и тестовых окружений:
 * не выполняет никаких действий при записи сбоев (аналог
 * NoopAnalyticsProvider).
 */
class NoopCrashReporter : CrashReporter {
    override fun logException(
        throwable: Throwable,
        message: String?
    ) = Unit
}
