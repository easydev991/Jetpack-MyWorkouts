# Tasks

## 1. Строки и чистые функции (TDD)

- [x] 1.1 Добавить ресурс `a11y_show_details` (ru «Показать детали», en
  "Show details") в `values/strings.xml` и `values-en/strings.xml`;
  переиспользовать существующие `entry_confirm_close` и
  `entry_details_weight_suffix` без изменений. Проверка: `make build` —
  ресурсы компилируются без ошибок
- [x] 1.2 Написать падающие JVM-тесты `formatEntryDetailsValue` и
  `formatDetailDateTime` в `EntryValueFormatterTest.kt`: подходы+вес →
  «4 х 10 (+ 5 кг)», один подход без веса → «10», длительность → «3 х 20
  сек», суффикс `null` → без суффикса; дата с фиксированной зоной и локалью
  → «20 сентября 2025, 18:30» (ru) / "20 September 2025, 18:30" (en).
  Проверка: `make test` — новые тесты падают (red)
- [x] 1.3 Реализовать `formatEntryDetailsValue` и `formatDetailDateTime`
  в `EntryValueFormatter.kt` (паттерн `formatEntryValue`: локализованные
  строки — параметрами; дата — `DateTimeFormatter.ofPattern("d MMMM yyyy,
  HH:mm")`). Проверка: `make test` — тесты раздела зелёные (green)

## 2. Компонент карточки деталей

- [x] 2.1 Создать `ui/screens/main/EntryDetailsCard.kt`:
  `EntryDetailsCard(item, onClose)` — перенос `DetailsCompactDialogVariant`
  - `DetailComment` из `WorkoutDetailsPreview.kt` (design Decision 5);
  иконка/название `titleLarge`/значение `headlineMedium` `primary`/дата
  `bodySmall`/комментарий целиком в `surfaceVariant`. Проверка: сборка
  без ошибок, компоновка соответствует спек-требованию «Содержимое
  карточки деталей записи»
- [x] 2.2 Создать `EntryDetailsCardPreview.kt`: светлая + тёмная темы
  (`UI_MODE_NIGHT_YES`, `showBackground = true`, `locale = "ru"`), по
  одному превью на состояние с явным маппингом на демо-модели:
  `demoPullups` — все поля; `demoDips` — без комментария; `demoPlank` —
  минимум полей, запись на время (она же без веса). Отдельное превью
  «один подход без веса» не нужно — состояние формата значения, покрыто
  JVM-тестами форматтера (1.2). Плюс превью «карточка поверх списка со скримом»
  (бывший `DetailsDemoScreen`; демо-модели выносятся из удаляемого
  `WorkoutDetailsPreview.kt`). Удалить
  `WorkoutDetailsPreview.kt` целиком: вариант `DetailsCompactDialogVariant`,
  демо-фон `DetailsDemoScreen` и хелперы `formatDetailDateTime`,
  `detailValueText`, `detailValueWithWeightText`, `DetailComment`
  (перенесён в `EntryDetailsCard.kt` в 2.1).
  Проверка: превью рендерятся без ошибок компиляции

## 3. Интеграция в главный экран

- [x] 3.1 Расширить `MainScreenState` (`detailsEntryId: Long?`,
  `onEntryTap`, `onCloseDetails`) и `EntryListCallbacks`
  (`onItemTap: (EntryListItem) -> Unit`); добавить `onTap` в существующий
  `detectTapGestures` рядом с `onLongPress` и второе custom-действие
  «Показать детали» в `EntryListItemRow` (параметр `onOpenDetails`)
  (design Decision 4). Проверка: сборка, существующие androidTest главного
  экрана зелёные (long-press не сломан)
- [x] 3.2 В `MainScreen` завести `detailsEntryId` через
  `rememberSaveable`, `BackHandler(enabled = detailsEntryId != null)` →
  закрытие; прокинуть в `MainScreenState` (design Decision 2).
  Проверка: сборка; сохранение состояния при повороте — androidTest
  задачи 4.6
- [x] 3.3 В `MainScreenContent` после `Scaffold` добавить overlay
  (отдельная composable-функция ради detekt `LongMethod`): скрим 32% с
  тапом-закрытием поверх всего экрана, `EntryDetailsCard` по центру с
  боковыми 24dp; запись — `success.entries.firstOrNull { it.id ==
  detailsEntryId }`, `null` → не рендерить (design Decisions 1, 3).
  Проверка: ручной прогон на эмуляторе — карточка по референсу, FAB
  затемнён скримом
- [x] 3.4 Расширить `isSelected`-лямбду `EntryList`:
  `contextMenu?.item?.id == it.id || detailsEntryId == it.id`.
  Проверка: сборка, ручной прогон — строка выделена при открытых деталях,
  меню при этом не показывается

## 4. androidTest главного экрана

- [x] 4.1 Тест «тап по строке открывает детали»: карточка видна (значение
    «4 х 10 (+ 5 кг)»), строка имеет `SemanticsProperties.Selected`,
    контекстное меню отсутствует. Проверка: тест зелёный
- [x] 4.2 Тесты закрытия: крестик / тап по скриму / системный Back —
    после каждого карточка скрыта и `Selected` снят. Проверка: тесты
    зелёные
- [x] 4.3 Тесты содержимого и регресс: длинный комментарий отображается
    целиком (без обрезки), дата/время записи отображаются; long-press
    по-прежнему открывает меню (не детали). Проверка: тесты зелёные
- [x] 4.4 Тест a11y-действия: активация custom-действия «Показать
    детали» открывает карточку. Проверка: тест зелёный
- [x] 4.5 Тесты удаления/изменения при открытой карточке (Room
    in-memory + репозиторий, мутация БД на фоне открытого экрана):
    удаление записи закрывает карточку автоматически; изменение записи
    обновляет данные карточки, выделение строки сохраняется. Проверка:
    тесты зелёные
- [x] 4.6 Тест поворота при открытых деталях: смена ориентации экрана —
    карточка остаётся открытой с данными той же записи, выделение строки
    сохранено. Проверка: тест зелёный
- [x] 4.7 Тест пустого комментария: у записи без комментария
    (`null`/blank) блок комментария в карточке отсутствует. Проверка:
    тест зелёный
- [x] 4.8 Тест тапа при открытом контекстном меню: тап по другой строке
    закрывает только меню, карточка не открывается; повторный тап открывает
    карточку. Проверка: тест зелёный
- [x] 4.9 Тест a11y-действия при открытом контекстном меню: активация
    «Показать детали» на другой строке закрывает меню и открывает карточку;
    выделена одна строка. Проверка: тест зелёный

## 5. Финальная проверка

- [x] 5.1 Прогнать `make format && make lint && make test` — всё зелёное,
    новых detekt-предупреждений нет (лимит `maxIssues: 5` не поднимать)
- [x] 5.2 Прогнать `make android-test` на подключённом эмуляторе — все
    тесты зелёные (один прогон, отчёт `scripts/test_report.py` в выводе).
    Попутно починены два пре-существующих flaky-теста (корень: фокус
    текстового поля открывал мягкую клавиатуру, чьё окно съедало клики
    по нижней части формы — «Категория» под иконочной сеткой и кнопка
    «Сохранить» в bottomBar): `ExerciseCreateScreenTest::roundTrip_…`
    и `EntryEditArchivedExerciseTest::entryEditScreen_…` — закрытие
    клавиатуры после ввода текста + ретраи кликов до наблюдаемого
    эффекта
