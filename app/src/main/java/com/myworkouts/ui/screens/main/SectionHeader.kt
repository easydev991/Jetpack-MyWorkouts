package com.myworkouts.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Sticky-заголовок секции (workout-entries-by-date-sections, design
 * Decision 5). Фон обязателен: `stickyHeader` без фона просвечивал бы
 * строками списка при скролле; цвет совпадает с фоном строк
 * ([EntryListItemRow]).
 *
 * Accessibility (accessibility-semantics-fixes, design D5): текст
 * помечен `semantics { heading() }` — TalkBack объявляет заголовок
 * и включает навигацию по заголовкам между секциями.
 *
 * @param title Готовый текст заголовка
 * @param modifier Внешний модификатор
 */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            thickness = 1.dp,
            color = lineColor
        )
        Text(
            text = title,
            color = lineColor,
            modifier =
                Modifier
                    .padding(horizontal = 12.dp)
                    .semantics { heading() }
        )
        HorizontalDivider(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
            thickness = 1.dp,
            color = lineColor
        )
    }
}
