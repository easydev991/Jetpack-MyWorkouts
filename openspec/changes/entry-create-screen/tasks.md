## 1. Интерфейс репозитория (рефакторинг, TDD не нужен — поведение не меняется)

- [x] 1.1 Извлечь интерфейс `WorkoutDataRepository` из
  `RoomWorkoutDataRepository` (все публичные методы без изменения
  сигнатур; `RoomWorkoutDataRepository : WorkoutDataRepository`) и
  проверить, что `make test` и
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myworkouts.data.repository.RoomWorkoutDataRepositoryTest`
  остаются зелёными без правок тестов

## 2. Red-тесты ViewModel и чистых функций (JVM)

- [x] 2.1 Создать `app/src/test/.../ui/viewmodel/EntryCreateViewModelTest.kt`
  с fake `WorkoutDataRepository` на `MutableStateFlow` (каталог: REPS- и
  TIME-упражнение) и `nowProvider` с фиксированным моментом; тесты
  начального состояния: каталог загружен, дата/время = фиксированный
  «сейчас», поле подходов содержит «1» (префилл), `canSave = false`. Запустить
  `./gradlew :app:testDebugUnitTest --tests "com.myworkouts.ui.viewmodel.EntryCreateViewModelTest"`
  — тест падает (класс не реализован)
- [x] 2.2 Добавить red-тесты выбора упражнения и `canSave`: REPS без
  повторов → `canSave = false`, с повторами → `true`; TIME без
  длительности → `false`, с длительностью → `true`; без упражнения →
  всегда `false`; смена REPS→TIME сбрасывает повторы. Прогнать команду
  из 2.1 — новые тесты падают
- [x] 2.3 Добавить red-тесты санитайзинга: нецифровые символы и ведущие
  нули отбрасываются в `sets`/`reps`; очищенное или нулевое поле подходов
  → `canSave = false`. Прогнать команду из 2.1 — падают
- [x] 2.4 Добавить red-тесты `save`: успех — `addEntry` вызван с
  `ExerciseEntry` (exerciseId, reps/durationSec по measuredBy, sets = 1
  из префилла, weightKg = null, date = собранный из LocalDate+час/минута
  millis) и `onSaved` вызван; провал репозитория — `onSaved` не
  вызван, состояние содержит ошибку. Прогнать команду из 2.1 — падают
- [x] 2.5 Создать `app/src/test/.../ui/screens/entry/EntryCreateFormattersTest.kt`
  (JVM): `formatDuration` (5 сек, 45 сек, 60→«1 мин», 150→«2 мин 30 сек»,
  3600→«1 час») и конвертации дата/время (LocalDate↔UTC millis для
  пикера; LocalDate+час/минута→epoch millis в фиксированной зоне).
  Запустить
  `./gradlew :app:testDebugUnitTest --tests "com.myworkouts.ui.screens.entry.EntryCreateFormattersTest"`
  — падает

## 3. Реализация ViewModel (green)

- [x] 3.1 Создать `ui/state/EntryCreateUiState.kt` (immutable data
  class: каталог, выбранное упражнение, тексты sets/reps, длительность
  `Int?`, LocalDate + час/минута, комментарий, `canSave`, ошибка) и
  `ui/viewmodel/EntryCreateViewModel.kt` (конструктор с
  `WorkoutDataRepository` + `nowProvider`, companion `factory`,
  StateFlow-состояние, обработчики ввода с санитайзингом, `save(
  onSaved)`) по Decision 2 из design.md; прогнать команды из 2.1 и 2.5 —
  все тесты зелёные

## 4. UI: экран, маршрут, строки (Compose UI-тесты — по факту верстки)

- [x] 4.1 Добавить строковые ресурсы ru/en: заголовок экрана, метки
  (упражнение, подходы, повторы, длительность, дата, время, комментарий),
  подсказки обязательности, «Сохранить»/«ОК»/«Отмена», plurals для
  минут/секунд форматирования длительности; проверить сборку
  `./gradlew :app:compileDebugKotlin`
- [x] 4.2 Создать `ui/screens/entry/EntryCreateScreen.kt`:
  `EntryCreateScreen(viewModel, onBackClick)` + internal
  `EntryCreateScreenContent(state)` — выпадающий список упражнений
  (ExposedDropdownMenuBox), числовые поля с санитайзингом из VM,
  dropdown длительности 5..3600 шаг 5 с `formatDuration`, диалоги
  DatePickerDialog (UTC-конвертации из 2.5) и TimePicker, поле
  комментария, кнопка сохранения с подсказками при `canSave = false`,
  TopAppBar с back-кнопкой (образец — AppearanceScreen). Обязательные
  `@Preview` для каждого composable-компонента из этой задачи (включая
  `EntryCreateScreenContent`): пара аннотаций — светлая и тёмная
  (`uiMode = Configuration.UI_MODE_NIGHT_YES`), обе
  `showBackground = true, locale = "ru"`, обёртка в `MyWorkoutsTheme`;
  состояния в Column: REPS без повторов (`canSave = false`, видны
  подсказки), REPS заполненное (`canSave = true`), TIME с длительностью;
  имя — `ИмяКомпонентаPreview`
- [x] 4.3 Ввести `Screen.EntryCreate(route = "entry_create")`, подключить
  composable в `RootScreen` (viewModel через `factory(repository)`,
  `onBackClick = popBackStack`), прокинуть `onAddEntryClick` в
  `MainScreen` (FAB), создать репозиторий в `MainActivity` и передать в
  `RootScreen`; проверить вручную: FAB открывает экран, панели нет,
  сохранение возвращает назад
- [x] 4.4 Написать `app/src/androidTest/.../ui/screens/entry/EntryCreateScreenTest.kt`
  (через `EntryCreateScreenContent` с plain state и колбэками, по
  образцу `AppearanceScreenTest`; ViewModel в androidTest не используется —
  покрыта JVM-тестами 2.1–2.4): состав полей для REPS/TIME, поле
  переключается при смене упражнения, подсказки при `canSave = false`.
  Запустить
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myworkouts.ui.screens.entry.EntryCreateScreenTest`
  — зелёный
- [x] 4.5 Написать `app/src/androidTest/.../ui/screens/main/MainScreenFabNavigationTest.kt`
  (NavHost-обёртка по образцу MoreScreenTest): тап по FAB открывает
  маршрут `entry_create`. Запустить
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myworkouts.ui.screens.main.MainScreenFabNavigationTest`
  — зелёный

## 5. Финальный прогон и документация

- [x] 5.1 Обновить Purpose главной спеки `openspec/specs/workouts-main-screen/spec.md`:
  убрать упоминание FAB-плейсхолдера (теперь FAB открывает экран
  создания записи); проверить `openspec validate --specs`
- [x] 5.2 Полный прогон: `make format && make lint && make test &&
  make android-test` — всё зелёное

## 6. Ревизия по итогам ручного тестирования (кнопка, пикеры, клавиатура)

- [x] 6.1 Строки ru/en: `entry_select_date`/`entry_select_time`
  (contentDescription иконок), метки `entry_field_reps` → «Повторы
  (обязательно)» / «Reps (mandatory)», `entry_field_duration` →
  «Длительность (обязательно)» / «Duration (mandatory)»; сборка
  `./gradlew :app:compileDebugKotlin`
- [x] 6.2 Red (androidTest): в `EntryCreateScreenTest` добавить
  состояние кнопки — при `canSave = false` (нет упражнения; REPS без
  повторов; TIME без длительности; очищенные подходы) кнопка
  «Сохранить» неактивна, при заполненном состоянии активна. Прогнать
  класс — падает
- [x] 6.3 Red (androidTest): открытие пикеров — тап по иконке
  (contentDescription «Выбрать дату») открывает диалог пикера даты,
  тап по «Выбрать время» — пикер времени. Прогнать класс — падает
- [x] 6.4 Green: `Button(enabled = uiState.canSave)` в `SaveSection`;
  `trailingIcon`-IconButton (календарь/часы) в `DateField`/`TimeField`,
  хелпер `clickableTextField` удалить; `.imePadding()` в
  `EntryCreateForm` (Decision 7). Обновить Preview. Прогнать классы
  6.2–6.3 — зелёные
- [x] 6.5 Ручная проверка на эмуляторе: иконки открывают пикеры,
  клавиатура не перекрывает комментарий, кнопка disabled/enabled,
  подсказки на месте
- [x] 6.6 Полный прогон: `make format && make lint && make test &&
  make android-test` — всё зелёное

## 7. Ревизия №2: «Бег» на время и сырой плейсхолдер «%1$d»

- [x] 7.1 Red (androidTest): `WorkoutDatabaseTest` — TIME = {Бег, Планка,
  Скакалка}, REPS = 10; `StandardExercisesLocalizationTest` — то же;
  `EntryCreateScreenTest` — ни один узел не содержит сырой плейсхолдер
  «%1$d» при durationSec = 65. Прогнать — падает
- [x] 7.2 Green: `StandardExercises` — Бег `measuredBy = TIME`;
  строки `entry_duration_minutes_short`/`entry_duration_seconds_short`
  без `%1$d` («мин»/«сек», «min»/«sec»). Тесты 7.1 — зелёные
- [x] 7.3 Инфраструктура: `WorkoutDatabaseTest.setUp` удаляет файл БД
  устройства (гарантия «первого запуска»: прода-фабрика открывает
  файловую БД, где оставались записи от ручных проверок)
- [x] 7.4 Схема БД без миграций (решение по итогам ревью: приложение
  не в релизе): `WorkoutDatabase` остаётся на версии 1 с
  `fallbackToDestructiveMigration()` — базы с расхождением версий
  пересоздаются с новым prepopulate. Первая попытка (миграция v1→v2 +
  `WorkoutDatabaseMigrationTest` + схема `2.json` + constraint
  kotlinx-serialization 1.8.1 для room-testing) откачена целиком
- [x] 7.5 Документация: дельта `workout-data` (MODIFIED prepopulate —
  бег в TIME + сценарий «Изменение схемы до релиза»),
  `openspec validate entry-create-screen`
- [x] 7.6 Ручная проверка на эмуляторе: база пересоздана, «Бег»
  показывает «Длительность», «Планка» — «5 сек» без плейсхолдеров
- [x] 7.7 Полный прогон: `make format && make lint && make test &&
  make android-test` — всё зелёное
