# Proposal

## Why

Приложение накапливает записи о тренировках, но не показывает прогресс: пользователь не видит, какие категории нагружены, какие личные рекорды поставлены и как регулярно он тренируется. Готовые UI-черновики (`app/src/main/java/com/myworkouts/ui/screens/overview/`, макеты `progress-drafts/iter4_v1.html` / `iter5_v1.html`) уже согласованы — фича переносит их в чистовые слои (Screen/UiState/ViewModel/use-cases) с удалением DRAFT-кода.

## What Changes

- **MainScreen**: в `TopAppBar` справа — кнопка со стандартной иконкой `donut_large`; показывается только при наличии хотя бы одной записи (`MainUiState.Success.entries.isNotEmpty()`); тап открывает `OverviewScreen`.
- **OverviewScreen** (новый, нетабовый): `TopAppBar` «Обзор» + кнопка настроек; `SecondaryTabRow` с вкладками Статистика/Рекорды/Активность; последняя выбранная вкладка и видимость вкладок хранятся в DataStore (`appearance_prefs`). При ровно одной видимой вкладке `SecondaryTabRow` скрыт, название вкладки — подзаголовком в `TopAppBar`.
- **Вкладка «Статистика»**: пикер периода День/Неделя/Месяц/Год; донат-чарт долей категорий (PUSH/PULL/LEGS/CORE/CARDIO); легенда с суммами; drill-down по категории (доли упражнений оттенками цвета категории + список записей категории, сгруппированный по датам); ландшафтная раскладка «донат | легенда-сетка | записи»; пустой период — подсказка.
- **Вкладка «Рекорды»**: переключатель «За подход»/«За день»; личные рекорды по упражнениям за всё время с датой рекорда, сортировка по дате убыванием; REPS — «повт.», TIME — `DurationFormatter`.
- **Вкладка «Активность»**: карточка текущей и рекордной серии тренировок с тремя состояниями (активная / ожидающая — серия жива без сегодняшней тренировки, приглушённая карточка / прерванная — при рекорде > 1 строка рекорда, при рекорде ≤ 1 секция скрыта); пикер периода хитмапа Месяц/Квартал/Полгода/Год; хитмап-сетка активностей по дням (уровни 0–4).
- **OverviewSettingsScreen** (новый): тогглы видимости трёх вкладок + кнопка «Включить все» (пока выключена хотя бы одна); последнюю включённую вкладку выключить нельзя.
- **Данные**: новый Flow-метод репозитория `entriesWithExercise()` (SQL JOIN записей с упражнениями — категория и `measuredBy` для расчётов); чистые use-cases расчёта долей, рекордов, серий, уровней хитмапа (TDD).
- **Тема**: демо-цвета категорий из превью и уровни хитмапа — именованные константы в `ui/theme/Color.kt` + extension `ExerciseCategory.color`.
- **Навигация/аналитика**: маршруты `Screen.Overview` / `Screen.OverviewSettings`, значения `AppScreen`, новые `UserActionType` (вкладки, тогглы видимости, смена периодов/режимов); нижняя панель скрыта на всех новых экранах (существующий контракт `shouldShowBottomBar`).
- **Локализация**: все новые строки в `strings.xml` ru/en, plurals для «дней»; «повт.» — строковый ресурс с числом; перечень новых ключей — design Decision 12.
- **DRAFT-код**: перенос вёрстки черновиков в чистовые слои и удаление всех DRAFT-файлов каталога `ui/screens/overview/` после переноса.

## Capabilities

### New Capabilities

- `overview-screen`: экран «Обзор» с вкладками Статистика/Рекорды/Активность, экран настроек видимости вкладок, сохранение выбранной вкладки и видимости в DataStore, расчёты статистики/рекордов/серий/хитмапа, безопасные зоны и ландшафтные раскладки.

### Modified Capabilities

- `workouts-main-screen`: добавляется требование «Кнопка обзора в TopAppBar» — появление только при непустом списке записей, навигация на экран обзора.
- `non-tab-screen-navigation`: добавляются маршруты `Screen.Overview` и `Screen.OverviewSettings` во множество зарегистрированных нетабовых направлений; нижняя панель скрыта на обоих (существующий контракт `shouldShowBottomBar` — без правок `RootScreen` для скрытия).

## Impact

- **Новые файлы**: `ui/screens/overview/{OverviewScreen,StatsScreen,RecordsScreen,ActivityScreen,OverviewSettingsScreen}.kt` + `*Preview.kt`; `ui/viewmodel/{OverviewViewModel,OverviewSettingsViewModel}.kt`; `ui/state/{OverviewUiState,OverviewSettingsUiState}.kt` (+ state-классы вкладок, `OverviewUserState`); `domain/usecase/{ComputeCategoryStats,ComputeExerciseRecords,ComputeStreaks,ComputeHeatmapLevels}.kt`; тесты в `app/src/test/` и `app/src/androidTest/`.
- **Изменяемые**: `MainScreen.kt` (TopAppBar), `Screen.kt`, `RootScreen.kt` (регистрация маршрутов, фабрики VM, `screenForRoute`), `WorkoutDataRepository` + `RoomWorkoutDataRepository` + `ExerciseEntryDao` (JOIN-проекция, без изменения схемы БД — version не меняется), `AppSettingsDataStore` (видимость вкладок + последняя вкладка), `ExportBackupUseCase`/`ImportBackupUseCase` (JSON версии 2: блок настроек вкладок), `analytics/` (enum'ы), `ui/theme/Color.kt`, `res/values/strings.xml` + `values-en/strings.xml`.
- **Удаляемые**: `ui/screens/overview/*Preview.kt`, `OverviewScreenDraft.kt` (DRAFT-раннер, переименован из `OverviewScreen.kt` в 6.2) — финальное удаление одной задачей (6.10) после полного переноса в чистовые файлы.
- **Зависимости**: новые не добавляются (донат и хитмап — Canvas; офлайн-only).
- **detekt**: лимит `maxIssues: 5` не поднимается; новые предупреждения не остаются.
