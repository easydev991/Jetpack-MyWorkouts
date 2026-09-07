package com.myworkouts.ui.screens.main

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
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.util.formatDurationResource

/**
 * Переиспользуемая строка списка записей главного экрана
 * (workout-entries-list, design Decision 5).
 *
 * `Column` из двух строк: первая — «название — значение» (формат
 * «подходы × значение» через [formatEntryValue]: повторы — «4 х 10»,
 * длительность — человекочитаемый текст [formatDurationResource] —
 * «3 х 20 сек», «2 мин»; оба текста `FontWeight.Medium` — design
 * Decision 4), вторая — доп. вес: слева метка «Дополнительный
 * вес» цветом `onSurfaceVariant`, справа значение («5 кг» через
 * [formatWeightKg] и ресурс `entry_list_weight_value`). Строка веса
 * рендерится только при `weightKg > 0.0`; без веса вид строки
 * идентичен предыдущему.
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
    // Тип значения различается: повторы или отформатированная длительность
    // (spec «Формат значения записи»). «Оба null» недостижимо при валидной
    // записи (`EntryValidator`); fallback «0» сохранён, чтобы ветка была
    // тотальной.
    val value =
        formatEntryValue(
            sets = item.sets,
            value =
                item.reps?.toString()
                    ?: formatDurationResource(item.durationSec ?: 0),
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
    val weightKg = item.weightKg

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = backgroundColor)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {
                    // Стандартный канал accessibility Compose; читается
                    // в androidTest через `SemanticsProperties.Selected`.
                    // `mergeDescendants = true` нужен, чтобы `onNodeWithText`
                    // находил строку (а не отдельный Text), иначе свойство
                    // Selected останется только у родителя.
                    selected = isSelected
                },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.exerciseName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End
            )
        }
        if (weightKg != null && weightKg > 0.0) {
            WeightRow(weightKg = weightKg)
        }
    }
}

/**
 * Вторая строка [EntryListItemRow]: слева метка «Дополнительный вес»
 * цветом `onSurfaceVariant`, справа значение («5 кг» через
 * [formatWeightKg]). Вынесено из [EntryListItemRow] ради лимита detekt
 * `LongMethod: 60` — как уже сделано в `MainScreen`.
 */
@Composable
private fun WeightRow(weightKg: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.entry_list_weight_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text =
                stringResource(
                    R.string.entry_list_weight_value,
                    formatWeightKg(weightKg)
                ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
