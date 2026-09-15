package com.myworkouts

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.myworkouts.domain.model.AppTheme
import com.myworkouts.domain.model.isDark
import com.myworkouts.ui.screens.root.RootScreen
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Главная (и единственная) Activity приложения.
 *
 * Ничего не создаёт: зависимости (`AppSettingsDataStore`, репозиторий)
 * берутся из [MyWorkoutsApplication] — композиционного корня процесса
 * (fix-entry-list-live-update, design.md Decision 1). Пересоздание
 * Activity больше не плодит инстансы БД/DataStore.
 */
class MainActivity : ComponentActivity() {
    /**
     * Запускает Compose-граф: подписывается на настройки темы и динамических
     * цветов, перекрашивает статус-бар под текущую тему.
     *
     * @implNote MainActivityViewModel не создан (YAGNI). Два collectAsState
     * на dataStore.{theme,useDynamicColors} — то же, что VM/factory +
     * stateIn(WhileSubscribed), но без 3 кейсов тестов на VM.
     * Двухфазный enableEdgeToEdge: первый вызов до
     * `setContent` (для cold-start), второй — в `LaunchedEffect(isDarkTheme)`
     * (для рантайм-переключения).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyWorkoutsApplication
        val dataStore = app.dataStore
        val repository = app.repository

        setContent {
            val theme by dataStore.theme.collectAsState(initial = AppTheme.SYSTEM)
            val useDynamicColors by dataStore.useDynamicColors.collectAsState(initial = true)
            val isDarkTheme = theme.isDark(isSystemInDarkTheme())

            // Перекрашиваем статус-бар при смене темы в рантайме. Без этого
            // MaterialTheme перекрасится, а статус-бар останется в стиле
            // cold-start (визуальная регрессия при переключении темы).
            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle =
                        if (isDarkTheme) {
                            SystemBarStyle.dark(Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        }
                )
            }

            JetpackMyWorkoutsTheme(
                appTheme = theme,
                dynamicColor = useDynamicColors
            ) {
                RootScreen(dataStore = dataStore, repository = repository)
            }
        }
    }
}
