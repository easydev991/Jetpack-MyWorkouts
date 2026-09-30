package com.myworkouts.ui.screens.more

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.myworkouts.analytics.NoopAnalyticsProvider
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview-компонентов экрана «Ещё» (конвенция проекта: светлая + тёмная
 * темы, `showBackground`, `locale = "ru"`, обёртка в
 * `JetpackMyWorkoutsTheme`). Ключевые состояния кнопки видимости вкладки
 * «Упражнения» — скрытая и показанная — рядом в одной колонке.
 */

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun MoreScreenPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            // Экран целиком: тоггл выключен, состояние по умолчанию
            MoreScreen(
                navController = rememberNavController(),
                showExercisesTab = false,
                onShowExercisesTabChange = {},
                analyticsProvider = NoopAnalyticsProvider()
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExercisesTabTogglePreview() {
    JetpackMyWorkoutsTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            // Вкладка скрыта: «Показать…» / показана: «Скрыть…»
            ExercisesTabToggle(showExercisesTab = false, onShowExercisesTabChange = {})
            ExercisesTabToggle(showExercisesTab = true, onShowExercisesTabChange = {})
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun GitHubButtonPreview() {
    JetpackMyWorkoutsTheme {
        GitHubButton(context = LocalContext.current)
    }
}
