package com.myworkouts.ui.screens.entrydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.components.EntryDeleteDialog
import com.myworkouts.ui.icons.ExerciseIcon
import com.myworkouts.ui.state.EntryDetailUiState
import com.myworkouts.ui.util.formatDetailDateTime
import com.myworkouts.ui.util.formatDurationResource
import com.myworkouts.ui.util.formatEntryDetailsValue
import com.myworkouts.ui.util.formatWeightKg
import com.myworkouts.ui.viewmodel.EntryDetailViewModel

/**
 * Test/Preview-state для [EntryDetailScreenContent]: поля состояния экрана
 * плюс колбэки. UI-тесты и превью передают готовый state без ViewModel —
 * тот же паттерн, что [com.myworkouts.ui.screens.entry.EntryCreateScreenState].
 */
internal data class EntryDetailScreenState(
    val uiState: EntryDetailUiState,
    val pendingDelete: Boolean,
    val onBackClick: () -> Unit,
    val onEditClick: () -> Unit,
    val onDeleteClick: () -> Unit,
    val onDeleteConfirm: () -> Unit,
    val onDeleteDismiss: () -> Unit
)

/**
 * Экран деталей записи (workout-entry-detail-screen, design Decision 6):
 * `Scaffold` с `TopAppBar` («Детали записи», стрелка назад, actions
 * «Изменить»/«Удалить» только в `Success`) и контент по центру свободного
 * места без Card-фона — иконка, название, значение, дата/время,
 * комментарий в тональном блоке.
 *
 * Состояния — [EntryDetailUiState]: `Loading` — пустой контент (до первой
 * эмиссии репозитория), `Success` — детали записи, `Error` — заглушка
 * «Запись не найдена» (запись удалена или id некорректен).
 *
 * Удаление — трёхшаговое через [EntryDetailViewModel]: «Удалить» →
 * [EntryDetailViewModel.requestDelete] (открывает алерт [EntryDeleteDialog]),
 * подтверждение → [EntryDetailViewModel.confirmDelete] с навигацией назад
 * после успешного удаления (design Decision 4 — отдельный навигационный
 * колбэк удаления не нужен, как `save(onSaved = onBackClick)` на экране
 * создания записи).
 *
 * @param viewModel ViewModel экрана
 * @param onBackClick Навигация назад (закрытие экрана)
 * @param onEditClick Навигация к редактированию записи
 */
@Composable
fun EntryDetailScreen(
    viewModel: EntryDetailViewModel,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()

    EntryDetailScreenContent(
        state =
            EntryDetailScreenState(
                uiState = uiState,
                pendingDelete = pendingDelete,
                onBackClick = onBackClick,
                onEditClick = onEditClick,
                onDeleteClick = viewModel::requestDelete,
                onDeleteConfirm = { viewModel.confirmDelete(onDeleted = onBackClick) },
                onDeleteDismiss = viewModel::cancelDelete
            )
    )
}

/**
 * Контент экрана деталей записи: TopAppBar и состояние по центру. UI-тесты
 * и превью используют [EntryDetailScreenState] без ViewModel.
 */
@Composable
internal fun EntryDetailScreenContent(
    state: EntryDetailScreenState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { EntryDetailTopAppBar(state = state) }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (val uiState = state.uiState) {
                EntryDetailUiState.Loading -> Unit
                is EntryDetailUiState.Success -> EntryDetailContent(item = uiState.data)
                EntryDetailUiState.Error ->
                    Text(
                        text = stringResource(R.string.entry_detail_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
            }
        }
        if (state.pendingDelete && state.uiState is EntryDetailUiState.Success) {
            val itemName = state.uiState.data.exerciseName
            EntryDeleteDialog(
                itemName = itemName,
                onConfirm = state.onDeleteConfirm,
                onCancel = state.onDeleteDismiss
            )
        }
    }
}

/**
 * TopAppBar экрана деталей: заголовок «Детали записи», стрелка назад;
 * actions «Изменить»/«Удалить» — только в `Success` (design Decision 6):
 * в `Loading`/`Error` действий над записью нет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDetailTopAppBar(
    state: EntryDetailScreenState,
    modifier: Modifier = Modifier
) {
    val showActions = state.uiState is EntryDetailUiState.Success
    TopAppBar(
        title = { Text(stringResource(R.string.entry_detail_title)) },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = state.onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            if (showActions) {
                IconButton(onClick = state.onEditClick) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.entry_list_menu_edit)
                    )
                }
                IconButton(onClick = state.onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.entry_list_menu_delete)
                    )
                }
            }
        }
    )
}

/**
 * Детали записи по центру свободного места, без Card-фона (утверждённый
 * вариант A2): иконка, название `titleLarge` по центру, крупное значение
 * (`headlineMedium` `primary`) в формате «4 х 10 (+ 5 кг)», дата/время
 * `bodySmall` `onSurfaceVariant`, комментарий целиком в тональном блоке
 * `surfaceVariant` (только при наличии).
 *
 * Ширина не ограничена: контент занимает доступную ширину с
 * горизонтальным паддингом 24dp (решение владельца после реализации —
 * cap 350dp снят).
 */
@Composable
private fun EntryDetailContent(
    item: EntryListItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExerciseIcon(iconNumber = item.iconNumber)
        Text(
            text = item.exerciseName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = entryDetailsValueText(item = item),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = formatDetailDateTime(dateMillis = item.date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val comment = item.comment
        if (!comment.isNullOrBlank()) {
            DetailComment(comment = comment)
        }
    }
}

/**
 * Значение деталей записи: повторы или длительность + опциональный
 * локализованный суффикс веса «(+ N кг)» (одной строкой через пробел).
 * Локализованный суффикс веса берётся из ресурса на стороне UI; чистая
 * склейка — [formatEntryDetailsValue]. Переезжает из `EntryDetailsCard.kt`
 * (задача 3.2 плана workout-entry-detail-screen).
 */
@Composable
private fun entryDetailsValueText(item: EntryListItem): String {
    val value =
        item.reps?.toString() ?: formatDurationResource(item.durationSec ?: 0)
    val separator = stringResource(R.string.entry_value_separator)
    val weightSuffix =
        item.weightKg?.takeIf { it > 0.0 }?.let { weight ->
            stringResource(R.string.entry_details_weight_suffix, formatWeightKg(weight))
        }
    return formatEntryDetailsValue(
        sets = item.sets,
        value = value,
        separator = separator,
        weightSuffix = weightSuffix
    )
}

/**
 * Комментарий в тональном блоке — целиком: без `maxLines`/`Ellipsis`,
 * в отличие от строки списка (spec «Содержимое карточки деталей записи»).
 * Высота не фиксирована: блок растёт по содержимому, при переполнении
 * доступного места текст скроллится внутри (решение владельца после
 * реализации — cap 200dp снят). Переезжает из `EntryDetailsCard.kt`
 * (задача 3.2 плана workout-entry-detail-screen).
 */
@Composable
private fun DetailComment(
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
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
        )
    }
}
