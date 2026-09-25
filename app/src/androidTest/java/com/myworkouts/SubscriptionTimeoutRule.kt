package com.myworkouts

import com.myworkouts.ui.viewmodel.SUBSCRIPTION_TIMEOUT_PROPERTY
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Класс-правило JUnit4: на время тест-класса понижает таймаут подписки
 * StateFlow с продовых 5 секунд до 100 мс, чтобы LiveUpdate-тесты не
 * пережидали реальный таймаут `WhileSubscribed(5000)`. Использовать как
 * `@JvmField @ClassRule val` в companion object тест-класса.
 */
class SubscriptionTimeoutRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                System.setProperty(SUBSCRIPTION_TIMEOUT_PROPERTY, "100")
                try {
                    base.evaluate()
                } finally {
                    System.clearProperty(SUBSCRIPTION_TIMEOUT_PROPERTY)
                }
            }
        }
}
