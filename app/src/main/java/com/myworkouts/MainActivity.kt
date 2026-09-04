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
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.db.standardExercises
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.model.AppTheme
import com.myworkouts.domain.model.isDark
import com.myworkouts.ui.screens.root.RootScreen
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Главная (и единственная) Activity приложения.
 *
 * Создаёт [com.myworkouts.data.preferences.AppSettingsDataStore] ровно один
 * раз — до `setContent` — и прокидывает его в [RootScreen]. DataStore нельзя
 * создавать дважды на один файл (`There are multiple DataStores active for
 * the same file`).
 */
class MainActivity : ComponentActivity() {
    /**
     * Запускает Compose-граф: создаёт DataStore, подписывается на настройки
     * темы и динамических цветов, перекрашивает статус-бар под текущую тему.
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

        val dataStore = AppSettingsDataStore(applicationContext)
        // DI-цепочка данных (design.md Decision 3): БД → репозиторий создаются
        // один раз, как и dataStore; prepopulate — standardExercises(context).
        val database =
            WorkoutDatabase.create(
                applicationContext,
                standardExercises(applicationContext)
            )
        val repository =
            RoomWorkoutDataRepository(
                exerciseDao = database.exerciseDao(),
                exerciseEntryDao = database.exerciseEntryDao()
            )

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
