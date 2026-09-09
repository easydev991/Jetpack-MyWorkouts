## Context

Данные приложения — Room-база (`exercises`, `exercise_entries`) за
`WorkoutDataRepository` / `RoomWorkoutDataRepository`; доступ только через
методы репозитория, ViewModel-ы создаются companion-фабриками (manual DI,
композиционный корень — `MainActivity`). Не-табовые экраны (Appearance)
уже подключаются в `RootScreen`; нижняя панель скрывается автоматически,
т.к. показывается только для маршрутов из `BottomTabs`. Мотивация — в
`proposal.md` (Why). Схема БД не меняется, миграций нет.

## Goals / Non-Goals

**Goals:**

- Полный цикл управления данными: экспорт в файл, восстановление
  слиянием, удаление всех данных — офлайн, через SAF.
- Предсказуемый, версионированный формат резервной копии, который можно
  читать и писать одним и тем же кодом.
- TDD: use-cases и ViewModel покрываются JVM-тестами до реализации.

**Non-Goals:**

- Автоматическое/фоновое резервирование, облако, экспорт по расписанию.
- Поддержка чужих форматов (Strong, Apple Health и т.п.) — только
  собственный JSON.
- Слияние «на конфликт» с выбором версии: merge только добавляет
  отсутствующее, существующее не перезаписывается.
- Прогресс-индикаторы операций.

## Decisions

### Decision 1: Сериализация — `org.json`, без runtime-зависимостей

`org.json` (Android stdlib) строит и парсит схему из ~15 полей без
плагинов и codegen. Альтернатива — `kotlinx.serialization`
(плагин + аннотации + `ignoreUnknownKeys` и т.п.): выигрыш только
на больших/сложных схемах, здесь она добавляет compile-time зависимость
и генерацию кода ради 15 полей — отклонена.

Для JVM unit-тестов (org.json в `android.jar` — заглушки, бросающие
«not mocked») добавляется **только test-зависимость**
`testImplementation("org.json:json:20240303")` — эталонная реализация
того же API. Runtime остаётся на stdlib.

### Decision 2: Формат файла — собственный JSON, `version: 1`

```json
{
  "version": 1,
  "exercises": [
    {
      "name": "Подтягивания",
      "category": "PULL",
      "measuredBy": "REPS",
      "isCustom": false,
      "comment": null,
      "createdAt": 1757371200000
    }
  ],
  "entries": [
    {
      "exerciseName": "Подтягивания",
      "date": 1757371200000,
      "sets": 4,
      "reps": 10,
      "weightKg": null,
      "durationSec": null,
      "comment": null,
      "createdAt": 1757371200000
    }
  ]
}
```

- `category` / `measuredBy` — имена enum-значений (`ExerciseCategory`,
  `ExerciseMeasuredBy`).
- Связь «запись → упражнение» — только по имени (`exerciseName`);
  id строк БД в файле нет — они не переносимы между устройствами.
- `null`-поля сериализуются явно (стабильная форма файла упрощает тесты);
  при чтении отсутствующее поле трактуется как `null`.
- Поле `modifiedAt` в файле не хранится: восстановленная запись получает
  `modifiedAt` = моменту импорта (та же семантика, что у `updateEntry`).
- Запись с отступом 2 (`JSONObject.toString(2)`) — файл открывается
  текстовым редактором и читается в diff.
- `version` — версия формата: экспорт всегда пишет `1`; импорт принимает
  только `1`, прочее — «файл несовместим». Новая версия = новое значение +
  миграция чтения в будущем.

### Decision 3: Use-cases — `ExportBackupUseCase`, `ImportBackupUseCase`; удаление — без use-case

Оба use-case получают в конструктор `context: Context` (в
`AppDataViewModel.factory` передаётся `context.applicationContext` —
хранить Activity-контекст в ViewModel нельзя) и `repository`: потоки
открываются через `context.contentResolver`.

- `ExportBackupUseCase(repository, context)`: `suspend operator fun
  invoke(uri: Uri): Result<Unit>` — снимок данных (`getAllExercises()` +
  `getAllEntries()`), сборка JSON, запись в
  `context.contentResolver.openOutputStream(uri)` на `Dispatchers.IO`.
Поток открывается лениво в use-case — после успешного снимка и сборки
JSON; при ошибке файл, созданный SAF, остаётся пустым — это шум, но не
потеря данных (SAF URI одноразовый).
- `ImportBackupUseCase(repository, context)`: `suspend operator fun
  invoke(uri: Uri): Result<Unit>` — чтение
  `openInputStream` на `Dispatchers.IO`, парсинг (Decision 5), merge
  (Decision 4) через `importData(...)`.
- Удаление всех данных — единственный вызов
  `repository.deleteAllData()` без собственной логики; отдельный use-case
  для одной строки — лишний слой, ViewModel вызывает репозиторий напрямую
  (как `MainViewModel` вызывает `deleteEntry`).

Своё исключение `BackupException(message, error)` — `error: BackupError`,
`enum class BackupError { EXPORT, INVALID_FILE }`
(рядом с исключением в `domain/usecase/`); русское
`message` — только для лога, пользовательский текст локализуется в UI по
`error` (Decision 7). Ошибки БД проходят в `Result.failure` как есть,
мапятся в `error` операции. Перенос SAF-обвязки из use-case'ов в
data-адаптер — при появлении второй точки потребления; пока домен
остаётся с `Context`/`Uri` осознанно.

### Decision 4: Merge и дедупликация

Ключи дедупликации (сравнение без записи в БД):

- Упражнение — `name.trim().lowercase()`. Нечувствительность к регистру
  покрывает «Подтягивания»/«подтягивания» после смены локали ввода.
- Запись — кортеж
  `(exerciseName.trim().lowercase(), date, sets, reps, weightKg,
  durationSec, comment)` — «то же содержание», независимо от id.
  `weightKg = 0.0` и `null` — разные значения (контракт `EntryValidator`).

Алгоритм импорта (парсинг, дедупликация и валидация — в use-case, до
транзакции; вставка подготовленных данных — одной транзакцией
`importData`, Decision 6):

1. Прочитать каталог в `Map<ключ, Exercise>`; для упражнений файла, чьих
   ключей нет, — вставить (поля файла, кроме `isCustom`: вставляемым
   всегда `true` — файл недоверенный, а вставляемое по определению вне
   канонического стандартного набора, иначе поддельным `isCustom = false`
   можно сделать неудаляемое «стандартное»; `createdAt` из файла;
   `id = 0` — присвоит БД, `autoGenerate`, в файле id нет и не пишется);
   существующие не трогаем.
2. Записи разрешаются по имени упражнения (`exerciseName` → тот же ключ,
   что у упражнений): валидация — против `measuredBy` найденного
   упражнения (существующего в каталоге или из файла).
   `EntryValidator.validate` принимает `ExerciseEntry`, а у `BackupEntry`
   нет `exerciseId`/`modifiedAt` — use-case собирает временный объект
   только для валидации: `exerciseId = -1L` (в проверке не участвует),
   `workoutId = null` (MVP-инвариант — валидатор отклоняет не-null),
   `modifiedAt = now`; реальные значения проставит репозиторий при
   вставке (Decision 6). Use-case работает
   только именными ключами; присвоение `exerciseId` — внутри транзакции
   `importData` (Decision 6), id ещё не вставленных упражнений use-case
   не знает и не должен.
3. Записи: пропускать (а) дубликаты по ключу содержимого среди текущих и
   уже вставленных, (б) записи без разрешённого упражнения,
   (в) записи, не прошедшие `EntryValidator` против разрешённого
   упражнения (`measuredBy`). Пропуск записи не роняет импорт —
   файл собственного формата мог быть отредактирован вручную.
4. Итог — `Result<Unit>` от `importData`; тост успеха — простая строка
   без счётчиков (Decision 7).

Диалог подтверждения не нужен: операция только добавляет, откат =
«Удалить все данные».

### Decision 5: Обработка ошибок импорта — два уровня

- **Ошибки файла** → `Result.failure(BackupException)`, данные не
  меняются, Toast с ошибкой: не-JSON; нет/не-объект полей `version`,
  `exercises`,   `entries`; `version != 1`; неизвестное значение enum
  (`category`, `measuredBy`) где-либо в файле; элемент массива без
  обязательных полей или с неверным типом (`name`/`exerciseName` —
  непустая строка, `sets` — целое, числа — числа). Диапазон
  `sets >= 1` — не ошибка файла: его проверяет `EntryValidator`,
  невалидная запись тихо пропускается (Decision 4, п. 3в) — один вход,
  один исход.
- **Ошибки элемента** → тихий пропуск (Decision 4, п. 3).
- Неизвестные поля объектов игнорируются — прямая совместимость вперёд
  (`version` останется 1, пока изменения читаемы старым парсером).

Файл читается и парсится целиком до открытия транзакции записи: битый
файл не может изменить данные частично.

### Decision 6: Репозиторий — снимки, массовая вставка, `deleteAllData`

`WorkoutDataRepository` расширяется:

- `suspend fun getAllExercises(): Result<List<Exercise>>` — разовый
  снимок каталога. Формально заменим `exercises("").first()`, но явный
  метод оставлен: `exercises(query)` — UI-поисковый `Flow` (LIKE-фильтр
  в SQL), переиспользовать его как разовый снимок — неявная семантика;
  `getAllExercises()` симметричен `getAllEntries()` и идёт прямым
  SQL-запросом без LIKE-обёртки. Фейки в тестах расширяются в любом
  случае (остальные три метода этого же блока их тоже требуют).
- `suspend fun getAllEntries(): Result<List<ExerciseEntry>>`
- `suspend fun importData(exercises: List<Exercise>, entries:
  List<BackupEntry>): Result<Unit>` — вставляет только
  переданное (дедупликация и валидация —
  задача use-case по своему снимку каталога, Decision 4; репозиторий —
  чистое хранение). `BackupEntry` (`domain/model`) — запись без `exerciseId` и
  `modifiedAt`, но с `exerciseName`: ровно поля элемента `entries` файла.
  Репозиторий внутри транзакции сам разрешает имена → id: вставляет
  упражнения одиночными `insert` в цикле (каждый возвращает id), строит
  карту «ключ имени → id» поверх текущего каталога и вставленных, и
  только затем мапит `BackupEntry` → entity. Неразрешимое имя —
  защита от бага use-case (`IllegalStateException`), use-case такие
  записи отфильтровал.
- `suspend fun deleteAllData(): Result<Unit>`

Реализация: `ExerciseDao.getAll() / deleteAll()`,
`ExerciseEntryDao.getAll() / deleteAll() / insertAll()`. Для атомарности
`importData` и `deleteAllData` конструктор репозитория меняется на
`(database: WorkoutDatabase, standardExercises: List<StandardExercise>)`:
DAO берутся из переданной базы (`database.exerciseDao()` /
`database.exerciseEntryDao()`), отдельные DAO-параметры упразднены —
DAO от другой базы сделал бы `androidx.room.withTransaction`
бессмысленным. `deleteAllData()` внутри
транзакции: удалить записи → удалить упражнения → вставить стандартный
набор.

Стандартный набор: параметр
`standardExercises` — тот же список, который
`MainActivity` уже вычисляет через `standardExercises(context)` для
`WorkoutDatabase.create(...)`. Один источник имён, prepopulate БД и
«удалить всё» дают идентичный каталог. Вставка — через существующий
маппинг с `isCustom = false`; `createdAt = now` (у «восстановленного»
каталога честные метки времени).

`deleteAllData()` — единственное намеренное исключение из правила
«стандартное упражнение нельзя удалить» (workout-data, «Удаление
упражнения из каталога»): оно работает с DAO-уровнем внутри транзакции,
а не через `deleteExercise`, который стандартные упражнения отклоняет;
после удаления набор восстанавливается заново, инвариант «стандартные
присутствуют» сохраняется.

### Decision 7: UI — маршрут, ViewModel, SAF, Toast

- `object AppData : Screen(route = "app_data")` в `navigation/Screen.kt`
  (по образцу `object Appearance : Screen(route = "appearance")`) +
  `composable` в `RootScreen` по
  образцу `Screen.Appearance`; нижняя панель скрывается сама (`route` не
  в `BottomTabs`). Кнопка на «Ещё» — `FilledTonalButton` под кнопкой
  «Оформление приложения», `navigate(Screen.AppData.route) {
  popUpTo(Screen.More.route) }` (нотация — по спеке more-screen; литерал
  `"app_data"` живёт только в декларации маршрута).
- `AppDataViewModel(repository, context): ViewModel()` с
  `companion object { fun factory(repository: WorkoutDataRepository,
  context: Context): ViewModelProvider.Factory }` (образец —
  `AppearanceViewModel`); фабрика сразу сохраняет
  `context.applicationContext` и создаёт use-case'ы Decision 3.
- `AppDataUiState(hasData, isBusy, result: AppDataResult?)`:
  `hasData` — `repository.entries().map { it.isNotEmpty() }` OR
  `repository.exercises("").map { list -> list.any { it.isCustom } }`
  (есть записи или хотя бы одно пользовательское упражнение; стандартный
  набор данными пользователя не считается; правки стандартных упражнений
  (комментарий) тоже не считаются — при «удалить всё» они теряются,
  Risks; оба потока уже существуют,
  новых методов репозитория не нужно); `isBusy` — один
  `MutableStateFlow<Boolean>`: любая запущенная операция блокирует все
  три кнопки (спека «Результаты операций»), параллельные операции
  исключены по построению — детерминированный порядок и ровно один
  результат; `result` — один приватный
  `MutableStateFlow<AppDataResult?>` (начальное
  `null`): `enum class AppDataResult { EXPORTED, RESTORED, DELETED,
  ERROR_EXPORT, ERROR_INVALID_FILE, ERROR_IMPORT, ERROR_DELETE }` в
  файле `AppDataUiState.kt`; выставляется атомарно по завершении
  операции, `clearResult()` сбрасывает в `null`, flow входит в
  общий `combine`; `combine` +
  `SharingStarted.WhileSubscribed(5000)`.
- UiState — обычный data class, как `AppearanceUiState` /
  `EntryCreateUiState`, по правилу проекта (AGENTS.md): UiState —
  data class по умолчанию (флаги + одноразовые события-kind'ы);
  sealed `Loading/Success/Error` — только когда экран имеет
  взаимоисключающие визуальные состояния. Здесь их нет: Loading-состояния
  нет (Room-Flow реактивный, старт — пустой `isBusy` и `hasData` из
  потока), ошибки — одноразовые события (`result`), а не состояние
  экрана.
- SAF: `rememberLauncherForActivityResult(
  ActivityResultContracts.CreateDocument("application/json"))` —
  предлагаемое имя `myworkouts-backup-<yyyy-MM-dd>.json`
  (`LocalDate.now()`, ISO-формат даёт `yyyy-MM-dd` без форматтера);
  `ActivityResultContracts.OpenDocument()` с `arrayOf("*/*")` — восстановление;
  `null`-результат лаунчера (отмена) — no-op: без запуска use-case и
  без Toast.
- Toast one-shot: экран в
  `LaunchedEffect(uiState.result)` мапит `AppDataResult` → строковый
  ресурс через `when` + `context.getString(...)` (ошибки →
  `Toast.LENGTH_LONG`) и вызывает `clearResult()`;
  сброс после показа исключает повтор на recomposition; техническое
  сообщение исключения — `Log.e` в ViewModel (по-русски), не в Toast.
- Кнопка «Удалить все данные» — `Button(colors =
  ButtonDefaults.buttonColors(containerColor =
  MaterialTheme.colorScheme.error, contentColor =
  MaterialTheme.colorScheme.onError))`, `AlertDialog` (confirm/deny),
  тексты — строковые ресурсы.
- Кнопки экспорта и удаления — `AnimatedVisibility`-скрытие не нужно:
  обычный `if (!hasData)` внутри `Column` (реактивно из `entries()` и
  `exercises("")`).
- Строки: `app_data`, `create_backup`, `restore_from_backup`,
  `delete_all_data`, `delete_all_dialog_text`, тосты успеха трёх
  операций и обобщённые тексты ошибок (`error_export_failed`,
  `error_import_failed`, `error_invalid_backup`, `error_delete_failed`)
  — ru + en; пользовательский текст выбирается по `BackupError`/
  `AppDataResult`, технический — только в лог;
  кнопка диалога — новый ключ `delete` (ключа
  `delete` в проекте нет, `cancel` уже существует — задача 8.1).

### Decision 8: Тесты (TDD)

- **Unit (JUnit 5, `app/src/test`)**, до реализации:
  `ExportBackupUseCaseTest` — формат файла (version, поля, связи по
  имени, отступы/читаемость), пустые данные; `ImportBackupUseCaseTest` —
  дедуп упражнений по имени, дедуп записей по содержимому, восстановление
  связей по имени, пропуск записи без упражнения, битый JSON,
  несовместимая версия, элемент без обязательных полей, неизвестные поля
  игнорируются, ошибки БД прокидываются; `AppDataViewModelTest` — флаги
  операций, `result`-kind'ы (успех/ошибка по типам `BackupError`) +
  `clearResult()`; `hasData` — из
  собственного `AppDataViewModelFakeRepository` на `MutableStateFlow` в
  самом `AppDataViewModelTest.kt` во всех состояниях: есть записи, только
  кастомные упражнения, полностью пусто. Существующие
  `MainViewModelFakeRepository` и `FakeWorkoutDataRepository` — top-level
  `private` (недоступны из другого файла) и моделируют только старый
  контракт: расширяются новыми методами исключительно чтобы старые тесты
  компилировались (задача 1.4), новый тест их не переиспользует.
- **androidTest (JUnit 4)**: новые DAO-методы и транзакционность
  (`deleteAllData` переустанавливает стандартный набор; `importData`
  атомарен при сбое), кнопка «Данные приложения» на «Ещё» (текст ru,
  навигация), экран (кнопки скрыты без записей, диалог удаления,
  заголовок). SAF-потоки в androidTest не эмулируются: use-case принимает
  `Uri` + `context`, unit-тесты вызывают внутренние парсер/сборщик JSON
  напрямую (чистые функции над `List<Exercise>`/`List<BackupEntry>`);
  invoke-тесты на JVM — через `mockk<Uri>()` и замоканный
  `contentResolver` (задачи 2.2, 4.2); URI-обвязка сводится к `openInputStream`/`openOutputStream` и
  покрывается ревью + ручной проверкой по сценариям спеки. Точное время
  в тестах не ассерцится: `createdAt` — из фикстур, `modifiedAt` и имя
  файла — только формат/диапазон.

## Risks / Trade-offs

- [Ошибки операций показываются обобщёнными локализованными
  сообщениями (4 ресурса, ru/en), технические детали — в лог] →
  пользователь не видит точную причину; разбор — по логу. Причина
  доступна типизированно (`BackupError`), детальные `messageRes` — при
  реальной потребности.
- [SAF: при отмене пикера `uri == null` — операция no-op, без запуска
  use-case и без Toast (файл при отмене не создаётся); пустой или
  частичный файл возможен только после успешного возврата URI и сбоя
  записи/чтения — UX-шум, не потеря данных. `openInputStream`/
  `openOutputStream`, вернувшие `null`, → `Result.failure`
  (`BackupException`); `CancellationException` прокидывается, а не
  превращается в ошибку пользователя] → принято.
- [Экспортный снимок из двух последовательных `getAll` не атомарен:
  между ними каталог теоретически может измениться] → принято:
  офлайн-приложение, один пользователь, окно между запросами
  миллисекунды; атомарный общий снимок — при реальной необходимости.
- [Дедупликация по имени работает в одной локали: бэкап из ru на
  en-устройстве создаст дубликаты стандартных упражнений] → принято для
  v1: большинство пользователей — ru; дубликаты получат `isCustom = true`
  и удалимы вручную; стабильные ключи стандартных упражнений — при
  реальной потребности переноса между локалями.
- [Правки стандартных упражнений (комментарий) не считаются данными
  пользователя: `hasData` их не видит, «удалить всё» их теряет] →
  принято для MVP; учёт правок через сравнение с каноническим набором —
  при запросах.
- [Пропуск «плохих» элементов при импорте может пройти незамеченным] →
  принято осознанно: счётчиков нет (читателей не было, см. амендмент
  о код-ревью), тост успеха — простая строка (без plurals);
  пропущенное не теряется молча — его просто нет.
- [`org.json:json` в testImplementation отличается от Android-реализации
  в редких edge-cases] → схема использует только базовые
  `JSONObject`/`JSONArray` операции; расхождение на них неизвестно.
- [Дедуп записей по содержимому не различает две одинаковые тренировки
  в один день] → оба экземпляра считаются дублями при повторном импорте
  того же файла — это и есть требование идемпотентности; вручную
  продублировать выполнение можно повторным импортом другого файла.

## Migration Plan

Схема БД и существующие данные не затрагиваются; откат — revert коммита.
Формат `version: 1` фиксируется с первого релиза фичи: будущие изменения
формата обязаны читать `1` или отказываться с понятной ошибкой.

## Amendments (по итогам ручной проверки)

- [Кнопки экрана «Данные приложения» — по ширине текста, по центру
  колонки] → паддинг кнопок из
  `button_horizontal_padding`, интервал — `spacing_xxsmall` через
  `Column.spacedBy`; контент-логика (`AppDataButtons`/
  `DeleteAllDialog`) вынесена из `AppDataScreenContent` в бюджет
  detekt.
- [Интервал между кнопками экрана «Ещё» — единый] → задаётся один раз
  в `Column` через `Arrangement.spacedBy(spacing_xxsmall)`;
  `NavigationButtons` (вынесенные кнопки «Оформление»/«Данные») не имеют
  layout-обёртки и являются прямыми measurables колонки, отдельные
  `Spacer` убраны.
- [`WorkoutDatabaseTest` не трогает файл прод-БД] → добавлена
  тест-фабрика `WorkoutDatabase.createInMemory` (та же настройка и
  prepopulate, что в `create`, сид-callback выделен в `seedCallback`);
  прежний setUp с `getDatabasePath(DATABASE_NAME).delete()` стирал
  данные приложения при каждом полном `make android-test`.
- [Мёртвый код по итогам код-ревью] → `BackupError` сокращён до
  `{ EXPORT, INVALID_FILE }`: IMPORT/DELETE никогда не конструировались,
  when-ветки ViewModel были недостижимы. `BackupImportResult` удалён —
  счётчики не имели читателей, `ImportBackupUseCase.invoke` возвращает
  `Result<Unit>`, идемпотентность в тестах проверяется по аргументам
  вызовов `importData` (пустые списки на повторе). Описания задач 1.2,
  4.2 и 5.1 в `tasks.md` отражают состояние на момент их выполнения
  и задним числом не переписываются. Инлайн `NavigationButtons` в
  `Column` рассмотрён и отклонён: тело `MoreScreen` превысило бы
  detekt `LongMethod: 60` при экономии ~9 строк.
