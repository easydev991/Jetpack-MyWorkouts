## 1. Testbed setup (DataStore + конструктор VM)

- [x] 1.0 (TDD — красные тесты DataStore, по
      `specs/appearance/spec.md` сценарии «dontShowArchiveDialog
      returns false on first launch» и «setDontShowArchiveDialog
      persists and Flow re-emits») Создать
      `app/src/test/.../AppSettingsDataStoreTest.kt`: поднять
      реальный `AppSettingsDataStore` поверх
      `PreferenceDataStoreFactory.create(produceFile = { tempFile })`
      (без Robolectric — `PreferenceDataStoreFactory` достаточно
      `TestScope` + temp-файл из `kotlinx-coroutines-test`);
      добавить тесты:
      (a) `dontShowArchiveDialog_first_collect_returns_false` —
      `Flow.first()` без предварительной записи возвращает `false`;
      (b) `setDontShowArchiveDialog_true_then_collect_returns_true` —
      после `dataStore.setDontShowArchiveDialog(true)` `Flow.first()`
      эмитирует `true`;
      (c) `setDontShowArchiveDialog_false_after_true_returns_false` —
      смена значения в ту и другую сторону. Запустить через
      `make test` (или
      `./gradlew testDebugUnitTest --tests "*AppSettingsDataStoreTest*"`)
      и убедиться, что они падают (красные) — нет ни `Flow`, ни
      сеттера. Существующие тесты в проекте мокают
      `AppSettingsDataStore` и эти сценарии не покрывают
      (Capability spec `appearance` уже содержит оба сценария как
      дельту — без собственных тестов они остаются непроверенными).
- [x] 1.1 Добавить в `AppSettingsDataStore` (`data/preferences/AppSettingsDataStore.kt`)
      свойства `val dontShowArchiveDialog: Flow<Boolean>` (default `false`)
      и `suspend fun setDontShowArchiveDialog(value: Boolean)`,
      работающие поверх того же `DataStore<Preferences>` с именем
      `appearance_prefs` (без миграции); прогнать `make test` и
      убедиться, что существующие тесты (в т.ч. `AppearanceViewModelTest`,
      как главный потребитель `AppSettingsDataStore` через mock) не
      сломаны.
- [x] 1.2 Расширить конструктор `ExercisesViewModel` параметром
      `dataStore: AppSettingsDataStore`; добавить factory
      `fun factory(repository: WorkoutDataRepository, dataStore:
      AppSettingsDataStore): ViewModelProvider.Factory` через
      `viewModelFactory { initializer { ExercisesViewModel(repository,
      dataStore) } }`; в существующих тестах `ExercisesViewModelTest`
      добавить `mockDataStore: AppSettingsDataStore = mockk(relaxed = true)`
      рядом с `repository` и прокинуть его в `ExercisesViewModel(...)`;
      убедиться, что сборка компилируется (`make test`) — на этом
      этапе DataStore пока не используется VM, это нужно только
      для компиляции тестов 2.x.
- [x] 1.3 Перевести `confirmArchive()` в
      `confirmArchive(suppress: Boolean = false)` (сигнатура — без
      нового поведения записи pref, `suppress` пока игнорируется);
      убедиться, что `make test` зелёный и существующие call-сайты
      `ExercisesScreen.kt:173` (`viewModel::confirmArchive`) не
      сломаны (default-значение `false`).

## 2. Тесты ExercisesViewModel (TDD — красные)

- [x] 2.1 В `app/src/test/.../ExercisesViewModelTest.kt` добавить
      тест `requestArchive_when_dontShowDialog_true_then_archive_without_dialog`:
      stub `dataStore.dontShowArchiveDialog` возвращает
      `true` → `repository.setArchived` вызван сразу с
      `(item.id, true)`, `confirmArchive` НЕ вызван, итоговый
      `pendingArchiveId == null`; запустить тест через
      `./gradlew testDebugUnitTest --tests "*requestArchive_when_dontShowDialog_true*"`
      и убедиться, что он падает (красный) по поведению
      (`pendingArchiveId` пока не сбрасывается / `setArchived`
      пока не вызывается), а не из-за ошибки компиляции.
- [x] 2.2 Добавить тест
      `requestArchive_when_dontShowDialog_false_then_open_dialog`:
      stub возвращает `false` → `repository.setArchived` НЕ вызван,
      `pendingArchiveId.value == item.id`; запустить тест и убедиться,
      что он падает (красный).
- [x] 2.3 Добавить тест
      `confirmArchive_when_suppressTrue_then_writes_pref_and_archives`:
      `confirmArchive(suppress = true)` → `repository.setArchived`
      вызван с `(id, true)`, `dataStore.setDontShowArchiveDialog(true)`
      вызван ровно один раз (`verify(exactly = 1)`); запустить тест
      и убедиться, что он падает (красный).
- [x] 2.4 Добавить тест
      `confirmArchive_when_suppressFalse_then_does_not_write_pref`:
      `confirmArchive(suppress = false)` → `repository.setArchived`
      вызван, `dataStore.setDontShowArchiveDialog` НЕ вызван
      (`verify(exactly = 0)`); тест — **regression-guard**, не
      TDD-red: на этапе §1.3 (`fun confirmArchive(suppress: Boolean
      = false)` без поведения записи pref) тест УЖЕ зелёный —
      `suppress = false` соответствует текущему поведению
      `confirmArchive()`. Запустить и убедиться, что остаётся
      зелёным после §3.2 (защита от лишней записи pref).

## 3. Реализация логики requestArchive / confirmArchive

- [x] 3.1 В `requestArchive(item)` ДО открытия диалога читать
      `dataStore.dontShowArchiveDialog.first()` внутри
      `viewModelScope.launch`; при `true` — немедленно вызвать
      `repository.setArchived(item.id, true)` и НЕ устанавливать
      `pendingArchiveId`; при `false` — установить
      `pendingArchiveId = item.id` (текущее поведение); убедиться,
      что `make test` зелёный (тесты 2.1–2.4 проходят).
- [x] 3.2 Реализовать поведение `suppress` в `confirmArchive(suppress:
      Boolean = false)` (сигнатура уже введена в §1.3): при `suppress
      = true` дополнительно вызвать `dataStore.setDontShowArchiveDialog(true)`
      в том же coroutine после dismiss (suspend-вызов, UI не
      блокируется); убедиться, что `make test` зелёный
      и тесты 2.3–2.4 проходят.

## 4. Реализация диалога и локализация

- [x] 4.1 В `ExercisesScreen.kt` (`ui/screens/exercises/`) расширить
      `ExerciseConfirmDialog` опциональным параметром
      `suppressContent: (@Composable () -> Unit)? = null`; при
      `null` рендеринг байт-в-байт прежний; при не-`null` —
      слот отрисовывается между текстом и кнопками; убедиться, что
      существующие call-сайты диалога удаления продолжают
      компилироваться (default-значение `null`).
- [x] 4.2 Добавить строку `exercise_archive_dont_show_again` в
      `app/src/main/res/values/strings.xml` («Больше не показывать»)
      и `app/src/main/res/values-en/strings.xml` ("Don't show
      again"); убедиться, что `make lint` (detekt) не сообщает о
      проблемах.
- [x] 4.3 В `SuccessBody` (узел, рендерящий алерт архивации, см.
      `ExercisesScreen.kt:347`) при показе архив-диалога передать
      в `suppressContent` мини-`Row { Checkbox(...) + Text(stringResource(R.string.exercise_archive_dont_show_again)) }`
      с локальным `var suppress by remember { mutableStateOf(false) }`
      в **scope `SuccessBody`** (вне composable-лямбды слота) —
      и слот, и `onConfirm`-лямбда SHALL захватывать одну и ту же
      `MutableState`-переменную через замыкание; сигнатура
      `ExerciseConfirmDialog.onConfirm` SHALL остаться `() -> Unit`
      (диалог не знает про `suppress`). Обоснование — `design.md`
      Decision 4. Изменить **три места в `ExercisesScreen.kt`**:
      (a) line 221 — сигнатура поля
      `ExercisesScreenState.onConfirmArchive` (`() -> Unit` →
      `(Boolean) -> Unit`);
      (b) line 173 — без правок;
      (c) line 357 — `onConfirm = state.onConfirmArchive` заменить
      на `onConfirm = { state.onConfirmArchive(suppress) }` —
      лямбда `() -> Unit` (совместима с диалогом), внутри читает
      захваченную `suppress` и передаёт в стейт-холдер.
      Line 342 (`onConfirm = state.onConfirmDelete`, диалог
      удаления) и оба превью `ExercisesScreenPreview.kt:193, 214`
      (используют `onConfirm = {}` для диалога) — без правок.
      Сам вызов архив-диалога на line 350 дополняется параметром
      `suppressContent = { Row { Checkbox(...) ; Text(...) } }`.
      Убедиться,
      что `make format && make lint` зелёные.

## 5. Композиционный корень и проверка сборки

- [x] 5.1 В `MainActivity.kt` — без изменений (dataStore уже
      доходит до `RootScreen` через существующую DI-цепочку
      `MyWorkoutsApplication` → `MainActivity.kt:42–44, 69`).
      В `RootScreen.kt`
      выполнены шаги (a)–(c) из `proposal.md` Impact (расширен
      `ExercisesNavDestination` (line 210) параметром `dataStore:
      AppSettingsDataStore`; передан из `AppNavHost`
      (line 142-147); внутри `ExercisesNavDestination` — factory
      `(repository, dataStore)`); debug-APK собирается через
      `make build` без ошибок.
- [x] 5.2 APK установлен на эмулятор через `make install` и
      визуально проверено: открыт экран «Упражнения», long-press
      на строке → тап «Архивировать», поставлена галочка «Больше не
      показывать», подтверждено → повторный long-press и тап
      «Архивировать» НЕ открывает диалог, упражнение архивируется
      сразу; скриншоты до/после сделаны через
      `android-debug-bridge_capture_screenshot` (см.
      `archive-alert-hide/00[1-9]_*.png`).

## 6. Превью (покрытие КРИТИЧНО)

- [x] 6.1 В существующем или соседнем `*Preview.kt` файле (рядом с
      `ExercisesScreen.kt`) добавлено превью для `ExerciseConfirmDialog`
      (generic, без `suppressContent`) — `Light` + `Dark`
      (`UI_MODE_NIGHT_YES`), `showBackground = true`, `locale = "ru"`,
      обёртка в `JetpackMyWorkoutsTheme`; требование покрыто
      `ExerciseDeleteDialogPreview` — единственным достижимым
      вариантом без слота (удаление); бывшее
      `ExerciseArchiveDialogPreview` (архивация без слота) удалено —
      состояние недостижимо, архивация всегда рендерится со слотом
      (см. `design.md` Risks). Превью рендерятся в Android Studio.
- [x] 6.2 Добавлен превью `ExerciseArchiveDialogWithSuppressPreview`
      (windowless-сэмпл `ExerciseConfirmDialogSample`): `ExerciseConfirmDialog` с
      заголовком/текстом архивации и заполненным `suppressContent`
      слотом (чекбокс снят, `checked = false`) — `Light` + `Dark`;
      превью рендерится.
- [x] 6.3 Добавлен второй вариант `ExerciseArchiveDialogWithSuppressPreviewChecked`
      — тот же узел, чекбокс поставлен (`checked = true`) — `Light` +
      `Dark`; превью рендерится.
- [x] 6.4 Запущен `make format && make lint && make test` и
      подтверждено, что весь pipeline зелёный (no new detekt warnings
      по новым composable/превью — 5 существующих warnings в
      `RootScreen.kt`, `ExerciseCreateViewModel.kt`, `EntryCreateFields.kt`,
      `RoomWorkoutDataRepository.kt` не относятся к этому change'у).

> Заметка: обоснование состава превью (3 варианта, недостижимый
> generic + slot) — см. `design.md` Risks, 4-й пункт.

## 7. Опционально — интеграционный UI-тест

- [ ] 7.1 (Опционально, не блокер) Расширить
      `app/src/androidTest/.../ExercisesScreenTest.kt` тестом
      `archive_dialog_when_check_dont_show_then_confirm_archives_and_keeps_flag`:
      long-press на строке → тап «Архивировать» → поставить чекбокс
      → подтвердить → диалог закрыт, упражнение пропало;
      повторный long-press на той же/другой строке → тап
      «Архивировать» НЕ открывает диалог, упражнение архивируется
      сразу. Запустить через `make android-test` и убедиться, что
      тест зелёный; если подключение эмулятора недоступно — отметить
      как отложенное (`cancelled`).
