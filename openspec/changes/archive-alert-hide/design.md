## Context

Текущий `ExerciseConfirmDialog` (используется и для алерта удаления,
и для алерта архивации) — приватный composable с фиксированной
структурой «заголовок + текст + кнопки». Подтверждение архивации в
`ExercisesViewModel` всегда идёт через
`requestArchive → pendingArchiveId → confirmArchive →
repository.setArchived(id, true)`, без какой-либо памяти о том,
что пользователь уже подтверждал ту же операцию раньше.
`AppSettingsDataStore` хранит `theme`, `useDynamicColors` и
`showExercisesTab` в файле `appearance_prefs`. Расширение этого DataStore новым ключом
`dontShowArchiveDialog` (default `false`) без миграции — это
минимальное изменение для opt-out.

## Goals / Non-Goals

**Goals:**

- Прокинуть `AppSettingsDataStore` в `ExercisesViewModel` через
  manual DI (composition root в `MainActivity` → `RootScreen` →
  `ExercisesScreen`), без Hilt.
- Расширить `AppSettingsDataStore` одним `Flow<Boolean>` и
  `suspend`-сеттером.
- Добавить в `ExerciseConfirmDialog` опциональный слот
  `suppressContent` (НЕ generic-параметр) так, чтобы диалог удаления
  остался без изменений, а диалог архивации получил под текстом
  мини-`Row` с `Checkbox` + `Text`.
- Локальное состояние чекбокса — `remember { mutableStateOf(false) }`
  в scope `SuccessBody`, рядом с вызовом `ExerciseConfirmDialog`
  (вне composable-лямбды слота, НЕ в `ExercisesUiState`).
- Покрыть превью все ключевые состояния (см. `tasks.md`).
- TDD для логики `requestArchive` / `confirmArchive`.

**Non-Goals:**

- Per-exercise флаг (только глобальный).
- Счётчик показов / автоскрытие после N раз.
- Изменение UI/UX диалога удаления (slot = `null`).
- `Result`-враппер для архивации (архив обратим — failure
  проглатывается, как и сейчас).
- Откат pref при ошибке записи архивации.
- Миграция DataStore (новый ключ — schema extend).
- Hilt / Koin / любая DI-библиотека.

## Decisions

1. **`AppSettingsDataStore` как единое место для pref архивации** —
   альтернатива «новый DataStore только для этого флага» отвергнута:
   это размножает файлы настроек и нарушает контракт capability
   `appearance` (DataStore для prefs приложения живёт в одном
   `appearance_prefs`). Без миграции — DataStore Preferences
   автоматически подхватывает новый ключ со значением по умолчанию.
   Default `false` гарантирует обратную совместимость: при апдейте
   приложения пользователь видит диалог как раньше, пока сам не
   поставит галочку.

2. **Чтение pref в `requestArchive` через `.first()`, а не
   `collect`** — нам нужен снимок на момент нажатия, а не
   реактивная подписка. `.first()` дешевле, не держит state-flow в
   `ExercisesUiState`, и при изменении pref во время открытого
   диалога пользователь всё равно успеет завершить текущее
   подтверждение. Альтернатива «подписаться на
   `dataStore.dontShowArchiveDialog` в combine внутри `uiState`»
   отвергнута: реактивное обновление флага во время открытого диалога
   не нужно, а подписка раздула бы `ExercisesUiState`.

3. **`ExerciseConfirmDialog.suppressContent: (@Composable () -> Unit)?`
   вместо generic-параметра** — узкая, локализованная точка
   расширения. Generic-параметр или sealed-тип «режим диалога» дали
   бы диалогу знание про «архивацию», что запрещено требованиями
   AGENTS.md и самой идеей переиспользуемого composable. При
   `suppressContent = null` рендеринг байт-в-байт прежний — диалог
   удаления остаётся без изменений. Альтернатива «отдельный
   `ExerciseArchiveConfirmDialog` composable» отвергнута:
   дублирование разметки (текст + кнопки) при единственном
   отличии — слот под текстом.

4. **`remember` живёт в scope `SuccessBody` (рядом с вызовом
   `ExerciseConfirmDialog`), а НЕ внутри composable-лямбды слота** —
   иначе значение чекбокса невозможно прочитать снаружи лямбды
   и передать дальше. `var suppress by remember { mutableStateOf(false) }`
   создаётся один раз в scope `SuccessBody` и захватывается двумя
   замыканиями: (a) composable-лямбда слота `suppressContent`
   читает/пишет его через `Checkbox(checked = suppress,
   onCheckedChange = { suppress = it })`; (b) колбэк подтверждения
   читает его и передаёт значение в стейт-холдер. Колбэк
   стейт-холдера `ExercisesScreenState.onConfirmArchive` SHALL
   стать `(Boolean) -> Unit` (вместо `() -> Unit`), чтобы значение
   `suppress` физически дошло до `viewModel::confirmArchive` —
   иначе method-reference `viewModel::confirmArchive` (текущая
   `fun confirmArchive()` без параметров, line 173) проигнорирует
   `suppress`, и pref никогда не запишется. Сигнатура самого
   диалога `ExerciseConfirmDialog.onConfirm` остаётся `() -> Unit`
   — диалог не знает про `suppress`. В `ExercisesScreen.kt:357`
   вместо `onConfirm = state.onConfirmArchive` пишется
   `onConfirm = { state.onConfirmArchive(suppress) }` — эта
   лямбда совместима с `() -> Unit` диалога. Method-reference
   `viewModel::confirmArchive` (line 173) компилируется без
   правок: после `fun confirmArchive(suppress: Boolean = false)`
   (§1.3) Kotlin-адаптирует ссылку к типу поля `(Boolean) -> Unit`.
   Чекбокс ephemeral — при закрытии алерта (`pendingArchiveId =
   null`) `SuccessBody` перестаёт рендерить диалог, `remember`
   исчезает естественным образом. Хранить это в VM — overengineering
   (UI state). Сводка правок по строкам: `ExercisesScreen.kt:221`
   — сигнатура поля `ExercisesScreenState.onConfirmArchive`
   (`() -> Unit` → `(Boolean) -> Unit`); line 173 — без правок
   (method-reference адаптируется к новой сигнатуре поля);
   line 357 — `onConfirm = { state.onConfirmArchive(suppress) }`;
   line 342 (delete) — без правок (`onConfirmDelete: () -> Unit`
   не меняется); превью `ExercisesScreenPreview.kt:193, 214`
   (диалог удаления и диалог архивации) — без правок: обе
   используют `onConfirm = {}` для диалога, чья сигнатура
   `() -> Unit` не изменилась.

5. **`confirmArchive(suppress: Boolean = false)` вместо двух методов
   или `Pair<Boolean, Boolean>`-флага** — единая точка входа
   (сохраняет обратную совместимость call-сайта из `SuccessBody`),
   один параметр с очевидным default. Параллельная запись pref через
   `viewModelScope.launch { dataStore.setDontShowArchiveDialog(true) }`
   без `await` — архивация не должна блокироваться записью pref;
   если запись pref упадёт, архив уже выполнен (и обратим вручную).

6. **Manual DI без Hilt** — фабрика `ExercisesViewModel` принимает
   `repository` и `dataStore`, компонуется в `RootScreen`. Single
   instance `AppSettingsDataStore` создаётся в `MainActivity.onCreate`
   и прокидывается вниз — соответствует требованию capability
   `appearance` (ровно один экземпляр на процесс).

## Risks / Trade-offs

- [Risk] При первом `requestArchive` чтение pref через `.first()`
  добавляет IO на UI-потоке (DataStore flush + read). →
  [Mitigation] `requestArchive` запускается в `viewModelScope.launch`
  (контракт зафиксирован в `tasks.md` §3.1 и
  `specs/exercises-screen/spec.md` — требование «Алерт
  подтверждения архивации»), поэтому `.first()` не блокирует
  UI-поток. DataStore `first()` на горячем Flow — дешёвая операция;
  компромисс не требует отдельного state в VM.

- [Risk] `confirmArchive(suppress = true)` пишет pref параллельно с
  архивацией; если запись pref упадёт (например, диск переполнен),
  пользователь не получит opt-out. → [Mitigation] Принимаем
  сознательно: архив уже выполнен (и обратим вручную), pref можно
  восстановить через UI в следующих итерациях; откатывать архивацию
  при ошибке записи pref — overengineering и потенциально опасно
  (двойная запись в БД).

- [Risk] Recompose при записи pref заставляет всех подписчиков
  перерисоваться. На текущем экране никто не подписан на
  `dontShowArchiveDialog` (только одноразовое чтение в
  `requestArchive`). →   [Mitigation] Список UI-подписчиков пуст,
  recompose не происходит (проверено: `ExercisesScreen` не собирает
  `dataStore.dontShowArchiveDialog` — контракт чтения через `.first()`
  зафиксирован в `specs/exercises-screen/spec.md`, требование
  «Алерт подтверждения архивации»).

- [Risk] Превью-покрытие требует явного `@Preview` для всех
  достижимых вариантов диалога (без слота — диалог удаления, со
  слотом unchecked/checked — диалог архивации). Без них фича не
  считается готовой. → [Mitigation] `tasks.md` §6 перечисляет
  6 превью (3 варианта `ExerciseConfirmDialog` — generic без слота,
  архив-диалог со слотом/unchecked, архив-диалог со слотом/checked
  — × `Light`/`Dark`). Универсальный «со слотом + не-архивный
  заголовок» в приложении недостижим (удаление всегда `null`-слот,
  архивация — всегда со слотом), поэтому такой пары превью нет.
  Превью `SuccessBody` невозможно (private fun) — проверка полного
  экрана делается вручную в §5.2 и опциональным androidTest в §7.1.

## Migration Plan

Не применимо — изменения обратно совместимы:

- Существующие пользователи получают default `false` для
  `dontShowArchiveDialog` (DataStore Preferences подхватывает новый
  ключ без миграции) → диалог архивации показывается как раньше.
- `confirmArchive()` остаётся вызываемым без аргументов (`default =
  false`) → старые call-сайты компилируются без изменений.
- `ExerciseConfirmDialog` со старой сигнатурой (без
  `suppressContent`) не существует — параметр имеет default `null`,
  диалог удаления вызывается с `suppressContent = null` (или
  опускает параметр).

Rollback: удаление pref-ключа из DataStore (новые версии его просто
не пишут/не читают), `confirmArchive` без `suppress`. Pref в уже
записанных `appearance_prefs` остаётся как мёртвый ключ — без
последствий.

## Open Questions

Нет. Все решения, влияющие на спеки (`AppSettingsDataStore` extension,
`ExercisesViewModel` factory, slot-параметр диалога, локализация),
зафиксированы в `proposal.md` и `specs/`.
