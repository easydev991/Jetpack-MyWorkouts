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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.icons.ExerciseIcon
import com.myworkouts.ui.util.formatDurationResource

/**
 * Переиспользуемая строка списка записей главного экрана
 * (workout-entries-list, design Decision 5).
 *
 * `Column` из двух строк: первая — «название — значение» (формат
 * «подходы × значение» через [formatEntryValue]: повторы — «4 х 10»,
 * длительность — человекочитаемый текст [formatDurationResource] —
 * «3 х 20 сек», «2 мин»; оба текста `FontWeight.Medium` — design
 * Decision 4), перед названием — иконка упражнения [ExerciseIcon]
 * (exercise-icon-display: известный номер — иконка 24dp слева от
 * названия, `null`/неизвестный — строка как до изменения), вторая —
 * доп. вес: слева метка «Дополнительный
 * вес» цветом `onSurfaceVariant`, справа значение («5 кг» через
 * [formatWeightKg] и ресурс `entry_list_weight_value`). Строка веса
 * рендерится только при `weightKg > 0.0`; без веса вид строки
 * идентичен предыдущему. Третья строка — комментарий к записи без
 * ведущей метки ([CommentRow]: `bodyMedium`, `onSurfaceVariant`,
 * максимум 2 строки с многоточием — design Decision 4 change
 * mainscreen-itemrow-comment); при `null`/blank блок не рендерится.
 *
 * Фон анимируется между `colorScheme.surface` и `surfaceVariant` в
 * зависимости от [isSelected] — пока открыто контекстное меню
 * этой записи, строка подсвечивается. Семантика `Selected`
 * (`Modifier.semantics { selected = ... }`) дублирует состояние
 * для accessibility и Compose UI Test.
 *
 * Accessibility-канал меню (accessibility-semantics-fixes, design D1/D2):
 * в тот же `semantics(mergeDescendants = true)`-блок добавляется
 * custom-действие с лейблом `a11y_open_menu` — TalkBack показывает его
 * в меню действий. Действие вызывает [onOpenMenu] без точки касания;
 * офсет меню (центр строки) вычисляет вызывающая сторона — она уже
 * отслеживает позицию строки для long-press.
 *
 * @param item Элемент списка (проекция из DAO JOIN)
 * @param separator Локализованный разделитель (« х »/« x »)
 * @param isSelected `true` пока для этой записи открыто контекстное
 *   меню (design Decision 5)
 * @param onOpenMenu Колбэк открытия контекстного меню (a11y-действие)
 * @param modifier Внешний модификатор (для gesture/position из списка)
 */
@Composable
internal fun EntryListItemRow(
    item: EntryListItem,
    separator: String,
    isSelected: Boolean,
    onOpenMenu: () -> Unit,
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
    val openMenuLabel = stringResource(R.string.a11y_open_menu)

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
                    // Меню действий TalkBack: то же контекстное меню, что
                    // по long-press (design D1).
                    customActions =
                        listOf(
                            CustomAccessibilityAction(openMenuLabel) {
                                onOpenMenu()
                                true
                            }
                        )
                },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TitleValueRow(
            iconNumber = item.iconNumber,
            exerciseName = item.exerciseName,
            value = value
        )
        if (weightKg != null && weightKg > 0.0) {
            WeightRow(weightKg = weightKg)
        }
        val comment = item.comment
        if (!comment.isNullOrBlank()) {
            CommentRow(comment = comment)
        }
    }
}

/**
 * Первая строка [EntryListItemRow]: «название — значение», оба текста
 * `bodyLarge` с `FontWeight.Medium` (design Decision 4); перед названием —
 * иконка упражнения по общим правилам [ExerciseIcon] (известный номер —
 * иконка 24dp, `null`/неизвестный — ничего не рисуется, место не
 * резервируется; название сохраняет `weight(1f)`, значение остаётся у
 * правого края). Вынесено из [EntryListItemRow] ради лимита detekt
 * `LongMethod: 60` — как [WeightRow] и [CommentRow].
 */
@Composable
private fun TitleValueRow(
    iconNumber: Int?,
    exerciseName: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExerciseIcon(
            iconNumber = iconNumber,
            // Отступ только при отрисованной иконке: при `null` рендер
            // не выполняется и модификатор не применяется (spec — место
            // под иконку не резервируется)
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            // Гарантированный минимальный зазор до названия: SpaceBetween
            // оставляет 0dp, когда длинное название (weight(1f)) занимает
            // всю ширину
            modifier = Modifier.padding(start = 12.dp)
        )
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

/**
 * Третья строка [EntryListItemRow]: комментарий к записи без ведущей
 * метки — `bodyMedium` с цветом `onSurfaceVariant`, максимум 2 строки,
 * переполнение — многоточие (design Decision 4 change
 * mainscreen-itemrow-comment). Вынесено из [EntryListItemRow] ради
 * лимита detekt `LongMethod: 60` — как [WeightRow].
 */
@Composable
private fun CommentRow(comment: String) {
    Text(
        text = comment,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
