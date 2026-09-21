package com.myworkouts.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.myworkouts.ui.state.MainUiState
import com.myworkouts.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
 * @param analyticsProvider Провайдер аналитики (FAB-событие в точке
 *   нажатия, design D10)
 * @param modifier внешний модификатор
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onAddEntryClick: () -> Unit,
    analyticsProvider: AnalyticsProvider,
    modifier: Modifier = Modifier,
    onEditEntryClick: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    // Транзитное состояние меню: какая запись выбрана + где на экране.
    // Само меню рисуется на уровне списка (design Decision 5), но
    // управляется корневым Composable — иначе его не видно из Content.
    var contextMenu by remember { mutableStateOf<ContextMenuState?>(null) }
    // Транзитное состояние карточки деталей: id открытой записи или null.
    // `rememberSaveable` — чтобы карточка переживала поворот экрана
    // (workout-detail-view, design Decision 2); данные самой записи
    // подтягиваются из `success.entries` по id на рендере.
    var detailsEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(enabled = detailsEntryId != null) { detailsEntryId = null }

    MainScreenContent(
        state =
            MainScreenState(
                uiState = uiState,
                contextMenu = contextMenu,
                detailsEntryId = detailsEntryId,
                onAddEntryClick = {
                    // FAB-событие в точке нажатия (design D10)
                    analyticsProvider.log(
                        AnalyticsEvent.UserAction(UserActionType.OPEN_ENTRY_CREATE)
                    )
                    onAddEntryClick()
                },
                onContextMenuItemChange = { newState -> contextMenu = newState },
                onEntryTap = { item ->
                    // Жест тапа и a11y-действие идут через разные колбэки:
                    // жест — если меню было открыто, popup его «поглощает»,
                    // карточку открывает повторный тап; a11y идёт мимо popup
                    // и всегда закрывает меню + открывает карточку. Без
                    // разделения при активном меню жест тапа открывал бы
                    // карточку сразу (поведение Material3 DropdownMenu по
                    // умолчанию тап не поглощает — work-out через
                    // `dismissOnClickOutside`), противореча спеке
                    // (workout-detail-view, design Risks/Trade-offs).
                    val menuOpen = contextMenu != null
                    contextMenu = null
                    if (!menuOpen) {
                        detailsEntryId = item.id
                    }
                },
                onEntryOpenDetails = { item ->
                    // A11y-канал деталей: всегда закрывает меню и открывает
                    // карточку — действие TalkBack обходит popup
                    // (workout-detail-view, design Risks/Trade-offs).
                    contextMenu = null
                    detailsEntryId = item.id
                },
                onCloseDetails = { detailsEntryId = null },
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
                onCancelDelete = viewModel::cancelDelete
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
 * @property detailsEntryId Id записи, для которой открыта карточка деталей
 *   (`null` — карточка закрыта); по этому id в `MainScreenContent`
 *   ищется запись в `uiState.Success.entries` (workout-detail-view,
 *   design Decision 3)
 */
internal data class MainScreenState(
    val uiState: MainUiState,
    val contextMenu: ContextMenuState? = null,
    val detailsEntryId: Long? = null,
    val onAddEntryClick: () -> Unit = {},
    val onContextMenuItemChange: (ContextMenuState?) -> Unit = {},
    val onEntryTap: (EntryListItem) -> Unit = {},
    val onEntryOpenDetails: (EntryListItem) -> Unit = {},
    val onCloseDetails: () -> Unit = {},
    val onRequestEdit: (EntryListItem) -> Unit = {},
    val onRequestDelete: (EntryListItem) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {}
)

/**
 * Контент главного экрана: TopAppBar, тело (список или заглушка), FAB,
 * long-press меню, алерт подтверждения удаления и overlay карточки
 * деталей поверх Scaffold. Используется для UI-тестов в изоляции без
 * ViewModel (задачи 5.1/5.3).
 *
 * Overlay рендерится ВНЕ `Scaffold` — внешний `Box` оборачивает и
 * Scaffold, и overlay: только так затемнение накрывает FAB/TopAppBar
 * (workout-detail-view, design Decision 1).
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
                TopAppBar(title = { Text(stringResource(R.string.workouts_title)) })
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
        // Карточка деталей ищется по id в уже загруженном списке —
        // без запроса к БД (workout-detail-view, design Decision 3);
        // `null` (запись удалили пока детали открыты) — карточка
        // анимированно закрывается (спека: «закрывается автоматически»).
        val success = state.uiState as? MainUiState.Success
        val detailsItem =
            success?.entries?.firstOrNull { it.id == state.detailsEntryId }
        DetailsOverlay(
            visible = detailsItem != null,
            item = detailsItem,
            onClose = state.onCloseDetails
        )
    }
}

/**
 * Overlay карточки деталей: скрим 32% по всему экрану (плавно проявляется
 * и гаснет) + карточка по центру с боковыми 24dp (выезжает снизу с
 * проявлением, закрывается — уезжает вниз с затуханием). Тап по скриму
 * закрывает карточку (workout-detail-view, design Decisions 1, 3, 6).
 *
 * Компонуется всегда, видимостью управляет [visible] — иначе не работают
 * анимации исчезновения. Во время exit-анимации карточка рендерится по
 * последней непустой записи ([item] уже `null`, а карточка ещё уезжает).
 *
 * Вынесено из [MainScreenContent] ради лимита detekt `LongMethod: 60` —
 * overlay добавляет достаточно строк, чтобы увести функцию за лимит.
 */
@Composable
private fun DetailsOverlay(
    visible: Boolean,
    item: EntryListItem?,
    onClose: () -> Unit
) {
    var lastItem by remember { mutableStateOf<EntryListItem?>(null) }
    item?.let { lastItem = it }
    val shownItem = item ?: lastItem
    val scrimInteractionSource = remember { MutableInteractionSource() }
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        // Скрим кликабелен по tap-зонам без карточки; ripple на
                        // затемнении не нужен (design Decision 6): indication
                        // отключён явно — дефолтный LocalIndication дал бы ripple.
                        .clickable(
                            interactionSource = scrimInteractionSource,
                            indication = null,
                            onClick = onClose
                        )
            )
        }
        // Компонуется ВСЕГДА: если создать AnimatedVisibility в момент,
        // когда visible уже true (первое открытие после старта/поворота
        // экрана), enter-анимация не выполнится — карточка появится рывком.
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            shownItem?.let { shown ->
                EntryDetailsCard(
                    item = shown,
                    onClose = onClose,
                    modifier =
                        Modifier
                            // Безопасные зоны: сбоку — системная панель навигации/cutout
                            // в ландшафте, сверху/снизу — статус-бар и жест-навигация
                            // (иначе высокая карточка уходит под них).
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            // Дыхание сверху/снизу: карточка не липнет к границам
                            // inset-зон при нехватке высоты (ландшафт).
                            .padding(vertical = 12.dp)
                            .padding(horizontal = 24.dp)
                )
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
                detailsEntryId = state.detailsEntryId,
                callbacks =
                    EntryListCallbacks(
                        onItemTap = state.onEntryTap,
                        onItemOpenDetails = state.onEntryOpenDetails,
                        onItemLongPress = { item, offsetInRoot ->
                            state.onContextMenuItemChange(
                                ContextMenuState(item = item, offsetInRoot = offsetInRoot)
                            )
                        },
                        onMenuDismiss = { state.onContextMenuItemChange(null) },
                        onRequestEdit = state.onRequestEdit,
                        onRequestDelete = state.onRequestDelete
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
    val onRequestEdit: (EntryListItem) -> Unit,
    val onRequestDelete: (EntryListItem) -> Unit,
    val onItemOpenDetails: (EntryListItem) -> Unit = {}
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
    detailsEntryId: Long?,
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
            // (старое поведение) ИЛИ карточка деталей (workout-detail-view,
            // design Decision 4 / task 3.4).
            isSelected = {
                contextMenu?.item?.id == it.id ||
                    detailsEntryId == it.id
            },
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
 * Жест тапа и a11y-действие «Показать детали» — разные колбэки: жест
 * учитывает открытое меню (popup поглощает тап, work-out через
 * `dismissOnClickOutside` Material3 DropdownMenu не делает этого сам —
 * см. `MainScreen.onEntryTap`), a11y всегда открывает карточку
 * (workout-detail-view, design Decision 4 + Risks/Trade-offs).
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
                    // A11y-действие «Показать детали» — отдельный
                    // колбэк: TalkBack обходит popup и должен всегда
                    // открывать карточку (workout-detail-view, design
                    // Risks/Trade-offs).
                    onOpenDetails = { callbacks.onItemOpenDetails(item) },
                    modifier =
                        Modifier
                            .onGloballyPositioned { coords ->
                                positionInRoot = coords.positionInRoot()
                                rowSize = coords.size
                            }.pointerInput(item.id) {
                                // Оба жеста в одном детекторе: long-press
                                // имеет приоритет, тап открывает карточку
                                // деталей (workout-detail-view, design
                                // Decision 4).
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
 * Меню `DropdownMenu` в точке long-press: «Изменить» (карандаш) выше
 * «Удалить» (корзина), у обоих `leadingIcon` с `contentDescription = null`
 * — текст пункта сам читается службами доступности (entry-edit-mode,
 * design Decision 6). Вынесено из
 * [EntryList], чтобы уложиться в лимит detekt `LongMethod: 60`.
 */
@Composable
private fun EntryContextMenu(
    item: EntryListItem,
    offset: DpOffset,
    onMenuDismiss: () -> Unit,
    onRequestEdit: (EntryListItem) -> Unit,
    onRequestDelete: (EntryListItem) -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onMenuDismiss,
        offset = offset
    ) {
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

/**
 * Алерт подтверждения удаления записи: заголовок «Удалить запись?»,
 * текст с названием упражнения, кнопка подтверждения в `colorScheme.error`
 * (design Decision 5).
 *
 * @param itemName Название упражнения в записи (для текста алерта)
 * @param onConfirm Обработчик «Удалить» — VM вызывает `confirmDelete()`
 * @param onCancel Обработчик «Отмена» — VM вызывает `cancelDelete()`
 */
@Composable
private fun EntryDeleteDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.entry_list_delete_dialog_title)) },
        text = {
            Text(stringResource(R.string.entry_list_delete_dialog_message, itemName))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
            ) {
                Text(stringResource(R.string.entry_list_menu_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
