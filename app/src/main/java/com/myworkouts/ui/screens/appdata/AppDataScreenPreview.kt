package com.myworkouts.ui.screens.appdata

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview экрана «Данные приложения» (конвенция проекта: светлая +
 * тёмная темы, `showBackground`, `locale = "ru"`, обёртка в тему
 * приложения, ключевые состояния рядом в Column).
 */
private fun previewScreenState(
    hasData: Boolean,
    isBusy: Boolean
): AppDataScreenState =
    AppDataScreenState(
        hasData = hasData,
        isBusy = isBusy,
        onExportRequest = {},
        onRestoreRequest = {},
        onDeleteAll = {},
        onBackClick = {}
    )

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun AppDataScreenContentPreview() {
    JetpackMyWorkoutsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppDataScreenContent(
                // Данные есть: видны все три кнопки, включая удаление
                // в цвете ошибки
                state = previewScreenState(hasData = true, isBusy = false)
            )
            AppDataScreenContent(
                // Данных нет: только восстановление из копии
                state = previewScreenState(hasData = false, isBusy = false)
            )
            AppDataScreenContent(
                // Операция идёт: все кнопки недоступны
                state = previewScreenState(hasData = true, isBusy = true)
            )
        }
    }
}
