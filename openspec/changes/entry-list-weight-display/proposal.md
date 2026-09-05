## Why

Запись о выполнении уже хранит дополнительный вес (`exercise_entries.weight_kg`, вводится на экране создания записи), но главный экран его не показывает: в списке видно только «Подтягивания 4 х 10» — с каким весом выполнялось упражнение, неизвестно, не открывая запись.

## What Changes

- Проекция `observeEntries` (DAO) добавляет `weight_kg` в `EntryListItemEntity`; схема БД не меняется — проекция не `@Entity`, миграция не нужна.
- Доменная модель `EntryListItem` и маппер `toListItem()` добавляют `weightKg: Double?`.
- `EntryListItemRow` становится `Column` из двух строк: первая — текущая (название упражнения слева, «4 х 10» справа) с `FontWeight.Medium` у обоих текстов, вторая — «Дополнительный вес» слева и значение справа (например «5 кг»).
- Строка веса показывается только когда у записи есть доп. вес; без него UI идентичен текущему.
- Формат значения веса: целые — без дробной части («5 кг», не «5.0 кг»), дробные — с дробной частью («2.5 кг»).
- Новые строковые ресурсы ru/en: метка строки веса и формат значения с единицей.
- Тесты: JVM (маппер, форматтер) и androidTest (репозиторий отдаёт `weightKg`, UI показывает/скрывает строку веса).

## Capabilities

### New Capabilities

_(нет)_

### Modified Capabilities

- `workouts-main-screen`: новое требование «Дополнительный вес в строке записи» — отображение веса второй строкой в `EntryListItemRow`, формат целых/дробных значений, скрытие строки при отсутствии веса.

## Impact

- `app/src/main/java/com/myworkouts/data/db/`: `ExerciseEntryDao.observeEntries` (SQL-проекция), `EntryListItemEntity` (+`weightKg`), `Mappers.toListItem()`.
- `app/src/main/java/com/myworkouts/domain/model/EntryListItem.kt` (+`weightKg`).
- `app/src/main/java/com/myworkouts/ui/screens/main/`: `EntryListItemRow` (Column с двумя Row), `EntryValueFormatter` (новый `formatWeightKg`), новый файл `EntryListItemRowPreview.kt` с превью строк с весом/без. Существующий `MainScreenPreview.kt` не меняется.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` — 2 новые строки.
- Тесты: `app/src/test/.../MappersTest.kt`, `EntryValueFormatterTest.kt`; `app/src/androidTest/.../RoomWorkoutDataRepositoryTest.kt`, `MainScreenTest.kt`.
- Схема БД и версия `WorkoutDatabase` не меняются; экран создания записи не затрагивается.
