## Context

Слой данных готов и протестирован: `RoomWorkoutDataRepository`
(`exercises(query): Flow`, `addEntry(entry): Result<Long>` с
`EntryValidator`), `Exercise.measuredBy` (`REPS`/`TIME`), FAB на
`MainScreen` — плейсхолдер (`ponytail`-комментарий в `MainScreen.kt:32`).
Навигационный паттерн для не-tab экранов отработан на «Оформлении»:
`Screen.Appearance` + `shouldShowBottomBar` в `RootScreen` + колбэк
`onBackClick`. KDoc `RoomWorkoutDataRepository` (Decision 5 прошлого
change) прямо завещает появление интерфейса репозитория вместе с первым
ViewModel-тестом, которому нужен fake, — этот момент наступил.

Проверенные в кодовой базе образцы: `AppearanceViewModel` (companion
`factory`, `StateFlow` + `stateIn(WhileSubscribed(5000))`),
`AppearanceScreen`/`AppearanceScreenContent` (plain-state для UI-тестов),
`MoreScreenTest` (навигация через NavHost-обёртку), M3 `DatePickerDialog`
с UTC-конвертацией дат через internal-функции с JVM-тестами.

## Goals / Non-Goals

Goals:

- Экран создания записи: логика в ViewModel, UI — «глупый» рендер state.
- Вся логика (валидация, санитайзинг ввода, сборка `ExerciseEntry`,
  конвертация даты/времени, форматирование длительности) — чистые
  internal-функции/методы, покрытые JVM-тестами (TDD: red → green).
- Введение интерфейса репозитория для fake-зависимости в тестах.

Non-Goals:

- Режим редактирования (маршрут `entry_edit/{id}` на этот же экран —
  в будущем change; сейчас экран и ViewModel именуются как create).
- Поле веса (`ExerciseEntry.weightKg` остаётся `null` — спека его не
  требует).
- Связывание записей в «тренировку» (`workoutId = null`).
- Сохранение состояния при process death сверх стандартного
  `rememberSaveable` для текстовых полей.

## Decisions

### Decision 1: интерфейс `WorkoutDataRepository`

Извлечь интерфейс из `RoomWorkoutDataRepository` без изменения API
(запланировано Decision 5 change `workout-data-layer-measured-by`).
ViewModel принимает интерфейс; JVM-тесты — fake на `MutableStateFlow`
(`flowOf`/`MutableStateFlow(listOf(...))` для каталога, заглушки
`suspend`-операций). Альтернатива — MockK-моки репозитория — отвергнута:
проектный стиль (навык `kotlin-testing`) предпочитает fake на Flow.

### Decision 2: `EntryCreateViewModel` — состояние и тесты

- Конструктор: `(repository: WorkoutDataRepository, nowProvider: () -> Long)`
  — провайдер текущего момента внедрён для JVM-тестов; используется в
  двух местах: начальное состояние пикеров даты/времени (Decision 5) и
  `createdAt`/`modifiedAt` при `save`. `factory(repository)` в
  companion — ручной DI.
- `EntryCreateUiState` (immutable data class, `ui/state/`): список
  упражнений, выбранное упражнение, тексты `sets`/`reps`, выбранная
  длительность (`Int?`, секунды), `LocalDate` + час/минута, текст
  комментария, вычисляемое `canSave`, флаг/ресурс ошибки.
- Поле «Подходы» префиллено значением `1` при открытии: модель данных
  требует `sets >= 1` (`ExerciseEntry.sets: Int`, `EntryValidator`
  возвращает `INVALID_SETS` при `sets < 1`) — «опциональность» для
  пользователя означает «не нужно вводить руками», а не «можно
  сохранить пустым».
- `canSave = выбрано упражнение && подходы >= 1 && (REPS → повторы
  заполнены || TIME → длительность выбрана)` — нормировано спекой.
  Кнопка сохранения `enabled = canSave` (ревизия по итогам ручного
  тестирования: первоначальный вариант «всегда активна» заменён);
  при `canSave = false` кнопка неактивна И показывается подсказка.
  Guard в `save` остаётся (защита в глубину — кнопка не единственный
  путь вызова).
- `save(onSaved: () -> Unit)`: собирает `ExerciseEntry` (date =
  `LocalDate.atTime(h, m)` в epoch millis, `createdAt`/`modifiedAt` =
  `nowProvider`), вызывает `addEntry`; успех → `onSaved()` (экран
  закрывает колбэком навигации), провал → сообщение об ошибке, экран
  открыт.
- Санитайзинг ввода: `onSetsChange`/`onRepsChange` отбрасывают
  нецифровые символы и ведущие нули — тестируем на JVM.
- Смена упражнения: типозависимое поле и его значение сбрасываются
  (spec-сценарий «Смена упражнения переключает поле»).

### Decision 3: маршрут и подключение

- `Screen.EntryCreate(route = "entry_create")` — не-tab маршрут, панель
  скрывается существующим `shouldShowBottomBar`. Для будущего
  редактирования — отдельный маршрут `entry_edit/{entryId}` на тот же
  composable (маршруты create/edit раздельные, как принято для
  save-state табов).
- `MainScreen(onAddEntryClick: () -> Unit)` — колбэк вместо navController
  (MainScreen не знает о навигации); FAB вызывает его. RootScreen
  подключает: `MainScreen(onAddEntryClick = { navController.navigate(...) })`.
- DI-цепочка: `MainActivity` создаёт `WorkoutDatabase` →
  `RoomWorkoutDataRepository` один раз (по образцу `dataStore`) и
  передаёт в `RootScreen`; `RootScreen` — `viewModel(factory = ...)` для
  экрана записи.

### Decision 4: пикер длительности — dropdown с шагом 5 с

`ExposedDropdownMenuBox` со значениями 5..3600 с шагом 5 (720 позиций),
отображение через `formatDuration(sec)`: «45 сек», «2 мин 30 сек»,
«1 час» (строки/plurals ru/en). Альтернативы отвергнуты: android
`NumberPicker` — View-виджет вне M3; сторонние wheel-пикеры — новая
зависимость; слайдер — неточный ввод. Dropdown переиспользует паттерн
выбора упражнения — один визуальный язык.

### Decision 5: дата и время — системные пикеры M3

- Открытие пикеров (ревизия): иконка-кнопка в `trailingIcon` поля
  (календарь/часы) с `contentDescription` — клик по самому
  readOnly-полю не работает, внутренний `BasicTextField` поглощает
  тапы. Образец — JetpackDays `DateSection`/`ReminderTimeField`.
- Дата: `DatePickerDialog` + `rememberDatePickerState(
  initialSelectedDateMillis)`; конвертация millis ↔ `LocalDate` через
  `ZoneOffset.UTC` в internal-функциях (JVM-тест), `showModeToggle =
  false`.
- Время: `AlertDialog` + M3 `TimePicker` (`rememberTimePickerState`).
- Сборка `date`: `LocalDate.atTime(hour, minute).atZone(
  ZoneId.systemDefault()).toInstant().toEpochMilli()` — internal-функция
  с JVM-тестом (зона — системная, фиксированная в тесте).
- Дефолт при открытии: `nowProvider` → текущие дата/время
  (spec-сценарий).

### Decision 6: структура UI-кода и тесты

- `ui/screens/entry/EntryCreateScreen.kt`: `EntryCreateScreen(viewModel,
  onBackClick)` + `internal EntryCreateScreenContent(state: ...,
  onExerciseChange/onSetsChange/... — колбэки)` — UI-тесты через plain
  state без ViewModel (образец — `AppearanceScreenTest`).
- Мелкие компоненты (поле-цифры, dropdown'ы, диалоги) живут в том же
  файле пакета `entry`; выделение в файлы — при разрастании.
- androidTest: `EntryCreateScreenTest` (состав полей для REPS/TIME,
  подсказки обязательности, callbacks) через
  `EntryCreateScreenContent(state, колбэки)` + тест FAB-навигации
  `Main → entry_create` через NavHost-обёртку (образец — MoreScreenTest).
  ViewModel-инстансы в androidTest не создаются: логика VM покрыта
  JVM-тестами (2.1–2.4), навык `kotlin-testing` запрещает новые
  интеграционные тесты ViewModel.

### Decision 7: клавиатура и метки обязательных полей (ревизия)

- Клавиатура не должна перекрывать поле комментария: форма
  `Column` с `.imePadding() + .verticalScroll(...)` — колонка
  получает нижний паддинг высотой с IME, скролл поднимает
  сфокусированное поле в видимую зону. `imeNestedScroll` отвергнут
  ручной проверкой: он двигает клавиатуру при скролле, а не контент
  при клавиатуре. При `enableEdgeToEdge()` манифестный `adjustResize`
  сам по себе insets не применяет. Автотест IME-insets не покрывает —
  ручная проверка.
- Метки обязательных типозависимых полей помечаются скобками:
  «Повторы (обязательно)» / «Длительность (обязательно)»
  (en: «Reps (mandatory)» / «Duration (mandatory)»). «Подходы» и
  «Упражнение» не помечаются: подходы префиллены «1», а необходимость
  выбора упражнения видна из подсказки-плейсхолдера.

## Risks / Trade-offs

- [UTC в DatePicker vs системная зона — классическая ловушка смещения
  даты] → все конвертации — только через internal-функции с JVM-тестами
  на фиксированных значениях; UI напрямую с millis не работает.
- [Dropdown на 720 позиций — утомительный скролл до средних значений
  (20 мин ≈ 240-я позиция)] → список упорядочен и открывается на
  выбранном значении; если UX-проверка покажет неудобство — заменить на
  диалог с двумя колонками (минуты/секунды), `formatDuration` и шаг
  5 с при этом сохраняются.
- [Дефолт «сейчас» фиксируется при открытии экрана, а не в момент
  сохранения] → осознанно: пользователь видит, что сохранит; поле
  редактируемо. `createdAt`/`modifiedAt` берутся в момент сохранения.
- [Process death: состояние VM теряется] → `rememberSaveable` для
  текстовых полей; полный Saver состояния — вместе с режимом
  редактирования (Non-Goal).

## Migration Plan

1. Извлечение интерфейса — чистый рефакторинг, существующие тесты
   репозитория остаются зелёными (тест-команды прежние).
2. Новые файлы + правки `RootScreen`/`MainScreen`/`MainActivity` —
   обратной миграции не требуется (UI-only change, слой данных не
   трогаем).
3. Откат = revert коммита; изменений схемы БД нет.

## Open Questions

Нет — обязательность полей подтверждена заказчиком (упражнение +
повторы либо длительность по `measuredBy`).
