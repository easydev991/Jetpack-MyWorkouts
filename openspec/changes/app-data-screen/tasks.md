## 1. Тестовая база и контракты данных

- [x] 1.1 Добавить в `gradle/libs.versions.toml` и `app/build.gradle.kts`
  test-зависимость `org.json:json` (`testImplementation`) — дизайн
  Decision 1; проверить: `make test` собирается, существующие тесты зелёные
- [x] 1.2 Создать типы: `domain/model/BackupImportResult.kt`
  (`addedExercises`, `addedEntries`), `domain/model/BackupEntry.kt`
  (запись с `exerciseName` вместо `exerciseId`, без `modifiedAt` —
  Decision 6) и `domain/usecase/BackupException.kt` вместе с
  `enum class BackupError { EXPORT, INVALID_FILE, IMPORT, DELETE }`
  (русские сообщения) — дизайн Decisions 3–6; проверить:
  компиляция `./gradlew compileDebugKotlin`
- [x] 1.3 Объявить новые методы интерфейса `WorkoutDataRepository`:
  `getAllExercises`, `getAllEntries`, `importData`, `deleteAllData`
  (сигнатуры — дизайн Decision 6) и сразу добавить компилирующиеся заглушки-реализации
  `RoomWorkoutDataRepository` (`TODO()`-тела; полная реализация —
  задача 6.2), чтобы main собирался; обновить androidTest-фейки
  интерфейса: анонимные `object : WorkoutDataRepository` в
  `MainScreenEditNavigationTest` и `MainScreenFabNavigationTest`,
  `SectionsFakeRepository` в `MainScreenSectionsTest` (заглушки
  `TODO()`/`error(...)` достаточно); проверить: `make lint` и
  `./gradlew compileDebugAndroidTestKotlin` проходят
- [x] 1.4 Расширить оба fake-репозитория JVM-тестов новыми методами на
  `MutableStateFlow` — только чтобы существующие тесты компилировались —
  `MainViewModelFakeRepository`
  (`MainViewModelTest.kt`) и `FakeWorkoutDataRepository`
  (`EntryCreateViewModelTest.kt`; новый тест пишет свой фейк — задача
  7.1); проверить: `make test` зелёные

## 2. Экспорт: тесты (red)

- [x] 2.1 Создать компилирующийся скелет `ExportBackupUseCase`
  (сигнатура из дизайна Decision 3, `TODO()`-тела), затем написать
  падающий `ExportBackupUseCaseTest`
  (`app/src/test/java/com/myworkouts/domain/usecase/`): сборка JSON
  содержит `version: 1`,
  массивы `exercises`/`entries`, все поля схемы, связь записи — по
  `exerciseName` без id БД, `null`-поля сериализуются, отступ 2,
  человекочитаемость — дизайн Decision 2; проверить: тест падает
  (`TODO()`-тела скелета), `make test` компилируется
- [x] 2.2 Дополнить тест: экспорт пустых данных даёт валидный файл с
  пустыми массивами, ошибка репозитория → `Result.failure`;
  проверить: красный перед реализацией

## 3. Экспорт: реализация (green)

- [x] 3.1 Реализовать сборку JSON (чистая функция над
  `List<Exercise>` / `List<ExerciseEntry>` через `org.json`) и
  `ExportBackupUseCase`: снимок через `getAllExercises` /
  `getAllEntries`, запись в `contentResolver.openOutputStream(uri)` на
  `Dispatchers.IO`, `try/catch` → `Result` — дизайн Decisions 1–3;
  проверить: `make test` зелёный по задачам 2.1–2.2

## 4. Импорт: тесты (red)

- [x] 4.1 Создать компилирующийся скелет `ImportBackupUseCase`
  (сигнатура из дизайна Decision 3, `TODO()`-тела), затем написать
  падающий `ImportBackupUseCaseTest`: корректный файл —
  новые упражнения добавлены, существующие по имени
  (`trim().lowercase()`) не дублируются, вставленному из файла
  ставится `isCustom = true` (значение из файла игнорируется), записи
  дедуплицируются по
  содержимому, запись связывается с упражнением по имени (дубликат имени
  не создаёт новое упражнение) — дизайн Decision 4; проверить: красный
- [x] 4.2 Дополнить тесты merge: запись без упражнения (ни в файле, ни в
  каталоге) пропускается без провала импорта, и use-case передаёт в
  `importData` только записи с разрешённым именем (неразрешимое имя не
  доходит до репозитория — его `IllegalStateException`-защита не
  срабатывает); запись, не прошедшая
  `EntryValidator`, пропускается; повторный импорт того же файла
  идемпотентен (счётчики добавлений = 0); проверить: красный
- [x] 4.3 Дополнить тесты ошибок файла: не-JSON, отсутствие/неверный тип
  `version`/`exercises`/`entries`, `version != 1`, элемент без
  обязательных полей, неизвестное значение enum — `Result.failure(
  BackupException)`, данные fake-репозитория не изменились; проверить:
  красный
- [x] 4.4 Дополнить тест: неизвестные поля объектов игнорируются,
  импорт успешен; проверить: красный

## 5. Импорт: реализация (green)

- [x] 5.1 Реализовать парсер JSON (валидация — дизайн Decision 5) и
  `ImportBackupUseCase`: чтение `openInputStream` на `Dispatchers.IO`,
  полный парсинг до записи, merge-алгоритм Decision 4, транзакционная
  вставка через `repository.importData`, возврат
  `BackupImportResult`; проверить: `make test` зелёный по задачам 4.1–4.4

## 6. Репозиторий и DAO (androidTest → реализация)

- [x] 6.1 Объявить DAO-методы (`ExerciseDao.getAll/deleteAll`,
  `ExerciseEntryDao.getAll/deleteAll/insertAll`) — Room-декларации
  компилируются сразу (реализация — аннотации), затем написать androidTest:
  DAO-методы возвращают/пишут корректные данные; `deleteAllData`
  удаляет всё и восстанавливает стандартный набор (`isCustom = false`),
  `importData` атомарен при сбое вставки (единственный детерминированный
  сбой через публичный контракт — неразрешимое имя записи:
  `IllegalStateException` внутри транзакции → откат; unique-индекса на
  имени упражнения в БД нет); проверить: тесты
  репозитория красные (`TODO()`-заглушки задачи 1.3), DAO-тесты зелёные
- [x] 6.2 Реализовать `RoomWorkoutDataRepository` на объявленных в 6.1
  DAO-методах:
  конструктор-параметры `database: WorkoutDatabase` и
  `standardExercises: List<StandardExercise>`, `withTransaction` для
  `importData`/`deleteAllData` — дизайн Decision 6; обновить
  композиционный корень `MainActivity` (тот же список
  `standardExercises(context)`, что для `WorkoutDatabase.create`) и
  существующий `RoomWorkoutDataRepositoryTest` под новый конструктор
  (строка с `RoomWorkoutDataRepository(exerciseDao, exerciseEntryDao)`
  сломается); проверить: `make android-test` зелёный по задаче 6.1,
  `make test` зелёный

## 7. ViewModel: тесты (red) → реализация (green)

- [x] 7.1 Создать компилирующиеся скелеты `AppDataUiState` (data class
  с полями из дизайна Decision 7) и `AppDataViewModel` (companion
  `factory`, `TODO()`-тела), затем написать падающий
  `AppDataViewModelTest`
  (`app/src/test/java/com/myworkouts/ui/viewmodel/`) с собственным
  `AppDataViewModelFakeRepository` на `MutableStateFlow` в том же файле
  (существующие фейки — top-level `private`, из другого файла
  недоступны): `hasData` во
  всех состояниях (есть записи / только кастомные упражнения / пусто),
  `isBusy` выставляется на время любой операции и сбрасывается,
  `result`-kind (успех/ошибка по `BackupError`) после
  операции, `clearResult()` сбрасывает; по kotlin-testing:
  `MainDispatcherRule` (`Dispatchers.setMain`/`resetMain`,
  `StandardTestDispatcher`) и `advanceUntilIdle()` после действий;
  проверить: красный
- [x] 7.2 Реализовать `ui/state/AppDataUiState.kt` и
  `ui/viewmodel/AppDataViewModel.kt` с companion
  `factory(repository, context)` (образец — `AppearanceViewModel`;
  фабрика сохраняет `context.applicationContext` и создаёт use-case'ы):
  `combine` +
  `SharingStarted.WhileSubscribed(5000)` — дизайн Decision 7; проверить:
  `make test` зелёный

## 8. Строки и навигация

- [x] 8.1 Добавить строки ru (`values/strings.xml`, значения по
  умолчанию) + en (`values-en/strings.xml`): `app_data`, `create_backup`,
  `restore_from_backup`, `delete_all_data`, `delete_all_dialog_text`,
  текст и кнопки диалога удаления (новый ключ `delete`; `cancel` уже
  существует), тосты успеха трёх операций и обобщённые тексты ошибок
  (`error_export_failed`, `error_import_failed`,
  `error_invalid_backup`, `error_delete_failed`) — навык
  `localization`; проверить:
  `make lint` без предупреждений о MissingTranslation
- [x] 8.2 Маршрут `object AppData : Screen(route = "app_data")` в
  `navigation/Screen.kt`; кнопка «Данные приложения» на `MoreScreen` (по
  образцу кнопки «Оформление приложения», `navigate(Screen.AppData.route)
  { popUpTo(Screen.More.route) }`); destination в `RootScreen`
  регистрируется позже — в 9.1, до этого кнопку тапами не проверять;
  проверить: `make lint` проходит, приложение собирается

## 9. Экран «Данные приложения»

- [x] 9.1 Реализовать `ui/screens/appdata/AppDataScreen.kt` (+ отдельный
  `AppDataScreenPreview.kt`) и зарегистрировать composable-destination
  `Screen.AppData.route` в `RootScreen` рядом с Appearance: TopAppBar со
  стрелкой назад, три кнопки; ViewModel — через
  `factory(repository, LocalContext.current.applicationContext)`;
  `CreateDocument("application/json")` с именем
  `myworkouts-backup-<yyyy-MM-dd>.json`, `OpenDocument` c `*/*`;
  `LaunchedEffect(result)` → Toast по `AppDataResult` через `when` +
  `context.getString(...)` + `clearResult()`;
  кнопка удаления в цвет ошибки + `AlertDialog`; экспорт/удаление скрыты
  при `!hasData` (нет ни записей, ни кастомных упражнений); кнопки
  недоступны во время любой операции — дизайн
  Decision 7, спека `app-data-management`; проверить: ручная проверка на
  эмуляторе по сценариям спеки (экспорт, импорт, отмена диалога,
  удаление, тосты, нижняя панель скрыта на экране)
- [x] 9.2 Preview светлая + тёмная тема, `showBackground = true`,
  `locale = "ru"`, ключевые состояния рядом (есть данные / нет данных /
  операция идёт) — конвенция AGENTS.md; проверить: превью рендерятся в
  IDE/`make lint`
- [x] 9.3 Написать androidTest экрана (`AppDataScreenTest`): заголовок,
  кнопки скрыты при `!hasData` (пустой каталог без кастомных упражнений),
  кнопка экспорта видима при наличии только кастомных упражнений, диалог
  удаления открывается/отменяется,
  кнопка удаления стилизована ошибкой — стилизация проверяется по
  preview (9.2) и ручной проверке (9.1), androidTest проверяет только
  семантику кнопки (видимость/кликабельность; пиксельные проверки цвета
  в проекте не применяются); проверить: зелёный
  `make android-test`
- [x] 9.4 Написать androidTest кнопки на «Ещё» (текст ru, навигация на
  `app_data`, нижняя панель скрыта на destination) по образцу
  `MoreScreenTest`; проверить: зелёный `make android-test`

## 10. Финальная проверка

- [x] 10.1 Прогнать `make format && make lint && make test` и
  `make android-test` на подключённом устройстве — всё зелёное
- [x] 10.2 Пройти по сценариям `specs/app-data-management/spec.md` и
  дельт `more-screen`, `workout-data`, `non-tab-screen-navigation`:
  каждый сценарий подтверждён тестом или ручной
  проверкой; отметить расхождения в `design.md` или исправить код
