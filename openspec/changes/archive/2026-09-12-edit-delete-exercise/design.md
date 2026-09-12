# Design: edit-delete-exercise

## Context

Каталог упражнений — read-only: `ExercisesScreen` рисует список + FAB,
`ExerciseCreateScreen` умеет только создавать. Слой данных готов с самого
начала: `updateExercise` есть в `RoomWorkoutDataRepository`; `deleteExercise`
уже имеет guard-ы (не найдено / стандартное / FK), но через generic
`IllegalStateException` + парсинг текста `SQLiteConstraintException`. Этот
change заменяет их на типизированные исключения и добавляет проактивный
pre-check по FK (см. Decision 3, Decision 8) — `deleteExercise` переписывается
целиком. Не хватает только чтения по идентификатору (`getExercise`).
Оба UI-паттерна уже реализованы на других экранах и переиспользуются
дословно: long-press-меню с алертом — `MainScreen`, edit-режим формы с
query-аргументом — `EntryCreateScreen`.

## Goals / Non-Goals

**Goals:**

- Паритет каталога с записями: edit + delete кастомных упражнений.
- Нулевая изобретательность: те же паттерны, что на MainScreen и
  EntryCreateScreen, те же имена полей (`pendingDeleteId`, `original*`),
  та же механика маршрута.

**Non-Goals:**

- Редактирование/удаление стандартных упражнений (guard уже в репозитории).
- Свайп-жесты, drag-and-drop, пакетные операции.
- Изменения схемы БД, модели `Exercise`, prepopulate, `MainScreen`,
  `EntryCreateScreen`.

## Decisions

### Decision 1: Query-параметр `exerciseId`, не path-сегмент

**Выбор:** `Screen.ExerciseCreate` → маршрут
`"exercise_create?exerciseId={exerciseId}"`, `NO_EXERCISE_ID = -1L`,
`navArgument` c `defaultValue = NO_EXERCISE_ID`, фабрика
`createRoute(exerciseId: Long = NO_EXERCISE_ID)`.

**Обоснование:** Точная копия `Screen.EntryCreate` (там тот же паттерн с
`entryId`): одно назначение в `NavHost` обслуживает оба режима,
`defaultValue` делает фабрику без параметра валидным create-маршрутом.
Path-сегмент потребовал бы двух маршрутов (`exercise_create` и
`exercise_create/{id}`) или ломал бы дефолт. Design-запись
`non-tab-screen-navigation` «аргументы только при реальной потребности»
снимается самим этим change-ом — потребность доказана edit-режимом.

**Альтернатива:** отдельный маршрут `exercise_edit/{id}` — отклонена:
дублирует `composable`-блок, расщепляет источник истины маршрута.

### Decision 2: `originalExercise` в ViewModel, не в UiState

**Выбор:** `private var originalExercise: Exercise? = null` — приватное поле
VM, зеркально `EntryCreateViewModel.originalEntry`.

**Обоснование:** Оригинал — не состояние интерфейса: его не рендерят, в
превью не передают, тесты состояния не сравнивают с ним. UiState остаётся
data class из плоских полей. В `UiState` попадает только производный флаг
`hasChanges` (и `isEditMode`). Сравнение `draftOf(uiState) != originalExercise`
даёт `hasChanges` бесплатно — `Exercise` уже data class с
структурным равенством.

**Альтернатива:** хранить оригинал в UiState — отклонена: лишнее поле в
каждом `copy()` и в сигнатурах превью, риск рассинхрона копий.

### Decision 3: Ошибка удаления — typed errors с понятной причиной в снекбаре

**Выбор:** `RoomWorkoutDataRepository.deleteExercise` возвращает
`Result<Unit>` с типизированными `IllegalStateException`-подклассами для
следующих случаев:
- `StandardExerciseDeletionException` — `isCustom == false` (упражнение
  в prepopulate, удалять запрещено дизайном);
- `LinkedEntriesDeletionException` — на упражнение ссылаются записи
  (pre-check через `exerciseEntryDao.countByExerciseId(id) > 0` ПЕРЕД
  `exerciseDao.delete(...)`, чтобы не парсить текст SQLite FK
  exception); реактивный safety net — `deleteById` обёрнут в
  `recoverCatching`: `SQLiteConstraintException` (запись добавлена в
  параллельной сессии между pre-check и delete) перебрасывается как
  тот же `LinkedEntriesDeletionException` — тот же sealed-вариант и
  текст снекбара, без парсинга текста;
- не найдено — `getById(id) == null` (race между сбором списка и
  подтверждением алерта; rare в офлайн-приложении) →
  `Result.failure(IllegalStateException("Упражнение не найдено"))`,
  маппится в `DeleteExerciseError.Other`. Конкретный текст
  исключения сохраняется в `Log.e`, пользователь видит generic снекбар
  «Не удалось удалить упражнение»;
- прочие — generic `IllegalStateException(message)`.

VM `ExercisesViewModel.confirmDelete` маппит исключение в
`DeleteExerciseError` (sealed-тип в `domain/model/`): `HasLinkedEntries`,
`StandardExercise`, `Other`. Текст оригинального исключения
SHALL логироваться через `Log.e` для диагностики.

UI `ExercisesScreen` (`LaunchedEffect(uiState)`) маппит тип в строку
через `when`. Канон маппинга `DeleteExerciseError →
R.string.exercise_delete_error_*` — норматив в спек-е
`exercises-screen`, requirement «Ошибка удаления — типизированные
причины в снекбаре».

**Обоснование:** Запрос пользователя: «снекбар должен отображать понятную
и краткую причину». Generic «Не удалось удалить упражнение» не говорит,
что делать; конкретные формулировки + понятное действие — «удалите сначала
записи» (FK) или «нельзя удалить стандартное» (отказ по дизайну).
Каждый слой тестируем: репозиторий — JUnit + Room in-memory (типы
исключений), VM — JUnit с fake-репозиторием (маппинг), UI — androidTest
через `SnackbarHostState` (текст). Pending НЕ оставляем открытым на
ошибке: алерт закрыт (или не был открыт, если pre-check сработал —
Decision 8), пользователь сразу может повторить действие.

**Альтернативы:**
- generic text + парсинг `exception.message.contains("FK")` — отклонено:
  хрупко, тексты в коде репозитория русскоязычные, локализация
  невозможна, тесты нестабильны;
- sealed `DeleteExerciseResult` вместо `Result<Unit>` — отклонено: ломает
  существующую сигнатуру ради одной альтернативы; typed exceptions +
  `Result.failure(IllegalStateException)` сохраняют совместимость с
  существующими вызывающими сторонами и unit-тестами репозитория.

### Decision 4: Стандартные упражнения — тишина вместо запретов

**Выбор:** на `isCustom == false` long-press не открывает меню и не
подсвечивает строку; отдельного guard-а «нельзя редактировать стандартное»
в VM не вводится.

**Обоснование:** Удаление стандартного уже отклонено репозиторием
(`IllegalStateException("Стандартное упражнение нельзя удалить")`) — это
последняя линия обороны, UI-защита избыточна. Редактирование стандартного
невозможно по построению: единственная точка входа в edit-режим — пункт
«Изменить», доступный только из меню кастомных. Никакой видимой
дискриминации строк (серый текст, замок) не добавляем — списки записей и
упражнений выглядят одинаково, поведение различается.

**Альтернатива:** показывать меню с disabled-пунктами — отклонена: обучение
пользователя через недоступные действия хуже, чем отсутствие действия.

### Decision 5: `isExerciseMissing` — подсказка + disabled Save, экран не закрываем

**Выбор:** при `getExercise` → `null` (или failure) ставим
`isExerciseMissing = true`; экран показывает подсказку
`exercise_hint_exercise_missing` под формой (новая строка), кнопка
«Сохранить» недостижимо disabled. Автоматического `popBackStack()` нет.

**Обоснование:** Паттерн «целевая строка отсутствует при открытии
edit-режима» реализован в `EntryCreateUiState.kt:31–32, 49` (поле +
KDoc) и `EntryCreateViewModel.kt:78–85` (`getEntry` → `success(null)` /
`failure` в `init {}`); см. также архивный
`openspec/changes/archive/2026-09-06-entry-edit-mode/design.md`
Decision 8 для исходного обоснования. Паттерн `isEntryMissing` уже
решает «целевая строка отсутствует при открытии edit-режима»: подсказка
объясняет, disabled-кнопка делает сохранение невозможным без навигационных
эффектов. Автозакрытие экрана — side-effect из композиции (`LaunchedEffect`
- навигация), мерцает и уводит пользователя без объяснений. Запрос задачи
формулировал «закрыть экран» — сознательно отклоняем в пользу консистентности
с реализованным паттерном.

### Decision 6: Снекбар удаления — one-shot флаг в UiState

**Выбор:** `ExercisesUiState.Success` получает `deleteError: DeleteExerciseError? = null`
(sealed-тип из `domain/model/DeleteExerciseError.kt` с вариантами
`HasLinkedEntries` / `StandardExercise` / `Other` —
см. Decision 3; one-shot-семантика: экран вызывает `onErrorShown()`
после показа снекбара) — в духе конвенции AGENTS.md «одноразовые
события типом enum/sealed-kind». Длительность показа — дефолтная
Material3 (`SnackbarDuration.Short`, без явного параметра `duration =`):
в проекте пока нет ни одного использования Snackbar, и явно выставлять
`Long`/`Indefinite` без пользовательского запроса преждевременно.
`val snackbarHostState = remember { SnackbarHostState() }` — в обёртке
`ExercisesScreen`, дальше передаётся параметром в
`ExercisesScreenContent`, где живёт `Scaffold(snackbarHost = {
SnackbarHost(snackbarHostState) })` — как на `MainScreen` и
`EntryCreateScreen`; показ через `LaunchedEffect(uiState)` (перезапуск
при любой эмиссии UiState; перед повторным показом текущий снекбар
закрывается `currentSnackbarData?.dismiss()`) —
`snackbarHostState.showSnackbar(message)` + `viewModel.onErrorShown()`,
маппинг `DeleteExerciseError → R.string.exercise_delete_error_*` — на
стороне UI (Decision 3). Upgrade path: если позже понадобится другая
длительность — добавляется одним параметром в `showSnackbar`.

**Обоснование:** Snackbar — API Scaffold-а `ExercisesScreen`, но для
тестируемости ошибка проходит через UiState: unit-тест VM проверяет
значение без композиции, androidTest проверяет снекбар напрямую.
Прямой вызов `snackbarHostState.showSnackbar` из VM связал бы
ViewModel с compose-типами — отклонено.

**Альтернатива:** `Boolean` one-shot-флаг — отклонена: не различает
причины ошибки (FK / Standard / Other), пришлось бы делать три отдельных
флага или парсить текст исключения в UI — нарушает Decision 3
(типизированные ошибки). Sealed-тип `DeleteExerciseError` решает оба
требования (one-shot + типизация) в одном поле.

### Decision 7: Строки меню упражнений — отдельные ключи, не переиспользование entry_*

**Выбор:** `exercise_list_menu_edit` / `exercise_list_menu_delete` (ru:
«Изменить»/«Удалить», en: "Edit"/"Delete") — свои ключи, хотя en-значения
совпадают с `entry_list_menu_*`.

**Обоснование:** Значения совпадают сегодня, но экраны развиваются независимо
(у записей меню потенциально расширится на «Дублировать»), и связывать их
одной строкой — ложная экономия. Русские значения и так различаются от
будущих формулировок. Прецедент: строки форм создания/редактирования
никогда не шарятся между entry/exercise.

### Decision 8: Предварительная проверка FK в `requestDelete` — Snackbar без диалога

**Выбор:** `ExercisesViewModel` подписывается на
`repository.exerciseIdsWithLinkedEntries(): Flow<Set<Long>>` —
реактивный запрос `SELECT exercise_id FROM exercise_entries GROUP BY
exercise_id` через новый DAO-метод
`ExerciseEntryDao.observeExerciseIdsWithLinkedEntries(): Flow<List<Long>>`.
В `requestDelete(item)`:
- если `item.id in exerciseIdsWithLinkedEntries.value` — VM SHALL
  устанавливать `deleteError = DeleteExerciseError.HasLinkedEntries`,
  диалог НЕ показывается, снекбар сразу;
- иначе — VM SHALL устанавливать `pendingDeleteId = item.id`,
  диалог показывается как раньше.

Repository `deleteExercise` всё равно возвращает
`LinkedEntriesDeletionException` для гонки (пользователь добавил запись
между pre-check и confirm на другой вкладке/сессии) — Snackbar покажется
после confirm, но текст будет специфичный (FK), не generic.

**Обоснование:** UX — лишний шаг подтверждения для заведомо невозможного
удаления раздражает («Почему я подтвердил, а потом мне сказали нет?»).
Pre-check синхронный (`StateFlow.value`), без async-задержки на тапе.
Подписка — `SharingStarted.Eagerly`, осознанное отклонение от
проектного `WhileSubscribed(5000)`: pre-check читает `.value`
синхронно на тапе, а при `WhileSubscribed` первая эмиссия приходит
только после подписки `uiState` — до этого кеш пуст и первый тап
ложно прошёл бы pre-check (подробнее KDoc
`ExercisesViewModel.exerciseIdsWithLinkedEntries`).
Раса возможна только при внешнем добавлении записи (EntryCreateScreen на
другой вкладке/сессии) — этот случай ловится repository typed error
(Decision 3) и Snackbar всё равно показывается с конкретным текстом.
Standard-упражнения не достигают этого пути из UI (Decision 4: нет
меню), но `StandardExerciseDeletionException` остаётся как safety net.

**Альтернативы:**
- async per-tap `hasLinkedEntries(id): Result<Boolean>` — отклонено:
  10–50 мс задержка после тапа ощутима, требуется loading-state в UI
  (мигание меню);
- скрыть пункт «Удалить» в меню для кастомных с записями (как для
  стандартных — Decision 4) — отклонено: пользователь не видит, что
  удаление вообще возможно; информативнее показать ошибку с
  пояснением «используется в тренировках» — пользователь понимает
  причину и знает, что делать.

## Risks / Trade-offs

- [Long-press на строке LazyColumn конфликтует со скроллом] → Mitigation:
  `detectTapGestures(onLongPress)` не перехватывает drag — паттерн MainScreen
  работает в проде; покрывается androidTest-ом.
- [DropdownMenu у нижней границы списка обрезается] → Mitigation: Material3
  `DropdownMenu` сам переворачивается; офсет якоря хранится в локальном
  `ContextMenuState` экрана, как на MainScreen.
- [Флаг `deleteError` может «мигнуть» дважды при реконфигурации] → Mitigation:
  one-shot-семантика (`onErrorShown()` после показа) — как у остальных
  одноразовых событий проекта.
- [RENAME требования «Сохранение» в спеке] → архивация применит
  RENAMED + MODIFIED к main specs; `openspec validate` перезапускается
  на archive-шаге (forward-looking, до архивации не выполняется).

## Migration Plan

Изменение аддитивное, без схемы БД и миграций. Rollback — revert коммитов:
маршрут с дефолтным аргументом обратно деградирует до `"exercise_create"`
без правок вызывающих сторон (FAB использует `createRoute()`).
