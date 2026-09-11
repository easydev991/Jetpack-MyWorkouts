## REMOVED Requirements

### Requirement: FAB — заглушка без навигации

**Reason**: FAB больше не заглушка — он открывает экран создания
упражнения `ExerciseCreateScreen` через колбэк `onAddExerciseClick`,
прокидываемый из `RootScreen`.

**Migration**: Заменяется требованием «FAB открывает ExerciseCreateScreen»
в разделе `## ADDED Requirements` этого же delta. Реализация: `ExercisesScreen`
принимает `onAddExerciseClick: () -> Unit` сверху (разделение
ответственности — `ExercisesScreen` не знает о `NavController`,
по аналогии с `MainScreen → EntryCreateScreen`).

## MODIFIED Requirements

### Requirement: Список упражнений реактивен

**Reason**: Существующее требование (`openspec/specs/exercises-screen/spec.md`,
строка L59) содержало фразу «Сортировка по названию — на стороне SQL
(`observeExercises`)», которая задавала `name` как единственный критерий
и не уточняла место критериев (кастомные vs стандартные, свежесть).
Новое ADDED-требование «Сортировка каталога — кастомные сверху, новые
первые» определяет трёхуровневый `ORDER BY is_custom DESC, created_at
DESC, name COLLATE NOCASE`. После sync старая формулировка заменяется
на ссылку на новое требование, чтобы избежать конкуренции двух
требований об одном и том же поведении.

**Migration**: Фраза «Сортировка по названию — на стороне SQL
(`observeExercises`)» в существующем требовании заменяется на
«Сортировка SHALL быть на стороне SQL (`observeExercises`); критерии и
порядок определены требованием «Сортировка каталога — кастомные сверху,
новые первые» в этом же capability». Остальной текст требования
(реактивность Flow, sealed-иерархия `ExercisesUiState.Loading`/`Success`
без `Error`, `SharingStarted.WhileSubscribed(5000)`) не меняется.
Scenarios существующего требования (`Loading до первой эмиссии`,
`Success со списком после первой эмиссии`, `Изменение Flow обновляет
список`) переезжают в MODIFIED-требование как есть (без изменений).

`ExercisesViewModel` SHALL подписываться на `repository.exercises("")`
(реактивный `Flow`, НЕ разовый `getAllExercises()`) и преобразовывать его в
`StateFlow<ExercisesUiState>` через `stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000), ExercisesUiState.Loading)`.
`ExercisesUiState` SHALL быть sealed-иерархией `Loading` /
`Success(exercises: List<Exercise>)` без `Error`-ветки (единственный источник
— офлайн Room-Flow, прецедент задокументирован в `MainUiState.kt`).
Сортировка SHALL быть на стороне SQL (`observeExercises`); критерии и
порядок определены требованием «Сортировка каталога — кастомные сверху,
новые первые» в этом же capability. Благодаря реактивности будущие
создание/удаление упражнений обновляют список без действий экрана.

#### Scenario: Loading до первой эмиссии

- **WHEN** `ExercisesViewModel` создан, но репозиторий ещё не эмитил
- **THEN** `uiState.value` is `ExercisesUiState.Loading`

#### Scenario: Success со списком после первой эмиссии

- **WHEN** репозиторий эмитит список упражнений
- **THEN** `uiState.value` is `ExercisesUiState.Success` с тем же списком

#### Scenario: Изменение Flow обновляет список

- **WHEN** в репозитории добавили/удалили упражнение (Flow эмитит новый
  список) при активной подписке
- **THEN** `uiState` эмитит обновлённый `Success` без перезапуска ViewModel

## ADDED Requirements

### Requirement: FAB открывает ExerciseCreateScreen

`ExercisesScreen` SHALL принимать `onAddExerciseClick: () -> Unit` в
качестве обязательного параметра (без значения по умолчанию) и
пробрасывать его в `ExercisesScreenState.onAddExerciseClick`. `FAB`
(компонент `FloatingActionButton`, без `IconButton`-обёртки —
паттерн существующего кода, `ExercisesScreen.kt` L101–108) SHALL
принимать `onClick = state.onAddExerciseClick`. Тап по FAB SHALL
инициировать навигацию на `Screen.ExerciseCreate.route` через
колбэк `onAddExerciseClick`, прокидываемый из `RootScreen` (как
`MainScreen → EntryCreateScreen`). Поле `ExercisesScreenState.onAddExerciseClick:
() -> Unit` SHALL быть обязательным (без дефолта `= {}`); `ExercisesScreen`
пробрасывает колбэк из своего параметра в `ExercisesScreenState.onAddExerciseClick`
при создании state. Экран создания упражнения определён в capability
`exercise-create-screen`.

#### Scenario: Тап по FAB открывает ExerciseCreateScreen

- **WHEN** пользователь нажимает FAB на `ExercisesScreen`
- **THEN** `onAddExerciseClick` вызывается ровно один раз
- **AND** активным становится `Screen.ExerciseCreate.route`

### Requirement: Сортировка каталога — кастомные сверху, новые первые

Каталог упражнений SHALL отображаться в порядке, определяемом SQL-запросом
репозитория: `ORDER BY is_custom DESC, created_at DESC, name COLLATE
NOCASE`. Кастомные упражнения (`isCustom = true`) SHALL находиться выше
стандартных (`isCustom = false`); внутри каждой группы — по убыванию
`createdAt` (новые сверху); при равных `createdAt` — по `name` без учёта
регистра (COLLATE NOCASE, по возрастанию) как финальный tie-break
(действует в обеих группах — кастомных и стандартных).

#### Scenario: Кастомное упражнение выше стандартного

- **WHEN** в каталоге есть упражнение «Жим» (`isCustom = true`) и
  стандартное «Приседания» (`isCustom = false`)
- **THEN** в списке «Жим» отображается выше «Приседаний»

#### Scenario: Несколько кастомных — новые сверху

- **WHEN** в каталоге есть два кастомных упражнения: «A» с `createdAt =
  T1` и «B» с `createdAt = T2 > T1`
- **THEN** в списке «B» отображается выше «A»

#### Scenario: Равные createdAt — сортировка по имени (COLLATE NOCASE)

- **WHEN** в каталоге есть два кастомных упражнения «Бег» и «Жим» с
  одинаковым `createdAt`
- **THEN** в списке «Бег» отображается выше «Жим» (по возрастанию
  `name` с `COLLATE NOCASE`)

#### Scenario: Стандартные сортированы по имени без учёта регистра (ASCII-выборка)

- **WHEN** в каталоге только стандартные упражнения на ASCII-выборке
  (паттерн — `ExerciseDaoTest.search_results_areSortedByNameCaseInsensitive`,
  комментарий L94–95: «SQLite NOCASE-коллация покрывает только ASCII»)
  с одинаковым `createdAt`: «alpha», «Beta», «CHARLIE», «delta»
- **THEN** порядок: `alpha`, `Beta`, `CHARLIE`, `delta`
