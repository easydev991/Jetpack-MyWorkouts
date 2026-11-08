package com.myworkouts.ui.screens.main

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.components.EntryDeleteDialog
import com.myworkouts.ui.state.MainUiState
import com.myworkouts.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Публичный composable с опциональными навигационными колбэками —
// параметр на переход (паттерн ExercisesScreen)

/**
 * Главный экран — вкладка «Тренировки»: список записей, удаление через
 * long-press, пустое состояние, FAB создания записи
 * (workout-entries-list, spec «MainScreen Scaffold with TopAppBar»).
 *
 * VM-обёртка: подписка на [MainViewModel.uiState] и локальное состояние
 * long-press меню (диалог удаления живёт в `MainUiState.pendingDeleteId`).
 * Вся верстка — в [MainScreenContent].
 *
 * @param viewModel ViewModel с реактивным списком записей и pending-удалением
 * @param onAddEntryClick Тап по FAB: открыть экран создания записи.
 *   MainScreen не знает о навигации — маршрутизацией занимается RootScreen.
 * @param onEditEntryClick Выбор пункта «Изменить» в long-press меню:
 *   открыть экран редактирования записи с этим id (entry-edit-mode).
 *   Дефолт-заглушка — как у [onAddEntryClick], навигация не здесь.
 * @param onOpenEntryClick Открытие экрана деталей записи с этим id:
 *   тап по строке (при закрытом меню), пункт «Открыть» и a11y-действие
 *   «Показать детали» (workout-entry-detail-screen, design Decision 7).
 *   Дефолт-заглушка — как у [onEditEntryClick], навигация не здесь.
 * @param analyticsProvider Провайдер аналитики (FAB-событие в точке
 *   нажатия, design D10)
 * @param modifier внешний модификатор
 */
@Suppress("LongParameterList")
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onAddEntryClick: () -> Unit,
    analyticsProvider: AnalyticsProvider,
    modifier: Modifier = Modifier,
    onEditEntryClick: (Long) -> Unit = {},
    onOverviewClick: () -> Unit = {},
    onOpenEntryClick: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    // Транзитное состояние меню: какая запись выбрана + где на экране.
    // Само меню рисуется на уровне списка (design Decision 5), но
    // управляется корневым Composable — иначе его не видно из Content.
    var contextMenu by remember { mutableStateOf<ContextMenuState?>(null) }
    // Единый канал открытия деталей: лог события в точке действия +
    // навигационный колбэк (workout-entry-detail-screen, design Decisions 7/9)
    val openEntryDetails: (Long) -> Unit = { id ->
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.OPEN_ENTRY_DETAIL)
        )
        onOpenEntryClick(id)
    }

    MainScreenContent(
        state =
            MainScreenState(
                uiState = uiState,
                contextMenu = contextMenu,
                onAddEntryClick = {
                    // FAB-событие в точке нажатия (design D10)
                    analyticsProvider.log(
                        AnalyticsEvent.UserAction(UserActionType.OPEN_ENTRY_CREATE)
                    )
                    onAddEntryClick()
                },
                onOverviewClick = {
                    analyticsProvider.log(
                        AnalyticsEvent.UserAction(UserActionType.OPEN_OVERVIEW)
                    )
                    onOverviewClick()
                },
                onContextMenuItemChange = { newState -> contextMenu = newState },
                onEntryTap = { item ->
                    if (contextMenu != null) {
                        // Popup Material3 DropdownMenu тап сам не поглощает:
                        // тап по строке при открытом меню гасит только меню
                        // (workout-detail-view, design Decision 4)
                        contextMenu = null
                    } else {
                        // Тап по строке при закрытом меню — открытие деталей
                        // (workout-entry-detail-screen, design Decision 7)
                        openEntryDetails(item.id)
                    }
                },
                onRequestView = { item ->
                    // Паттерн onRequestEdit: обёртка закрывает меню,
                    // открывает детали тем же каналом (design Decision 7)
                    contextMenu = null
                    openEntryDetails(item.id)
                },
                onRequestEdit = { item ->
                    // Паттерн onRequestDelete: обёртка закрывает меню,
                    // делегирует без побочных эффектов (design Decision 6)
                    contextMenu = null
                    onEditEntryClick(item.id)
                },
                onRequestDelete = { item ->
                    contextMenu = null
                    viewModel.requestDelete(item)
                },
                onConfirmDelete = viewModel::confirmDelete,
                onCancelDelete = viewModel::cancelDelete,
                onOpenDetails = { item ->
                    // A11y-канал (design Decision 7): действие «Показать
                    // детали» всегда открывает экран, минуя popup — меню
                    // гасится навигацией
                    contextMenu = null
                    openEntryDetails(item.id)
                }
            ),
        modifier = modifier
    )
}

/** Транзитное состояние long-press меню: какая запись + где показать. */
internal data class ContextMenuState(
    val item: EntryListItem,
    val offsetInRoot: Offset
)

/**
 * Test/Preview-state для [MainScreenContent]: данные [MainUiState] плюс
 * колбэки. UI-тесты и превью передают готовый state без ViewModel
 * (образец — `EntryCreateScreenState`).
 *
 * @property contextMenu Транзитное состояние long-press меню (`null` — закрыто)
 */
internal data class MainScreenState(
    val uiState: MainUiState,
    val contextMenu: ContextMenuState? = null,
    val onAddEntryClick: () -> Unit = {},
    val onOverviewClick: () -> Unit = {},
    val onContextMenuItemChange: (ContextMenuState?) -> Unit = {},
    val onEntryTap: (EntryListItem) -> Unit = {},
    val onRequestView: (EntryListItem) -> Unit = {},
    val onRequestEdit: (EntryListItem) -> Unit = {},
    val onRequestDelete: (EntryListItem) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onOpenDetails: (EntryListItem) -> Unit = {}
)

/**
 * Контент главного экрана: TopAppBar, тело (список или заглушка), FAB,
 * long-press меню и алерт подтверждения удаления. Используется для
 * UI-тестов в изоляции без ViewModel (задачи 5.1/5.3).
 *
 * @param state Данные экрана и колбэки (см. [MainScreenState])
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreenContent(
    state: MainScreenState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            // System nav bar inset уже учтён NavigationBar в RootScreen;
            // здесь оставляем только горизонтальные insets (displayCutout и т.п.).
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            topBar = {
                // Кнопка обзора видима, пока есть записи (спека:
                // исчезает при удалении последней записи)
                val overviewVisible =
                    (state.uiState as? MainUiState.Success)?.entries?.isNotEmpty() == true
                TopAppBar(
                    title = { Text(stringResource(R.string.workouts_title)) },
                    actions = {
                        if (overviewVisible) {
                            IconButton(onClick = state.onOverviewClick) {
                                Icon(
                                    Icons.Filled.DonutLarge,
                                    contentDescription = stringResource(R.string.a11y_open_overview)
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = state.onAddEntryClick) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_workout)
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
                    MainUiState.Loading -> Unit
                    is MainUiState.Success -> SuccessBody(state = state, success = s)
                }
            }
        }
    }
}

/**
 * Тело экрана для `MainUiState.Success`: список (или заглушка),
 * long-press меню и алерт удаления. Извлечено из [MainScreenContent]
 * ради лимита detekt `LongMethod: 60` — `Scaffold` с обвязкой уже
 * занимает ~40 строк, сюда выносится только основная развилка.
 */
@Composable
private fun SuccessBody(
    state: MainScreenState,
    success: MainUiState.Success
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (success.entries.isEmpty()) {
            // Заглушка пустого состояния (spec «Пустое состояние списка
            // записей», design Decision 6): один текст без подзаголовка.
            Text(
                text = stringResource(R.string.entry_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
            )
        } else {
            // Группировка — производное от записей, считается на рендере:
            // `remember` кэширует до смены `entries` (design Decision 2)
            val sections = remember(success.entries) { groupEntriesByDate(success.entries) }
            EntryList(
                sections = sections,
                separator = stringResource(R.string.entry_value_separator),
                contextMenu = state.contextMenu,
                callbacks =
                    EntryListCallbacks(
                        onItemTap = state.onEntryTap,
                        onItemLongPress = { item, offsetInRoot ->
                            state.onContextMenuItemChange(
                                ContextMenuState(item = item, offsetInRoot = offsetInRoot)
                            )
                        },
                        onMenuDismiss = { state.onContextMenuItemChange(null) },
                        onRequestView = state.onRequestView,
                        onRequestEdit = state.onRequestEdit,
                        onRequestDelete = state.onRequestDelete,
                        onOpenDetails = state.onOpenDetails
                    )
            )
        }

        val pendingItem = success.entries.firstOrNull { it.id == success.pendingDeleteId }
        if (pendingItem != null) {
            EntryDeleteDialog(
                itemName = pendingItem.exerciseName,
                onConfirm = state.onConfirmDelete,
                onCancel = state.onCancelDelete
            )
        }
    }
}

/**
 * Колбэки тела списка записей (workout-entries-list, design Decision 5).
 * Группировка — чтобы [EntryList] уложился в лимит detekt по параметрам
 * (6) без `ignoreDefaultParameters: true`.
 */
internal data class EntryListCallbacks(
    val onItemTap: (EntryListItem) -> Unit,
    val onItemLongPress: (EntryListItem, Offset) -> Unit,
    val onMenuDismiss: () -> Unit,
    val onRequestView: (EntryListItem) -> Unit,
    val onRequestEdit: (EntryListItem) -> Unit,
    val onRequestDelete: (EntryListItem) -> Unit,
    val onOpenDetails: (EntryListItem) -> Unit
)

/**
 * Тело списка: `LazyColumn` с секциями [EntryListItems] + long-press
 * меню `DropdownMenu` поверх (workout-entries-list, design Decision 5;
 * workout-entries-by-date-sections, design Decision 5).
 *
 * Длинный body — ради лимита detekt `LongMethod: 60` — `Scaffold` с
 * обвязкой уже занимает ~40 строк, сюда выносится только основная развилка.
 */
@Composable
private fun EntryList(
    sections: List<DateSection>,
    separator: String,
    contextMenu: ContextMenuState?,
    callbacks: EntryListCallbacks
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
        EntryListItems(
            sections = sections,
            separator = separator,
            // Строка выделена, если для неё открыто контекстное меню
            isSelected = { contextMenu?.item?.id == it.id },
            callbacks = callbacks
        )
        if (contextMenu != null) {
            val density = LocalDensity.current
            val menuOffset =
                DpOffset(
                    x =
                        with(density) {
                            (contextMenu.offsetInRoot.x - anchorOrigin.x).toDp()
                        },
                    y =
                        with(density) {
                            (contextMenu.offsetInRoot.y - anchorOrigin.y - anchorHeight)
                                .toDp()
                        }
                )
            EntryContextMenu(
                item = contextMenu.item,
                offset = menuOffset,
                onMenuDismiss = callbacks.onMenuDismiss,
                onRequestView = callbacks.onRequestView,
                onRequestEdit = callbacks.onRequestEdit,
                onRequestDelete = callbacks.onRequestDelete
            )
        }
    }
}

/**
 * `LazyColumn` секций: sticky-заголовки (кроме «Сегодня») + строки
 * с tap и long-press. Разные `contentType` у заголовков/строк — раздельные
 * пулы переиспользования против «моргания» при скролле.
 *
 * Жест тапа закрывает открытое меню: popup Material3 DropdownMenu тап сам
 * не поглощает (`dismissOnClickOutside` не работает в эту сторону —
 * см. `MainScreen.onEntryTap`).
 */
@Composable
private fun EntryListItems(
    sections: List<DateSection>,
    separator: String,
    isSelected: (EntryListItem) -> Boolean,
    callbacks: EntryListCallbacks
) {
    val yesterdayLabel = stringResource(R.string.entry_section_yesterday)
    val currentYearFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }
    val archiveFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy") }
    val today = remember { LocalDate.now() }
    val yesterday = today.minusDays(1)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            // Секция «Сегодня» — без заголовка (design Decision 5)
            if (section.date != today) {
                stickyHeader(key = section.date.toEpochDay(), contentType = "section") {
                    SectionHeader(
                        title =
                            if (section.date == yesterday) {
                                yesterdayLabel
                            } else if (section.date.year == today.year) {
                                section.date.format(currentYearFormatter)
                            } else {
                                section.date.format(archiveFormatter)
                            }
                    )
                }
            }
            items(items = section.items, key = { it.id }, contentType = { "row" }) { item ->
                var positionInRoot by remember { mutableStateOf(Offset.Zero) }
                var rowSize by remember { mutableStateOf(IntSize.Zero) }
                EntryListItemRow(
                    item = item,
                    separator = separator,
                    isSelected = isSelected(item),
                    // A11y-канал меню: центр строки по позиции и размеру из
                    // того же onGloballyPositioned, что и long-press
                    // (accessibility-semantics-fixes, design D2/D3).
                    onOpenMenu = {
                        callbacks.onItemLongPress(
                            item,
                            positionInRoot +
                                Offset(x = rowSize.width / 2f, y = rowSize.height / 2f)
                        )
                    },
                    // A11y-канал деталей: реальный колбэк вместо дефолтного
                    // no-op — действие «Показать детали» открывает экран
                    // (workout-entry-detail-screen, design Decision 7)
                    onOpenDetails = { callbacks.onOpenDetails(item) },
                    modifier =
                        Modifier
                            .onGloballyPositioned { coords ->
                                positionInRoot = coords.positionInRoot()
                                rowSize = coords.size
                            }.pointerInput(item.id) {
                                // Оба жеста в одном детекторе: long-press
                                // имеет приоритет, тап закрывает открытое
                                // меню (см. `MainScreen.onEntryTap`).
                                detectTapGestures(
                                    onTap = { callbacks.onItemTap(item) },
                                    onLongPress = { localOffset ->
                                        callbacks.onItemLongPress(item, positionInRoot + localOffset)
                                    }
                                )
                            }
                )
            }
        }
    }
}

/**
 * Меню `DropdownMenu` в точке long-press: «Открыть» (глаз) выше
 * «Изменить» (карандаш) выше «Удалить» (корзина), у всех `leadingIcon`
 * с `contentDescription = null` — текст пункта сам читается службами
 * доступности (entry-edit-mode design Decision 6,
 * workout-entry-detail-screen design Decision 7). Вынесено из
 * [EntryList], чтобы уложиться в лимит detekt `LongMethod: 60`.
 */
@Composable
private fun EntryContextMenu(
    item: EntryListItem,
    offset: DpOffset,
    onMenuDismiss: () -> Unit,
    onRequestView: (EntryListItem) -> Unit,
    onRequestEdit: (EntryListItem) -> Unit,
    onRequestDelete: (EntryListItem) -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onMenuDismiss,
        offset = offset
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.entry_list_menu_view)) },
            onClick = { onRequestView(item) },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Visibility, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.entry_list_menu_edit)) },
            onClick = { onRequestEdit(item) },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.entry_list_menu_delete)) },
            onClick = { onRequestDelete(item) },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
            }
        )
    }
}
