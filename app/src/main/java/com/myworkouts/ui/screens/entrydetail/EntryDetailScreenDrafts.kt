package com.myworkouts.ui.screens.entrydetail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.icons.ExerciseIcon
import com.myworkouts.ui.icons.ExerciseIcons
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Референс утверждённого варианта A2 детального экрана записи (замена
 * всплывающей карточки на главном экране): TopAppBar «Детали записи»
 * (назад + «Изменить»/«Удалить») и контент по центру свободного места
 * БЕЗ Card-фона.
 *
 * ВРЕМЕННЫЙ ФАЙЛ: варианты A, B, C срезаны после выбора владельцем
 * (их рендер в PNG — удалённый EntryDetailDraftsScreenshotTest, коммит
 * 2efd52a1); перерабатывается в боевой экран задачей 3.2 плана
 * openspec/changes/workout-entry-detail-screen (строки — в ресурсы),
 * целиком удаляется задачей 3.4.
 *
 * Демо-данные и строки захардкожены на русском — при реализации
 * тексты переезжают в строковые ресурсы (ru/en).
 */

// ==== Демо-данные ====

/** Millis демо-модели по «настенным» дате и времени — стабильно в любой таймзоне. */
private fun draftDemoDate(date: LocalDateTime): Long = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Демо-запись: несколько подходов, повторы, вес, длинный комментарий.
 * Suppress `MagicNumber` — литеральные sample-значения демо-модели,
 * тот же паттерн, что у exclude `*Preview*.kt` в detekt-конфиге
 * (данные для отображения, не логика).
 */
@Suppress("MagicNumber")
private val draftDemoItem =
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
        date = draftDemoDate(LocalDateTime.of(2025, 9, 20, 18, 30))
    )

// ==== Общий TopAppBar ====

/**
 * TopAppBar черновика: заголовок «Детали записи», стрелка назад и
 * actions «Изменить»/«Удалить» — как в референсе JetpackDays
 * (DetailAppBar: назад → Edit → Delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDetailDraftTopAppBar(modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text("Детали записи") },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад"
                )
            }
        },
        actions = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Изменить"
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Удалить"
                )
            }
        }
    )
}

/** Комментарий в тональном блоке — клон DetailComment из EntryDetailsCard. */
@Composable
private fun EntryDetailDraftComment(
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

// ==== Утверждённый вариант A2 — контент по центру без фона ====

/**
 * Контент по центру свободного места прямо на фоне экрана, без
 * Card-фона; комментарий остаётся в тональном блоке. Перерабатывается
 * в боевой `EntryDetailScreen` задачей 3.2 плана
 * workout-entry-detail-screen.
 */
@Composable
fun EntryDetailDraftCenterNoCard(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { EntryDetailDraftTopAppBar() }
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseIcon(iconNumber = draftDemoItem.iconNumber)
                Text(
                    text = draftDemoItem.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "4 х 10 (+ 5 кг)",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "20 сентября 2025, 18:30",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EntryDetailDraftComment(comment = checkNotNull(draftDemoItem.comment))
            }
        }
    }
}

// ==== Превью (светлая + тёмная, по конвенциям AGENTS.md) ====

@Preview(name = "Вариант A2 — по центру без фона", showBackground = true, locale = "ru")
@Preview(
    name = "Вариант A2 — по центру без фона — Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun EntryDetailDraftCenterNoCardPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            EntryDetailDraftCenterNoCard()
        }
    }
}
