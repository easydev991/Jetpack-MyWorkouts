## Why

Каталог упражнений уже существует в данных (стандартные + пользовательские,
`WorkoutDataRepository.exercises()`), но у пользователя нет экрана, где его
видно: упражнения доступны только косвенно — через выбор при создании записи
тренировки. Нужна точка входа «Упражнения» в нижней навигации, с которой
потом вырастут создание/редактирование/архивация.

Каталог нужен не всем: большинству хватает выбора упражнения при записи
тренировки, и третий пункт навигации им только мешает. Поэтому вкладка
включается тогглом «Показывать вкладку "Упражнения"» на экране «Ещё» и по
умолчанию выключена — те, кому экран пригодится (просмотр каталога, будущее
редактирование), включат её сами.

## What Changes

- Новая вкладка «Упражнения» в нижней навигации, между «Тренировки» и «Ещё»:
  `object Exercises : Screen(route = "exercises")` + `BottomTab` с
  `Icons.Filled.FitnessCenter` и `R.string.exercises_title`. Вкладка
  условная: видна только при включённой настройке.
- Настройка `show_exercises_tab` (boolean, default `false`) в
  `AppSettingsDataStore` + кнопка видимости вкладки «Упражнения» на экране
  «Ещё» (надпись «Показать вкладку…» / «Скрыть вкладку…» по состоянию):
  включено — вкладка появляется в нижней навигации, выключено — скрыта.
  Настройка сохраняется в DataStore и переживает перезапуск.
- Новый экран `ExercisesScreen` (`ui/screens/exercises/`): Scaffold с
  TopAppBar «Упражнения», `LazyColumn` каталога (название + лейбл типа
  измерения + опциональный комментарий), пустое состояние, FAB-заглушка
  (экран создания — будущий change).
- Новые `ExercisesUiState` (sealed `Loading` / `Success`) и
  `ExercisesViewModel` (`repository.exercises("")` → `stateIn`).
- Новые строковые ресурсы ru/en: `exercises_title`, `exercises_add_exercise`,
  `exercise_measured_by_reps`, `exercise_measured_by_time`,
  `exercises_list_empty`, `more_exercises_tab_show`, `more_exercises_tab_hide`.
- Тесты: unit `ExercisesViewModelTest` и `VisibleTabsTest` (JVM, чистая
  функция видимых вкладок), androidTest `ExercisesScreenTest`
  (+ актуализация `MoreScreenTest`, завязанного на `BottomTabs` и кнопку
  видимости вкладки).

Только чтение каталога. Создание/редактирование/архивация, архив, поиск,
фильтрация по категории, группировка — вне скоупа.

## Capabilities

### New Capabilities

- `exercises-screen`: экран «Упражнения» — реактивный список каталога
  (стандартные + пользовательские), строка с названием/лейблом типа
  измерения/комментарием, пустое состояние, FAB-заглушка, UiState/ViewModel.

### Modified Capabilities

- `non-tab-screen-navigation`: `BottomTabs` расширяется вкладкой Exercises
  между Main и More (было ровно 2 вкладки — станет 3). `BottomTabs`
  остаётся полным статичным списком; видимость отдельной вкладки в рантайме
  — не его забота.
- `tab-navigation`: `NavHost` в `RootScreen` регистрирует destination
  `Screen.Exercises.route` с `ExercisesScreen` (destination регистрируется
  всегда — независимо от настройки); видимость вкладки в `NavigationBar`
  и видимость нижнего бара теперь вычисляются из списка видимых вкладок
  (`BottomTabs` без Exercises при выключенной настройке).
- `more-screen`: на экране «Ещё» появляется кнопка видимости вкладки
  «Упражнения» (надпись «Показать вкладку…» / «Скрыть вкладку…» по
  состоянию) — показывает/скрывает вкладку «Упражнения», состояние
  хранится в `AppSettingsDataStore`.

## Impact

- `navigation/Screen.kt` — `Screen.Exercises`, `BottomTab` в `BottomTabs`.
- `ui/screens/root/RootScreen.kt` — composable-destination для exercises,
  подписка на настройку, фильтр видимых вкладок, передача состояния кнопки
  в `MoreScreen`.
- `ui/screens/more/MoreScreen.kt` — параметры `showExercisesTab` /
  `onShowExercisesTabChange`, кнопка видимости вкладки.
- `data/preferences/AppSettingsDataStore.kt` — ключ `show_exercises_tab`
  (Flow c default `false` + setter).
- Новые файлы: `ui/screens/exercises/ExercisesScreen.kt`,
  `ExercisesScreenPreview.kt`, `ui/state/ExercisesUiState.kt`,
  `ui/viewmodel/ExercisesViewModel.kt`.
- `res/values/strings.xml`, `res/values-en/strings.xml` — 6 новых строк.
- Тесты: `app/src/test/.../ExercisesViewModelTest.kt`,
  `app/src/test/.../navigation/VisibleTabsTest.kt` (JVM, JUnit 5),
  `app/src/androidTest/.../ExercisesScreenTest.kt`, проверка/обновление
  `MoreScreenTest.kt` (количество вкладок + новый тоггл).
- Репозиторий и домен НЕ расширяются — только потребляется существующий
  `WorkoutDataRepository.exercises(query)`.
