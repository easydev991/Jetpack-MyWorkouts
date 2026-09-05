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
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Превью строки записи с доп. весом (конвенция проекта: светлая + тёмная
 * темы, `showBackground`, `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`).
 *
 * Ключевые состояния компонента (spec «Дополнительный вес в строке
 * записи»): целый вес (5.0 → «5 кг»), дробный (2.5 → «2.5 кг») и без
 * веса — строка выглядит как одна «название — значение». Отдельно от
 * `EntryListItemRowPreview` в `MainScreenPreview.kt`, который остался
 * без изменений.
 */

private val previewWholeWeightItem =
    EntryListItem(
        id = 1L,
        exerciseName = "Подтягивания",
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
        sets = 1,
        reps = null,
        durationSec = 20,
        date = 1_721_200_000_000L
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
                    isSelected = false
                )
                // 3×12 + вес 2.5 → «2.5 кг»
                EntryListItemRow(
                    item = previewFractionalWeightItem,
                    separator = " х ",
                    isSelected = false
                )
                // 20 без веса — одна строка, как до изменения
                EntryListItemRow(
                    item = previewNoWeightItem,
                    separator = " х ",
                    isSelected = false
                )
            }
        }
    }
}
