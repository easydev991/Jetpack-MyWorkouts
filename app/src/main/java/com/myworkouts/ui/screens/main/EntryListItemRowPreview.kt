package com.myworkouts.ui.screens.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Превью строки записи: иконка упражнения, доп. вес и комментарий
 * (конвенция проекта: светлая + тёмная темы, `showBackground`,
 * `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`).
 *
 * Ключевые состояния иконки (exercise-icon-display): назначенная у
 * большинства фикстур (Подтягивания — 13, Брусья — 7, Планка — 15) и
 * отсутствующая у «Становой тяги» — строка без иконки. Ключевые
 * состояния веса (spec «Дополнительный вес в строке записи»):
 * целый (5.0 → «5 кг»), дробный (2.5 → «2.5 кг») и без веса — строка
 * выглядит как одна «название — значение». Ключевые состояния
 * комментария (spec «Комментарий в строке записи», change
 * mainscreen-itemrow-comment) — в [EntryListItemRowCommentPreview]
 * ниже. Отдельно от `EntryListItemRowPreview` в `MainScreenPreview.kt`,
 * который покрывает форматы значения.
 */

private val previewWholeWeightItem =
    EntryListItem(
        id = 1L,
        exerciseName = "Подтягивания",
        iconNumber = ExerciseIcons.PULLUPS,
        sets = 4,
        reps = 10,
        durationSec = null,
        weightKg = 5.0,
        date = 1_721_000_000_000L
    )

private val previewFractionalWeightItem =
    EntryListItem(
        id = 2L,
        exerciseName = "Отжимания на брусьях",
        iconNumber = ExerciseIcons.BARS,
        sets = 3,
        reps = 12,
        durationSec = null,
        weightKg = 2.5,
        date = 1_721_100_000_000L
    )

private val previewNoWeightItem =
    EntryListItem(
        id = 3L,
        exerciseName = "Планка",
        iconNumber = ExerciseIcons.PUSHUPS,
        sets = 1,
        reps = null,
        durationSec = 20,
        date = 1_721_200_000_000L
    )

private val previewShortCommentItem =
    EntryListItem(
        id = 4L,
        exerciseName = "Подтягивания",
        iconNumber = ExerciseIcons.PULLUPS,
        sets = 4,
        reps = 10,
        durationSec = null,
        comment = "сразу после сна",
        date = 1_721_400_000_000L
    )

private val previewLongCommentWithWeightItem =
    EntryListItem(
        id = 5L,
        exerciseName = "Становая тяга",
        // Кастомное упражнение без назначенной иконки — состояние
        // «строка без иконки» (exercise-icon-display)
        iconNumber = null,
        sets = 5,
        reps = 5,
        durationSec = null,
        weightKg = 80.0,
        // Длиннее двух строк: в превью видно «…» (обрезка по maxLines)
        comment =
            "Держал спину прямо, лопатки сведены, темп медленный; " +
                "в следующий раз добавить вес и отдохнуть подольше между подходами",
        date = 1_721_500_000_000L
    )

private val previewNoCommentItem =
    EntryListItem(
        id = 6L,
        exerciseName = "Планка",
        iconNumber = ExerciseIcons.PUSHUPS,
        sets = 1,
        reps = null,
        durationSec = 20,
        date = 1_721_600_000_000L
    )

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun EntryListItemRowWeightPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 4×10 + вес 5.0 → «Дополнительный вес … 5 кг»
                EntryListItemRow(
                    item = previewWholeWeightItem,
                    separator = " х ",
                    isSelected = false,
                    onOpenMenu = {}
                )
                // 3×12 + вес 2.5 → «2.5 кг»
                EntryListItemRow(
                    item = previewFractionalWeightItem,
                    separator = " х ",
                    isSelected = false,
                    onOpenMenu = {}
                )
                // 20 без веса — одна строка, как до изменения
                EntryListItemRow(
                    item = previewNoWeightItem,
                    separator = " х ",
                    isSelected = false,
                    onOpenMenu = {}
                )
            }
        }
    }
}

/**
 * Ключевые состояния комментария (spec «Комментарий в строке записи»,
 * change mainscreen-itemrow-comment): короткий — целиком под «название —
 * значение»; длинный — обрезается в 2 строки с многоточием и идёт после
 * строки веса (заодно показывает «комментарий вместе с весом»); без
 * комментария — вид как до изменения.
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun EntryListItemRowCommentPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Короткий комментарий без веса — под «название — значение»
                EntryListItemRow(
                    item = previewShortCommentItem,
                    separator = " х ",
                    isSelected = false,
                    onOpenMenu = {}
                )
                // Длинный комментарий с весом — после строки веса, «…»
                EntryListItemRow(
                    item = previewLongCommentWithWeightItem,
                    separator = " х ",
                    isSelected = false,
                    onOpenMenu = {}
                )
                // Без комментария — как до изменения
                EntryListItemRow(
                    item = previewNoCommentItem,
                    separator = " х ",
                    isSelected = false,
                    onOpenMenu = {}
                )
            }
        }
    }
}
