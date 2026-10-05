package com.myworkouts.ui.screens.exercises

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import com.myworkouts.R
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.Exercise

/**
 * Список с long-press контекстным меню — общий рендер для каталога
 * и архива, упражнений и категорий (custom-categories, реюз
 * четырёх одинаковых списков): anchor-`Box` для смещения меню к точке
 * нажатия (паттерн MainScreen.kt:261–271), `LazyColumn` со
 * стабильным ключом [keyOf], per-row `positionInRoot` +
 * [applyLongPress] и слот меню с готовым [DpOffset].
 *
 * @param items Элементы списка
 * @param keyOf Стабильный ключ строки (`id`)
 * @param menuOffsetInRoot Офсет long-press открытого меню в корне
 *   (`null` — меню закрыто, слот [menu] не вызывается)
 * @param onLongPress Открытие меню: элемент + офсет long-press в корне;
 *   вызывающий собирает свой контекст-стейт
 * @param rowContent Строка списка; второй аргумент — модификатор с
 *   long-press и a11y-действием «Открыть меню» (обязателен к применению)
 * @param menu Слот `DropdownMenu`: вызывается с офсетом относительно
 *   списка; видимость решает вызывающий через [menuOffsetInRoot]
 */
@Suppress("LongParameterList")
@Composable
internal fun <T> ContextMenuList(
    items: List<T>,
    keyOf: (T) -> Long,
    menuOffsetInRoot: Offset?,
    onLongPress: (T, Offset) -> Unit,
    rowContent: @Composable (T, Modifier) -> Unit,
    menu: @Composable (DpOffset) -> Unit
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
                items = items,
                key = keyOf,
                contentType = { "row" }
            ) { item ->
                var positionInRoot by remember { mutableStateOf(Offset.Zero) }
                rowContent(
                    item,
                    Modifier
                        .onGloballyPositioned { coords ->
                            positionInRoot = coords.positionInRoot()
                        }.applyLongPress(
                            item = item,
                            positionInRootProvider = { positionInRoot },
                            onItemLongPress = onLongPress,
                            openMenuLabel = stringResource(R.string.a11y_open_menu)
                        )
                )
            }
        }
        menuOffsetInRoot?.let { offsetInRoot ->
            menu(contextMenuDpOffset(offsetInRoot, anchorOrigin, anchorHeight))
        }
    }
}

/**
 * Вычисляет `DpOffset` для `DropdownMenu` относительно якоря списка:
 * позиция long-press в корне минус позиция списка в корне, по высоте
 * дополнительно учитывается высота контейнера (long-press-меню
 * привязывается к нижней границе строки; паттерн MainScreen.kt).
 * Общий хелпер `ExercisesScreen` и `ArchivedExercisesScreen`; вынесен
 * из `ExercisesScreen.kt` ради лимита detekt `TooManyFunctions`.
 */
@Composable
internal fun contextMenuDpOffset(
    offsetInRoot: Offset,
    anchorOrigin: IntOffset,
    anchorHeight: Int
): DpOffset {
    val density = LocalDensity.current
    return DpOffset(
        x = with(density) { (offsetInRoot.x - anchorOrigin.x).toDp() },
        y = with(density) { (offsetInRoot.y - anchorOrigin.y - anchorHeight).toDp() }
    )
}

/**
 * Подключает `detectTapGestures(onLongPress)` для строки списка.
 * Long-press активен для всех строк каталога: для кастомных — меню из
 * трёх пунктов (Изменить/Архивировать/Удалить), для стандартных —
 * единственный пункт «Архивировать». Меню выбирает пункты по `isCustom`
 * само (exercises-archive Decision 4; custom-categories — то же для
 * категорий). Общий хелпер строк упражнений и категорий экранов
 * `ExercisesScreen` и `ArchivedExercisesScreen`; обобщён по типу строки
 * ([Exercise]/[Category]) в custom-categories 5.4, вынесен из
 * `ExercisesScreen.kt` ради лимита detekt `TooManyFunctions`.
 *
 * Accessibility-канал (accessibility-semantics-fixes, design D1/D3):
 * на тот же узел добавляется `semantics { customActions }` с лейблом
 * `a11y_open_menu` — TalkBack показывает действие «Открыть меню»;
 * выполнение открывает то же меню (офсет — верхний левый угол строки,
 * `positionInRootProvider`). Одна правка хелпера покрывает оба экрана.
 *
 * `positionInRootProvider` — лямбда, возвращающая актуальную позицию
 * строки в корне: closure ссылается на `mutableStateOf<Offset>`, чтобы
 * избежать пересоздания `pointerInput` при каждом изменении позиции
 * (иначе long-press отваливался бы на скролле).
 *
 * @param item Строка списка ([Exercise] или [Category]) — заодно ключ
 *   `pointerInput` (data class, рестарт при смене значения)
 * @param onItemLongPress Открытие меню: элемент + офсет long-press
 *   в корне; вызывающий собирает свой контекст-стейт
 *   ([ExerciseContextMenuState] / [CategoryContextMenuState])
 */
internal fun <T> Modifier.applyLongPress(
    item: T,
    positionInRootProvider: () -> Offset,
    onItemLongPress: (item: T, offsetInRoot: Offset) -> Unit,
    openMenuLabel: String
): Modifier =
    this
        .pointerInput(item) {
            detectTapGestures(
                onLongPress = { localOffset ->
                    onItemLongPress(item, positionInRootProvider() + localOffset)
                }
            )
        }.semantics(mergeDescendants = true) {
            // mergeDescendants: действие обязано попасть в тот же узел,
            // что и текст строки — иначе TalkBack не покажет его в меню
            // действий строки.
            customActions =
                listOf(
                    CustomAccessibilityAction(openMenuLabel) {
                        onItemLongPress(item, positionInRootProvider())
                        true
                    }
                )
        }
