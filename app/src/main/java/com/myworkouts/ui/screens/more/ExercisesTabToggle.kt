package com.myworkouts.ui.screens.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myworkouts.R

/**
 * Кнопка видимости вкладки «Упражнения» (exercises-tab, design
 * Decision 8): текст зависит от состояния — «Показать вкладку…» при
 * скрытой вкладке, «Скрыть…» при показанной. Тап по кнопке переключает
 * настройку.
 *
 * Вынесена из [MoreScreen] ради лимита detekt `TooManyFunctions: 11`.
 */
@Composable
internal fun ExercisesTabToggle(
    showExercisesTab: Boolean,
    onShowExercisesTabChange: (Boolean) -> Unit
) {
    ScreenButton(
        stringResource(
            if (showExercisesTab) R.string.more_exercises_tab_hide else R.string.more_exercises_tab_show
        )
    ) {
        onShowExercisesTabChange(!showExercisesTab)
    }
}
