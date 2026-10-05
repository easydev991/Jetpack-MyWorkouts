package com.myworkouts.ui.screens.exercises

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Превью [ExerciseListItemRow] (конвенция проекта: светлая + тёмная
 * темы, `showBackground`, `locale = "ru"`, обёртка в
 * `JetpackMyWorkoutsTheme`). Ключевые состояния: обычная строка
 * с комментарием, подсвеченная (открытое long-press меню) и строка
 * с длинным названием — «Австралийские подтягивания» + комментарий.
 */

private val previewPullups =
    Exercise(
        id = 1L,
        name = "Подтягивания",
        category = "PULL",
        isCustom = false,
        comment = "широкий хват",
        createdAt = 1_721_000_000_000L,
        measuredBy = ExerciseMeasuredBy.REPS,
        iconNumber = ExerciseIcons.PULLUPS
    )

private val previewPlank =
    Exercise(
        id = 2L,
        name = "Планка",
        category = "CORE",
        isCustom = false,
        comment = null,
        createdAt = 1_721_100_000_000L,
        measuredBy = ExerciseMeasuredBy.TIME,
        iconNumber = ExerciseIcons.PUSHUPS
    )

private val previewAustralianPullups =
    Exercise(
        id = 3L,
        name = "Австралийские подтягивания",
        category = "PULL",
        isCustom = false,
        comment = "ноги на опоре, тело в прямой линии",
        createdAt = 1_721_200_000_000L,
        measuredBy = ExerciseMeasuredBy.REPS,
        iconNumber = ExerciseIcons.PULLUPS_AUSTRALIAN
    )

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseListItemRowPreview() {
    JetpackMyWorkoutsTheme {
        // Surface задаёт LocalContentColor = onSurface — без него тексты
        // (без явного цвета) остаются чёрными в тёмной теме
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                // Обычная строка: с комментарием и лейблом «повторы»
                ExerciseListItemRow(exercise = previewPullups)
                // Подсвеченная — как при открытом long-press меню
                ExerciseListItemRow(exercise = previewPlank, isSelected = true)
            }
        }
    }
}

/**
 * Строка с длинным названием + комментарием: контроль отступов
 * между названием, лейблом «повторы» и комментарием на длинных
 * названиях (название переносится на вторую строку).
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun ExerciseListItemRowLongNamePreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            ExerciseListItemRow(
                exercise = previewAustralianPullups,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
