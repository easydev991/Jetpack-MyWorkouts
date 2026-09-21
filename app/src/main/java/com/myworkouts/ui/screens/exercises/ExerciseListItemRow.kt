package com.myworkouts.ui.screens.exercises

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.icons.ExerciseIcon

/**
 * Строка каталога: иконка упражнения + название + лейбл типа измерения,
 * комментарий ниже при наличии (spec «Строка списка ExerciseListItemRow»;
 * образец — `EntryListItemRow` главного экрана). Иконка — по общим
 * правилам [ExerciseIcon] (exercise-icon-display, design Decision 4);
 * `null`/неизвестный номер — строка как до изменения. Фон анимируется
 * в `surfaceVariant`, пока для строки открыто контекстное меню
 * (edit-delete-exercise §4.6.b).
 *
 * Семантика `Selected` — стандартный канал accessibility Compose;
 * читается androidTest-ом через `SemanticsProperties.Selected`.
 * `mergeDescendants = true` нужен, чтобы `onNodeWithText` находил
 * строку, а не отдельный `Text`, иначе свойство Selected останется
 * только у родителя (образец — `EntryListItemRow.kt:88–95`).
 */
@Composable
internal fun ExerciseListItemRow(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val backgroundColor =
        animateColorAsState(
            targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
            label = "rowBackground"
        ).value

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = backgroundColor)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {
                    selected = isSelected
                },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            // `start = 12.dp` на лейбле — гарантированный минимальный
            // зазор до названия: SpaceBetween оставляет 0dp, когда длинное
            // название (weight(1f)) занимает всю ширину.
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка упражнения перед названием по общим правилам
            // ExerciseIcon (exercise-icon-display): известный номер —
            // иконка 24dp, `null`/неизвестный — ничего не рисуется, место
            // не резервируется; лейбл типа измерения остаётся у правого
            // края (название сохраняет `weight(1f)`).
            ExerciseIcon(
                iconNumber = exercise.iconNumber,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // Локализованный лейбл типа измерения. Тотальный `when` без
            // `else`: при добавлении значения enum компилятор заставит
            // дописать ветку (design decision 5).
            Text(
                text =
                    when (exercise.measuredBy) {
                        ExerciseMeasuredBy.REPS -> stringResource(R.string.exercise_measured_by_reps)
                        ExerciseMeasuredBy.TIME -> stringResource(R.string.exercise_measured_by_time)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        exercise.comment?.let { comment ->
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
