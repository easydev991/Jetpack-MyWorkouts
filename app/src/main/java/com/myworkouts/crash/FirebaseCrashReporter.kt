@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package com.myworkouts.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myworkouts.domain.crash.CrashReporter

/**
 * Firebase-реализация [CrashReporter] (release): тело бывшего
 * `CrashlyticsHelper`. На JVM не тестируется (Firebase — Android API,
 * аналог FirebaseAnalyticsProvider, design D9).
 */
class FirebaseCrashReporter : CrashReporter {
    /** Кастомный ключ Crashlytics с контекстом операции. */
    private companion object {
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    override fun logException(
        throwable: Throwable,
        message: String?
    ) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            message?.let { crashlytics.setCustomKey(KEY_ERROR_MESSAGE, it) }
            crashlytics.recordException(throwable)
        } catch (e: Exception) {
            // Телеметрия не должна прерывать пользовательский сценарий.
        }
    }
}
