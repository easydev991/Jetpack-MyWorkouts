# Proposal

## Why

Набор категорий упражнения захардкожен в enum `ExerciseCategory` (PUSH/PULL/LEGS/CORE/CARDIO):
пользователь не может завести свою категорию («Спина», «Гантельная»), цвета категорий —
статический `when` в `ui/theme/Color.kt`, а импорт бэкапа отвергает весь файл при неизвестной
категории. Категории становятся данными: пользователь создаёт, переименовывает, архивирует
и удаляет собственные категории, стандартные пять остаются системными.

## What Changes

- **Модель данных**: новая таблица `categories` (`name` unique, `colorArgb`, `isCustom`,
  `isArchived`); домен-модель `Category` вместо enum `ExerciseCategory` в UI-потребителях;
  упражнения продолжают ссылаться на категорию по имени. Миграция Room v3 → v4
  (CREATE TABLE + INSERT 5 стандартных категорий) — первая рукописная, в паре с
  `MigrationTest` (androidTest) и подключением `androidx.room:room-testing`.
- **Цвет категории**: генерируется при создании (случайный hue в HSV при фиксированных
  S/V, с минимальным отступом hue от существующих), хранится как ARGB Int — задел под
  будущую ручную настройку (вне скопа). Стандартным категориям фиксируются текущие цвета
  из `Color.kt`.
- **Экран упражнений**: табы «Упражнения» / «Категории» (`SecondaryTabRow`, состояние в
  ViewModel — паттерн `OverviewScreen`); FAB действует по активному табу.
- **Вкладка «Категории»**: список категорий с цветовым индикатором, empty-state,
  long-press меню (кастомные: Изменить / Архивировать / Удалить; стандартные: только
  Архивировать), sealed `DeleteCategoryError` со снекбаром, пре-чек использования.
- **Экран создания/редактирования категории**: новый маршрут `category_create?categoryId={categoryId}`,
  одно поле «Название», «Сохранить» в bottomBar, валидация (непустое, уникальность по
  `name.trim().lowercase()`), BackHandler-подтверждение — по образцу `ExerciseCreate`.
- **Архивация категории**: переиспользование `ExerciseConfirmDialog` + `ArchiveSuppressSlot`
  с текстами для категорий; отдельный флаг `dont_show_category_archive_dialog` в DataStore.
- **Архив**: экран получает табы «Упражнения» / «Категории» (таб-бар только когда в архиве
  есть и те, и другие); «Вернуть» для категорий; кнопка архива в TopAppBar видна при
  `archivedExercises + archivedCategories > 0`.
- **Удаление категории**: только кастомные и только без единого упражнения (активного или
  архивного) — пре-чек + алерт; иначе snackbar «Эта категория уже используется в
  упражнении».
- **Переименование кастомной категории** — одной Room-транзакцией вместе с обновлением
  `exercises.category`.
- **Экран упражнения**: `CategoryDropdownField` тянет категории из БД; архивные скрыты
  (кроме текущей при редактировании).
- **Статистика**: `ComputeCategoryStats` и донат/легенда обзора работают с кастомными
  категориями и берут цвета из модели.
- **Бэкап**: секция `categories[]` (`name`, `colorArgb`, `isArchived`); импорт создаёт
  отсутствующие категории, дедуп по `name.trim().lowercase()`; упражнение с категорией,
  которой нет ни в файле, ни в БД, пропускается — файл больше не отвергается целиком.
- **Аналитика**: новые экраны/действия по существующей модели (`AppScreen`,
  `UserActionType`, `AppErrorOperation`). Строки ru/en для всего нового.

## Capabilities

### New Capabilities

- `category-data`: домен-модель `Category`, таблица `categories` с prepopulate стандартных
  категорий, CRUD (создание с генерацией цвета, транзакционное переименование, удаление
  с блокировкой по используемости, архивация/восстановление), реактивные Flow, миграция
  v3 → v4 с `MigrationTest`.
- `category-create-screen`: экран создания/редактирования категории (один роут на
  create/edit, поле «Название», валидация уникальности, BackHandler-подтверждение,
  «Сохранить» в bottomBar).

### Modified Capabilities

- `workout-data`: `importData` принимает категории; `deleteAllData` пересеивает
  стандартные категории; снимок `getAllCategories` для экспорта бэкапа.
- `exercises-screen`: табы «Упражнения» / «Категории» и FAB по активному табу; содержимое
  вкладки «Категории» (список, long-press меню, алерты удаления/архивации, типизированные
  ошибки, пре-чек); кнопка архива видна при непустом архиве любого типа.
- `archived-exercises-screen`: экран архива становится экраном с табами
  «Упражнения» / «Категории» (таб-бар при ≥ 2 непустых типов), «Вернуть» для категорий,
  заглушка «В архиве пусто» без закрытия экрана.
- `exercise-create-screen`: `CategoryDropdownField` выбирает из категорий БД (не enum);
  архивные скрыты, кроме текущей категории редактируемого упражнения.
- `app-data-management`: формат бэкапа расширяется секцией `categories[]`; импорт —
  толерантный парсинг категорий, создание отсутствующих, пропуск упражнений с неизвестной
  категорией вместо отвержения файла.
- `appearance`: отдельный DataStore-флаг `dont_show_category_archive_dialog`
  для алерта архивации категории (требование «AppSettingsDataStore persists
  archive-dialog preference»).
- `overview-screen`: доли категорий в статистике включают кастомные категории; цвета
  доната и легенды — из модели категории.
- `non-tab-screen-navigation`: новый маршрут `Screen.CategoryCreate`
  (`category_create?categoryId={categoryId}`) по паттерну `ExerciseCreate`.

## Impact

- **Data**: `ExerciseEntity.category` остаётся строкой (enum-имя для стандартных);
  новая `CategoryEntity` + `CategoryDao`, `Mappers`, `WorkoutDatabase` (version 4,
  экспорт schema JSON, `Migration`, `room-testing` в androidTest); prepopulate в
  `RoomDatabase.Callback` и `deleteAllData`.
- **Domain**: `ExerciseCategory` (enum) удаляется после миграции потребителей на
  `Category`; `Exercise.category` становится именем категории; затрагивает `Mappers`,
  `ComputeCategoryStats`, use-case'ы бэкапа (`ExportBackupUseCase`, `ImportBackupUseCase`
  — `requiredEnum` для категории заменяется резолвом по имени).
- **UI**: `ExercisesScreen` (табы, вкладка категорий), новый `CategoryCreateScreen`,
  `ArchivedExercisesScreen` (табы), `ExerciseCreateScreen` (дропдаун), `OverviewScreen`
  (цвета), `Color.kt` (when → цвет из модели); расширение существующих ViewModel
  (`ExercisesViewModel`, `ArchivedExercisesViewModel`) и новый
  `CategoryCreateViewModel`; новые поля UiState-классов.
- **Навигация/настройки**: `Screen.CategoryCreate`; флаг
  `dont_show_category_archive_dialog` в `AppSettingsDataStore`.
- **Аналитика**: расширение `AppScreen` / `UserActionType` / `AppErrorOperation`.
- **Локализация**: новые строки ru/en по списку задачи 5.1 + общий ключ
  `archive_title`; удаляются ставшие неиспользуемыми `exercises_archive_button`
  (5.4) и `archived_exercises_title` (7.3) — см. спеки `exercises-screen`
  и `archived-exercises-screen`.
- **Тесты**: репозиторий (JVM), ViewModel'и, Import/Export, `MigrationTest` (androidTest),
  обновление `ExerciseCategoryTest` / `ComputeCategoryStatsTest`; Compose UI-тесты
  (меню стандартной категории, табы).
- **Ломающих изменений формата бэкапа нет**: `categories[]` — обратно совместимое
  расширение версии 1 (старые файлы импортируются, категории резолвятся из БД);
  канон версий — экспорт пишет 1, импорт принимает 1 и 2 (фикс спек-дрейфа,
  мастер-код уже так делает; design.md D9).
