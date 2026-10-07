# Proposal

## Why

Цвет кастомной категории выбирается алгоритмом один раз при создании и
больше никогда не меняется: пользователь не может повлиять на цвет, даже
если сгенерированный оттенок не нравится или плохо различим на фоне
остальных. `colorArgb` уже хранится, читается списками и статистикой —
не хватает только механизма выбора: показать цвет до сохранения и дать
регенерацию по желанию.

## What Changes

- **CategoryColorPicker**: новый composable внутри `CategoryCreateScreen.kt`
  (пакет `ui/screens/exercises/`): заголовок «Цвет» + `IconButton` со стандартной
  иконкой `Icons.Default.Refresh` + кружок текущего цвета (`CircleShape`,
  заливка `colorArgb`); размещается в `CategoryCreateScreenContent` под
  полем «Название». Работает в обоих режимах экрана (create/edit);
  строки ru/en, включая contentDescription для доступности.
- **Поведение цвета**: первое создание — в кружке заранее
  сгенерированный цвет по правилам Decision 3 заархивированного change
  `custom-categories` (максимальный разрыв hue-кольца, `MIN_HUE_OFFSET`
  15°, фиксированные saturation/value); нажатие «refresh» — регенерация
  теми же правилами; в edit исходный цвет — текущий `colorArgb`
  категории. Регенерация, вернувшая значение, равное текущему по
  `colorArgb`, не считается изменением (сравнение строго по значению).
- **Сохранение**: `renameCategory(id, newName)` заменяется на
  `updateCategory(id, newName, colorArgb)` — одна транзакция сохраняет
  имя и цвет; каскад переименования ссылок упражнений (по имени)
  сохраняется, смена цвета ссылки упражнений не трогает; guard
  стандартной категории (`StandardCategoryDeletionException`) как в
  rename. **BREAKING** (внутренний API): `addCategory(name)` меняет
  сигнатуру на `addCategory(name, colorArgb)` — цвет приходит готовым
  из формы, генерация больше не внутри репозитория.
- **UiState**: `hasChanges` / `canSave` / `hasUnsavedChanges`
  `CategoryCreateUiState` расширяются на цвет: в edit «Сохранить»
  доступна при изменении имени ИЛИ цвета; смена цвета — несохранённое
  изменение → диалог подтверждения закрытия
  (`ExerciseCreateCloseConfirmDialog`). В create условие прежнее
  (непустое уникальное имя).
- Превью: `CategoryColorPicker` (светлая + тёмная), обновлённые превью
  `CategoryCreateScreenContent` с кружком в create и edit.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `category-create-screen`: состав формы (поле + пикер), ViewModel и
  UiState (`colorArgb` в состоянии, регенерация, сравнение по значению),
  условие доступности «Сохранить» (имя ИЛИ цвет), диалог закрытия
  учитывает цвет; ADDED-требование по самому `CategoryColorPicker`
  (верстка, refresh, оба режима, превью).
- `category-data`: генерация при первом создании по правилам Decision 3
  заархивированного change `custom-categories` и регенерация по запросу
  теми же правилами (генерация переезжает из
  операции создания в механизм выбора цвета), персистенция цвета при
  создании и обновлении; переименование становится обновлением (имя +
  цвет одной транзакцией, цвет не касается упражнений).
- `workout-data`: сигнатуры операций репозитория — `addCategory(name,
  colorArgb)`, `renameCategory` → `updateCategory(id, newName,
  colorArgb)` (одна транзакция, guard стандартной категории, каскад
  ссылок по имени сохраняется).

## Impact

- **Data**: `WorkoutDataRepository` / `RoomWorkoutDataRepository` —
  `addCategory` принимает цвет, `renameCategory` заменён на
  `updateCategory` (DAO: `updateName` → обновление имени и цвета);
  генерация цвета (`generateCategoryColor` / `pickCategoryHue`) больше
  не вызывается из `addCategory` — правила остаются в data-слое и
  переиспользуются для пикера (механизм — design.md).
- **UI**: `CategoryCreateScreen.kt` (+ новый `CategoryColorPicker`),
  `CategoryCreateScreenPreview.kt`, `CategoryCreateUiState`,
  `CategoryCreateViewModel`; навигация и `RootScreen` — без изменений
  маршрутов.
- **Локализация**: 3 новых ключа ru/en — заголовок «Цвет» и
  contentDescription кнопки refresh и кружка.
- **Тесты**: JVM — репозиторий (новые сигнатуры, транзакция updateCategory,
  guard), ViewModel (начальная генерация, refresh, canSave по цвету,
  hasUnsavedChanges, save с цветом); androidTest — пикер виден, refresh
  меняет кружок, сохранение обоих полей в edit (Room in-memory); все
  фейковые реализации `WorkoutDataRepository` (JVM + androidTest) — под
  новые сигнатуры.
- **Вне scope**: ручной выбор цвета из палитры, смена цвета стандартных
  категорий, отображение цвета в остальных местах каталога (уже читают
  `colorArgb`), формат бэкапа (`colorArgb` уже экспортируется/импортируется).
