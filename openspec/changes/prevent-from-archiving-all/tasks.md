# Tasks

## 1. Скаффолдинг: исключения и DAO-запросы

- [x] 1.1 Переименовать `data/repository/DeletionExceptions.kt` → `OperationalExceptions.kt` (файл покрывает и удаления, и архивации; ссылок на имя файла в коде нет, синхронизировать упоминание в AGENTS.md); добавить `LastActiveExerciseException` / `LastActiveCategoryException` (наследники `IllegalStateException`, русские сообщения — design Decision 2); обновить файловый заголовок — guard'ы покрывают и удаление, и архивацию; проверить компиляцию `./gradlew :app:compileGithubDebugKotlin`
- [x] 1.2 Добавить non-reactive `activeCount(): Int` (`SELECT COUNT(*) … WHERE is_archived = 0`) в `ExerciseDao` и `CategoryDao` (design Decision 3); проверить компиляцию той же командой

## 2. Guard упражнений в репозитории (TDD, androidTest)

- [x] 2.1 Падающие тесты в `RoomWorkoutDataRepositoryTest` (androidTest, реальная Room in-memory — канон `room-repository.md` навыка `kotlin-ui-testing`): `setArchived(lastActiveId, true)` → `Result.failure(LastActiveExerciseException)`; `deleteExercise(lastActiveId)` → failure; не-последний активный → успех; `setArchived(restore)` при единственном активном → успех; удаление архивного при единственном активном → успех. Прогон на эмуляторе (после `make emulator-fast`): `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.data.repository.RoomWorkoutDataRepositoryTest'` — новые тесты красные; существующие сценарии класса на этом прогоне ещё зелёные — упавшие чинить данными (≥ 2 активных), не ослаблением guard (design Risks)
- [x] 2.2 Реализация guard в `RoomWorkoutDataRepository.setArchived` и `deleteExercise` (порядок проверок — design Decision 3); тот же точечный прогон — зелёные, включая существующие сценарии класса (регрессия репозитория)
- [x] 2.3 `make test` — контроль, что изменение репозитория не затронуло JVM-слои; полный androidTest-прогон — задача 6.4

## 3. Guard категорий в репозитории (TDD, androidTest)

- [x] 3.1 Падающие категорийные guard-сценарии в том же `RoomWorkoutDataRepositoryTest`: `setCategoryArchived(lastActiveId, true)` → `Result.failure(LastActiveCategoryException)`; `deleteCategory(lastActiveId)` (кастомная, без ссылок) → failure; не-последняя → успех; restore при единственной активной → успех; удаление архивной категории при единственной активной → успех. Тот же точечный прогон — новые тесты красные
- [x] 3.2 Реализация guard в `setCategoryArchived` и внутри транзакции `deleteCategory` после проверок `isCustom`/`UsedByExercises` (design Decision 3); тот же точечный прогон — зелёные

## 4. Domain-типы, UiState и ViewModel (TDD)

- [x] 4.1 Domain и UiState: варианты `LastActive` в `DeleteExerciseError` / `DeleteCategoryError`; архивные ошибки — Boolean-поля `archiveError` / `categoryArchiveError` в `ExercisesUiState.Success` (design Decision 4; ревизия после ревью — вместо исходных sealed-типов, см. design.md). Проверка компиляции
- [x] 4.2 Падающие JVM-тесты в `ExercisesViewModelTest`: blocked `confirmDelete` → `deleteError = DeleteExerciseError.LastActive`; blocked `confirmArchive` и прямой путь `requestArchive` (при `dontShowArchiveDialog = true`) → `archiveError = true`; категорийные аналоги (`categoryDeleteError` / `categoryArchiveError`); race-failure архивации остаётся тихим; `onErrorShown()` / `onCategoryErrorShown()` сбрасывают каждый свою пару каналов (`deleteError` + `archiveError` / `categoryDeleteError` + `categoryArchiveError`). Точечный прогон — красные
- [x] 4.3 VM: `AppErrorOperation` + `ARCHIVE_EXERCISE` / `ARCHIVE_CATEGORY`; переименование `CATEGORY_DELETE` → `DELETE_CATEGORY` (design Decision 6; `AnalyticsEvent.kt` + call-site + JVM-тест; релиза в проде нет — wire-непрерывность не требуется); маппинг guard-фейлов в 6 точках входа (`requestArchive`, `confirmArchive`, `requestCategoryArchive`, `confirmCategoryArchive`, `confirmDelete`, `confirmCategoryDelete`) с `CrashlyticsHelper.logException` + `AppError` + `Log.e`; в точках удаления — только ветка `LastActive` в существующий `when`, телеметрия переиспользуется (design Decision 5); точечный прогон — зелёные
- [x] 4.4 `make test` — полный прогон зелёный; `make format && make lint` — ранний detekt-фидбек после VM-слоя

## 5. Строки и UI экрана упражнений (Compose — по факту)

- [x] 5.1 Строки ru/en: `exercise_last_active_archive_error`, `exercise_last_active_delete_error`, `category_last_active_archive_error`, `category_last_active_delete_error` (design Decision 7); проверка компиляции `./gradlew :app:compileGithubDebugKotlin`
- [x] 5.2 `ExercisesScreen.kt`: скрытие «Архивировать»/«Удалить» у последнего активного (упражнения и категории), отказ от открытия пустого меню для long-press и accessibility-действия (design Decision 7); превью светлая + тёмная, `locale = "ru"`, отдельное превью состояния «последний активный»
- [x] 5.3 Снекбары: ветки `LastActive` в `deleteErrorText` / `categoryDeleteErrorText`, новые мапперы для `archiveError` / `categoryArchiveError`, one-shot сброс через `onErrorShown` / `onCategoryErrorShown`; переименование `ExercisesDeleteErrorSnackbar` → `ExercisesErrorSnackbar` (private, один call-сайт); проверка компиляции
- [x] 5.4 `make format && make lint` — detekt в лимите `maxIssues: 5`

## 6. androidTest (Compose UI)

- [x] 6.1 `ExercisesScreenTest`: у последнего активного кастомного меню содержит только «Изменить»; у последнего активного стандартного меню не открывается (long-press и accessibility-действие)
- [x] 6.2 `ExercisesCategoriesTabTest`: категорийные аналоги 6.1; «Изменить» навигирует на edit-экран как раньше
- [x] 6.3 `ExercisesDeleteFlowTest`: существующие флоу удаления/архивации не регрессируют (фейки без guard — UI-видимость вычисляется из списков)
- [x] 6.4 `make android-test` на эмуляторе (после `make emulator-fast`) — полный прогон зелёный

## 7. Финальная верификация

- [x] 7.1 `make format && make lint && make test` — зелёные, detekt ≤ 5, новых detekt-предупреждений нет
- [x] 7.2 `openspec validate --change "prevent-from-archiving-all" --strict` — без ошибок
