package com.myworkouts.ui.icons

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/** Тег узла иконки упражнения в Compose UI-тестах. */
internal const val EXERCISE_ICON_TEST_TAG = "exercise_icon"

/**
 * Единый рендер иконки упражнения по номеру
 * (exercise-icon-display, design Decision 4).
 *
 * [ExerciseIcons.resolve] + выбор варианта по теме системы: тёмный —
 * при тёмной теме, светлый — при светлой. Размер 24dp, tint отсутствует.
 * `contentDescription = null` — название упражнения озвучивается рядом
 * стоящим текстом строки. `null` или номер вне таблицы — ничего не
 * рисует (тихий fallback, без плейсхолдера и без резерва места).
 *
 * Для UI-тестов рисуемая иконка помечается [EXERCISE_ICON_TEST_TAG]
 * (`Modifier.testTag` — канал только для тестов, в accessibility не
 * попадает): иначе `Image` без contentDescription не найти из
 * Compose-тестов.
 */
@Composable
internal fun ExerciseIcon(
    iconNumber: Int?,
    modifier: Modifier = Modifier
) {
    val ref = ExerciseIcons.resolve(iconNumber) ?: return
    val resId = if (isSystemInDarkTheme()) ref.dark else ref.light
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier =
            modifier
                .testTag(EXERCISE_ICON_TEST_TAG)
                .size(24.dp)
    )
}

/** Строка-образец для превью: иконка (если есть) + подпись состояния. */
@Composable
private fun ExerciseIconSample(
    iconNumber: Int?,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExerciseIcon(iconNumber = iconNumber)
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(name = "Иконка есть — Light", showBackground = true, locale = "ru")
@Composable
private fun ExerciseIconPresentLightPreview() {
    JetpackMyWorkoutsTheme {
        ExerciseIconSample(
            iconNumber = ExerciseIcons.PULLUPS,
            label = "Подтягивания"
        )
    }
}

@Preview(
    name = "Иконка есть — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun ExerciseIconPresentDarkPreview() {
    JetpackMyWorkoutsTheme {
        ExerciseIconSample(
            iconNumber = ExerciseIcons.PULLUPS,
            label = "Подтягивания"
        )
    }
}

@Preview(name = "Иконки нет — Light", showBackground = true, locale = "ru")
@Composable
private fun ExerciseIconAbsentLightPreview() {
    JetpackMyWorkoutsTheme {
        ExerciseIconSample(
            iconNumber = null,
            label = "Без иконки"
        )
    }
}

@Preview(
    name = "Иконки нет — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun ExerciseIconAbsentDarkPreview() {
    JetpackMyWorkoutsTheme {
        ExerciseIconSample(
            iconNumber = null,
            label = "Без иконки"
        )
    }
}
