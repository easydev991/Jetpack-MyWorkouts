package com.myworkouts.ui.screens.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.EntryListItem

/**
 * Переиспользуемая строка списка записей главного экрана
 * (workout-entries-list, design Decision 5).
 *
 * Слева — название упражнения, справа с выравниванием по правому краю —
 * значение подходов и повторов/длительности (формат
 * «4 х 10»/«10» через [formatEntryValue]). Без разделителя-запятой
 * между ними — `Row` с `SpaceBetween` распределяет визуально.
 *
 * Фон анимируется между `colorScheme.surface` и `surfaceVariant` в
 * зависимости от [isSelected] — пока открыто контекстное меню
 * этой записи, строка подсвечивается. Семантика `Selected`
 * (`Modifier.semantics { selected = ... }`) дублирует состояние
 * для accessibility и Compose UI Test.
 *
 * @param item Элемент списка (проекция из DAO JOIN)
 * @param separator Локализованный разделитель (« х »/« x »)
 * @param isSelected `true` пока для этой записи открыто контекстное
 *   меню (design Decision 5)
 * @param modifier Внешний модификатор (для gesture/position из списка)
 */
@Composable
internal fun EntryListItemRow(
    item: EntryListItem,
    separator: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val value =
        formatEntryValue(
            sets = item.sets,
            value = item.reps ?: item.durationSec ?: 0,
            separator = separator
        )
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

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = backgroundColor)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {
                    // Стандартный канал accessibility Compose; читается
                    // в androidTest через `SemanticsProperties.Selected`.
                    // `mergeDescendants = true` нужен, чтобы `onNodeWithText`
                    // находил Row (а не отдельный Text), иначе свойство
                    // Selected останется только у родителя.
                    selected = isSelected
                },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.exerciseName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End
        )
    }
}
