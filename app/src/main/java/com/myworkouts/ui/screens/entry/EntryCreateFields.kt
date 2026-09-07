package com.myworkouts.ui.screens.entry

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.ui.util.formatDurationResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Минимальная длительность в пикере (spec: от 5 секунд). */
private const val DURATION_MIN_SEC: Int = 5

/** Максимальная длительность в пикере (spec: до 1 часа включительно). */
private const val DURATION_MAX_SEC: Int = 3600

/** Шаг длительности в пикере (spec: кратные 5 секундам). */
private const val DURATION_STEP_SEC: Int = 5

/**
 * Выпадающий список упражнений каталога (`ExposedDropdownMenuBox`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseDropdownField(
    exercises: List<Exercise>,
    selectedExercise: Exercise?,
    onExerciseChange: (Exercise) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedExercise?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.entry_field_exercise)) },
            placeholder = { Text(stringResource(R.string.entry_hint_select_exercise)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            exercises.forEach { exercise ->
                DropdownMenuItem(
                    text = { Text(exercise.name) },
                    onClick = {
                        onExerciseChange(exercise)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Поле числового ввода (подходы/повторы/вес): клавиатура настраивается
 * параметром [keyboardType] (вес — `KeyboardType.Decimal`), санитайзинг —
 * на стороне ViewModel.
 */
@Composable
internal fun NumberEntryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Выпадающий список длительности: значения 5..3600 с шагом 5
 * (design.md Decision 4), отображение через [formatDurationResource].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DurationDropdownField(
    durationSec: Int?,
    onDurationChange: (Int?) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = durationSec?.let { formatDurationResource(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.entry_field_duration)) },
            placeholder = { Text(stringResource(R.string.entry_duration_not_selected)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // ponytail: 720 пунктов внутри popup; если UX-проверка покажет
            // неудобство скролла — диалог с двумя колонками (design Risk)
            for (seconds in DURATION_MIN_SEC..DURATION_MAX_SEC step DURATION_STEP_SEC) {
                DropdownMenuItem(
                    text = { Text(formatDurationResource(seconds)) },
                    onClick = {
                        onDurationChange(seconds)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Граница доступности дат в `DatePicker`: дни позже локального дня открытия
 * экрана невыделяемы, сам день открытия — ещё доступен (design Decision 2,
 * сравнение в UTC-millis пикера через [localDateToPickerMillis]).
 * Pure-функция — чтобы граница была покрыта JVM-тестами без пикера.
 */
internal fun entrySelectableDates(openLocalDate: LocalDate): SelectableDates =
    object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= localDateToPickerMillis(openLocalDate)
    }

/**
 * Поле даты: пикер открывает иконка-календарь в `trailingIcon` — клик по
 * самому readOnly-полю не работает, внутренний `BasicTextField` поглощает
 * тапы (design.md Decision 5). millis ↔ LocalDate — только через
 * [localDateToPickerMillis]/[pickerMillisToLocalDate] (UTC). Дни позже дня
 * открытия экрана ([openMomentMillis]) невыделяемы (spec «Даты позже дня
 * открытия недоступны в пикере»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    date: LocalDate,
    openMomentMillis: Long,
    onDateChange: (LocalDate) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    OutlinedTextField(
        value = date.format(dateFormatter),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.entry_field_date)) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = stringResource(R.string.entry_select_date)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (showDialog) {
        val openLocalDate =
            Instant.ofEpochMilli(openMomentMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = localDateToPickerMillis(date),
                selectableDates = entrySelectableDates(openLocalDate)
            )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let { pickerMillisToLocalDate(it) }
                            ?.let(onDateChange)
                        showDialog = false
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Поле времени: пикер открывает иконка-часы в `trailingIcon`
 * (см. [DateField]); диалог — `AlertDialog` с M3 `TimePicker`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeField(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = "%02d:%02d".format(hour, minute),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.entry_field_time)) },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = stringResource(R.string.entry_select_time)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (showDialog) {
        val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.entry_field_time)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(pickerState.hour, pickerState.minute)
                        showDialog = false
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}
