package com.myworkouts.ui.screens.entry

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.myworkouts.domain.model.EntryField
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

// Конвенция превью проекта: светлая + тёмная темы, `showBackground`,
// `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`. Ключевые состояния:
// все поля включены (кнопки нет) и часть выключена (кнопка «Включить все»
// есть, видны подсказки).

/** Все поля включены — дефолтное состояние, кнопки «Включить все» нет. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun EntrySettingsScreenContentAllEnabledPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            EntrySettingsScreenContent(
                state =
                    EntrySettingsScreenState(
                        visibility = EntryField.entries.associateWith { true },
                        hasAnyDisabled = false,
                        onToggleField = { _, _ -> },
                        onEnableAll = {},
                        onBackClick = {}
                    )
            )
        }
    }
}

/** «Доп. вес» и «Комментарий» выключены — кнопка «Включить все» видна. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun EntrySettingsScreenContentSomeDisabledPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            EntrySettingsScreenContent(
                state =
                    EntrySettingsScreenState(
                        visibility =
                            EntryField.entries.associateWith { true } +
                                mapOf(EntryField.WEIGHT to false, EntryField.COMMENT to false),
                        hasAnyDisabled = true,
                        onToggleField = { _, _ -> },
                        onEnableAll = {},
                        onBackClick = {}
                    )
            )
        }
    }
}
