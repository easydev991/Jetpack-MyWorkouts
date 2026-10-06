# Design

## Context

База — заархивированный change `custom-categories` (design Decision 3:
генерация цвета как private-функция репозитория). Текущее состояние:
`addCategory(name)` генерирует цвет внутри себя через injectable
`colorGenerator: (List<Int>, Random) -> Int = ::generateCategoryColor`
(internal ctor-property `RoomWorkoutDataRepository`); `pickCategoryHue` —
internal, `generateCategoryColor` — private, оба в
`RoomWorkoutDataRepository.kt`; JVM-тестом покрыт только чистый
`pickCategoryHue` (`CategoryColorGeneratorTest`) — `generateCategoryColor`
использует `android.graphics.Color` (HSV), в JVM-тестах платформенные
методы не замоканы (проект не включает `returnDefaultValues`).
`CategoryCreateViewModel` уже подписан на объединение `categories()` +
`archivedCategories()` (снимок `knownCategories` — все категории с
`colorArgb`), редактирование достижимо только для custom-категорий.
`renameCategory` — транзакция с каскадом `exercises.category` по имени.

## Goals / Non-Goals

**Goals:**

- Цвет виден в кружке ДО сохранения, в обоих режимах; регенерация —
  те же правила Decision 3 заархивированного change `custom-categories`,
  без дублирования алгоритма.
- Минимальный диф: переиспользование существующих injectable-паттернов
  и реактивного снимка категорий в VM.
- Одна транзакция сохранения имени и цвета; каскад ссылок упражнений
  по имени сохраняется дословно.

**Non-Goals:**

- Ручной выбор цвета из палитры; смена цвета стандартных категорий.
- Вынос генерации в domain-слой (отвергнуто — Decision 1).
- Аналитика нажатия refresh (не запрашивалась; CREATE/EDIT_CATEGORY на
  save остаются как есть).
- Смена схемы БД/формата бэкапа — `colorArgb` уже в схеме v4 и в
  `categories[]` бэкапа.

## Decisions

### Decision 1: Генерация для кружка — правила в data-слое, в VM инжектится функция-генератор

Развилка «метод репозитория vs use-case в domain» решается третьим,
наименьшим вариантом: правила и тесты остаются в data-слое, а VM
получает генератор через конструктор.

- `generateCategoryColor`: `private` → `internal` (тот же файл, рядом с
  уже internal `pickCategoryHue`); сигнатура не меняется
  (`(existingColors: List<Int>, random: Random) -> Int`).
- `CategoryCreateViewModel` принимает
  `categoryColorGenerator: (List<Int>) -> Int =
  { generateCategoryColor(it, Random.Default) }` — default-параметр,
  поэтому фабрика в `RootScreen` НЕ меняется (manual DI без Hilt:
  default-параметры — идиоматичный способ впрыснуть зависимость только
  в тестах).
- VM вызывает генератор синхронно от цветов `knownCategories` (все
  активные + архивные — тот же вход, что сегодня читает `addCategory`
  из DAO). В edit-режиме редактируемая категория остаётся во входном
  списке — новый цвет отводится от её текущего hue; возврат равного
  значения — edge case по значению (спека).
- `onRegenerateColorClick()` — единый колбэк регенерации; сбрасывает
  `isSaveError = false` (паттерн `onXChange`).

*Почему не (a) метод интерфейса `nextCategoryColor()`*: +1 метод
`WorkoutDataRepository` → обязательный override во всех ~13 фейках
(androidTest `EditFakeRepository`, `FakeRepository` × N, JVM-фейки) —
широкий механический диф; suspend-запрос к БД за списком, который у VM
уже есть реактивно; асинхронность без нужды (кружок обновлялся бы с
задержкой корутины).

*Почему не (b) use-case в domain*: `generateCategoryColor` считает HSV
через `android.graphics.Color` — платформенный API; в domain-слое он
ломает JVM-тестируемость (не замокан без `returnDefaultValues`),
альтернатива — переписать HSV-конверсию чистым Котлином (~40 строк
дублирования платформы). Правило репо: «слои добавляй по мере фич»;
потребитель генерации один (пикер) — use-case-файл ради одной функции
не заводим. `pickCategoryHue`-тесты не переезжают.

*Injectable random для тестов*: сохраняется в data-слое (сигнатура
генератора принимает `Random`); VM-тесты инжектят одноаргументную
lambda-заглушку и от random не зависят.

*Сознательная связь ui→data*: конструктор VM получает ссылку на
функцию из файла data-слоя (`RoomWorkoutDataRepository.kt`) —
осознанное временное исключение из слоёв, аналогично явным отметкам в
коде (прецедент — `@Suppress("TooManyFunctions")` с KDoc в том же
файле). Потребитель генератора один; при появлении второго — вынести
генератор из data-файла (use-case в domain или общий модуль).

### Decision 2: Контракты репозитория — цвет извне, update вместо rename

- `addCategory(name: String, colorArgb: Int): Result<Category>` —
  проверки имени дословно как сейчас (trim, дубль по
  `name.trim().lowercase()`); внутренняя генерация и ctor-property
  `colorGenerator` репозитория удаляются (единственный потребитель —
  `addCategory`).
- `updateCategory(id: Long, newName: String, colorArgb: Int):
  Result<Unit>` заменяет `renameCategory` — та же транзакция:
  guard `isCustom` → `StandardCategoryDeletionException`, проверки
  имени; DAO-метод `CategoryDao.updateName(id, name)` заменяется на
  `updateNameAndColor(id, name, colorArgb)` (других потребителей у
  `updateName` нет — метод заменяется, а не добавляется рядом), его
  и вызывает репозиторный `updateCategory`; каскад
  `exerciseDao.updateCategoryName(old, new)` сохраняется безусловно
  (при неизменном имени — практический no-op).
- Смена цвета ссылки упражнений не касается: упражнения хранят только
  имя категории.

### Decision 3: UiState — только colorArgb, один флаг hasChanges

`CategoryCreateUiState` расширяется единственным полем `colorArgb: Int?`
(`null` — не сгенерирован/не загружен → кружок-плейсхолдер цветом темы).
Отдельный флаг изменения цвета не вводится: `hasChanges` — единый флаг
относительно стартовой линии, которую VM хранит приватно (имя: blank в
create / оригинал в edit; цвет: первый сгенерированный в create /
исходный `colorArgb` в edit; сравнение строго по значению) —
`hasChanges` = изменение имени после `trim()` ИЛИ цвета. `canSave` не
меняется — терм `(!isEditMode || hasChanges)` в create истинен по
построению; `hasUnsavedChanges == hasChanges` в обоих режимах. По
сравнению с вариантом с отдельным флагом — на одно поле UiState,
сценарий и ассерт меньше, поведение то же.

- create: начальный цвет генерируется при первой эмиссии потока
  категорий в `init` (Room Flow эмитит мгновенно — до интеракции
  пользователя); VM запоминает его как стартовую линию цвета.
- Защита в `save()`: `state.colorArgb ?: categoryColorGenerator(...)` —
  если имя введено раньше эмиссии (практически исключено), цвет
  генерируется на месте.

### Decision 4: CategoryColorPicker — компонент рядом с экраном

`CategoryColorPicker(colorArgb: Int?, onRegenerateColorClick: () -> Unit,
modifier)` в `CategoryCreateScreen.kt`: `Row` → `Text(«Цвет»)` с
`Modifier.weight(1f)` + вложенный `Row`: `IconButton(Icons.Default.Refresh)`
(стандартная иконка material-icons, без новой зависимости) и
кружок `Box(size = 24.dp, CircleShape, background = colorArgb,
Modifier.semantics { contentDescription = ... })` (у `Box` нет
параметра contentDescription — доступность через `semantics {}`,
как у heatmap в `ActivityScreen`).
`null` → заливка `MaterialTheme.colorScheme.outlineVariant`. Размещение
— в `CategoryCreateScreenContent` под `OutlinedTextField` (Column уже
`spacedBy(16.dp)`). `CategoryCreateScreenState` получает
`onRegenerateColorClick`. Превью — в существующем
`CategoryCreateScreenPreview.kt`: пикер в светлой/тёмной теме,
обновлённые create/edit-превью экрана с кружком. Строки:
`category_field_color` («Цвет»/"Color"), `category_color_regenerate`
(«Обновить цвет»/"Regenerate color"), `category_color_current`
(«Текущий цвет»/"Current color") — последние две как contentDescription.

### Decision 5: Тест-стратегия

- Смена сигнатур `WorkoutDataRepository` ломает компиляцию всех фейков
  сразу — «красная» фаза JVM-тестов репозитория вырождается в
  компиляционную. Осознанное исключение из «тест раньше реализации»
  (прецедент — задача 1.2 custom-categories): тесты и сигнатуры
  правятся в одной задаче, поведение фиксируется новыми падающими
  assertions до правки логики.
- VM: стандартный JVM-сетап (`MainDispatcherRule` + fake-репозиторий +
  stub-генератор, детерминированные цвета).
- androidTest: Room in-memory, реальные `WorkoutDatabase` /
  `RoomWorkoutDataRepository` — пикер виден, refresh меняет кружок,
  edit-сохранение пишет оба поля.

## Risks / Trade-offs

- [~13 фейков `WorkoutDataRepository` (JVM + androidTest) не
  компилируются после смены сигнатур] → механический override в задаче
  обновления фейков; тестирующая ценность сохраняется (фиксирующие
  assertions добавляются).
- [Регенерация вернула тот же цвет при малом каталоге] → зафиксировано
  в спеке `category-data`: совпадение по значению — не изменение.
- [Hue-кольцо исчерпано при >12 категорий] → существующий канон
  `category-data` (повторы допустимы); ручная палитра — вне скопа.
- [Гонка «имя введено до первой эмиссии» → `colorArgb == null`] →
  guard-генерация в `save()` (Decision 3).
- [Detekt `maxIssues: 5`] → новые предупреждения гасятся в ходе задач;
  `make format && make lint && make test` зелёные перед каждым коммитом.

## Migration Plan

Схема БД и формат бэкапа не меняются (`colorArgb` — в схеме v4 и в
`categories[]`). Откат — возврат предыдущего коммита до релиза.
