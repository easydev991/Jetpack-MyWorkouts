package com.myworkouts.ui.screens.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.navigation.Screen
import com.myworkouts.navigation.resolve
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Обработка одноразового запроса шортката лаунчера «Добавить
 * тренировку» (add-workout-quick-action, design D4/D5): запрос от
 * MainActivity выполняется после построения графа — форма кладётся
 * поверх текущего стека (обычно main → entry_create, при старте с
 * другой вкладки — например main → exercises → entry_create). Guard
 * резолвера смотрит весь back stack: форма не открывается повторно
 * ни когда она наверху, ни когда над ней открыт экран настроек записи
 * (entry_settings). Эффект стартует после первой композиции, когда
 * NavHost уже выставил граф и стартовый destination. Флаг сбрасывается
 * сразу (re-arm на следующий шорткат), решение резолвера логируется
 * в аналитику тем же событием, что и FAB.
 */
@Composable
internal fun ShortcutLaunchEffect(
    navController: NavHostController,
    pendingAddWorkout: MutableStateFlow<Boolean>,
    analyticsProvider: AnalyticsProvider
) {
    LaunchedEffect(navController) {
        pendingAddWorkout.collect { requested ->
            if (!requested) return@collect
            pendingAddWorkout.value = false
            val entryCreateOnStack =
                navController.currentBackStack.value
                    .any { it.destination.route == Screen.EntryCreate.route }
            val decision = resolve(entryCreateOnStack) ?: return@collect
            analyticsProvider.log(AnalyticsEvent.UserAction(decision))
            navController.navigate(Screen.EntryCreate.createRoute())
        }
    }
}
