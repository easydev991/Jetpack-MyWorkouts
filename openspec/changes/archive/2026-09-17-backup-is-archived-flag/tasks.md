## 1. Тесты: экспорт `isArchived`

- [x] 1.1 Добавить в `ExportBackupUseCaseTest` кейс «архивное упражнение
  пишется в JSON с `isArchived = true`»: собрать каталог с одним
  упражнением `isArchived = true`, прогнать `buildBackupJson`, проверить
  наличие и значение поля. Verify: `./gradlew testDebugUnitTest --tests
  "*ExportBackupUseCaseTest*"` зелёный.
- [x] 1.2 Добавить в `ExportBackupUseCaseTest` кейс «активное упражнение
  пишется в JSON с `isArchived = false`»: симметрично 1.1, упражнение
  `isArchived = false`. Verify: тот же прогон зелёный.

## 2. Тесты: импорт `isArchived`

- [x] 2.1 Добавить в `ImportBackupUseCaseTest` кейс «архивное упражнение
  из файла попадает в архив при импорте»: файл с упражнением
  `isArchived = true` (имени нет в каталоге) → после `importData` в
  каталоге упражнение с `isArchived = true` и `isCustom = true`
  (закрепляет правило Decision 4: «файл недоверенный →
  `isCustom = true`» поверх пользовательского `isArchived`). Verify:
  `./gradlew testDebugUnitTest --tests "*ImportBackupUseCaseTest*"`
  зелёный.
- [x] 2.2 Добавить в `ImportBackupUseCaseTest` кейс «обратная
  совместимость — старый файл без `isArchived` импортируется успешно»:
  файл v1 без поля `isArchived` → импорт `success`, упражнения получают
  `isArchived = false`. Verify: тот же прогон зелёный.
- [x] 2.3 Добавить в `ImportBackupUseCaseTest` кейс «`isArchived`
  существующего упражнения при дедупликации не меняется»: в каталоге
  активное упражнение, в файле то же имя с `isArchived = true` → после
  импорта существующее остаётся `isArchived = false`. Verify: тот же
  прогон зелёный.

## 3. Реализация: экспорт

- [x] 3.1 В `ExportBackupUseCase.buildBackupJson` добавить
  `.put("isArchived", exercise.isArchived)` в цепочку `JSONObject.put`
  для каждого элемента `exercises`. Verify: тесты 1.1 и 1.2 зелёные.

## 4. Реализация: импорт

- [x] 4.1 В `ImportBackupUseCase` добавить приватный хелпер
  `optionalBoolean(key: String): Boolean?` по образцу `optionalInt`
  (return `null` для `null`/`JSONObject.NULL`, `value` для `Boolean`,
  throw `invalidFile` для остальных типов). Verify: компиляция проекта
  (`./gradlew compileDebugKotlin`) без ошибок.
- [x] 4.2 В `ImportBackupUseCase.parseExercise` добавить чтение поля
  `isArchived = obj.optionalBoolean("isArchived") ?: false` в
  конструктор `Exercise`. Verify: тесты 2.1, 2.2, 2.3 зелёные.

## 5. Проверки

- [x] 5.1 Прогнать `make format && make lint` — без замечаний.
- [x] 5.2 Прогнать `make test` — все unit-тесты зелёные, в том числе
  новые кейсы 1.1–1.2 и 2.1–2.3.
