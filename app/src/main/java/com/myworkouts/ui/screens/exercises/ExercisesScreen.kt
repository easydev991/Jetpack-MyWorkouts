package com.myworkouts.ui.screens.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExercisesUiState
import com.myworkouts.ui.viewmodel.ExercisesViewModel

/**
 * Экран «Упражнения» — вкладка каталога: реактивный список
 * стандартных и пользовательских упражнений, пустое состояние,
 * FAB-заглушка (exercises-screen, спека).
 *
 * VM-обёртка: подписка на [ExercisesViewModel.uiState]. Вся верстка —
 * в [ExercisesScreenContent].
 *
 * @param viewModel ViewModel с реактивным списком каталога
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    ExercisesScreenContent(
        state =
            ExercisesScreenState(uiState = uiState),
        modifier = modifier
    )
}

/**
 * Test/Preview-state для [ExercisesScreenContent]: данные
 * [ExercisesUiState] плюс колбэк FAB. UI-тесты и превью передают
 * готовый state без ViewModel (образец — `MainScreenState`).
 *
 * @property uiState Состояние каталога (Loading/Success)
 * @property onAddExerciseClick Тап по FAB (заглушка — design Decision 4)
 */
internal data class ExercisesScreenState(
    val uiState: ExercisesUiState,
    val onAddExerciseClick: () -> Unit = {}
)

/**
 * Контент экрана «Упражнения»: TopAppBar, тело (список или заглушка),
 * FAB-заглушка. Используется UI-тестами и превью без ViewModel
 * (design Decision 3).
 *
 * @param state Данные экрана и колбэки (см. [ExercisesScreenState])
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisesScreenContent(
    state: ExercisesScreenState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        // System nav bar inset уже учтён NavigationBar в RootScreen;
        // здесь оставляем только горизонтальные insets (displayCutout и т.п.).
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.exercises_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = state.onAddExerciseClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.exercises_add_exercise)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            when (val s = state.uiState) {
                ExercisesUiState.Loading -> Unit
                is ExercisesUiState.Success -> SuccessBody(success = s)
            }
        }
    }
}

/**
 * Тело экрана для `ExercisesUiState.Success`: каталог или заглушка
 * пустого состояния (spec «Пустое состояние списка» — один текст по
 * центру, образец — главный экран).
 */
@Composable
private fun SuccessBody(success: ExercisesUiState.Success) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (success.exercises.isEmpty()) {
            Text(
                text = stringResource(R.string.exercises_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Стабильные ключи по id — конвенция AGENTS.md
                items(success.exercises, key = { it.id }) { exercise ->
                    ExerciseListItemRow(exercise = exercise)
                }
            }
        }
    }
}

/**
 * Строка каталога: название + лейбл типа измерения, комментарий ниже
 * при наличии (spec «Строка списка ExerciseListItemRow»; образец —
 * `EntryListItemRow`/`WeightRow` главного экрана).
 */
@Composable
internal fun ExerciseListItemRow(
    exercise: Exercise,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = measuredByLabel(exercise.measuredBy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
        exercise.comment?.let { comment ->
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Локализованный лейбл типа измерения. Тотальный `when` без `else`:
 * при добавлении значения enum компилятор заставит дописать ветку
 * (design Decision 5).
 */
@Composable
private fun measuredByLabel(measuredBy: ExerciseMeasuredBy): String =
    when (measuredBy) {
        ExerciseMeasuredBy.REPS -> stringResource(R.string.exercise_measured_by_reps)
        ExerciseMeasuredBy.TIME -> stringResource(R.string.exercise_measured_by_time)
    }
