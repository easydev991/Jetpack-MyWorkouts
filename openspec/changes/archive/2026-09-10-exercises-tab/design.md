# Design — exercises-tab

## Context

Каталог упражнений уже есть в data-слое: `WorkoutDataRepository.exercises(query)`
возвращает реактивный `Flow<List<Exercise>>` (Room `observeExercises`,
сортировка по имени на стороне SQL). Экран «Тренировки» (`MainScreen`) —
образец структуры: ViewModel + sealed UiState + internal Content с
Test/Preview-state. Мотивация — proposal.md (Why).

## Goals / Non-Goals

Goals: вкладка только-для-чтения с реактивным списком; условная видимость
вкладки через тоггл на «Ещё» (настройка в DataStore, default `false`);
нулевые изменения в data-слое каталога и домене; не мешать будущим
созданию/редактированию/архивации.

Non-Goals: экран создания (FAB — заглушка), редактирование, архивация,
поиск/фильтрация/группировка — см. proposal (вне скоупа).

## Decisions

### Decision 1: источник данных — `exercises("")`, не `getAllExercises()`

`getAllExercises()` — разовый снимок для экспорта/импорта (его KDoc это
закрепляет). Экрану нужен реактивный `Flow`: когда появятся создание и
удаление, список обновится сам, без ручных рефрешей и нового кода в
ViewModel. Пустая строка-запрос возвращает весь каталог.

Альтернатива — `getAllExercises()` + ручной рефреш — отвергнута: добавила бы
код сейчас и переделку при первой же фиче записи.

### Decision 2: sealed `ExercisesUiState` без Error-ветки

`Loading` / `Success(exercises)` — копия прецедента `MainUiState`
(задокументирован в `MainUiState.kt`): единственный источник — офлайн
Room-Flow, который в штатной работе не ошибается. Пустое состояние — derived
(`exercises.isEmpty()` на use site), без отдельного подкласса. Общая
конвенция AGENTS.md (sealed Loading/Success/Error) здесь сознательно
сужена — так же, как на главном экране.

### Decision 3: структура экрана — обёртка + `ExercisesScreenContent`

Как у `MainScreen`: `ExercisesScreen(viewModel, modifier)` собирает
`collectAsState()`, пакует в `ExercisesScreenState` (uiState +
`onAddExerciseClick`) и делегирует в `internal ExercisesScreenContent`.
UI-тесты и превью работают без ViewModel и fake-репозитория.

### Decision 4: FAB — заглушка с пустым колбэком

`onAddExerciseClick` в `ExercisesScreenState` имеет дефолт `= {}`; экран
не передаёт лямбду. Никакой навигации, состояния и логирования: `Log.d`
с тегом-`private const` убраны — future change всё равно заменит колбэк
целиком на `navController.navigate(...)` по образцу `onAddEntryClick` у
Main, сигнатура `ExercisesScreenState` уже готова (Decision 3). Внешняя
`ExercisesScreen` при этом принимает только `viewModel` (канон
`tab-navigation/spec.md`) — будущий change добавит `onAddExerciseClick` и
в её сигнатуру с проводкой из `RootScreen` (у него `navController`,
Decision 8), т.е. затронет обе сигнатуры, а не только внутреннюю.

### Decision 5: лейбл `measuredBy` — private `@Composable` с тотальным `when`

`ExerciseMeasuredBy.REPS` → `exercise_measured_by_reps`, `TIME` →
`exercise_measured_by_time`. `when` без `else`: при добавлении значения enum
компилятор заставит дописать ветку. Маппинг живёт в файле экрана — больше
некому переиспользовать (главный экран работает с записями, не каталогом).

### Decision 6: настройка видимости — `show_exercises_tab` в `AppSettingsDataStore`, default `false`

Хранение настройки — существующий `AppSettingsDataStore` (`appearance_prefs`):
его KDoc прямо предписывает «новые ключи — точечно по фичам». Новый ключ
`show_exercises_tab: Flow<Boolean>` (default `false`) + suspend-setter — по
образцу `useDynamicColors`. Default `false` — мотивация в proposal §Why:
каталог нужен не всем, кому нужен — включит тоггл; навигация у большинства
остаётся двухвкладочной.

### Decision 7: `BottomTabs` статичен, видимые вкладки — pure-функция `visibleTabs`; destination регистрируется всегда

Вкладка попадает в `BottomTabs` между Main и More. `BottomTabs` остаётся
полным статичным списком из трёх вкладок (порядок и состав — единый источник
правды). Видимые вкладки вычисляет pure-функция
`visibleTabs(showExercisesTab): List<BottomTab>` рядом с `BottomTabs` в
`Screen.kt` (`BottomTabs.filterNot { !showExercisesTab && it.route ==
Screen.Exercises.route }`): извлечение в именованную функцию даёт JVM-тест
на оба состояния бара (2/3 вкладки) без androidTest с реальным DataStore;
`shouldShowBottomBar` и `AppBottomBar` в `RootScreen` считаются по её
результату (на новом табе бар появляется сам — без отдельной логики
видимости, лишь фильтр списка). `composable(Screen.Exercises.route)`
регистрируется в `NavHost` ВСЕГДА: скрытие настройкой убирает только
`NavigationBarItem`, поэтому включение таба мгновенное и без перестройки
графа. Имя `visibleTabs` — внутреннее решение реализации, не часть
контракта: спека `tab-navigation` описывает только поведение
(«`BottomTabs` filtered by the `show_exercises_tab` setting»).

### Decision 8: MoreScreen — параметры `showExercisesTab` + `onShowExercisesTabChange`, без ViewModel

Состояние настройки и запись живут в `RootScreen`: он уже получает
`dataStore`, собирает `showExercisesTab` через `collectAsState`, пишет через
`rememberCoroutineScope().launch { dataStore.setShowExercisesTab(it) }`.
MoreScreen остаётся без ViewModel (его текущий принцип) и получает только
`Boolean` + колбэк — MoreScreenTest не нужен fake-DataStore. Альтернатива
`MoreViewModel` отвергнута: целый класс + factory ради одной кнопки.

## Risks / Trade-offs

- [FAB-заглушка разочаровывает пользователя] → осознанно: экран создания —
  отдельный future change; тап без видимой реакции принят как трейд-офф.
- [Каталог большой, без поиска] → сортировка по имени уже есть, поиск —
  future change (query-параметр `exercises(query)` уже поддерживает его на
  стороне данных).
- [MoreScreenTest завязан на количество вкладок] → проверить и актуализировать
  тест в составе change.
- [Скрытие вкладки, пока exercises — текущий destination] → через тоггл на
  «Ещё» недостижимо: тоггл на другом экране, а back из «Ещё» возвращается на
  Main (start destination), не на exercises. Если будущий change сделает
  переключение возможным из другого места — при выключении настройки нужно
  уводить навигацию с exercises-маршрута (напр., `popBackStack`), иначе
  нижний бар исчезнет и пользователь застрянет. Зафиксировано здесь как
  контракт для будущих изменений.

## Migration Plan

Чистое добавление: новый маршрут, вкладка, экран. Откат — удалить вкладку и
destination. Данные и схема БД не меняются.

Чек-лист архивации:
- поправить Purpose в `openspec/specs/tab-navigation/spec.md` — «три
  вкладки» уже неверно сегодня (2), после архивации станет «2 или 3»;
- имя сценария «BottomTabs contains exactly Main and More» в
  `non-tab-screen-navigation` при переносе останется историческим:
  openspec-валидатор запрещает переименование сценария внутри MODIFIED
  (переименование = дроп, архиватор отказывается терять сценарии) — тело
  сценария перечисляет актуальные маршруты, имя не трогать.

## Open Questions

Нет.
