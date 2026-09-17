package com.myworkouts.ui.screens.exercises

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import com.myworkouts.domain.model.Exercise

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
 * Подключает `detectTapGestures(onLongPress)` для строки упражнения.
 * Long-press активен для всех упражнений каталога: для кастомных —
 * меню из трёх пунктов (Изменить/Архивировать/Удалить), для стандартных —
 * единственный пункт «Архивировать». `ExerciseContextMenu` выбирает пункты
 * по `isCustom` сам (exercises-archive Decision 4). Общий хелпер
 * `ExercisesScreen` и `ArchivedExercisesScreen`; вынесен из
 * `ExercisesScreen.kt` ради лимита detekt `TooManyFunctions`.
 *
 * `positionInRootProvider` — лямбда, возвращающая актуальную позицию
 * строки в корне: closure ссылается на `mutableStateOf<Offset>`, чтобы
 * избежать пересоздания `pointerInput` при каждом изменении позиции
 * (иначе long-press отваливался бы на скролле).
 */
internal fun Modifier.applyLongPress(
    item: Exercise,
    positionInRootProvider: () -> Offset,
    onItemLongPress: (ExerciseContextMenuState) -> Unit
): Modifier =
    this.pointerInput(item.id) {
        detectTapGestures(
            onLongPress = { localOffset ->
                onItemLongPress(
                    ExerciseContextMenuState(item, positionInRootProvider() + localOffset)
                )
            }
        )
    }
