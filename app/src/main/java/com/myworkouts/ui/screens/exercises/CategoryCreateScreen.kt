package com.myworkouts.ui.screens.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.ui.components.SaveBottomBar
import com.myworkouts.ui.state.CategoryCreateUiState
import com.myworkouts.ui.viewmodel.CategoryCreateViewModel

/**
 * Экран создания/редактирования категории: VM-обёртка — подписка на
 * состояние и колбэки, вся верстка — в [CategoryCreateScreenContent]
 * (custom-categories, design Decision 7 — уменьшенная копия
 * [ExerciseCreateScreen]).
 *
 * @param viewModel ViewModel экрана
 * @param onBackClick Навигация назад (закрытие экрана)
 * @param modifier внешний модификатор
 */
@Composable
fun CategoryCreateScreen(
    viewModel: CategoryCreateViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    CategoryCreateScreenContent(
        state =
            CategoryCreateScreenState(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onSaveClick = { viewModel.save(onSaved = onBackClick) },
                onBackClick = onBackClick
            ),
        modifier = modifier
    )
}

/**
 * Test/Preview-state для [CategoryCreateScreenContent]: поля
 * [CategoryCreateUiState] плюс колбэки. UI-тесты и превью передают
 * готовый state без ViewModel.
 */
internal data class CategoryCreateScreenState(
    val uiState: CategoryCreateUiState,
    val onNameChange: (String) -> Unit,
    val onSaveClick: () -> Unit,
    val onBackClick: () -> Unit
)

/**
 * Контент экрана создания/редактирования категории: TopAppBar со стрелкой
 * «назад» (заголовок зависит от режима), форма с единственным полем
 * «Название» и нижняя панель с кнопкой «Сохранить».
 *
 * Закрытие — единая точка [attemptClose] (стрелка TopAppBar и системный
 * back): при несохранённых изменениях показывается диалог подтверждения
 * (канон `ExerciseCreateScreenContent`, строки `entry_confirm_close_*`
 * переиспользуются без новых ключей).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryCreateScreenContent(
    state: CategoryCreateScreenState,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val attemptClose = {
        if (state.uiState.hasUnsavedChanges) showConfirmDialog = true else state.onBackClick()
    }

    // Системный «назад» идёт через то же подтверждение, что и стрелка
    // TopAppBar; без несохранённых изменений обработчик отключён
    BackHandler(enabled = state.uiState.hasUnsavedChanges) { attemptClose() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // Режим редактирования — «Изменить категорию», создание —
                // «Новая категория»
                title = {
                    Text(
                        stringResource(
                            if (state.uiState.isEditMode) {
                                R.string.category_edit_title
                            } else {
                                R.string.category_create_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = attemptClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Подсказка «категория не найдена» не нужна — кнопка
            // недостижимо disabled по `canSave`.
            SaveBottomBar(
                canSave = state.uiState.canSave,
                onSave = state.onSaveClick,
                showError = state.uiState.isSaveError,
                errorRes = R.string.category_save_error
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.uiState.name.orEmpty(),
                onValueChange = state.onNameChange,
                label = { Text(stringResource(R.string.category_field_name)) },
                singleLine = true,
                // Дубликат — подсказка под полем (spec «Валидация
                // уникальности имени категории»)
                supportingText = {
                    if (state.uiState.isDuplicateName) {
                        Text(
                            text = stringResource(R.string.category_error_duplicate),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showConfirmDialog) {
        ExerciseCreateCloseConfirmDialog(
            onConfirm = {
                showConfirmDialog = false
                state.onBackClick()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}
