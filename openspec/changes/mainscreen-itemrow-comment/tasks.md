# Tasks

## 1. JVM-тесты маппера (TDD — красные)

- [ ] 1.1 `app/src/test/.../data/db/MappersTest.kt`: тесты `toListItem`
  прокидывают `comment` (непустой / null). Проверка: `make test` —
  красная фаза: ошибка компиляции тест-сорс-сета (поле `comment`
  отсутствует); зелёная проверка — §3.5 после реализации.

## 2. androidTest DAO (TDD — красные)

- [ ] 2.1 `app/src/androidTest/.../data/db/ExerciseEntryDaoTest.kt`: тест —
  `observeEntries` возвращает `comment` в проекции (запись с комментарием
  и без). Проверка: тесты написаны, но не компилируются, пока поле
  `comment` отсутствует; запуск отложен до §6.1.

## 3. Прокидывание comment по слоям (зелёные)

- [ ] 3.1 `EntryListItemEntity`: поле `comment: String?` + KDoc. Проверка:
  компиляция.
- [ ] 3.2 `ExerciseEntryDao.observeEntries`: SQL-проекция добавляет
  `"e.comment AS comment"` перед `date`. Проверка: компиляция, Room-валидация.
- [ ] 3.3 `Mappers.toListItem()`: прокидывает `comment` в доменную модель.
- [ ] 3.4 `EntryListItem`: поле `comment: String? = null` (дефолт обязателен),
  KDoc класса пересмотрен — прежняя фраза «Без comment/createdAt — строке
  списка они не нужны (design Decision 2)» заменена: comment теперь в
  модели, про `createdAt` — сохраняется.
- [ ] 3.5 `make test` — один JVM-прогон после §3.1–§3.4: тесты §1.1
  зелёные, остальные JVM-тесты зелёные (фиксатуры компилируются без
  правок).

## 4. androidTest UI строки (TDD — красные)

- [ ] 4.1 Добавить в `MainScreenTest` androidTest
  `row_when_comment_then_shows_comment_text`: строка с комментарием
  показывает его текст (эталон — row-тесты веса в `MainScreenTest`,
  change `2026-09-05-entry-list-weight-display` §4.1–4.2). Проверка:
  тесты написаны до реализации §5.1; запуск — §6.1.
- [ ] 4.2 Добавить в `MainScreenTest` androidTest
  `row_when_no_comment_then_hides_comment_line`: запись без комментария
  и с blank-комментарием — текст комментария не находится, вид строки
  прежний (Selected-семантика не ломается). Проверка: как §4.1.

## 5. UI строки записи

- [ ] 5.1 `EntryListItemRow`: после блока веса — условный блок комментария:
  гард `!comment.isNullOrBlank()`, `Text` без ведущей метки (параметры —
  design Decision 4). Проверка: компиляция; androidTest §4.1–§4.2 зелёные
  (запуск — §6.1).
- [ ] 5.2 `EntryListItemRowPreview.kt`: новые `@Preview` (светлая + тёмная,
  `showBackground`, `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`) —
  короткий комментарий, длинный (обрезается в 2 строки с многоточием),
  запись без комментария; комментарий вместе с весом. Проверка: рендер
  превью в IDE, ключевые состояния видны.

## 6. Верификация

- [ ] 6.1 `make android-test` — единственный androidTest-прогон: тесты
  §2.1, §4.1, §4.2 зелёные, существующие androidTest не сломаны. Отчёт —
  `scripts/test_report.py` уже в make-цели.
- [ ] 6.2 `make format && make lint` — без новых замечаний.
- [ ] 6.3 `make test` — JVM-тесты зелёные (финальный прогон после всех
  правок).
