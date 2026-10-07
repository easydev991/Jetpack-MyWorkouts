# Design

## Context

Активные списки (`exercises()` / `categories()`) — единственный источник
пикеров создания (`ExerciseCreateScreen`, главный экран), поэтому
пустой активный каталог блокирует создание сущностей. Сейчас
`setArchived` / `setCategoryArchived` — голый `UPDATE` без проверок
количества, а pre-check'и удаления (`deleteExercise`, `deleteCategory`)
не считают активные строки. Согласованное решение — гибрид: UI-превенция
(скрытие пунктов меню) + репозиторный guard (typed failure) как safety
net для race-случаев и прямых путей (suppress-режим архивации).
Мотивация — proposal.md; контракты поведения — дельты specs.

## Goals / Non-Goals

**Goals:**

- Активный каталог упражнений и активный список категорий не могут
  опустошиться ни одной операцией пользователя (архивация, удаление).
- UI не предлагает недоступные действия; guard в репозитории ловит
  race и прямые пути.
- Guard-фейлы видны пользователю (снекбар) и телеметрии
  (`AppError` + Crashlytics).

**Non-Goals:**

- Экран архива: restore и удаление архивных не ограничиваются
  (удаление архивной строки никогда не снижает число активных).
- `deleteAllData` — массовая транзакция с восстановлением стандартного
  набора, guard'ы не применяются (канон — требование «Массовый сброс
  данных» спеки `workout-data`).
- `importData`/бэкап: импорт файла с полностью архивным каталогом
  остаётся возможным (восстановление архивного состояния — ответственность
  use-case импорта; см. Risks).
- Реактивные счётчики активных (`observeActiveCount`) — не нужны:
  guard одноразовый, UI-видимость вычисляется из уже подписанных
  списков.

## Decisions

### Decision 1 — два слоя защиты: UI-превенция + репозиторный guard

UI скрывает пункты «Архивировать»/«Удалить» у последнего активного —
штатный путь блокируется до алертов. Репозиторный guard — типизированный
`Result.failure` — закрывает race (элемент стал последним активным
между рендером списка и подтверждением) и прямые пути: `requestArchive`
при `dontShowArchiveDialog = true` и `requestCategoryArchive` при
`dontShowCategoryArchiveDialog = true` архивируют без диалога, минуя алерт-подтверждение —
второй барьер проверки (меню-превенция применяется при рендере меню
независимо от suppress-флага; suppress-путь выполняет архивацию сразу
по тапу — без второго шанса заметить изменение состояния, у диалогового
пути окно длиннее на время алерта, guard в обоих путях один и тот же).

Альтернатива — только UI или только guard: только UI не закрывает
suppress-путь и race; только guard даёт плохой UX (алерт-подтверждение,
завершающееся ошибкой).

### Decision 2 — исключения `LastActiveExerciseException` / `LastActiveCategoryException`

Новые классы в `DeletionExceptions.kt` — файл переименовывается в
`OperationalExceptions.kt` (покрывает и удаления, и архивации; ссылок
на имя файла в коде нет), наследники
`IllegalStateException` (паттерн файла: обратная совместимость с
`result.isFailure`-вызывающими сторонами), русские сообщения
(«Нельзя архивировать или удалять единственное активное упражнение» /
«…категорию»). По одному типу на сущность, а не на операцию: и архивация,
и удаление отклоняются одним и тем же фактом («это последний активный»),
различие операции восстанавливается на стороне VM. Файловый заголовок
при добавлении расширяется: guard'ы файла
покрывают и удаление, и архивацию обеих сущностей (сейчас заголовок
упоминает только `deleteExercise`).

Альтернатива — по исключению на операцию (4 класса): дублирует факт
без пользы; альтернатива — generic `IllegalStateException` — вернула бы
парсинг текстов, от которого проект уже ушёл.

### Decision 3 — проверка «последний активный»: getById + non-reactive count

Guard проверяет: цель активна AND активных ровно 1. В DAO добавляются
non-reactive count-запросы (по образцу существующего
`ExerciseEntryDao.countByExerciseId`):

- `ExerciseDao.activeCount()`:
  `SELECT COUNT(*) FROM exercises WHERE is_archived = 0`;
- `CategoryDao.activeCount()`:
  `SELECT COUNT(*) FROM categories WHERE is_archived = 0`.

Реализации:

- `setArchived`: внутри `runCatching` — `getById(id)` (null →
  существующее «Упражнение не найдено»); если
  `isArchived && !entity.isArchived && activeCount() == 1` →
  `throw LastActiveExerciseException()`; далее существующий
  `updateArchived(id, isArchived) == 0` → «не найдено» (race-check
  сохраняется).
- `deleteExercise`: существующие pre-check'и (не найдено → стандартное →
  связанные записи) → новый pre-check
  `if (!exercise.isArchived && exerciseDao.activeCount() == 1)`
  → `Result.failure(LastActiveExerciseException())` → удаление.
  Порядок сохраняет приоритет более actionable-сообщений (у последнего
  активного с записями сначала «есть записи»).
- `setCategoryArchived`: симметрично `setArchived`
  (`LastActiveCategoryException`).
- `deleteCategory`: проверка внутри существующей транзакции после
  `StandardCategoryDeletionException` и `CategoryInUseDeletionException`:
  `if (!category.isArchived && categoryDao.activeCount() == 1)`
  → `throw LastActiveCategoryException()`.

Альтернатива — реактивный Flow-счётчик: не нужен, guard не UI-состояние.
Альтернатива — один count-запрос «активных кроме цели»: `(SELECT COUNT(*)
WHERE is_archived = 0 AND id != :id) == 0` — компактнее в SQL, но
читаемость пары «цель активна AND активных == 1» выше и отладочнее.

### Decision 4 — маппинг в UiState: новые sealed-типы для архивации, новые варианты для удаления

- `DeleteExerciseError` + вариант `object LastActive`;
  `DeleteCategoryError` + вариант `object LastActive` — переиспользуют
  существующие каналы `deleteError` / `categoryDeleteError` и
  снекбар-пайплайн `confirmDelete` / `confirmCategoryDelete`.
- Новые sealed-типы в `domain/model/`:
  `ArchiveExerciseError.LastActive`, `ArchiveCategoryError.LastActive`
  (по образцу `DeleteExerciseError`). Sealed с одним вариантом —
  осознанно: паттерн консистентен, `when`-маппинг в UI явный, точка
  расширения для будущих причин отказа архивации.
- `ExercisesUiState.Success`: + `archiveError: ArchiveExerciseError? = null`,
  + `categoryArchiveError: ArchiveCategoryError? = null`.
- `onErrorShown()` сбрасывает `deleteError` и `archiveError`;
  `onCategoryErrorShown()` — `categoryDeleteError` и
  `categoryArchiveError` (пар каналов на сущность, one-shot семантика
  не меняется — снекбар один на экран).

Альтернатива — 4 варианта в двух существующих типах
(`DeleteExerciseError.LastActiveArchive` и т.п.): ноль новых полей, но
семантически неверно (архивная причина в типе удаления) и против
согласованного решения «новые sealed-типы».

Альтернатива — один общий тип `LastActiveError` для обеих сущностей:
смешивает каналы упражнений и категорий, у каждой пары свой reset-хендлер.

### Decision 5 — VM: guard-фейл архивации ломает тишину только для LastActive

`confirmArchive`/`confirmCategoryArchive` сейчас игнорируют исход
(`repository.setArchived(id, true)` без обработки) — race «не найдено»
остаётся тихим (спека `exercises-screen`, сценарий «Failure при
подтверждении»). Меняется только ветка guard-фейла:

```kotlin
repository.setArchived(id, true)
    .onFailure { error ->
        if (error is LastActiveExerciseException) {
            CrashlyticsHelper.logException(error, "Архивация упражнения")
            analyticsProvider.log(AnalyticsEvent.AppError(AppErrorOperation.ARCHIVE_EXERCISE, error))
            Log.e(LOG_TAG, "Архивация упражнения заблокирована: id=$id, ${error.message}", error)
            // archiveError — приватный MutableStateFlow по образцу
            // существующего deleteError; комбинируется в Success
            archiveError.value = ArchiveExerciseError.LastActive
        } // прочие failure — тихо, как раньше
    }
```

То же в прямых путях `requestArchive` / `requestCategoryArchive`
(suppress-режим). `confirmDelete` / `confirmCategoryDelete` — по одной
строке в существующий `when`:
`is LastActiveExerciseException -> DeleteExerciseError.LastActive`
(телеметрия `DELETE_EXERCISE` / `DELETE_CATEGORY` — существующий failure-код
переиспользуется, отдельных операций для удаления не нужно).

### Decision 6 — аналитика: `ARCHIVE_EXERCISE` / `ARCHIVE_CATEGORY` в `AppErrorOperation`

Новые enum-константы (wire-значения, UPPER_SNAKE_CASE). Заодно
`CATEGORY_DELETE` → `DELETE_CATEGORY` — унификация формата
`OPERATION_ENTITY` (публичного релиза и прод-пользователей нет,
wire-непрерывность метрик не требуется). `CrashlyticsHelper.logException`
вызывается для всех guard-фейлов (упражнение и категория, архивация и
удаление) — по образцу существующих веток удаления.

### Decision 7 — UI: вычисление «последний активный» из существующего UiState

Хелпер в `ExercisesScreen.kt`:

```kotlin
private fun isLastActive(id: Long, active: List<Exercise>): Boolean =
    active.size == 1 && active.single().id == id
```

(и аналог для `Category`). Состав меню упражнения:
«Изменить» — `isCustom`; «Архивировать» — `!lastActive`; «Удалить» —
`isCustom && !lastActive`. Если список пунктов пуст — `onLongPress` и
custom accessibility-действие не открывают меню и не включают подсветку
(early return до смены state). Для категорий — симметрично по
`categories()`. Новых запросов в VM нет — оба списка уже в
`ExercisesUiState.Success`.

Снекбар: расширение существующих мапперов (`deleteErrorText`,
`categoryDeleteErrorText`) ветками `LastActive` и два новых маппера для
`archiveError` / `categoryArchiveError`. Отображение — существующий
паттерн (закрытие текущего снекбара перед новым, one-shot через
`onErrorShown` / `onCategoryErrorShown`); компонент
`ExercisesDeleteErrorSnackbar` переименовывается в
`ExercisesErrorSnackbar` — private, один call-сайт, имя перестаёт
соответствовать содержанию (показывает и архивные ошибки).

Строковые ресурсы (ru / en):

- `exercise_last_active_archive_error` — «Нельзя архивировать
  единственное упражнение» / "Cannot archive the only exercise";
- `exercise_last_active_delete_error` — «Нельзя удалить единственное
  упражнение» / "Cannot delete the only exercise";
- `category_last_active_archive_error` — «Нельзя архивировать
  единственную категорию» / "Cannot archive the only category";
- `category_last_active_delete_error` — «Нельзя удалить единственную
  категорию» / "Cannot delete the only category".

Preview: обновлённые превью экрана/меню — светлая + тёмная
(`UI_MODE_NIGHT_YES`), `showBackground = true`, `locale = "ru"`,
отдельное превью для состояния «последний активный».

### Decision 8 — интерфейс `WorkoutDataRepository` не меняется

Guard — поведение внутри `RoomWorkoutDataRepository`; сигнатуры
`setArchived` / `deleteExercise` / `setCategoryArchived` /
`deleteCategory` прежние. Фейки androidTest остаются совместимыми;
фейки не эмулируют guard — UI-тесты проверяют видимость пунктов
(вычисляется из списков, не из репозитория). Guard-тесты обеих сущностей
живут в `RoomWorkoutDataRepositoryTest` (androidTest, реальная Room
in-memory — канон `room-repository.md` навыка `kotlin-ui-testing`;
red/green через точечный прогон runner-arguments): новая часть
guard'а — SQL `activeCount()`, который MockK не валидирует.

## Risks / Trade-offs

- [Race между count-проверкой и UPDATE — два параллельных запроса могут
  оба пройти guard] → Окно миллисекундное; UI-превенция закрывает штатный
  путь; остаточный риск допустим — существующие safety net'ы
  (`LinkedEntriesDeletionException`) устроены так же.
  → ponytail: операции не сериализуем; при реальных жалобах — транзакция
  вокруг check+update в `setArchived`.
- [Существующие androidTest-сценарии `RoomWorkoutDataRepositoryTest`
  могут архивировать/удалять последний активный элемент на малом наборе]
  → Точечный прогон класса в TDD-цикле покажет падения; тесты чинить
  данными (оставлять ≥ 2 активных), не ослаблением guard.
- [Импорт бэкапа может оставить 0 активных (файл с полностью архивным
  каталогом)] → Пре-существующая возможность, вне скоупа; пользователь
  может восстановить элементы на экране архива (restore не ограничен).
- [Снекбар-мапперы: 4 канала ошибок на одном SnackbarHost] →
  Одновременно не-null может быть максимум один (все one-shot,
  выставляются из последовательных confirm-колбэков); порядок проверки
  фиксирован — приоритет как у существующей пары `deleteError` /
  `categoryDeleteError`.

## Migration Plan

Без изменения схемы БД, без миграций, без изменения сигнатур публичных
API. Деплой — обычный релиз; откат — revert коммита (поведение обратно
совместимо: guard только добавляет failure-ветки).

## Open Questions

Нет.
