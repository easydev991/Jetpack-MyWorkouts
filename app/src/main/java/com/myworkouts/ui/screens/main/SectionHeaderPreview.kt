package com.myworkouts.ui.screens.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Превью [SectionHeader]: заголовки секций, которые рендерятся —
 * «Вчера» и форматированные даты (секция «Сегодня» отображается
 * без заголовка, design Decision 5). Светлая + тёмная тема, ru-локаль.
 */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun SectionHeaderPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            Column {
                SectionHeader(title = "Вчера")
                SectionHeader(title = "01.09.2026")
                SectionHeader(title = "31.08.2026")
            }
        }
    }
}
