# Data Layer

Data layer тесты — для mapper, validator, JSON-сборки/парсинга.
Эти классы не имеют ViewModel-специфики, поэтому проще.

## Mapper (entity ↔ domain)

`MappersTest` — round-trip для `Exercise` ↔ `ExerciseEntity` и
`EntryListItemEntity` → `EntryListItem`. Категория хранится строкой
(`name` enum-значения) — `ExerciseCategory.valueOf(category)`.

Канонический пример — `EXAMPLE.md` §2 (полный класс с пакетом и импортами).

Правила:
- `toDomain`/`toEntity` — `internal` extension-функции в `data/db/Mappers.kt`.
  В тесте доступны, потому что тесты в том же модуле.
- Round-trip — отдельный тест, проверяет, что `toEntity` + `toDomain`
  = identity.
- Разные «нулевые» значения (`null` vs `0.0`) сохраняются раздельно —
  это семантика данных, не округление.

## Validator

`EntryValidator` — `object` с pure-методом `validate(entry, measuredBy)`.

Канонический пример — `EXAMPLE.md` §1 (полный класс с пакетом и импортами,
`baseEntry` + два `@ParameterizedTest` + `@ValueSource`).

Особенности:
- `EntryValidator.validate(...)` возвращает `Result<Unit>`.
- Ошибка типизирована через `EntryValidationException(error: EntryValidationError)`.
- `@ParameterizedTest` + `@ValueSource(ints = [0, -1, -100])` для граничных
  значений — повторяющаяся форма тестов.
- `baseEntry(...)` — helper для построения валидной записи с дефолтами;
  тесты меняют только то поле, которое проверяют.

## JSON-сборка и парсинг (Export/ImportBackupUseCase)

В проекте нет файлов `app/src/test/resources/*.json` — JSON
подаётся/получается строкой в коде теста. Это держит тесты JVM-only
(не нужен Android Context, не нужен SAF).

### Сборка: `buildBackupJson` (ExportBackupUseCaseTest)

`buildBackupJson` — pure: принимает списки, возвращает строку. Тесты
проверяют **формат** файла, не use-case целиком — проще и быстрее, чем
гонять через `ContentResolver`. Канонический пример — `EXAMPLE.md` §4.1.

Особенности:
- `buildBackupJson` — `internal` функция в `ExportBackupUseCase.kt`.
- `null`-поля сериализуются явно (`JSONObject.NULL`) — стабильная
  структура файла.

### Парсинг: `parseBackupJson` через `ImportBackupUseCase.invoke`

Канонический пример (полный) — `EXAMPLE.md` §4.2. Использует
`mockk<Context>`/`mockk<ContentResolver>` + `ByteArrayInputStream`,
чтобы подать JSON-строку без Android-зависимостей.

Особенности:
- `slot` + `capture` — для перехвата аргументов `importData`.
- `plankFile` — `String.trimIndent()` с фигурными скобками JSON, как
  в `ImportBackupUseCaseTest.kt`. Не нужен `app/src/test/resources/`.
- `@ParameterizedTest` + `@MethodSource("invalidFiles")` — для
  таблицы невалидных файлов (`"не JSON"`, `version != 1`, неизвестная
  `category`, и т.п.).

## Чеклист для data layer тестов

- [ ] Mapper: round-trip тест (entity → domain → entity → equals)
- [ ] Validator: каждое правило валидации как отдельный тест
      (часто через `@ParameterizedTest` + `@ValueSource`)
- [ ] JSON-сборка: тест на формат (поля, `version`, связь по имени)
      + edge-кейсы (пустые данные, `null`-поля сериализуются явно)
- [ ] JSON-парсинг: тест на merge (новое упражнение → `isCustom = true`)
      + таблица невалидных файлов через `@ParameterizedTest`
- [ ] Подача JSON-строкой (через `ByteArrayInputStream`), не из
      `app/src/test/resources/` — JVM-only, без Android-зависимостей
- [ ] Не использовать `Context` напрямую — нужен `mockk<Context>()`
      или `mockk<ContentResolver>()`, иначе это не unit