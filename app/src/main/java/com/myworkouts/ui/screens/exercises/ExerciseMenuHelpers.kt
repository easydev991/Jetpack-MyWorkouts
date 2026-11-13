package com.myworkouts.ui.screens.exercises

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.myworkouts.R
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.Exercise
import kotlin.math.roundToInt

/**
 * Список с long-press контекстным меню — общий рендер для каталога
 * и архива, упражнений и категорий (custom-categories, реюз
 * четырёх одинаковых списков): `LazyColumn` со стабильным ключом
 * [keyOf], per-row `positionInRoot` + [applyLongPress] и слот меню,
 * якорем которого служит нулевой `Box` в точке нажатия (см. ниже).
 *
 * Якорь меню: `DropdownMenu` позиционируется относительно ближайшего
 * layout-родителя своего anchor-Layout'а, поэтому при открытом меню
 * контент рендерится в отдельном узле-Box (0x0), который корневой
 * custom `Layout` размещает в точке long-press В КООРДИНАТАХ СПИСКА
 * (`menuOffsetInRoot − listOrigin`; origin списка —
 * `onGloballyPositioned`). Важно: якорь обязан быть ОТДЕЛЬНЫМ layout-
 * узлом, размещённым родителем — смещение через `Modifier.layout` не
 * работает: `parentLayoutCoordinates` popup'а видит внешний Node
 * (верх списка), а не placement детей layout-модификатора. И переносить
 * `menuOffsetInRoot` в place напрямую нельзя — это координаты корня
 * окна, и меню уезжает вниз ровно на смещение списка в окне
 * (TopAppBar + табы). С нулевым якорем в точке нажатия лестница
 * `DropdownMenuPositionProvider` при любом раскладе остаётся
 * локальной у пальца: меню открывается «верхом в точку нажатия», а
 * если не влезает вниз экрана — переворачивается низом к точке
 * нажатия. Смещать меню offset'ом от границ большого контейнера
 * нельзя: у нижних строк переполненного списка фолбэк-кандидаты
 * лестницы трактуют отрицательный offset относительно ВЕРХА якоря, и
 * меню улетает под TopAppBar (репро: long-press нижней видимой строки
 * каталога; exercises-screen «Жест и позиция меню»).
 *
 * @param items Элементы списка
 * @param keyOf Стабильный ключ строки (`id`)
 * @param menuOffsetInRoot Офсет long-press открытого меню в корне
 *   (`null` — меню закрыто, слот [menu] не вызывается)
 * @param onLongPress Открытие меню: элемент + офсет long-press в корне;
 *   вызывающий собирает свой контекст-стейт
 * @param rowContent Строка списка; второй аргумент — модификатор с
 *   long-press и a11y-действием «Открыть меню» (обязателен к применению)
 * @param menu Слот `DropdownMenu` (позиционируется нулевым якорем);
 *   видимость решает вызывающий через [menuOffsetInRoot]
 */
@Suppress("LongParameterList")
@Composable
internal fun <T> ContextMenuList(
    items: List<T>,
    keyOf: (T) -> Long,
    menuOffsetInRoot: Offset?,
    onLongPress: (T, Offset) -> Unit,
    rowContent: @Composable (T, Modifier) -> Unit,
    menu: @Composable () -> Unit
) {
    var listOrigin by remember { mutableStateOf(IntOffset.Zero) }
    Layout(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    listOrigin = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                },
        content = {
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
            // Якорь меню — ОТДЕЛЬНЫЙ узел (Box 0x0), размещаемый родительским
            // Layout: смещение через `Modifier.layout` даёт ДВОЙНОЙ учёт
            // смещения списка — popup-родитель (parentLayoutCoordinates)
            // включает placement, сделанный внутри layout-модификатора якоря,
            // т.е. offsetInRoot прибавляется к позиции узла ещё раз
            menuOffsetInRoot?.let { Box { menu() } }
        }
    ) { measurables, constraints ->
        // Guard: content обязан содержать ровно список + опциональный
        // якорь; третий ребёнок был бы молча отброшен
        check(measurables.size <= 2) { "ContextMenuList: лишний ребёнок content" }
        val listPlaceable = measurables[0].measure(constraints)
        val anchorPlaceable =
            measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val offsetX = (menuOffsetInRoot?.x ?: 0f) - listOrigin.x
        val offsetY = (menuOffsetInRoot?.y ?: 0f) - listOrigin.y
        layout(constraints.maxWidth, constraints.maxHeight) {
            listPlaceable.placeRelative(IntOffset.Zero)
            // place (не placeRelative): координата — абсолютная точка
            // нажатия, зеркалить в RTL её нельзя. Разность координат:
            // place отсчитывает от верхнего левого угла контейнера, а
            // menuOffsetInRoot — от корня окна
            anchorPlaceable?.place(offsetX.roundToInt(), offsetY.roundToInt())
        }
    }
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
