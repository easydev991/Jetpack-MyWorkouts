# Tasks

## 1. Слой данных: история упражнения

- [x] 1.1 Тест (TDD): в `ExerciseEntryDaoTest` (androidTest) добавить
  тест нового `historyForExercise(id)` — записи упражнения в порядке
  `date DESC, created_at DESC`, записи других упражнений не попадают,
  пустой результат для упражнения без записей. Прогон:
  `./gradlew :app:connectedGithubDebugAndroidTest` с
  `-Pandroid.testInstrumentationRunnerArguments.class=…ExerciseEntryDaoTest`
  — падает компиляция androidTest (метода нет), а не сам тест — обычный
  TDD-red.
- [x] 1.2 Реализация: `@Query("SELECT * FROM exercise_entries WHERE
  exercise_id = :id ORDER BY date DESC, created_at DESC") suspend fun
  historyForExercise(id: Long): List<ExerciseEntryEntity>` в
  `ExerciseEntryDao`. Прогон теста 1.1 — зелёный.
- [x] 1.3 Тест (TDD): в `RoomWorkoutDataRepositoryTest` (androidTest) —
  тест `exerciseHistory(exerciseId)`: возвращает домен-записи в том же
  порядке, пустая история — `success(emptyList())`. Прогон — падает
  компиляция androidTest (метода нет), а не сам тест.
- [x] 1.4 Реализация: метод `exerciseHistory(exerciseId: Long):
  Result<List<ExerciseEntry>>` в `WorkoutDataRepository` (KDoc) и
  `RoomWorkoutDataRepository` (try/catch, `toDomain()`); добавить метод во
  все Fake-репозитории JVM-тестов с дефолтом `emptyList()` — компилятор
  `./gradlew compileDebugUnitTestKotlin` перечислит файлы. Прогон
  теста 1.3 и JVM `make test` — зелёные.

## 2. ViewModel: предзаполнение при выборе упражнения

- [x] 2.1 Тесты (TDD) в `EntryCreateViewModelTest` (JVM, Fake-репозиторий
  возвращает историю): предзаполнение reps из последней записи; duration
  из последней записи; непустое поле не
  перезаписывается при смене упражнения в пределах типа; у упражнения без
  записей поля остаются пустыми; в режиме редактирования предзаполнения нет.
  (Тай-брейк по `created_at` проверяется на уровне DAO —
  `ExerciseEntryDaoTest`, design D1.)
  Прогон: `./gradlew testDebugUnitTest --tests "*EntryCreateViewModel*"`
  — падают.
- [x] 2.2 Тесты (TDD): правило веса — большинство с весом (3 из 4) →
  вес последней записи с `weightKg > 0`; ровно половина (2 из 4) → без
  предзаполнения; последняя без веса, ранняя с весом → вес ранней;
  `weightKg = 0` не считается записью с весом; введённое пользователем
  значение (в том числе 0) не перезаписывается; правило работает для REPS
  и TIME. Прогон — падают.
- [x] 2.3 Тесты (TDD): гонка — смена упражнения до ответа репозитория не
  применяет результат к новому упражнению (fake с задержкой); применение
  только в режиме создания. Прогон — падают.
- [x] 2.4 Реализация в `EntryCreateViewModel.onExerciseChange`: после
  синхронного `update` — `viewModelScope.launch` запроса истории
  (design D3), private-расчёт значений (design D4), флаг `weightTouched` —
  условие «поле не изменено пользователем» (design D5), тройная проверка
  перед применением. Прогон тестов 2.1–2.3 — зелёные; `make test` — зелёный.

## 3. UI-тесты экрана

- [x] 3.1 Тесты в `EntryCreateScreenTest` (androidTest, Room in-memory с
  предзаписанной историей): выбор упражнения с историей предзаполняет
  поля «Повторы»/«Длительность» и «Доп. вес» по правилам спеки; выбор
  упражнения без истории оставляет поля пустыми/нулевыми; скрытое поле
  «Доп. вес» не отображается, но предзаполняется и попадает в сохранённую
  запись (spec-сценарий «Скрытое поле веса предзаполняется на общих
  правилах»). Прогон:
  `./gradlew :app:connectedGithubDebugAndroidTest` с
  `-Pandroid.testInstrumentationRunnerArguments.class=…EntryCreateScreenTest`
  — зелёные.
- [x] 3.2 `make emulator-fast` перед прогоном androidTest на свежем
  эмуляторе (выключает анимации).

## 4. Финальная проверка

- [x] 4.1 `make format && make lint && make test` — зелёные, новых
  detekt-предупреждений нет (лимит не повышать).
- [x] 4.2 Полный `make android-test` на подключённом эмуляторе —
  зелёные (отчёт `scripts/test_report.py` в выводе).
