package com.myworkouts.ui.screens.entrydetail

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.state.EntryDetailUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Превью экрана деталей записи (задача 3.3 плана workout-entry-detail-screen):
 * по одному превью на состояние — Loading, Success (все поля), Success без
 * комментария, Success (запись на время), Success (длинный многоабзацный
 * комментарий — проверка скролла блока), Error; каждое в светлой и тёмной
 * теме. ViewModel не используется — [EntryDetailScreenState] собирается
 * вручную, колбэки-заглушки.
 */

/** Millis демо-модели по «настенным» дате и времени — стабильно в любой таймзоне. */
private fun previewDate(date: LocalDateTime): Long = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Success «все поля»: подходы, повторы, вес, длинный комментарий. */
private val previewItemFull =
    EntryListItem(
        id = 1L,
        exerciseName = "Подтягивания",
        iconNumber = ExerciseIcons.PULLUPS,
        sets = 4,
        reps = 10,
        durationSec = null,
        weightKg = 5.0,
        comment =
            "Хват сверху на ширине плеч, лопатки сводить в начале движения. " +
                "Последние два подхода почти до отказа — в следующей серии " +
                "попробовать добавить вес.",
        date = previewDate(LocalDateTime.of(2025, 9, 20, 18, 30))
    )

/** Success без комментария. */
private val previewItemNoComment =
    EntryListItem(
        id = 2L,
        exerciseName = "Отжимания",
        iconNumber = ExerciseIcons.PUSHUPS,
        sets = 3,
        reps = 20,
        durationSec = null,
        weightKg = null,
        comment = null,
        date = previewDate(LocalDateTime.of(2025, 9, 21, 7, 15))
    )

/** Success «запись на время»: длительность вместо повторов. */
private val previewItemDuration =
    EntryListItem(
        id = 3L,
        exerciseName = "Бег",
        iconNumber = ExerciseIcons.RUN,
        sets = 1,
        reps = null,
        durationSec = 1800,
        weightKg = null,
        comment = "Держать спину прямой, дышать ровно.",
        date = previewDate(LocalDateTime.of(2025, 9, 22, 8, 0))
    )

/** Success «полотно текста»: многоабзацный комментарий заведомо выше блока — проверка скролла. */
private val previewItemLongComment =
    EntryListItem(
        id = 4L,
        exerciseName = "Становая тяга",
        iconNumber = ExerciseIcons.BARBELL,
        sets = 5,
        reps = 5,
        durationSec = null,
        weightKg = 80.0,
        comment =
            "Разминка: десять минут на велотренажёре, суставная гимнастика, " +
                "мобилити бёдер и голеностопа, два разогревочных подхода с пустым " +
                "грифом и подъёмом до рабочего веса.\n\n" +
                "Подход 1: 80 кг — разминка движения, хват прямой на ширине плеч, " +
                "спина жёсткая, лопатки зафиксированы до съёма штанги.\n\n" +
                "Подход 2: 80 кг — рабочие пять повторов, темп ровный, выдох на " +
                "усилии. Поясница не круглится, гриф ведёт траекторию вдоль ног.\n\n" +
                "Подход 3: 80 кг — на четвёртом повторе почувствовал лёгкое " +
                "напряжение в правой пояснице, сбавил темп и сделал паузу в " +
                "нижней точке на секунду дольше обычного.\n\n" +
                "Подход 4: 80 кг — техника вернулась, но решил не рисковать и " +
                "не добавлять вес к следующему подходу, хотя по ощущениям был " +
                "запас минимум на десять килограммов.\n\n" +
                "Подход 5: 80 кг — завершающий, амплитуда полная, лопатки не " +
                "расходились. Отдых между подходами держал по таймеру — 180 " +
                "секунд, дыхание успевало восстановиться полностью.\n\n" +
                "Итоги: работать в этой дисциплине комфортно, в следующую " +
                "тренировку пробовать 82,5 кг на рабочие пятёрки, но только " +
                "если разминка не покажет усталости в пояснице. Замечания по " +
                "технике: следить за положением шеи — к концу подходов начал " +
                "задирать подбородок вверх, лучше держать взгляд в точке на " +
                "полу в метре перед собой.",
        date = previewDate(LocalDateTime.of(2025, 9, 23, 19, 45))
    )

/** Собирает state с Success-данными и колбэками-заглушками. */
private fun previewState(uiState: EntryDetailUiState): EntryDetailScreenState =
    EntryDetailScreenState(
        uiState = uiState,
        pendingDelete = false,
        onBackClick = {},
        onEditClick = {},
        onDeleteClick = {},
        onDeleteConfirm = {},
        onDeleteDismiss = {}
    )

@Preview(name = "Loading", showBackground = true, locale = "ru")
@Preview(
    name = "Loading — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailLoadingPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailScreenContent(state = previewState(EntryDetailUiState.Loading))
    }
}

@Preview(name = "Success — все поля", showBackground = true, locale = "ru")
@Preview(
    name = "Success — все поля — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailSuccessFullPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailScreenContent(state = previewState(EntryDetailUiState.Success(previewItemFull)))
    }
}

@Preview(name = "Success — без комментария", showBackground = true, locale = "ru")
@Preview(
    name = "Success — без комментария — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailSuccessNoCommentPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailScreenContent(
            state = previewState(EntryDetailUiState.Success(previewItemNoComment))
        )
    }
}

@Preview(name = "Success — запись на время", showBackground = true, locale = "ru")
@Preview(
    name = "Success — запись на время — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailSuccessDurationPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailScreenContent(
            state = previewState(EntryDetailUiState.Success(previewItemDuration))
        )
    }
}

@Preview(name = "Success — длинный комментарий", showBackground = true, locale = "ru")
@Preview(
    name = "Success — длинный комментарий — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailSuccessLongCommentPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailScreenContent(
            state = previewState(EntryDetailUiState.Success(previewItemLongComment))
        )
    }
}

@Preview(name = "Error — запись не найдена", showBackground = true, locale = "ru")
@Preview(
    name = "Error — запись не найдена — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailErrorPreview() {
    JetpackMyWorkoutsTheme {
        EntryDetailScreenContent(state = previewState(EntryDetailUiState.Error))
    }
}
