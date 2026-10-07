# Proposal

## Why

Каталог можно опустошить: архивация или удаление последнего активного
упражнения/категории оставляет пикеры без единого варианта — упражнение
и запись о выполнении становится невозможно создать. Оба списка берутся
только из активных строк, а guard'ов на «последний активный» нет
(`setArchived`/`setCategoryArchived` — голый UPDATE, pre-check'и удаления
количество активных не считают).

## What Changes

- **UI-превенция**: в long-press меню упражнения и категории
  (`ExercisesScreen.kt`) пункты «Архивировать» и «Удалить» скрываются,
  если элемент — единственный активный в своём списке; «Изменить»
  остаётся (только custom). Если действий не остаётся (последнее
  стандартное упражнение — в его меню только «Архивировать») — меню
  при долгом нажатии не открывается вовсе.
- **Guard в репозитории** (safety net по образцу существующих
  pre-check'ов): `setArchived(id, true)`, `setCategoryArchived(id, true)`,
  `deleteExercise`, `deleteCategory` возвращают `Result.failure` с новыми
  типизированными исключениями, если цель — единственный активный элемент
  списка. Восстановление из архива не ограничивается; экран архива
  (restore/удаление архивных) не меняется; `deleteAllData` не трогается.
- **VM**: guard-фейлы маппятся в новые sealed-типы ошибок (по образцу
  `DeleteExerciseError`/`DeleteCategoryError`) и показываются одноразовым
  снекбаром через существующий паттерн + `onErrorShown`/
  `onCategoryErrorShown`; снекбар-компонент переименовывается:
  `ExercisesDeleteErrorSnackbar` → `ExercisesErrorSnackbar`.
  Отсекаются все четыре точки входа архивации: `requestArchive`/
  `requestCategoryArchive` (прямой путь при флагах
  `dontShowArchiveDialog`/`dontShowCategoryArchiveDialog`) и
  `confirmArchive`/`confirmCategoryArchive`.
- **Строки** ru/en: «Нельзя архивировать/удалить единственное
  упражнение/категорию» (4 сообщения). Снекбар — fallback для race:
  в штатной работе пункты меню скрыты и он не показывается.
- **Аналитика**: в `AppErrorOperation` добавляются `ARCHIVE_EXERCISE`,
  `ARCHIVE_CATEGORY`; заодно унификация нейминга: `CATEGORY_DELETE` →
  `DELETE_CATEGORY` (константа — wire-значение, публичного релиза и
  прод-пользователей нет — непрерывность метрик не требуется).
  Guard-фейлы логируются как `AppError` по образцу
  `DELETE_EXERCISE`/`DELETE_CATEGORY`.

Не меняется: сигнатуры методов репозитория (поведенческий guard внутри
реализаций), экран архива, `deleteAllData`, restore-операции.

## Capabilities

### New Capabilities

(нет — изменение усиливает существующие правила каталога и экрана
упражнений)

### Modified Capabilities

- `workout-data`: требование «Архивация и восстановление упражнений
  каталога» — `setArchived(id, true)` отклоняет архивацию последнего
  активного упражнения типизированным исключением; требование
  «Удаление упражнения из каталога» — pre-check «последний активный»
  перед удалением.
- `category-data`: требование «Архивация и восстановление категории» —
  guard в `setCategoryArchived(id, true)`; требование «Удаление
  категории» — pre-check «последний активный» внутри существующей
  транзакции.
- `exercises-screen`: требования «Контекстное меню по долгому нажатию —
  с учётом типа упражнения» и «Контекстное меню категории по типу» —
  скрытие пунктов «Архивировать»/«Удалить» у последнего активного,
  отказ от открытия пустого меню; новое требование — типизированные
  одноразовые снекбары блокировки (4 локализованных сообщения).
- `crash-reporting`: требование «Логирование перехваченных ошибок» —
  guard-фейлы архивации логируются событием `app_error` с новыми
  операциями `ARCHIVE_EXERCISE`/`ARCHIVE_CATEGORY`; дополнительно дельта
  восполняет пропуск «удаление категории» в списке ключевых операций
  (спека отставала от кода).

## Impact

- `data/repository/RoomWorkoutDataRepository.kt` — guard'ы в
  `setArchived`, `deleteExercise`, `setCategoryArchived`, `deleteCategory`.
- `data/repository/DeletionExceptions.kt` — переименование в
  `OperationalExceptions.kt` (файл покрывает и удаления, и архивации);
  новые исключения «последний активный», обновление файлового заголовка.
- `data/db/` — count-запросы активных упражнений/категорий в DAO.
- `ui/viewmodel/ExercisesViewModel.kt` — маппинг guard-фейлов в ошибки
  UiState в `requestArchive`, `confirmArchive`, `requestCategoryArchive`,
  `confirmCategoryArchive`, `confirmDelete`, `confirmCategoryDelete`.
- `ui/state/ExercisesUiState.kt` — новые one-shot ошибки в `Success`.
- `ui/screens/exercises/ExercisesScreen.kt` — видимость пунктов меню,
  отказ от открытия пустого меню, новые снекбар-сообщения;
  переименование `ExercisesDeleteErrorSnackbar` → `ExercisesErrorSnackbar`.
- `domain/model/` — новые sealed-типы ошибок рядом с
  `DeleteExerciseError.kt`/`DeleteCategoryError.kt`; в самих
  `DeleteExerciseError`/`DeleteCategoryError` — новые варианты
  `LastActive`.
- `analytics/AnalyticsEvent.kt` — новые `AppErrorOperation`; переименование
  `CATEGORY_DELETE` → `DELETE_CATEGORY` (унификация формата
  `OPERATION_ENTITY`).
- Строковые ресурсы `values/strings.xml`, `values-en/strings.xml`.
- Тесты: JVM (`ExercisesViewModelTest`) и androidTest
  (`RoomWorkoutDataRepositoryTest` — guard-сценарии на реальной Room
  in-memory, `ExercisesDeleteFlowTest`, `ExercisesCategoriesTabTest`,
  `ExercisesScreenTest`); существующие фейковые репозитории androidTest
  сигнатурно не меняются.
