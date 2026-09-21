package com.myworkouts.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.icons.ExerciseIcon
import com.myworkouts.ui.util.formatDurationResource

/**
 * Карточка деталей записи: иконка, название (`titleLarge` по центру),
 * крупное значение (`headlineMedium` `primary`) в формате «4 х 10 (+ 5 кг)»,
 * дата/время `bodySmall` `onSurfaceVariant`, комментарий целиком в
 * тональном блоке `surfaceVariant` (только при наличии). Закрытие —
 * крестик в правом верхнем углу с локализованным `contentDescription`.
 *
 * Карточка позиционируется вызывающей стороной (overlay `MainScreenContent`):
 * по центру с боковыми отступами 24dp, скрим 32% под карточкой. Ширина
 * ограничена 350dp — без лимита `fillMaxWidth` контента растягивает
 * карточку на весь экран (ландшафт). Здесь
 * только визуал — без скрима, без жестов закрытия (workout-detail-view,
 * design Decision 5).
 *
 * Значение и дата считаются чистыми JVM-функциями [formatEntryDetailsValue]
 * и [formatDetailDateTime]: локализованные части передаются параметрами,
 * что покрыто JVM-тестами без Android-инфраструктуры.
 *
 * @param item Запись для отображения
 * @param onClose Колбэк нажатия на крестик (закрытие карточки)
 * @param modifier Внешний модификатор (позиционирование overlay'ом)
 */
@Composable
internal fun EntryDetailsCard(
    item: EntryListItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(shape = RoundedCornerShape(28.dp), modifier = modifier.widthIn(max = 350.dp)) {
        Box {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseIcon(iconNumber = item.iconNumber)
                Text(
                    text = item.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = entryDetailsValueText(item = item),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatDetailDateTime(dateMillis = item.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val comment = item.comment
                if (!comment.isNullOrBlank()) {
                    DetailComment(comment = comment)
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.entry_confirm_close)
                )
            }
        }
    }
}

/**
 * Значение карточки деталей: повторы или длительность + опциональный
 * локализованный суффикс веса «(+ N кг)» (одной строкой через пробел).
 * Локализованный суффикс веса берётся из ресурса на стороне UI; чистая
 * склейка — [formatEntryDetailsValue].
 */
@Composable
private fun entryDetailsValueText(item: EntryListItem): String {
    val value =
        item.reps?.toString() ?: formatDurationResource(item.durationSec ?: 0)
    val separator = stringResource(R.string.entry_value_separator)
    val weightSuffix =
        item.weightKg?.takeIf { it > 0.0 }?.let { weight ->
            stringResource(R.string.entry_details_weight_suffix, formatWeightKg(weight))
        }
    return formatEntryDetailsValue(
        sets = item.sets,
        value = value,
        separator = separator,
        weightSuffix = weightSuffix
    )
}

/**
 * Комментарий в тональном блоке — целиком: без `maxLines`/`Ellipsis`,
 * в отличие от строки списка (spec «Содержимое карточки деталей записи»).
 * Блок не выше 200dp — более длинный комментарий скроллится внутри,
 * не вытягивая карточку по высоте.
 */
@Composable
private fun DetailComment(
    comment: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = comment,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
        )
    }
}
