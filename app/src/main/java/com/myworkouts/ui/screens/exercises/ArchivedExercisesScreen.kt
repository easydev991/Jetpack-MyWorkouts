package com.myworkouts.ui.screens.exercises

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.Exercise
import com.myworkouts.ui.state.ArchivedExercisesUiState
import com.myworkouts.ui.viewmodel.ArchivedExercisesViewModel

/**
 * Экран «Архив упражнений» — реактивный список архивных
 * упражнений каталога с возможностью восстановления через long-press
 * меню «Вернуть» (exercises-archive, capability
 * `archived-exercises-screen`).
 *
 * Не участвует в нижней навигации (`Screen.ArchivedExercises` НЕ в
 * `BottomTabs`) — паттерн `Screen.Appearance`
 * (non-tab-screen-navigation/spec.md).
 *
 * VM-обёртка: подписка на [ArchivedExercisesViewModel.uiState], локальное
 * состояние long-press меню. Вся верстка — в
 * [ArchivedExercisesScreenContent].
 *
 * @param viewModel ViewModel с реактивным списком архивных
 * @param onBackClick Тап стрелки «назад» в TopAppBar → навигация
 *   обратно на [com.myworkouts.navigation.Screen.Exercises]
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedExercisesScreen(
    viewModel: ArchivedExercisesViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var contextMenu by remember { mutableStateOf<ExerciseContextMenuState?>(null) }

    ArchivedExercisesScreenContent(
        state =
            ArchivedExercisesState(
                uiState = uiState,
                contextMenu = contextMenu,
                onContextMenuItemChange = { contextMenu = it },
                onUnarchive = { item ->
                    // Паттерн ExercisesScreen.onRequestArchive — обёртка
                    // закрывает меню и делегирует без побочных эффектов
                    contextMenu = null
                    viewModel.unarchive(item)
                },
                onBackClick = onBackClick
            ),
        modifier = modifier
    )
}

/**
 * Test/Preview-state для [ArchivedExercisesScreenContent]: данные
 * [ArchivedExercisesUiState] плюс колбэки. UI-тесты и превью передают
 * готовый state без ViewModel (образец — `ExercisesScreenState`).
 *
 * `contextMenu` хранит [ExerciseContextMenuState] (общий тип с
 * `ExercisesScreen`) — структура идентична, отдельный тип не нужен.
 *
 * @property contextMenu Транзитное состояние long-press меню
 *   (`null` — закрыто). Прокидывается из VM-обёртки
 *   [ArchivedExercisesScreen], которая хранит `remember`
 *   `mutableStateOf`.
 */
internal data class ArchivedExercisesState(
    val uiState: ArchivedExercisesUiState,
    val contextMenu: ExerciseContextMenuState? = null,
    val onContextMenuItemChange: (ExerciseContextMenuState?) -> Unit = {},
    val onUnarchive: (Exercise) -> Unit = {},
    val onBackClick: () -> Unit = {}
)

/**
 * Контент экрана «Архив упражнений»: TopAppBar со стрелкой
 * «назад» + тело (LazyColumn со списком или заглушка пустого
 * состояния) + long-press меню «Вернуть».
 *
 * Используется для UI-тестов и превью без ViewModel (design Decision 3,
 * change `entry-create-screen`).
 *
 * @param state Данные экрана и колбэки (см. [ArchivedExercisesState])
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchivedExercisesScreenContent(
    state: ArchivedExercisesState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        // System nav bar inset уже учтён NavigationBar в RootScreen;
        // здесь оставляем только горизонтальные insets (displayCutout
        // и т.п.) — паттерн ExercisesScreen/AppearanceScreen.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archived_exercises_title)) },
                navigationIcon = {
                    IconButton(onClick = state.onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            when (val s = state.uiState) {
                ArchivedExercisesUiState.Loading -> Unit
                is ArchivedExercisesUiState.Success ->
                    SuccessBody(state = state, success = s)
            }
        }
    }
}

/**
 * Тело экрана для `ArchivedExercisesUiState.Success`: список
 * архивных упражнений или заглушка пустого состояния. Длина
 * списка = счётчик архивных (design Decision 8).
 */
@Composable
private fun SuccessBody(
    state: ArchivedExercisesState,
    success: ArchivedExercisesUiState.Success
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (success.exercises.isEmpty()) {
            // Заглушка пустого состояния — паттерн `exercises_list_empty`
            // на ExercisesScreen.
            Text(
                text = stringResource(R.string.archived_exercises_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
            )
        } else {
            ArchivedExercisesList(state = state, success = success)
        }
    }
}

/**
 * Список архивных упражнений + контекстное меню «Вернуть»:
 * long-press открывает меню у точки нажатия (паттерн
 * `ExercisesList` в `ExercisesScreen.kt`).
 */
@Composable
private fun ArchivedExercisesList(
    state: ArchivedExercisesState,
    success: ArchivedExercisesUiState.Success
) {
    var anchorOrigin by remember { mutableStateOf(IntOffset.Zero) }
    var anchorHeight by remember { mutableIntStateOf(0) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    anchorOrigin = IntOffset(pos.x.toInt(), pos.y.toInt())
                    anchorHeight = coords.size.height
                }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = success.exercises,
                key = { it.id },
                contentType = { "row" }
            ) { exercise ->
                var positionInRoot by remember { mutableStateOf(Offset.Zero) }
                ExerciseListItemRow(
                    exercise = exercise,
                    isSelected = state.contextMenu?.item?.id == exercise.id,
                    modifier =
                        Modifier
                            .onGloballyPositioned { coords ->
                                positionInRoot = coords.positionInRoot()
                            }.applyLongPress(
                                item = exercise,
                                positionInRootProvider = { positionInRoot },
                                onItemLongPress = state.onContextMenuItemChange,
                                openMenuLabel = stringResource(R.string.a11y_open_menu)
                            )
                )
            }
        }
        state.contextMenu?.let { menu ->
            ExerciseContextMenu(
                offset = contextMenuDpOffset(menu.offsetInRoot, anchorOrigin, anchorHeight),
                mode = ExerciseMenuMode.Unarchive,
                isCustom = false,
                onMenuDismiss = { state.onContextMenuItemChange(null) },
                onUnarchiveClick = { state.onUnarchive(menu.item) }
            )
        }
    }
}
