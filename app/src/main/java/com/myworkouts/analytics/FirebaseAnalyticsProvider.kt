@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package com.myworkouts.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase-провайдер аналитики (release, design D4). Wire-значения —
 * имена enum-констант (спека «Событийная модель аналитики»); на JVM не
 * тестируется (Bundle/Firebase — Android API, design D9). Wire-стабильность
 * прикрыта code review и openspec-циклом (design D9).
 *
 * @param context контекст приложения
 */
class FirebaseAnalyticsProvider(
    context: Context
) : AnalyticsProvider {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun log(event: AnalyticsEvent) {
        try {
            when (event) {
                is AnalyticsEvent.ScreenView -> logScreenView(event)
                is AnalyticsEvent.UserAction -> logUserAction(event)
                is AnalyticsEvent.AppError -> logAppError(event)
            }
        } catch (e: Exception) {
            // Телеметрия не должна прерывать пользовательский сценарий
            // (спека «Безопасность вызовов телеметрии»).
        }
    }

    private fun logScreenView(event: AnalyticsEvent.ScreenView) {
        val params =
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, event.screen.name)
            }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
    }

    private fun logUserAction(event: AnalyticsEvent.UserAction) {
        val params =
            Bundle().apply {
                putString("action", event.action.name)
                event.value?.let { putString("value", it) }
            }
        firebaseAnalytics.logEvent("user_action", params)
    }

    private fun logAppError(event: AnalyticsEvent.AppError) {
        val params =
            Bundle().apply {
                putString("operation", event.operation.name)
                putString("error_domain", event.throwable.javaClass.name)
            }
        firebaseAnalytics.logEvent("app_error", params)
    }
}
