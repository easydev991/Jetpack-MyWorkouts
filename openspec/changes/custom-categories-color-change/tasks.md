# Tasks

> Соглашение о красной фазе для сигнатурных изменений (design Decision 5):
> смена сигнатуры `WorkoutDataRepository` требует правки интерфейса,
> реализации и фейков в одной задаче с тестами — иначе тест не
> компилируется. Красная фаза сохраняется поведенчески: временная
> реализация игнорирует новый параметр / бросает `error(...)`,
> assertions о новом поведении падают (прецедент — custom-categories 1.2).

## 1. Data-слой: addCategory с цветом и updateCategory

- [x] 1.1 Написать JVM-тесты `addCategory(name, colorArgb)` — красные:
  переданный `colorArgb` персистится как есть; пустое/blank имя
  отклоняется; дубль по `name.trim().lowercase()` отклоняется;
  `isCustom = true`, `isArchived = false`. В этой же задаче сменить
  сигнатуру в `WorkoutDataRepository` / `RoomWorkoutDataRepository` /
  всех фейках (JVM и androidTest) и вызовах (иначе не компилируется);
  create-ветку
  `CategoryCreateViewModel.save()` временно перевести на
  `addCategory(name, colorArgb = 0)` (минимальная правка для
  компиляции, полный переход VM — задача 2.2); реализация временно
  игнорирует `colorArgb` (старое тело с внутренней генерацией) —
  assertion «персистит переданный цвет» красное. Верификация: тест
  падает, проект компилируется
- [x] 1.2 Реализовать `addCategory(name, colorArgb)`: сохранять
  переданный цвет; удалить внутреннюю генерацию и ctor-property
  `colorGenerator` репозитория; `generateCategoryColor` сделать
  `internal` (для инжекта в VM, design Decision 1). В этой же задаче
  обновить `RoomWorkoutDataRepositoryCategoryTest` (иначе не
  компилируется): конструктор без `colorGenerator`, вызовы
  `addCategory(name, colorArgb = testColor)`, ассерт «Цвет — из
  colorGenerator» заменить на «переданный цвет сохраняется как есть».
  Верификация: тесты 1.1 зелёные, `make test` зелёный
- [x] 1.3 Написать JVM-тесты `updateCategory(id, newName, colorArgb)` —
  красные: одна транзакция обновляет имя и цвет; каскад
  `exercises.category` по старому имени (активные и архивные
  упражнения); смена только цвета НЕ трогает упражнения; стандартная
  категория → `StandardCategoryDeletionException`; пустое имя / дубль
  имени / несуществующий id → `Result.failure` с полным откатом. В этой
  же задаче: ввести `updateCategory` в интерфейс/реализацию/фейки (JVM
  и androidTest) с заглушкой `error("updateCategory не реализован")`,
  удалить `renameCategory` (вызовы в
  `RoomWorkoutDataRepositoryCategoryTest` перевести на
  `updateCategory`); вызов в `CategoryCreateViewModel.save()` временно
  перевести на `updateCategory(id, name, colorArgb = цвет оригинала)`
  (минимальная правка для компиляции, полный переход VM — задача 2.2).
  Верификация: тесты падают, проект компилируется
- [x] 1.4 Реализовать `updateCategory`: транзакция по образцу
  `renameCategory` (guard `isCustom`, проверки имени); DAO-метод
  `CategoryDao.updateName` заменить на `updateNameAndColor(id, name,
  colorArgb)`, вызываемый репозиторным `updateCategory`; каскад
  `exerciseDao.updateCategoryName` без изменений.
  Верификация: тесты 1.3 зелёные, `make test` зелёный
- [x] 1.5 Актуализировать KDoc под новые сигнатуры:
  `Category.colorArgb` («задел под будущую ручную настройку» → цвет
  выбирается на экране категории — пикер: стартовый + refresh — и
  передаётся в операцию готовым; ручной выбор из палитры вне scope);
  `WorkoutDataRepository.addCategory` (создание
  «с сгенерированным цветом» → «с переданным») и KDoc удалённого
  `renameCategory`; `RoomWorkoutDataRepository` — KDoc удалённого
  ctor-property `colorGenerator`, addCategory («цвет генерируется от
  цветов…» → сохранение переданного) и битые ссылки
  `[RoomWorkoutDataRepository.colorGenerator]` и
  `[CategoryDao.updateName]` (KDoc `ExerciseDao.updateCategoryName`).
  Верификация: `make lint` зелёный

## 2. ViewModel и UiState: цвет в состоянии

- [x] 2.1 Написать JVM-тесты `CategoryCreateViewModel` /
  `CategoryCreateUiState` — красные: create — начальная генерация при
  первой эмиссии категорий (stub-генератор → фикс. цвет в
  `colorArgb`); edit — предзаполнение `colorArgb` оригинала;
  `onRegenerateColorClick` — новый цвет от цветов `knownCategories`,
  сброс `isSaveError`; регенерация тем же значением →
  `hasChanges = false`, `hasUnsavedChanges = false`; edit —
  изменённый только цвет → `canSave = true`, `hasChanges = true`;
  create —
  регенерированный в другое значение цвет при пустом имени →
  `hasUnsavedChanges = true` (stub-генератор возвращает цвет, отличный
  от стартового);
  save: create → `addCategory(name, colorArgb)`, edit →
  `updateCategory(id, name, colorArgb)`. Верификация: тесты падают
- [x] 2.2 Реализовать: `CategoryCreateUiState` — поле `colorArgb:
  Int?`, формулы `canSave` / `hasChanges` / `hasUnsavedChanges`
  (design Decision 3); `CategoryCreateViewModel` —
  `categoryColorGenerator` default-параметр, стартовый цвет create при
  первой эмиссии, `onRegenerateColorClick`, `save` с цветом и
  guard-генерацией `?:`. Верификация: тесты 2.1 зелёные, `make test`
  зелёный

## 3. UI: пикер, строки, превью

- [x] 3.1 Добавить строки ru/en: `category_field_color` («Цвет» /
  "Color"), `category_color_regenerate` («Обновить цвет» / "Regenerate
  color"), `category_color_current` («Текущий цвет» / "Current color").
  Верификация: ключи в `values/` и `values-en/`, сборка без ошибок
- [x] 3.2 Реализовать `CategoryColorPicker` (Row: заголовок
  `weight(1f)` + IconButton `Icons.Default.Refresh` + кружок
  `CircleShape` c `Modifier.semantics { contentDescription = ... }`,
  `null` → плейсхолдер темы) и разместить в
  `CategoryCreateScreenContent` под полем «Название»;
  `CategoryCreateScreenState.onRegenerateColorClick` + обёртка.
  Превью: `CategoryColorPicker` — 2 состояния (цвет задан, `colorArgb ==
  null` — плейсхолдер), каждое светлая + тёмная; обновлённые превью
  экрана с кружком в create и edit (`*Preview.kt`, `locale = "ru"`).
  Верификация: превью-файлы компилируются, `make lint` без новых
  предупреждений
- [x] 3.3 Написать androidTest (Room in-memory, реальные
  `WorkoutDatabase` / `RoomWorkoutDataRepository`): пикер виден в
  create- и edit-режимах; refresh перерисовывает кружок; сохранение в
  edit-режиме обновляет в БД имя И цвет (updateCategory). Верификация:
  `make android-test` зелёный

## 4. Интеграционная проверка

- [x] 4.1 Полный прогон: `make format && make lint && make test`,
  затем `make android-test`; detekt без новых предупреждений (лимит
  `maxIssues: 5` не поднимается). Верификация: все команды зелёные
  (format/lint чистые, JVM 1074/1074, androidTest
  CategoryCreateScreenTest 6/6)

## 5. Архивация

- [ ] 5.1 При архивации поправить Purpose основной спеки
  `category-create-screen` (дельты Purpose не трогают): «форма с
  единственным полем „Название“» → форма = поле «Название» +
  `CategoryColorPicker` (create- и edit-режимы).
