# Tasks

> Pre-condition: канон версий бэкап-формата — design.md Decision 9
> (правок кода не требуется). Перевод парсера категории на
> `requiredString` пре-кондишном НЕ является — это задача 4.4
> (возможна после перевода типа в 3.2).

## 1. Модель данных: таблица categories и миграция v3 → v4

- [x] 1.1 Создать `data/db/StandardCategories.kt` (5 категорий: имена-ключи
  PUSH/PULL/LEGS/CORE/CARDIO, `colorArgb` — текущие значения из `Color.kt`,
  `isCustom = false`) и file-level хелпер `standardCategoryLabelResId(name):
  Int?` в `ui/util` (`when` по пяти ключам, `else -> null` → существующие
  `exercise_category_*`; зависит только от констант + `R.string`, enum не
  трогает)   и переработать `ExerciseCategoryTest` в контракт-тест набора
  стандартных ключей и их label-ресурсов (через хелпер); контракт
  фиксирует `colorArgb` всех пяти категорий (текущие значения из
  `Color.kt`). Верификация: контракт-тест зелёный (`make test`)
- [x] 1.2 Добавить `CategoryEntity` (`name` unique, `color_argb`, `is_custom`,
  `is_archived`, `created_at`), `CategoryDao` (active/archived списки,
  счётчик, вставки) и `CategoryDaoTest` (androidTest: unique-индекс отклоняет
  дубль имени, сортировка `is_custom DESC, created_at DESC, name COLLATE
  NOCASE`, архивный фильтр, счётчик). Осознанное исключение из
  «тест раньше реализации»: DAO-контракт не компилируется без сущности —
  тест и реализация пишутся вместе, «красная» фаза вырождается
  в компиляционную. Верификация: `make android-test` зелёный
- [x] 1.3 Написать `MigrationTest` (androidTest, `MigrationTestHelper`:
  create v3 с упражнениями/записями → runMigrationsAndValidate v4 → 5
  стандартных категорий, данные упражнений целы) и подключить
  `androidx.room:room-testing` в androidTest-зависимости + assets-источник
  схем в `build.gradle.kts` (`sourceSets.getByName("androidTest").assets.
  srcDir("$projectDir/schemas")` — без него helper не найдёт schema JSON).
  Верификация: компилируется; до 1.4 красный падением helper'а на
  отсутствии schema-asset 4.json (bump и экспорт схемы — там), зелёный
  после 1.4
- [x] 1.4 Реализовать миграцию v3 → v4 по Design Decision 5 (bump
  версии, `MIGRATION_3_4` с CREATE TABLE/unique-индексом/INSERT из
  `StandardCategories`, регистрация в `addMigrations(...)`, экспорт и
  коммит `app/schemas/…4.json`). Верификация: `MigrationTest` зелёный,
  schema JSON в git
- [x] 1.5 Prepopulate категорий в callback первого создания БД (в той же
  транзакции, что упражнения) и пересеивание в `deleteAllData`; обновить
  затронутые androidTest-классы `RoomWorkoutDataRepositoryTest` /
  `RoomWorkoutDataRepositoryDataManagementTest` (`deleteAllData`) и
  JVM-тесты с фейками репозитория при смене контракта. Верификация: соответствующие
  тесты `make test` / `make android-test` зелёные

## 2. Репозиторий: CRUD категорий, цвет, транзакции

- [x] 2.1 Написать JVM-тест генерации цвета по Design Decision 3 (чистая
  функция с injectable `Random`; отступ hue ≥ 15° при зазоре ≥ 30°) —
  красный. Верификация: тест падает (функции нет)
- [x] 2.2 Реализовать генерацию цвета (Design Decision 3). Верификация: тест
  из 2.1 зелёный
- [x] 2.3 Написать JVM-тесты `addCategory` (пустое/blank имя отклоняется,
  дубль по `name.trim().lowercase()` отклоняется, успех → `isCustom = true`,
  `isArchived = false`, цвет сгенерирован) — красные. Верификация: тесты
  падают
- [x] 2.4 Реализовать `addCategory` в `RoomWorkoutDataRepository` +
  `WorkoutDataRepository`. Верификация: тесты из 2.3 зелёные
- [x] 2.5 Написать JVM-тесты `renameCategory` (обновляет `categories.name`
  и `exercises.category` в одной транзакции — активные и архивные
  упражнения; отклоняет стандартную категорию и дубль имени; несуществующее
  имя — ошибка) — красные. Верификация: тесты падают
- [x] 2.6 Реализовать `renameCategory` (Room-транзакция, Design Decision 1)
  и создать в `DeletionExceptions.kt` исключения
  `StandardCategoryDeletionException` (бросается и rename, и delete) и
  `CategoryInUseDeletionException` (только delete — в rename in-use не
  возникает, см. спеку `category-data`).
  Верификация: тесты из 2.5 зелёные
- [x] 2.7 Написать JVM-тесты `deleteCategory` + sealed `DeleteCategoryError`
  (UsedByExercises / StandardCategory / Other при прочих) и
  `setCategoryArchived` (архивация/восстановление, несуществующий id —
  failure, упражнения не трогаются) — красные. Верификация: тесты падают
- [x] 2.8 Реализовать `deleteCategory` (pre-check в транзакции) и
  `setCategoryArchived`. Верификация: тесты из 2.7 зелёные
- [x] 2.9 Реализовать реактивные запросы `categories()` /
  `archivedCategories()` / `categoryNamesWithLinkedExercises()` и снимок
  `getAllCategories()`; JVM-тест
  делегирования к DAO (MockK). Верификация: `make test` зелёный

## 3. Домен-рефакторинг: ExerciseCategory → Category/String

- [x] 3.1 Обновить `ComputeCategoryStatsTest` под категорию-строку и
  кастомные категории (группировка по имени не меняется). Верификация:
  осознанное исключение из «тест раньше реализации» (как в 1.2): на текущем
  enum-типе тест не компилируется — красная фаза вырождается в
  компиляционную; зелёный после 3.2
- [x] 3.2 Перевести `Exercise.category` на `String` (имя категории),
  `EntryWithExercise.category` → `String`; перевести `Mappers` и
  `StandardExercises` на `StandardCategories` (константы и цвета вместо
  enum и `Color.kt`-when); хелпер `standardCategoryLabelResId` уже создан
  в 1.1 — потребители: 3.4, 5.4, 8.3; обновить `computeCategoryStats`. Верификация:
  domain/data-модули компилируются (полный зелёный прогон — в 3.3: до него
  сломаны UI-потребители enum'а)
- [x] 3.3 Удалить enum `ExerciseCategory` и статический `when` в
  `ui/theme/Color.kt`; починить всех компиляционных потребителей (превью,
  UiState, тесты). Верификация: `make lint` без новых предупреждений,
  `make test` зелёный
- [x] 3.4 Подключить цвета в обзоре: `OverviewViewModel` подписывается на
  объединение `categories()` и `archivedCategories()` (архивные остаются
  в статистике — Design Decision 10), резолвит `colorArgb` и отображаемое имя (стандартные —
  label-хелпер, unknown — нейтральный fallback); обновить
  `OverviewViewModelTest`. Верификация: JVM-тест зелёный, донат/легенда
  и drill-down (оттенки от `colorArgb` выбранной категории, выбор по имени,
  кастомные категории) красятся из модели

## 4. Бэкап: секция categories[] и толерантный импорт

- [x] 4.1 Написать JVM-тест экспорта с `categories[]` (все категории,
  поля `name`/`colorArgb`/`isArchived`, без `isCustom`; `"version": 1` —
  канон Design Decision 9, код уже переведён на запись 1) — красный.
  Верификация: тест падает (секции `categories[]` нет). Красная фаза
  вырождается в компиляционную (новый параметр `buildBackupJson`;
  как в 1.2/3.1)
- [x] 4.2 Реализовать экспорт (`ExportBackupUseCase`, Backup-модель
  категории, `getAllCategories()`). Верификация: тест из 4.1 зелёный
- [x] 4.3 Написать JVM-тесты импорта (дедуп по `name.trim().lowercase()`;
  создание отсутствующих с `isCustom = true` и цветом/архивом из файла;
  пропуск упражнения с категорией, которой нет ни в файле, ни в БД, вместе
  с его записями; старый файл без `categories[]`; односторонняя архивация
  существующих; идемпотентность повторного импорта; **регрессионный**:
  файл с меткой `"version": 2` (в т.ч. с `categories[]`) импортируется
  успешно, а `"version": 3` — отклоняется — канон Design Decision 9,
  ранее не покрыт) — красные.
  Верификация: тесты падают. Красная фаза вырождается в компиляционную
  (новая сигнатура `mergeBackup`/`BackupMergeResult`)
- [x] 4.4 Реализовать импорт (`ImportBackupUseCase`: `requiredString` для
  категории вместо `requiredEnum`; резолв имён; расширение
  `importData(categories, exercises, entries)` до одной транзакции).
  Верификация: тесты из 4.3 зелёные
- [x] 4.5 Кнопка «Создать резервную копию» видима и при наличии только
  кастомных категорий; обновить тесты экрана «Данные приложения».
  Верификация: соответствующие JVM-тесты зелёные

## 5. Экран упражнений: табы и вкладка «Категории»

- [x] 5.1 Добавить строки ru/en по навыку `localization`: табы
  (`exercises_tab_exercises`, `exercises_tab_categories`), пункты меню
  категории (`category_list_menu_edit`, `category_list_menu_archive`,
  `category_list_menu_delete`), алерты удаления
  (`category_delete_dialog_title`, `category_delete_dialog_text`) и
  архивации (`category_archive_dialog_title`,
  `category_archive_dialog_text`), снекбары ошибок удаления
  (`category_delete_error_used_by_exercises`,
  `category_delete_error_standard`, `category_delete_error_generic`),
  empty-state (`categories_list_empty`), contentDescription FAB
  (`categories_add_category`),
  строки экрана категории (`category_create_title`, `category_edit_title`,
  `category_field_name`, `category_error_duplicate`, `category_save_error`)
  и добавить новый ключ `archive_title` — общий заголовок архива
  («Архив» / "Archive"; потребители переключаются на него в 5.4 и 7.3);
  добавить
  `dont_show_category_archive_dialog` в
  `AppSettingsDataStore` (+ тест DataStore). Верификация: `make lint`
  (как минимум — отсутствие missing strings), тест флага зелёный
- [x] 5.2 Написать `ExercisesViewModelTest` на новые поведения (selectedTab,
  categories в Success, hasArchived, пре-чек удаления
  категории, pending-флаги и ошибки категорий, requestCategoryArchive с
  чтением флага suppress) — красные. Верификация: тесты падают
- [x] 5.3 Расширить `ExercisesViewModel` (selectedTab, подписки, действия
  категорий) и `ExercisesUiState` по спеке. Верификация: тесты из 5.2
  зелёные
- [x] 5.4 UI: `SecondaryTabRow` + `when`-свитч, `CategoryListItemRow`
  (название через `standardCategoryLabelResId` из 1.1: стандартные —
  локализованный label, кастомные — `name`; цветовой маркер), empty-state,
  контекстное меню категории по типу, алерты удаления/архивации
  (переиспользование `ExerciseConfirmDialog` + `ArchiveSuppressSlot`,
  тексты категорий), снекбары, FAB по табу, кнопка архива при непустом
  архиве любого типа — перевод на `archive_title` (обновить 6 ссылок
  `R.string.exercises_archive_button` в `ExercisesScreenTest.kt` и
  прод-вызов в `ExercisesScreen.kt:277`) и удаление ставшего
  неиспользуемым ключа `exercises_archive_button`. Полная сигнатура
  `ExercisesScreen` (см. exercises-screen spec) вводит обязательный
  `onAddCategoryClick` (в `RootScreen.kt` дополнительно `onEditCategory` →
  `Screen.CategoryCreate.createRoute(categoryId)`) — обновить 7
  существующих именованных вызовов: `RootScreen.kt` (1),
  `ExercisesDeleteFlowTest` (3) и `ExercisesLiveUpdateTest` (3).
  Выполнять после 6.1 (маршрута `Screen.CategoryCreate` достаточно для
  лямбд навигации). Верификация: сборка, превью светлой/тёмной темы
  для строки и вкладки
- [x] 5.5 Compose UI-тест (androidTest): меню стандартной категории — только
  «Архивировать»; переключение табов; выбранная вкладка переживает поворот;
  FAB на вкладке категорий ведёт на
  `CategoryCreate` (выполнять после 6.1 — маршрут создаётся там).
  Верификация: `make android-test` зелёный

## 6. Экран создания/редактирования категории

- [x] 6.1 Маршрут `Screen.CategoryCreate` (ARG/NO_CATEGORY_ID/createRoute/
  arguments) и регистрация в `NavHost` `RootScreen`. Верификация: сборка,
  сценарий навигации компилируется
- [x] 6.2 Написать `CategoryCreateViewModelTest` (canSave c дубликатом и
  без, hasUnsavedChanges, edit-предзаполнение и hasChanges,
  isCategoryMissing, save → add/rename с popBackStack-колбэком,
  isSaveError) — красные. Верификация: тесты падают
- [x] 6.3 Реализовать `CategoryCreateViewModel` + `CategoryCreateUiState`.
  Верификация: тесты из 6.2 зелёные
- [x] 6.4 UI `CategoryCreateScreen` (обёртка + Content + ScreenState, поле
  «Название», «Сохранить» в bottomBar, подсказка дубликата, блок ошибки
  сохранения под кнопкой при `isSaveError`, BackHandler — канон
  `ExerciseCreate`; строки из 5.1) + превью — 5 состояний × 2 темы
  (пустая create-форма, форма с именем, edit-форма с предзаполнением,
  ошибка дубликата, ошибка сохранения под кнопкой — перечень требование
  «Превью в обеих темах» спеки `category-create-screen`).
  Верификация: сборка, превью рендерятся
- [x] 6.5 Связать `RootScreen`: фабрика VM `CategoryCreateViewModel`
  (колбэки `onAddCategoryClick`/`onEditCategory` подключены в 5.4 —
  единственном владельце обновления `RootScreen.kt`). Выполнять после
  5.4 и 6.4. Верификация: ручной прогон create → save → категория в
  списке; edit → rename обновляет упражнения

## 7. Архив: табы «Упражнения» / «Категории»

- [x] 7.1 Написать `ArchivedExercisesViewModelTest` (Success(exercises,
  categories, selectedTab), таб-бар при обоих непустых, автопереключение
  при опустошении вкладки, unarchive категории) — красные. Верификация:
  тесты падают
- [x] 7.2 Реализовать расширение `ArchivedExercisesViewModel` /
  UiState / State-колбэков. Верификация: тесты из 7.1 зелёные
- [x] 7.3 UI архива: `SecondaryTabRow` по правилу ≥ 2, вкладка категорий
  (переиспользование `CategoryListItemRow`, меню «Вернуть»), заглушка
  «В архиве пусто» (`archived_exercises_empty` — уже существующий ключ,
  не переиспользовать `archive_title` и не удалять), заголовок «Архив»
  (`archive_title` — TopAppBar и contentDescription кнопки архива),
  удаление ставшего неиспользуемым ключа `archived_exercises_title`,
  превью. Верификация: сборка, превью; заглушка не закрывает экран
- [x] 7.4 Compose UI-тест (androidTest): таб-бар в архиве при двух типах,
  отсутствие таб-бара при одном, «Вернуть» категорию. Верификация:
  `make android-test` зелёный

## 8. ExerciseCreate: дропдаун категорий из БД

- [x] 8.1 Обновить `ExerciseCreateViewModelTest` (categoryName: String?,
  availableCategories, dropdownCategories = активные + текущая архивная в
  edit, реактивность списка, формулы canSave/hasUnsavedChanges) — красные.
  Верификация: тесты падают
- [x] 8.2 Реализовать подписку VM на `categories()` и поля UiState.
  Верификация: тесты из 8.1 зелёные
- [x] 8.3 `CategoryDropdownField`: список из state, лейблы стандартных
  через label-хелпер, скрытие архивных; обновить androidTest
  `ExerciseCreateScreenTest` по факту. Верификация: UI-тест зелёный
  (скрытие архивных, текущая архивная видна в edit); превью рендерятся

## 9. Аналитика и финальные проверки

- [x] 9.1 Аналитика: `AppScreen.CategoryCreateScreen`; `UserActionType` —
  создание/сохранение/удаление/архивация/восстановление категории + выбор
  вкладки категорий; `AppErrorOperation.CATEGORY_DELETE`, `screenForRoute`
  для нового маршрута; сетап событий в новых экранах/VM. Верификация: сборка,
  `make lint` зелёный
- [x] 9.2 Финальный прогон: `make format && make lint && make test` — зелёные;
  `make android-test` (один флейвор, один прогон → один отчёт); ручная
  проверка сценария: создание категории → упражнение с ней → попытка
  удаления категории (ожидаемо блокируется — снекбар
  `category_delete_error_used_by_exercises`) → удаление упражнения →
  архивация категории → успешное удаление категории. Верификация: все
  команды зелёные, отчёты без regressions
