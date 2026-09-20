package com.myworkouts.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.myworkouts.R

/**
 * Маршруты экранов приложения.
 *
 * Содержит только `route` — без UI-метаданных (icon, title). Они живут
 * отдельно в [BottomTab], потому что у нижней навигации и у самих экранов
 * разные потребности: экрану из таба нужен только маршрут, а элементу
 * `NavigationBarItem` — ещё и иконка с заголовком. Слияние через
 * nullable-поля приводит к `?.let { ... }.orEmpty()` в цикле таба.
 */
sealed class Screen(
    val route: String
) {
    /** Главный экран — список тренировок (нижняя навигация). */
    object Main : Screen(route = "main")

    /** Экран «Упражнения» — каталог упражнений (нижняя навигация, условная видимость). */
    object Exercises : Screen(route = "exercises")

    /** Экран «Ещё» — точка входа в дополнительные настройки (нижняя навигация). */
    object More : Screen(route = "more")

    /** Экран «Оформление приложения» — выбор темы и динамических цветов (не в нижней навигации). */
    object Appearance : Screen(route = "appearance")

    /** Экран «Данные приложения» — экспорт, восстановление и удаление данных (не в нижней навигации). */
    object AppData : Screen(route = "app_data")

    /**
     * Экран «Настроить экран» — видимость опциональных полей формы записи
     * (не в нижней навигации). Единственная точка входа — IconButton в
     * `TopAppBar` экрана [EntryCreate]. Паттерн — [Appearance]: маршрут
     * регистрируется в `NavHost`, нижний бар скрыт контрактом
     * `currentRoute in tabs.map { it.route }` (entry-settings-screen,
     * non-tab-screen-navigation).
     */
    object EntrySettings : Screen(route = "entry_settings")

    /**
     * Экран «Архив упражнений» — список архивных упражнений с
     * возможностью восстановления (не в нижней навигации). Доступен через
     * `TopAppBar`-кнопку архива на [Exercises]. Паттерн — [Appearance]:
     * маршрут регистрируется в `NavHost`, нижний бар скрыт контрактом
     * `currentRoute in tabs.map { it.route }` (exercises-archive,
     * non-tab-screen-navigation).
     */
    object ArchivedExercises : Screen(route = "archived_exercises")

    /**
     * Экран записи о выполнении (не в нижней навигации): создание и
     * редактирование на одном composable. Опциональный query-аргумент
     * `entryId` выбирает режим: отсутствует/[NO_ENTRY_ID] — создание (FAB),
     * иначе — редактирование записи с этим id (entry-edit-mode, design
     * Decision 1). [arguments] передаётся в каждое место регистрации
     * `composable(route, arguments)` — без navArgument query-параметр
     * считается обязательным и `navigate("entry_create")` не сматчится.
     */
    object EntryCreate : Screen(route = "entry_create?entryId={entryId}") {
        /** Ключ query-аргумента маршрута. */
        const val ARG_ENTRY_ID: String = "entryId"

        /** Id «запись не передана» — режим создания. */
        const val NO_ENTRY_ID: Long = -1L

        /** Аргументы маршрута для регистрации в `composable(...)`. */
        val arguments =
            listOf(
                navArgument(ARG_ENTRY_ID) {
                    type = NavType.LongType
                    defaultValue = NO_ENTRY_ID
                }
            )

        /**
         * Route для навигации: всегда `entry_create?entryId={id}` —
         * `defaultValue` в `navArgument` подставит [NO_ENTRY_ID] для
         * create-режима, явный id — режим редактирования.
         */
        fun createRoute(entryId: Long = NO_ENTRY_ID): String = "entry_create?$ARG_ENTRY_ID=$entryId"
    }

    /**
     * Экран создания упражнения (не в нижней навигации): создание и
     * редактирование на одном composable (edit-delete-exercise, design
     * Decision 1). Опциональный query-аргумент `exerciseId` выбирает
     * режим: отсутствует/[NO_EXERCISE_ID] — создание (FAB), иначе —
     * редактирование упражнения с этим id (edit-режим из long-press
     * меню каталога). [arguments] передаётся в каждое место регистрации
     * `composable(route, arguments)` — без navArgument query-параметр
     * считается обязательным и `navigate("exercise_create")` не
     * сматчится.
     */
    object ExerciseCreate : Screen(route = "exercise_create?exerciseId={exerciseId}") {
        /** Ключ query-аргумента маршрута. */
        const val ARG_EXERCISE_ID: String = "exerciseId"

        /** Id «упражнение не передано» — режим создания. */
        const val NO_EXERCISE_ID: Long = -1L

        /** Аргументы маршрута для регистрации в `composable(...)`. */
        val arguments =
            listOf(
                navArgument(ARG_EXERCISE_ID) {
                    type = NavType.LongType
                    defaultValue = NO_EXERCISE_ID
                }
            )

        /**
         * Route для навигации: всегда `exercise_create?exerciseId={id}` —
         * `defaultValue` в `navArgument` подставит [NO_EXERCISE_ID] для
         * create-режима, явный id — режим редактирования.
         */
        fun createRoute(exerciseId: Long = NO_EXERCISE_ID): String = "exercise_create?$ARG_EXERCISE_ID=$exerciseId"
    }
}

/**
 * Описание нижней вкладки для `NavigationBarItem`.
 *
 * @property route маршрут для навигации
 * @property icon иконка для `NavigationBarItem`
 * @property titleResId строковый ресурс заголовка вкладки
 *
 * @implNote Screen хранит только route; UI-метаданные вынесены сюда.
 * Слить через nullable-поля в Screen нельзя — сломает NavigationBarItem
 * (?.let/orEmpty в цикле). При добавлении ещё одного не-tab экрана —
 * расширять BottomTabs/BottomTab не надо.
 */
data class BottomTab(
    val route: String,
    val icon: ImageVector,
    val titleResId: Int
)

/**
 * Список нижних вкладок в порядке отрисовки слева направо. Полный
 * статичный список — единый источник правды по составу и порядку;
 * рантайм-видимость отдельных вкладок здесь НЕ фильтруется
 * (exercises-tab, design Decision 7) — см. [visibleTabs].
 */
val BottomTabs: List<BottomTab> =
    listOf(
        BottomTab(
            route = Screen.Main.route,
            icon = Icons.Filled.Home,
            titleResId = R.string.workouts_title
        ),
        BottomTab(
            route = Screen.Exercises.route,
            icon = Icons.Filled.FitnessCenter,
            titleResId = R.string.exercises_title
        ),
        BottomTab(
            route = Screen.More.route,
            icon = Icons.Filled.Menu,
            titleResId = R.string.more_title
        )
    )

/**
 * Видимые нижние вкладки при текущем значении настройки
 * `show_exercises_tab` (exercises-tab, design Decision 7): pure-функция
 * вместо фильтра на use site — JVM-тест покрывает оба состояния бара
 * без androidTest с реальным DataStore.
 *
 * @param showExercisesTab Показывать вкладку «Упражнения»
 * @return [BottomTabs] без Exercises при выключенной настройке
 */
fun visibleTabs(showExercisesTab: Boolean): List<BottomTab> =
    BottomTabs.filterNot { !showExercisesTab && it.route == Screen.Exercises.route }
