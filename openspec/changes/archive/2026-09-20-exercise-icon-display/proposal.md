# Proposal

## Why

Набор из 19 пар иконок (светлая/тёмная), таблица `ExerciseIcons` со
стабильными номерами и поле `Exercise.iconNumber` уже существуют — но UI
их не использует, а все 13 стандартных упражнений имеют
`iconNumber = null`. Пользователь видит только текстовые строки без
визуальных якорей, хотя вся инфраструктура для отображения готова.

## What Changes

- Проставить `iconNumber` всем 13 стандартным упражнениям в
  `StandardExercises.kt` (до change — `null` у всего набора). Планка
  получает `PUSHUPS` (15) — осознанный выбор: отдельной иконки plank нет,
  планка попадает в группу push/core-статики; при появлении иконки —
  пересмотреть.
- Расширить проекцию списка записей: `EntryListItem` получает
  `iconNumber: Int? = null` (дефолт сохраняет существующие фикстуры
  компилируемыми), `EntryListItemEntity` и SELECT в DAO джойнят
  `icon_number` из таблицы `exercises`, `Mappers.toListItem()`
  пробрасывает значение.
- Показывать иконку упражнения 24dp в трёх местах UI:
  1. строка записи на главном экране (`EntryListItemRow`, слева от названия);
  2. строка списка упражнений (`ExerciseListItemRow`, слева от названия);
  3. опции выпадающего списка выбора упражнения на экране записи
     (`DropdownMenuItem.leadingIcon`).
- Правила рендера во всех трёх местах: `ExerciseIcons.resolve()` →
  светлый/тёмный вариант по `isSystemInDarkTheme()`, без tint,
  `contentDescription = null` (название упражнения уже рядом);
  `resolve(null)` и неизвестный номер → иконка не рисуется (тихий
  fallback, без плейсхолдера).

## Capabilities

### New Capabilities

(нет — решение: capability `exercise-display` не создаётся. Отображение
иконки — построчное поведение, владельцем которого являются specs
конкретных экранов; общий код выбора (`ExerciseIcons.resolve`, пара
light/dark) уже существует в `ui/icons/` и не является новым поведением.
Отдельный capability дублировал бы практически пустую спецификацию.)

### Modified Capabilities

- `workout-data`: требование «Опциональный номер иконки упражнения» —
  prepopulate теперь назначает номера иконок стандартным упражнениям
  (раньше весь набор — `null`); требование «Реактивный список записей» —
  элемент списка включает `iconNumber` упражнения (JOIN).
- `workouts-main-screen`: новое требование — строка записи показывает
  иконку упражнения слева от названия.
- `exercises-screen`: новое требование — строка списка упражнений
  показывает иконку слева от названия.
- `workout-entry-screen`: новое требование — опции выпадающего списка
  выбора упражнения показывают иконку как `leadingIcon`.

## Impact

- Данные: `data/db/StandardExercises.kt` (назначение номеров),
  `data/db/EntryListItemEntity.kt`, `data/db/Mappers.kt` (`toListItem`),
  SELECT-запрос проекции списка записей в `ExerciseEntryDao` (JOIN
  `icon_number`). Схема БД НЕ меняется — колонка `exercises.icon_number`
  уже в schema v3, миграция не нужна.
- Домен: `domain/model/EntryListItem.kt` — новое поле `iconNumber`.
- UI: `ui/screens/main/EntryListItemRow.kt`,
  `ui/screens/exercises/ExercisesScreen.kt` (включая переиспользование
  в `ArchivedExercisesScreen.kt` — архивный экран получает иконку
  автоматически по контракту «Переиспользование ExerciseListItemRow»),
  `ui/screens/entry/EntryCreateFields.kt`, `ui/icons/` — новый общий
  internal `@Composable ExerciseIcon` (детали реализации — design
  Decision 4) + фикстуры превью.
- Тесты: новый `ExerciseIconsTest` (JVM), `MappersTest` (маппинг
  `toListItem` сохраняет `iconNumber`), androidTest `WorkoutDatabaseTest` (prepopulate теперь с
  номерами — обновить утверждение), `RoomWorkoutDataRepositoryTest`
  (иконка читается JOIN-ом), UI-тесты трёх экранов.
- Risks: сигнатура `EntryListItem` расширяется — ручные фикстуры в
  превью и androidTest ревизуются (дефолт `null` смягчает). 7 drawable
  (BARBELL, BARBELL_1..4, DUMBBELLS, DUMBBELLS_1) остаются без
  стандартного упражнения — by design, для будущих кастомных записей.
