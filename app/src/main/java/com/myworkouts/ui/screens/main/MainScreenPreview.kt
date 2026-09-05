package com.myworkouts.ui.screens.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.state.MainUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Preview-компонентов главного экрана (конвенция проекта: светлая + тёмная
 * темы, `showBackground`, `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`).
 *
 * Полноэкранный `MainScreenContent` показывается отдельными превью на
 * состояние (пустой список / список с записями): два `Scaffold` друг над
 * другом в одном Column выглядели бы как один сломанный экран.
 * Long-press меню и алерт удаления живут в интерактивных сценариях — на
 * превью не показываются, чтобы статичный preview не «зависал» в раскрытом
 * состоянии. Покрываются ручной проверкой на эмуляторе (задача 5.5).
 */

private val previewRepsItem =
    EntryListItem(
        id = 1L,
        exerciseName = "Подтягивания",
        sets = 4,
        reps = 10,
        durationSec = null,
        date = 1_721_000_000_000L
    )

private val previewTimeItem =
    EntryListItem(
        id = 2L,
        exerciseName = "Планка",
        sets = 1,
        reps = null,
        durationSec = 20,
        date = 1_721_100_000_000L
    )

private val previewSingleRepsItem =
    EntryListItem(
        id = 3L,
        exerciseName = "Отжимания",
        sets = 3,
        reps = 12,
        durationSec = null,
        date = 1_721_200_000_000L
    )

private fun previewScreenState(
    uiState: MainUiState,
    onAddEntryClick: () -> Unit = {}
): MainScreenState =
    MainScreenState(
        uiState = uiState,
        onAddEntryClick = onAddEntryClick,
        onContextMenuItemChange = {},
        onRequestDelete = {},
        onConfirmDelete = {},
        onCancelDelete = {}
    )

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun MainScreenContentEmptyPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Пустое состояние
            MainScreenContent(
                state =
                    previewScreenState(
                        uiState = MainUiState.Success(entries = emptyList())
                    )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun MainScreenContentListPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Список с записями: 4×10, «20», 3×12
            MainScreenContent(
                state =
                    previewScreenState(
                        uiState =
                            MainUiState.Success(
                                entries =
                                    listOf(
                                        previewRepsItem,
                                        previewTimeItem,
                                        previewSingleRepsItem
                                    )
                            )
                    )
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun EntryListItemRowPreview() {
    JetpackMyWorkoutsTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 4×10 (REPS)
            EntryListItemRow(
                item = previewRepsItem,
                separator = " х ",
                isSelected = false
            )
            // 20 (один подход TIME) — выделена, как при показе контекстного меню
            EntryListItemRow(
                item = previewTimeItem,
                separator = " х ",
                isSelected = true
            )
            // 3×12 (REPS)
            EntryListItemRow(
                item = previewSingleRepsItem,
                separator = " х ",
                isSelected = false
            )
        }
    }
}
