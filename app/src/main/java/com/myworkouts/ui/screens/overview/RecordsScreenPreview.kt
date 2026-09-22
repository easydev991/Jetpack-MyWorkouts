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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.util.formatDurationResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * DRAFT экрана «Рекорды» по макету `progress-drafts/iter4_v1.html`
 * (вкладка records): сегмент-переключатель «За подход»/«За день» + карточка
 * со строками «название — значение рекорда, дата».
 *
 * Черновик в одном файле: экран, модели, вычисление рекорда и демо-данные.
 * Цвета и стили макета не копируются — только расположение и иерархия
 * элементов. При переносе в фичу:
 * - [RecordsScreenContent] и модели → `RecordsScreen.kt` (+ UiState/VM),
 * - тексты «За подход»/«За день»/«Нет тренировок»/«повт.» → strings.xml,
 * - вычисление рекорда → use-case/VM по TDD (сначала падающий тест).
 */

/** Режим пикера рекордов: лучший отдельный подход или лучшая запись за день. */
internal enum class RecordsMode { PER_SET, PER_DAY }

/** Строка списка рекордов: рекорд одного упражнения. */
internal data class ExerciseRecord(
    val exerciseName: String,
    /** Значение рекорда: повторы или секунды (зависит от [measuredBy]). */
    val value: Int,
    val measuredBy: ExerciseMeasuredBy,
    /** Дата записи-рекордсмена (epoch millis). */
    val dateMillis: Long
)

/** UiState экрана «Рекорды». */
internal data class RecordsUiState(
    val mode: RecordsMode = RecordsMode.PER_SET,
    val records: List<ExerciseRecord> = emptyList()
)

/**
 * Содержимое экрана «Рекорды»: пикер режима + карточка со списком рекордов
 * (пустое состояние — подсказка «Нет тренировок», как `.empty-hint` макета).
 */
@Composable
internal fun RecordsScreenContent(
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
        // DRAFT: строки захардкожены, при переносе — строковые ресурсы
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.mode == RecordsMode.PER_SET,
                onClick = { onModeChange(RecordsMode.PER_SET) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(text = "За подход") }
            SegmentedButton(
                selected = state.mode == RecordsMode.PER_DAY,
                onClick = { onModeChange(RecordsMode.PER_DAY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(text = "За день") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state.records.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Нет тренировок",
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
 * Строка рекорда (`.rec-row` макета): название слева, справа колонка —
 * значение (акцент, жирное) над датой (мелкой, приглушённой).
 */
@Composable
private fun RecordRow(record: ExerciseRecord) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = record.exerciseName,
            style = MaterialTheme.typography.bodyLarge,
            // Card перекрашивает content color в onSurfaceVariant (отсюда
            // «бледное» название) — возвращаем стандартный цвет текста
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
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
                text = formatRecordValue(record),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            // Дата в форматах заголовков секций главного экрана (см.
            // EntryListItems в MainScreen): «Вчера» / «пн, 22 сент.»
            // (текущий год) / «пт, 26.12.2025» (прошлый год).
            // DRAFT: подпись «Вчера» и локаль захардкожены — при переносе
            // строковые ресурсы
            val recordDate =
                run {
                    val date =
                        Instant
                            .ofEpochMilli(record.dateMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    val today = LocalDate.now()
                    when {
                        date == today.minusDays(1) -> "Вчера"
                        date.year == today.year ->
                            date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale("ru")))
                        else ->
                            date.format(DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale("ru")))
                    }
                }
            Text(
                text = recordDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Значение рекорда с единицей: «повт.» для повторов, длительность для секунд. */
@Composable
private fun formatRecordValue(record: ExerciseRecord): String =
    when (record.measuredBy) {
        // DRAFT: «повт.» — при переносе строковый ресурс
        ExerciseMeasuredBy.REPS -> "${record.value} повт."
        ExerciseMeasuredBy.TIME -> formatDurationResource(seconds = record.value)
    }

/** Значение записи для режима рекорда: один подход или запись целиком. */
private fun recordValue(
    entry: ExerciseEntry,
    mode: RecordsMode
): Int {
    val single = entry.reps ?: entry.durationSec ?: 0
    return if (mode == RecordsMode.PER_SET) single else entry.sets * single
}

// ===== Демо-данные и вычисление рекордов (черновик логики будущего use-case) =====

/** Упражнение демо-набора: минимум полей, нужных экрану. */
private data class DemoExercise(
    val id: Long,
    val name: String,
    val measuredBy: ExerciseMeasuredBy
)

private val pushups = DemoExercise(id = 1, name = "Отжимания", measuredBy = ExerciseMeasuredBy.REPS)
private val pullups = DemoExercise(id = 2, name = "Подтягивания", measuredBy = ExerciseMeasuredBy.REPS)
private val squats = DemoExercise(id = 3, name = "Приседания", measuredBy = ExerciseMeasuredBy.REPS)
private val plank = DemoExercise(id = 4, name = "Планка", measuredBy = ExerciseMeasuredBy.TIME)

// Длинное название — проверка переноса строки в строке рекорда
private val australianPullups =
    DemoExercise(id = 5, name = "Австралийские подтягивания", measuredBy = ExerciseMeasuredBy.REPS)

/** Запись демо-тренировки: `daysAgo` дней назад в фиксированное время дня. */
private fun DemoExercise.entry(
    daysAgo: Long,
    sets: Int,
    reps: Int? = null,
    durationSec: Int? = null
): ExerciseEntry {
    val date =
        LocalDate
            .now()
            .minusDays(daysAgo)
            .atTime(18, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    return ExerciseEntry(
        id = 0L,
        exerciseId = id,
        workoutId = null,
        date = date,
        sets = sets,
        reps = reps,
        weightKg = null,
        durationSec = durationSec,
        comment = null,
        createdAt = date,
        modifiedAt = date
    )
}

/**
 * Демо-тренировки: по две записи на упражнение, у каждой доминирует одна
 * запись — она же рекорд и «за подход», и «за день». Поэтому в превью
 * обоих режимов у упражнения одна и та же дата рекорда (и один порядок
 * строк), различаются только значения. Рекорды подтягиваний уходят
 * в прошлый год — архивный формат даты.
 */
private val pushupsLogs =
    listOf(
        pushups.entry(daysAgo = 10, sets = 4, reps = 15),
        // Рекорд в обоих режимах: 20 повт. / 100 повт. за день
        pushups.entry(daysAgo = 3, sets = 5, reps = 20)
    )

private val pullupsLogs =
    listOf(
        pullups.entry(daysAgo = 6, sets = 3, reps = 8),
        // Рекорд в обоих режимах, прошлый год — архивный формат даты
        // в превью («пт, 26.12.2025»): 10 повт. / 50 повт. за день
        pullups.entry(daysAgo = 270, sets = 5, reps = 10)
    )

private val squatsLogs =
    listOf(
        squats.entry(daysAgo = 30, sets = 3, reps = 25),
        // Рекорд в обоих режимах: 30 повт. / 120 повт. за день
        squats.entry(daysAgo = 2, sets = 4, reps = 30)
    )

private val plankLogs =
    listOf(
        plank.entry(daysAgo = 7, sets = 2, durationSec = 80),
        // Рекорд в обоих режимах: 90 сек / 270 сек за день
        plank.entry(daysAgo = 1, sets = 3, durationSec = 90)
    )

private val australianPullupsLogs =
    listOf(
        australianPullups.entry(daysAgo = 14, sets = 2, reps = 20),
        // Рекорд в обоих режимах: 25 повт. / 75 повт. за день
        australianPullups.entry(daysAgo = 9, sets = 3, reps = 25)
    )

private val demoLogs: List<Pair<DemoExercise, List<ExerciseEntry>>> =
    listOf(
        pushups to pushupsLogs,
        pullups to pullupsLogs,
        squats to squatsLogs,
        plank to plankLogs,
        australianPullups to australianPullupsLogs
    )

/**
 * Рекорд упражнения для режима: «За подход» — лучший отдельный подход,
 * «За день» — лучшая запись целиком (подходы × значение).
 * Как и в приложении, у упражнения не бывает двух записей за один день,
 * поэтому группировка по календарному дню не нужна.
 */
private fun bestRecord(
    exercise: DemoExercise,
    entries: List<ExerciseEntry>,
    mode: RecordsMode
): ExerciseRecord? =
    entries
        .maxByOrNull { recordValue(it, mode) }
        ?.let { best ->
            val value = recordValue(best, mode)
            if (value > 0) {
                ExerciseRecord(
                    exerciseName = exercise.name,
                    value = value,
                    measuredBy = exercise.measuredBy,
                    dateMillis = best.date
                )
            } else {
                null
            }
        }

/** UiState экрана для демо-данных: рекорды сортируются по дате убыванию. */
internal fun demoRecordsState(mode: RecordsMode): RecordsUiState =
    RecordsUiState(
        mode = mode,
        records =
            demoLogs
                .mapNotNull { (exercise, entries) -> bestRecord(exercise, entries, mode) }
                .sortedByDescending { it.dateMillis }
    )

// ===== Превью (конвенция: светлая + тёмная темы, showBackground, locale = "ru") =====

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun RecordsScreenPerSetPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            RecordsScreenContent(
                state = demoRecordsState(RecordsMode.PER_SET),
                onModeChange = {}
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun RecordsScreenPerDayPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            RecordsScreenContent(
                state = demoRecordsState(RecordsMode.PER_DAY),
                onModeChange = {}
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun RecordsScreenEmptyPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            RecordsScreenContent(
                state = RecordsUiState(mode = RecordsMode.PER_SET, records = emptyList()),
                onModeChange = {}
            )
        }
    }
}
