## 1. Интерфейс репозитория (рефакторинг, TDD не нужен — поведение не меняется)

- [ ] 1.1 Извлечь интерфейс `WorkoutDataRepository` из
  `RoomWorkoutDataRepository` (все публичные методы без изменения
  сигнатур; `RoomWorkoutDataRepository : WorkoutDataRepository`) и
  проверить, что `make test` и
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myworkouts.data.repository.RoomWorkoutDataRepositoryTest`
  остаются зелёными без правок тестов

## 2. Red-тесты ViewModel и чистых функций (JVM)

- [ ] 2.1 Создать `app/src/test/.../ui/viewmodel/EntryCreateViewModelTest.kt`
  с fake `WorkoutDataRepository` на `MutableStateFlow` (каталог: REPS- и
  TIME-упражнение) и `nowProvider` с фиксированным моментом; тесты
  начального состояния: каталог загружен, дата/время = фиксированный
  «сейчас», поле подходов содержит «1» (префилл), `canSave = false`. Запустить
  `./gradlew :app:testDebugUnitTest --tests "com.myworkouts.ui.viewmodel.EntryCreateViewModelTest"`
  — тест падает (класс не реализован)
- [ ] 2.2 Добавить red-тесты выбора упражнения и `canSave`: REPS без
  повторов → `canSave = false`, с повторами → `true`; TIME без
  длительности → `false`, с длительностью → `true`; без упражнения →
  всегда `false`; смена REPS→TIME сбрасывает повторы. Прогнать команду
  из 2.1 — новые тесты падают
- [ ] 2.3 Добавить red-тесты санитайзинга: нецифровые символы и ведущие
  нули отбрасываются в `sets`/`reps`; очищенное или нулевое поле подходов
  → `canSave = false`. Прогнать команду из 2.1 — падают
- [ ] 2.4 Добавить red-тесты `save`: успех — `addEntry` вызван с
  `ExerciseEntry` (exerciseId, reps/durationSec по measuredBy, sets = 1
  из префилла, weightKg = null, date = собранный из LocalDate+час/минута
  millis) и `onSaved` вызван; провал репозитория — `onSaved` не
  вызван, состояние содержит ошибку. Прогнать команду из 2.1 — падают
- [ ] 2.5 Создать `app/src/test/.../ui/screens/entry/EntryCreateFormattersTest.kt`
  (JVM): `formatDuration` (5 сек, 45 сек, 60→«1 мин», 150→«2 мин 30 сек»,
  3600→«1 час») и конвертации дата/время (LocalDate↔UTC millis для
  пикера; LocalDate+час/минута→epoch millis в фиксированной зоне).
  Запустить
  `./gradlew :app:testDebugUnitTest --tests "com.myworkouts.ui.screens.entry.EntryCreateFormattersTest"`
  — падает

## 3. Реализация ViewModel (green)

- [ ] 3.1 Создать `ui/state/EntryCreateUiState.kt` (immutable data
  class: каталог, выбранное упражнение, тексты sets/reps, длительность
  `Int?`, LocalDate + час/минута, комментарий, `canSave`, ошибка) и
  `ui/viewmodel/EntryCreateViewModel.kt` (конструктор с
  `WorkoutDataRepository` + `nowProvider`, companion `factory`,
  StateFlow-состояние, обработчики ввода с санитайзингом, `save(
  onSaved)`) по Decision 2 из design.md; прогнать команды из 2.1 и 2.5 —
  все тесты зелёные

## 4. UI: экран, маршрут, строки (Compose UI-тесты — по факту верстки)

- [ ] 4.1 Добавить строковые ресурсы ru/en: заголовок экрана, метки
  (упражнение, подходы, повторы, длительность, дата, время, комментарий),
  подсказки обязательности, «Сохранить»/«ОК»/«Отмена», plurals для
  минут/секунд форматирования длительности; проверить сборку
  `./gradlew :app:compileDebugKotlin`
- [ ] 4.2 Создать `ui/screens/entry/EntryCreateScreen.kt`:
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
- [ ] 4.3 Ввести `Screen.EntryCreate(route = "entry_create")`, подключить
  composable в `RootScreen` (viewModel через `factory(repository)`,
  `onBackClick = popBackStack`), прокинуть `onAddEntryClick` в
  `MainScreen` (FAB), создать репозиторий в `MainActivity` и передать в
  `RootScreen`; проверить вручную: FAB открывает экран, панели нет,
  сохранение возвращает назад
- [ ] 4.4 Написать `app/src/androidTest/.../ui/screens/entry/EntryCreateScreenTest.kt`
  (через `EntryCreateScreenContent` с plain state и колбэками, по
  образцу `AppearanceScreenTest`; ViewModel в androidTest не используется —
  покрыта JVM-тестами 2.1–2.4): состав полей для REPS/TIME, поле
  переключается при смене упражнения, подсказки при `canSave = false`.
  Запустить
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myworkouts.ui.screens.entry.EntryCreateScreenTest`
  — зелёный
- [ ] 4.5 Написать `app/src/androidTest/.../ui/screens/main/MainScreenFabNavigationTest.kt`
  (NavHost-обёртка по образцу MoreScreenTest): тап по FAB открывает
  маршрут `entry_create`. Запустить
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myworkouts.ui.screens.main.MainScreenFabNavigationTest`
  — зелёный

## 5. Финальный прогон и документация

- [ ] 5.1 Обновить Purpose главной спеки `openspec/specs/workouts-main-screen/spec.md`:
  убрать упоминание FAB-плейсхолдера (теперь FAB открывает экран
  создания записи); проверить `openspec validate --specs`
- [ ] 5.2 Полный прогон: `make format && make lint && make test &&
  make android-test` — всё зелёное
