# Proposal

## Why

Запись о выполнении уже хранит комментарий (`exercise_entries.comment`,
вводится на экране создания записи), но главный экран его не показывает:
пользователь видит «Подтягивания 4 х 10», не видя заметку к выполнению
(«сразу после сна»), не открывая запись. Отображение идёт по той же цепочке
DAO-проекция → entity → маппер → domain-модель → UI, что и дополнительный
вес (см. заархивированный change `2026-09-05-entry-list-weight-display`).

## What Changes

- SQL-проекция `ExerciseEntryDao.observeEntries` добавляет `e.comment AS
  comment` в `EntryListItemEntity`. Колонка называется `comment` (без
  `@ColumnInfo`). Схема БД не меняется — проекция не `@Entity`, миграция
  не нужна.
- `EntryListItemEntity`: новое поле `comment: String?`.
- `Mappers.toListItem()`: прокидывает `comment` в доменную модель.
- `EntryListItem`: новое поле `comment: String? = null` (дефолт обязателен —
  около 28 call-site'ов с именованными аргументами и фиксатурами
  тестов/превью должны компилироваться как раньше). KDoc класса пересмотрен:
  прежняя формулировка «Без comment/createdAt — строке списка они не нужны
  (design Decision 2)» больше не верна — comment теперь нужен.
- `EntryListItemRow`: после блока веса — условный блок комментария:
  гард `!comment.isNullOrBlank()` (обоснование — design Decision 1);
  `Text` с `style = MaterialTheme.typography.bodyMedium`,
  `color = MaterialTheme.colorScheme.onSurfaceVariant`, `maxLines = 2`,
  `overflow = TextOverflow.Ellipsis`; текст — сам комментарий без метки
  слева (в отличие от строки веса). Комментарий показывается независимо
  от наличия веса: у записи без веса он встаёт сразу под строкой
  «название — значение».
- Превью `EntryListItemRowPreview.kt` расширены новыми `@Preview`
  (светлая + тёмная, `showBackground`, `locale = "ru"`, обёртка в
  `JetpackMyWorkoutsTheme`): короткий комментарий, длинный (обрезается
  в 2 строки с многоточием), запись без комментария; комментарий вместе
  с весом.
- Тесты (TDD — задачи «тест» раньше задач «реализация»): JVM
  `MappersTest` (toListItem прокидывает comment: непустой / null);
  androidTest `ExerciseEntryDaoTest` (observeEntries возвращает comment);
  androidTest UI в `MainScreenTest` (строка с комментарием показывает
  текст; без комментария/с blank — вид строки прежний). UI-тест на
  обрезку (эллипсис) не пишется — состояние покрывается превью
  (design Decision 5).

## Capabilities

### New Capabilities

(нет)

### Modified Capabilities

- `workouts-main-screen`: новое требование «Комментарий в строке записи» —
  отображение комментария записи под строкой веса (или под «название —
  значение», если веса нет), `bodyMedium` / `onSurfaceVariant`, максимум
  2 строки с многоточием; пустой (null/blank) комментарий не отображается.

## Impact

- `app/src/main/java/com/myworkouts/data/db/ExerciseEntryDao.kt`
  (SQL-проекция), `EntryListItemEntity.kt`, `Mappers.kt` (`toListItem`).
- `app/src/main/java/com/myworkouts/domain/model/EntryListItem.kt`.
- `app/src/main/java/com/myworkouts/ui/screens/main/EntryListItemRow.kt`,
  `EntryListItemRowPreview.kt`.
- Тесты: `app/src/test/.../data/db/MappersTest.kt`;
  `app/src/androidTest/.../data/db/ExerciseEntryDaoTest.kt`, UI-тест в
  `MainScreenTest`.
- Схема БД, версия `WorkoutDatabase`, экран создания записи — не
  затрагиваются.
- Строковые ресурсы не добавляются (комментарий выводится как есть, метки
  нет).
