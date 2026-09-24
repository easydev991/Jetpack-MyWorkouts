package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.usecase.ExerciseRecordData
import com.myworkouts.domain.usecase.RecordsMode
import com.myworkouts.ui.state.RecordsUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.util.formatDurationResource
import com.myworkouts.ui.util.sectionDateLabelResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Вкладка «Рекорды» экрана обзора (overview-screen, макет
 * `progress-drafts/iter4_v1.html`, вкладка records): сегмент-переключатель
 * «За подход»/«За день» + карточка со строками «название — значение
 * рекорда, дата».
 */

/**
 * Вкладка «Рекорды»: пикер режима + карточка со списком рекордов
 * (пустое состояние — подсказка «Нет тренировок»).
 *
 * @param state Состояние вкладки ([RecordsUiState])
 * @param onModeChange Смена режима значения рекорда
 * @param modifier Модификатор корневого контейнера
 */
@Composable
internal fun RecordsScreen(
    state: RecordsUiState,
    onModeChange: (RecordsMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.mode == RecordsMode.PER_SET,
                onClick = { onModeChange(RecordsMode.PER_SET) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(text = stringResource(R.string.records_mode_per_set))
            }
            SegmentedButton(
                selected = state.mode == RecordsMode.PER_DAY,
                onClick = { onModeChange(RecordsMode.PER_DAY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(text = stringResource(R.string.records_mode_per_day))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state.records.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.records_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                )
            }
        } else {
            // Светлее дефолта Card (surfaceContainerHighest): почти в тон
            // фону экрана, серый только намёком
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
            ) {
                state.records.forEachIndexed { index, record ->
                    if (index > 0) {
                        // Дефолт divider (outlineVariant) темноват на светлой
                        // карточке — берём тон на шаг выше контейнера
                        // карточки (surfaceContainerLow)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                    RecordRow(record = record)
                }
            }
        }
    }
}

/**
 * Строка рекорда: название (и комментарий при наличии, по образцу
 * `ExerciseListItemRow`) слева; справа колонка — значение (акцент,
 * жирное) над датой (мелкой, приглушённой).
 */
@Composable
private fun RecordRow(record: ExerciseRecordData) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.exerciseName,
                style = MaterialTheme.typography.bodyLarge,
                // Card перекрашивает content color в onSurfaceVariant (отсюда
                // «бледное» название) — возвращаем стандартный цвет текста
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!record.comment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            // Гарантированный минимальный зазор до названия — как на
            // главном экране (TitleValueRow) и в каталоге
            // (ExerciseListItemRow): SpaceBetween оставляет 0dp, когда
            // длинное название (weight(1f)) занимает всю ширину
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text(
                text = recordValueText(record),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = recordDateText(record),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Значение рекорда с единицей: «N повт.» для повторов, длительность для секунд. */
@Composable
private fun recordValueText(record: ExerciseRecordData): String =
    when (record.measuredBy) {
        ExerciseMeasuredBy.REPS -> stringResource(R.string.overview_unit_reps, record.value)
        ExerciseMeasuredBy.TIME -> formatDurationResource(seconds = record.value)
    }

/**
 * Дата записи-рекордсмена — форматы заголовков главного экрана
 * ([sectionDateLabelResource]); «сегодня» там даёт `null` (секции без
 * заголовка), для даты рекорда — краткий формат текущего года
 * (`EEE, d MMM`).
 */
@Composable
private fun recordDateText(record: ExerciseRecordData): String {
    val today = remember { LocalDate.now() }
    val currentYearFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }
    val date =
        remember(record.dateMillis) {
            Instant
                .ofEpochMilli(record.dateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    return sectionDateLabelResource(date = date, today = today)
        ?: date.format(currentYearFormatter)
}

// ===== Превью (обе темы, showBackground, locale = "ru") =====

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun RecordsPerSetPreview() {
    RecordsScreenPreview(mode = RecordsMode.PER_SET)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun RecordsPerDayPreview() {
    RecordsScreenPreview(mode = RecordsMode.PER_DAY)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun RecordsEmptyPreview() {
    RecordsScreenPreview(mode = RecordsMode.PER_SET, records = emptyList())
}

@Composable
private fun RecordsScreenPreview(
    mode: RecordsMode,
    records: List<ExerciseRecordData> = demoRecords
) {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            RecordsScreen(
                state = RecordsUiState(mode = mode, records = records),
                onModeChange = {}
            )
        }
    }
}

/** Демо-рекорды превью (переиспользуются OverviewScreen.kt и тестом). */
internal val demoRecords =
    listOf(
        ExerciseRecordData(
            exerciseName = "Отжимания",
            measuredBy = ExerciseMeasuredBy.REPS,
            value = 100,
            dateMillis = System.currentTimeMillis(),
            comment = "темп медленный"
        ),
        ExerciseRecordData(
            exerciseName = "Подтягивания",
            measuredBy = ExerciseMeasuredBy.REPS,
            value = 50,
            dateMillis = System.currentTimeMillis() - 86_400_000L,
            comment = "прямой хват, до отказа"
        ),
        ExerciseRecordData(
            exerciseName = "Планка",
            measuredBy = ExerciseMeasuredBy.TIME,
            value = 90,
            dateMillis = System.currentTimeMillis() - 2 * 86_400_000L,
            comment = null
        )
    )
