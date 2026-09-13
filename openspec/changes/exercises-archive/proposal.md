# Proposal: exercises-archive

## Why

Каталог упражнений сейчас — только для чтения или удаления: пользователь
не может «спрятать» ненужное упражнение из каталога без полного удаления.
Удаление опасно из-за FK-нарушения (на упражнение ссылаются записи) и
по дизайну доступно только для кастомных упражнений — стандартные нельзя
ни удалить, ни редактировать. У пользователя нет «мягкого» способа убрать
стандартное упражнение (например, «Бег», если он не бегает) из видимости,
не потеряв при этом стандартный набор. Нужен механизм «мягкой» архивации:
упражнение остаётся в БД, пропадает из каталога, но его можно вернуть.

## What Changes

- Долгое нажатие на строке упражнения (`isCustom == true` и
  `isCustom == false`) открывает контекстное меню. Для кастомного
  упражнения меню содержит три пункта: «Изменить»,
  «Архивировать», «Удалить». Для стандартного упражнения меню
  содержит один пункт: «Архивировать» (контракт архивного
  `edit-delete-exercise` Decision 4 фиксирует «нет меню для
  удаления/редактирования стандартных» — для архивации делаем
  исключение осознанно).
- Тап «Архивировать» открывает `AlertDialog` подтверждения
  («Архивировать упражнение?»). При подтверждении вызывается
  `repository.setArchived(item.id, true)`. Алерт использует обычный
  цвет confirm-кнопки (`ButtonDefaults.textButtonColors()`), не
  `colorScheme.error`, как у `ExerciseDeleteDialog` — это не
  необратимая операция.
- В `TopAppBar` `ExercisesScreen` появляется `IconButton` с иконкой
  `Icons.Filled.Archive` и `contentDescription =
  exercises_archive_button`, видимый только когда
  `archivedCount > 0`. Тап инициирует навигацию на
  `Screen.ArchivedExercises.route`.
- Новый экран `ArchivedExercisesScreen` с `TopAppBar` (заголовок
  `archived_exercises_title`, навигационная стрелка «назад») и
  `LazyColumn` архивных упражнений. Переиспользует
  `ExerciseListItemRow`. Long-press меню в архивном списке содержит
  единственный пункт «Вернуть» (`Icons.Filled.Unarchive`), тап
  вызывает `repository.setArchived(item.id, false)` — реактивный
  список архива обновляется. При пустом архиве — заглушка
  `archived_exercises_empty`. Сортировка — та же
  `is_custom DESC, created_at DESC, name COLLATE NOCASE`, что и на
  `ExercisesScreen`.
- На `EntryCreateScreen` в выпадающем списке выбора упражнения
  фильтруются архивные (`isArchived == false`). Если выбранное
  упражнение оказывается в архиве между открытием экрана и
  сохранением (race), X продолжает отображаться в поле выбора как
  текущее значение формы (UI не падает, выбор сохраняется в
  `selectedExercise`); список опций при этом реактивно обновляется и
  X из него исключается. Реализация — фильтрация в
  `repository.exercises(query)` (внутренне `WHERE is_archived = 0`).
  Это автоматически покрывает `ExercisesViewModel`,
  `EntryCreateViewModel`, `AppDataViewModel`.
- В `WorkoutDatabase`: `ExerciseEntity` получает
  `@ColumnInfo(name = "is_archived", defaultValue = "0") val
  isArchived: Boolean = false`. Механика изменения схемы
  (`version` БД, `fallbackToDestructiveMigration`) — канон в
  design Decision 1.
- Новые методы репозитория: `setArchived(id, isArchived): Result<Unit>`
  (покрывает оба направления — архивация и возврат),
  `archivedCount(): Flow<Int>` (для реактивной кнопки в TopAppBar),
  `archivedExercises(): Flow<List<Exercise>>` (для экрана архива).
  Метод `getAllExercises()` возвращает все упражнения включая
  архивные (для импорта/экспорта и восстановления).

## Capabilities

### New Capabilities

- `archived-exercises-screen`: новый экран для просмотра и
  восстановления архивных упражнений. Не участвует в нижней
  навигации. Достигается через `Screen.ArchivedExercises.route`
  (паттерн `Screen.Appearance`).

### Modified Capabilities

- `exercises-screen`: расширенное long-press меню для кастомных
  (добавлен пункт «Архивировать») и стандартных (новое меню с
  единственным пунктом «Архивировать»); новый алерт
  подтверждения архивации (confirm обычным цветом, не error);
  кнопка архива в `TopAppBar` (видима при `archivedCount > 0`,
  навигация на `Screen.ArchivedExercises.route`).
- `workout-data`: поле `isArchived: Boolean = false` на `Exercise`
  и `ExerciseEntity`; новые методы `setArchived(id, isArchived)`,
  `archivedCount()`, `archivedExercises()`; `exercises(query)`
  возвращает только не-архивные (зафиксировать явно);
  `getAllExercises()` возвращает все включая архивные (зафиксировать
  явно). Изменение схемы БД — канон в design Decision 1.
- `workout-entry-screen`: в выпадающем списке выбора упражнения на
  `EntryCreateScreen` показываются только не-архивные упражнения в
  обоих режимах (create/edit); выбранное упражнение, оказавшееся в
  архиве между открытием и сохранением, продолжает отображаться как
  текущий выбор (UI не падает).
- `non-tab-screen-navigation`: новый `Screen.ArchivedExercises` —
  `object ArchivedExercises : Screen(route = "archived_exercises")`,
  паттерн `Screen.Appearance`. Не входит в `BottomTabs`. Bottom-bar
  скрыт (контракт по образцу `Appearance`).

## Impact

- `app/src/main/java/com/myworkouts/data/db/ExerciseEntity.kt` —
  новое поле `isArchived: Boolean = false` с
  `@ColumnInfo(name = "is_archived", defaultValue = "0")`.
- `app/src/main/java/com/myworkouts/data/db/WorkoutDatabase.kt` —
  `version` БД поднимается с `1` до `2` (механика и обоснование — в
  design Decision 1; здесь только file-change).
- `app/src/main/java/com/myworkouts/domain/model/Exercise.kt` — поле
  `isArchived: Boolean = false` в data class.
- `app/src/main/java/com/myworkouts/data/db/Mappers.kt` — проброс
  `isArchived` в обе стороны (`toDomain`, `toEntity`).
- `app/src/main/java/com/myworkouts/data/db/ExerciseDao.kt` —
  фильтрация `WHERE is_archived = 0` в `observeExercises`;
  новые методы `observeArchivedExercises(): Flow<List<ExerciseEntity>>`,
  `observeArchivedCount(): Flow<Int>`.
- `app/src/main/java/com/myworkouts/data/repository/WorkoutDataRepository.kt`
  + `RoomWorkoutDataRepository.kt` — `setArchived(id, isArchived)`;
  `archivedCount()` (через DAO); `archivedExercises()` (через DAO);
  `exercises(query)` теперь фильтрует `is_archived = 0` в SQL.
- `app/src/main/java/com/myworkouts/navigation/Screen.kt` —
  `object ArchivedExercises : Screen(route = "archived_exercises")`.
- `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt` —
  регистрация destination `Screen.ArchivedExercises.route` в
  `NavHost`. Bottom-bar уже скрыт (контракт — `currentRoute in
  tabs.map { it.route }`, archived_exercises не в списке).
- `app/src/main/java/com/myworkouts/ui/state/ExercisesUiState.kt` —
  `Success` получает `pendingArchiveId: Long?` и
  `archivedCount: Int` (реактивный счётчик для кнопки в TopAppBar).
- `app/src/main/java/com/myworkouts/ui/viewmodel/ExercisesViewModel.kt`
  — `requestArchive`/`confirmArchive`/`cancelArchive`,
  подписка на `repository.archivedCount()`.
- `app/src/main/java/com/myworkouts/ui/screens/exercises/ExercisesScreen.kt`
  — расширенное меню (для кастомных — 3 пункта, для стандартных —
  1 пункт «Архивировать»); `ExerciseArchiveDialog`; кнопка архива в
  `TopAppBar` (видима при `archivedCount > 0`); `onArchivedExercisesClick`
  колбэк навигации.
- `app/src/main/java/com/myworkouts/ui/screens/exercises/ArchivedExercisesScreen.kt`
  (новый) + `ArchivedExercisesViewModel.kt` (новый) —
  реактивный список архивных, long-press меню «Вернуть»,
  заглушка пустого состояния.
- `app/src/main/res/values/strings.xml` (ru) +
  `app/src/main/res/values-en/strings.xml` (en) — 9 новых ключей
  (состав и тексты — нормативный источник в спек-е:
  `exercises-screen` — алерт, кнопка архива, новые пункты меню;
  `archived-exercises-screen` — заголовок, empty, пункт «Вернуть»).
- Тесты: `MappersTest` (tasks §1.1 — проброс `isArchived`
  через `toDomain`/`toEntity`); `RoomWorkoutDataRepositoryTest`
  (tasks §2.1–§2.3 — `setArchived`, `archivedExercises`,
  фильтрация в `exercises()`, реальный Room); `ExercisesViewModelTest`
  (расширить — pending-архивация, счётчик archivedCount);
  `ArchivedExercisesViewModelTest` (новый, JVM); `ExercisesScreenTest`
  (расширить — меню с 3 пунктами, меню с 1 пунктом для стандартных,
  кнопка архива, алерт архивации; тест §4.1 о безменюшности
  стандартных переименовать и переписать ассерты); `ArchivedExercisesScreenTest`
  (новый, androidTest); `EntryCreateScreenTest` (расширить —
  проверка фильтрации архивных; см. tasks §8 и уточнение уровня
  теста).
- Fake-репозитории в JVM + androidTest — добавить override
  `setArchived`, `archivedCount`, `archivedExercises`
  (5 JVM + 7 androidTest-классов в 5 файлах =
  12 override-сайтов по аналогии с архивным `edit-delete-exercise`
  tasks §1.4; полный список — в tasks §2.7).
- Превью: `ExerciseArchiveDialog` (светлая + тёмная);
  `ArchivedExercisesScreenContent` с состояниями (список, пусто).
- Не затрагивается: явная `Migration` (обоснование — design Decision 1
  и Migration Plan), `MainScreen`, prepopulate стандартных (seed-CALLBACK
  перечисляет колонки явно и не зависит от `is_archived` — дефолт
  срабатывает штатно), `EntryCreateViewModel` (фильтрация уже в
  `repository.exercises`), `AppDataViewModel` (то же), `EntryCreateScreen`
  (только проверка поведения).
