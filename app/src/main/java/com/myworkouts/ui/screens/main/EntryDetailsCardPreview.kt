package com.myworkouts.ui.screens.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.state.MainUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Превью карточки деталей записи (workout-detail-view):
 * - 3 превью по демо-моделям: все поля / без комментария / минимум полей;
 * - 1 превью «карточка поверх главного экрана» — боевой `MainScreenContent`
 *   с `detailsEntryId`: список, скрим и позиционирование карточки
 *   отрисовываются реальным кодом, без ручного дублирования.
 *
 * Светлая + тёмная темы (`UI_MODE_NIGHT_YES`), `showBackground = true`,
 * `locale = "ru"` — единые конвенции превью в проекте (AGENTS.md).
 */

// ==== Демо-модели (общие для всех превью) ====

/** Millis демо-модели по «настенным» дате и времени — стабильно в любой таймзоне. */
private fun demoDate(date: LocalDateTime): Long = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Модель 1 — все поля: несколько подходов, повторы, вес, длинный комментарий. */
private val demoPullups =
    EntryListItem(
        id = 101L,
        exerciseName = "Подтягивания",
        iconNumber = ExerciseIcons.PULLUPS,
        sets = 4,
        reps = 10,
        durationSec = null,
        weightKg = 5.0,
        comment =
            "Хват сверху на ширине плеч. Последние два подхода почти до отказа, " +
                "в следующей серии попробовать добавить вес. Лопатки сводить в начале движения.",
        date = demoDate(LocalDateTime.of(2025, 9, 20, 18, 30))
    )

/** Модель 2 — все поля кроме комментария, один подход (подходы скрыты). */
private val demoDips =
    EntryListItem(
        id = 102L,
        exerciseName = "Отжимания на брусьях",
        iconNumber = ExerciseIcons.BARS,
        sets = 1,
        reps = 12,
        durationSec = null,
        weightKg = 10.0,
        date = demoDate(LocalDateTime.of(2025, 9, 20, 20, 0))
    )

/** Модель 3 — минимум полей: один подход «на время», без веса и комментария. */
private val demoPlank =
    EntryListItem(
        id = 103L,
        exerciseName = "Планка",
        iconNumber = ExerciseIcons.PUSHUPS,
        sets = 1,
        reps = null,
        durationSec = 60,
        date = demoDate(LocalDateTime.of(2025, 9, 18, 8, 15))
    )

/** Список для фона превью «карточка поверх списка» — как на реальном экране, по дате убыванием. */
private val demoEntries = listOf(demoDips, demoPullups, demoPlank)

// ==== Превью: одна карточка в чистом виде ====

@Preview(name = "Детали — все поля", showBackground = true, locale = "ru")
@Preview(
    name = "Детали — все поля — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailsCardFullPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailsCard(
            item = demoPullups,
            onClose = {},
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Preview(name = "Детали — без комментария", showBackground = true, locale = "ru")
@Preview(
    name = "Детали — без комментария — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailsCardNoCommentPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailsCard(
            item = demoDips,
            onClose = {},
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Preview(name = "Детали — минимум полей", showBackground = true, locale = "ru")
@Preview(
    name = "Детали — минимум полей — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailsCardMinimalPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailsCard(
            item = demoPlank,
            onClose = {},
            modifier = Modifier.padding(24.dp)
        )
    }
}

// ==== Превью: карточка поверх списка со скримом ====

/**
 * Карточка поверх главного экрана: боевой `MainScreenContent` с открытыми
 * деталями (`detailsEntryId`) — скрим, insets и позиционирование карточки
 * отрисовываются реальным overlay-кодом (workout-detail-view).
 */
@Preview(name = "Детали поверх списка", showBackground = true, locale = "ru")
@Preview(
    name = "Детали поверх списка — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailsCardOverlayPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MainScreenContent(
                state =
                    MainScreenState(
                        uiState = MainUiState.Success(entries = demoEntries),
                        detailsEntryId = demoPullups.id
                    )
            )
        }
    }
}
