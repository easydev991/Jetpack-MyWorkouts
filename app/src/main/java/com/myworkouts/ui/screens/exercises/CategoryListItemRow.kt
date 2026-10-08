package com.myworkouts.ui.screens.exercises

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.Category
import com.myworkouts.ui.util.categoryLabel

/**
 * Строка списка категорий (custom-categories, spec «Строка списка
 * категорий»): круглый цветовой маркер 24dp (`category.colorArgb`,
 * без contentDescription — название строки уже озвучивается) и
 * название (`bodyLarge`, `Medium`). Образец — [ExerciseListItemRow]:
 * подсветка при открытом контекстном меню через `Selected` semantic.
 *
 * Long-press подключается вызывающим через `Modifier.applyLongPress`
 * (меню по типу категории — [ActiveContextMenu]).
 *
 * @param category Категория из списка активных
 * @param isSelected Подсветка строки при открытом контекстном меню
 * @param modifier внешний модификатор
 */
@Composable
internal fun CategoryListItemRow(
    category: Category,
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
            label = "categoryRowBackground"
        ).value

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = backgroundColor)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {
                    selected = isSelected
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Цветовой индикатор: круг 24dp цветом category.colorArgb
        // слева от названия (spec — без описания доступности).
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color = Color(category.colorArgb))
        )
        Text(
            text = categoryLabel(category.name),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
