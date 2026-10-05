package com.myworkouts.data.db

/**
 * Стандартная категория для prepopulate таблицы `categories`.
 *
 * Аналог [StandardExercise]: неизменяемая тройка для предзаполнения и
 * пересева каталога; имя-ключ — не локализуемая строка (значения в БД и
 * бэкапах), отображение резолвится через
 * [com.myworkouts.ui.util.standardCategoryLabelResId].
 *
 * @property name Имя-ключ категории (`PUSH`/`PULL`/`LEGS`/`CORE`/`CARDIO`)
 * @property colorArgb Цвет ARGB — значения унаследованы от статического
 *   `when` в `ui/theme/Color.kt` на момент change (custom-categories,
 *   design Decision 2); после удаления `when` — единственный источник
 */
data class StandardCategory(
    val name: String,
    val colorArgb: Int
)

/**
 * Пять стандартных категорий — единый источник для prepopulate
 * (callback первого создания БД), пересева в `deleteAllData`,
 * `Migration(3, 4)` и asserts `MigrationTest` (custom-categories,
 * design Decision 2).
 *
 * Полный состав — контракт, проверяемый `StandardCategoriesTest`:
 * если имя добавлено/удалено, синхронизируй миграцию, prepopulate
 * и [com.myworkouts.ui.util.standardCategoryLabelResId].
 */
val standardCategories: List<StandardCategory> =
    listOf(
        StandardCategory(name = "PUSH", colorArgb = 0xFFFF5252.toInt()),
        StandardCategory(name = "PULL", colorArgb = 0xFF448AFF.toInt()),
        StandardCategory(name = "LEGS", colorArgb = 0xFF69F0AE.toInt()),
        StandardCategory(name = "CORE", colorArgb = 0xFFE040FB.toInt()),
        StandardCategory(name = "CARDIO", colorArgb = 0xFFFFD740.toInt())
    )
