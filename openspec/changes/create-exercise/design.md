## Context

В проекте уже есть зрелый образец не-tab экрана с формой —
`EntryCreateScreen` + `EntryCreateViewModel` + `EntryCreateUiState`.
Все конвенции (обёртка +
internal `Content` + data class `ScreenState` + `Scaffold` с `topBar`
/ `bottomBar`, `SaveBottomBar` поверх клавиатуры, manual DI через
`viewModelFactory`, реактивная подписка на `repository.exercises(...)`)
устоялись. Этот change повторяет тот же паттерн для нового экрана
`ExerciseCreateScreen` и подключает существующий FAB на `ExercisesScreen` к
навигации.

Каталог упражнений уже отсортирован на SQL-стороне (Decision 5 в
`openspec/changes/archive/2026-09-02-workout-data-layer/design.md`,
L112–113 — «Сортировка/поиск — в SQL: `ORDER BY name COLLATE NOCASE`»):
фильтрация и сортировка по имени не делаются в Kotlin — это YAGNI в
приложении на одного пользователя. Этот change
расширяет существующий `ORDER BY` единственным изменением — добавляется
выделение кастомных упражнений (`is_custom DESC`) и сортировка внутри
группы кастомных по `created_at DESC`.

Изменения в data-слое — только строка SQL-запроса, никаких миграций
(`ExerciseEntity` уже содержит `isCustom` и `createdAt`).

## Goals / Non-Goals

**Goals:**

- Экран создания упражнения с валидацией формы и сохранением через
  существующий `repository.addExercise`.
- Подключение FAB к навигации на новый экран (через колбэк сверху, как
  `MainScreen → EntryCreateScreen`).
- Сортировка каталога: кастомные сверху, внутри каждой группы — по
  убыванию `createdAt`, при равных `createdAt` — по `name` без учёта
  регистра (COLLATE NOCASE) как финальный tie-break в обеих группах.
  Реализация — на SQL-стороне одним изменением `ORDER BY`
  (полные критерии — канон в спеке `specs/exercises-screen/spec.md`).
- Полное покрытие TDD: red-тесты перед реализацией (JVM unit для
  ViewModel/сортировки, androidTest для UI).

**Non-Goals:**

- Архивирование, удаление, редактирование существующих упражнений из UI —
  отдельные future changes.
- Поиск по каталогу / фильтрация по `category` / `measuredBy` /
  группировка.
- Ручная сортировка/перетаскивание упражнений.

## Decisions

### Decision 1: Сегментированная кнопка (SingleChoiceSegmentedButtonRow) для ExerciseMeasuredBy, не Dropdown

**Выбор:** `SingleChoiceSegmentedButtonRow` из Material3 с двумя сегментами
(`REPS` / `TIME`), по умолчанию активен `REPS`.

**Обоснование:** Явное требование пользователя. Сверх того — у варианта
всего два значения, и они меняют семантику ввода на последующих шагах
(хотя в этом change «последующих шагов» нет; для будущего расширения
записи это критично). Сегментированная кнопка показывает текущее
состояние всегда, не требует дополнительного тапа для просмотра
альтернативы.

**Альтернативы рассмотрены:**

- `ExposedDropdownMenuBox` (как для `category`) — отклонён пользователем.
- `RadioButton` в `Row` — визуально слабее, без явного состояния
  «выбран/не выбран» из коробки.

### Decision 2: Сортировка каталога — на SQL-стороне одним ORDER BY

**Выбор:** Изменить `ExerciseDao.observeExercises` так, чтобы новый
`@Query` содержал `ORDER BY is_custom DESC, created_at DESC, name COLLATE
NOCASE`. Никаких изменений в `ExercisesUiState` или `ExercisesViewModel`
не требуется: они уже подписываются на `repository.exercises("")` и
получают список в нужном порядке.

**Обоснование:** Текущий `ExerciseDao` уже сортирует на SQL-стороне
(Decision 5 в `archive/2026-09-02-workout-data-layer/design.md`,
L112–113). Расширение `ORDER BY` — естественное продолжение.
Двухэтапная сортировка (SQL по имени, потом Kotlin-`partition` по
`isCustom`) — лишняя работа на UI-потоке и легко рассинхронизируется
с фильтрацией/поиском.

**Альтернативы рассмотрены:**

- Двухэтапная сортировка (SQL → Kotlin-`partition`) — лишний проход и
  смешение логики в `ExercisesViewModel`.
- Только Kotlin-сортировка всего списка — откат от уже принятого
  Decision 5 в `archive/2026-09-02-workout-data-layer/design.md`,
  противоречит существующему контракту метода `exercises(query)`
  (документация: «отсортированный по имени»; сам kdoc правится в
  tasks.md 2.2, но общий принцип «сортировка в SQL» остаётся).

### Decision 3: Route `Screen.ExerciseCreate` — без аргументов

**Выбор:** `object ExerciseCreate : Screen(route = "exercise_create")`,
без `navArgument`. Никаких `createRoute(...)`, `ARG_*`, `NO_*_ID`.

**Обоснование:** В этом change нет ни редактирования, ни режима
выбора по id — экран всегда в режиме «создание нового». Аргументы добавляются
только при реальной потребности (аналог `EntryCreate.route` имеет
`entryId`, потому что обслуживает и создание, и редактирование).

**Альтернативы рассмотрены:**

- Унаследовать форму `EntryCreate.route` с `?exerciseId={exerciseId}` и
  sentinel `NO_EXERCISE_ID = -1L` — лишний код на будущее. YAGNI:
  добавление — локальное изменение `Screen.ExerciseCreate` в одном
  файле.

### Decision 4: ViewModel + factory по образцу `EntryCreateViewModel`

**Выбор:** `ExerciseCreateViewModel(repository: WorkoutDataRepository)` с
фабрикой через `viewModelFactory { initializer { ... } }` (exposed как
`companion object fun factory(repository): ViewModelProvider.Factory`).
Фабрика создаётся внутри `composable(Screen.ExerciseCreate.route) { ... }`
блока `NavHost` в `RootScreen.AppNavHost`, по образцу блока
`composable(Screen.EntryCreate.route, arguments = ...) { ... }` в
`RootScreen.AppNavHost` (где фабрика
`EntryCreateViewModel.factory(repository, entryId)` строится прямо в
`composable`-блоке и `viewModel(factory = ...)` передаётся в
`EntryCreateScreen`). Сам `ExerciseCreateScreen` (как и `EntryCreateScreen`)
фабрику не строит — принимает готовый `viewModel` параметром, чтобы экран
оставался тестируемым и переиспользуемым без NavHost. `MainActivity` не
нуждается в изменениях — композиционный корень уже знает, как прокидывать
репозиторий.

**Обоснование:** Manual DI без Hilt уже используется во всём проекте.
Альтернативы (singleton-VM, SavedStateHandle, AssistedInject) — избыточны
для одного экрана.

### Decision 5: SaveBottomBar — единственная кнопка, поверх клавиатуры

**Выбор:** `Column` с модификаторами
`.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal
= 16.dp, vertical = 8.dp)` и `verticalArrangement =
Arrangement.spacedBy(4.dp)` — контейнер-близнец
`EntryCreateScreen.SaveBottomBar` (`EntryCreateScreen.kt` L295–324:
`Column` с `Button` + `SaveHintsSection` + опциональным `Text` ошибки;
у нового экрана секции подсказок нет). Содержит ровно одну
`Button(fillMaxWidth, enabled = canSave)` и опциональный блок ошибки
под ней (канон — спека, требование «Кнопка «Сохранить» и блок ошибки в
bottomBar»). Никаких вспомогательных «отмена»/«сброс» —
навигация назад уже доступна через стрелку `TopAppBar` (как в
`EntryCreateScreen`).

**Обоснование:** Прямое требование пользователя и соответствие
`EntryCreateScreen`. Никакой новой проводки не нужно — паттерн
переиспользуется один-в-один.

### Decision 6: Новый `CategoryDropdownField` по образцу `DurationDropdownField`

**Выбор:** В проекте нет существующего dropdown-компонента для выбора
`ExerciseCategory` (в `EntryCreateScreen` категория определяется
неявно через выбранное `Exercise` в `ExerciseDropdownField` — функция
`ExerciseDropdownField` в `EntryCreateFields.kt`). Создаём новый
internal-компонент
`CategoryDropdownField` в файле `ExerciseCreateScreen.kt`, пакет
`ui/screens/exercises/` — паттерн-близнец `DurationDropdownField`
(функция `DurationDropdownField` в `EntryCreateFields.kt`):
тот же `ExposedDropdownMenuBox` + `OutlinedTextField(readOnly = true,
label = ...)`, итерация по `ExerciseCategory.entries`, `DropdownMenuItem`
с локализованным `label` через приватный file-level хелпер
`ExerciseCategory.labelResId(): Int @StringRes`. Полная сигнатура
`when`-выражения и список строковых ресурсов зафиксированы в спеке
`specs/exercise-create-screen/spec.md` (Requirement «Категория через
ExposedDropdownMenuBox — обязательная») — это единственный нормативный
источник, чтобы при добавлении нового значения `ExerciseCategory`
(например `MOBILITY`) правка делалась в одном месте, а не в трёх.
Здесь — короткая фиксация контекста: лейблы в русской локали
соответствуют kdoc-описаниям в `ExerciseCategory.kt` (Толкающие/
Тянущие/Ноги/Кор/Кардио) — это семантически согласованная локализация,
а не дословный перевод `enum.name` (`PUSH`/`PULL`/`LEGS`/`CORE`/`CARDIO`
отображались бы как английские ключи и непонятны русскому пользователю).
Способ хранения `ExerciseCategory` в БД (enum, не локализованная строка)
зафиксирован в спеке `specs/exercise-create-screen/spec.md`,
требование «Категория через ExposedDropdownMenuBox — обязательная»
(канон).

**Обоснование:** В проекте установился один способ выбора значения из
`enum` через `ExposedDropdownMenuBox` (`DurationDropdownField`,
`ExerciseDropdownField`). Следование этому паттерну — требование
консистентности UX (тот же control, та же анимация, тот же layout).

**Альтернативы рассмотрены:**

- Извлечение общего `SelectFromEnumField<T : Enum<T>>` в `ui/components/` —
  правильнее в долгосрочной перспективе, но это уже отдельный
  refactoring change (затрагивает `EntryCreateFields.kt` и существующие
  тесты). В рамках этого change — близнец; рефакторинг откладывается.
- Использование `DropdownMenu`/`Menu` вместо `ExposedDropdownMenuBox` —
  отклонено как рассинхрон с существующим UX dropdown в проекте.

### Decision 7: createdAt = System.currentTimeMillis() в момент сохранения

**Выбор:** Поле `createdAt` заполняется в `ExerciseCreateViewModel.save(...)`
прямо перед вызовом `repository.addExercise(exercise)`. Никаких дефолтов в
data-слое, никаких сдвигов на стороне Room.

**Обоснование:** `System.currentTimeMillis()` — стандартный способ для
offline-only приложения без NTP-синхронизации. Тест-кейс `1.1` сравнивает
`Exercise.createdAt` (зафиксированный внутри `save(...)`) с
`System.currentTimeMillis()`, захваченным **после** `save(...)` — допуск
`±5s` покрывает разрыв между моментом создания `Exercise` и моментом
чтения времени в тесте: время fake-репозитория, scheduling JVM и GC-паузы.
YAGNI — нет причин тянуть `java.time.Instant` ради одного поля.

**Альтернативы рассмотрены:**

- `Instant.now().toEpochMilli()` (java.time) — функционально
  эквивалентен и доступен нативно при minSdk = 26
  (`minSdk` в `app/build.gradle.kts`); отклонён не из-за совместимости, а
  ради меньшего импорта и YAGNI (одно поле, без других java.time
  потребителей в этом use-case).

### Decision 8: BackHandler + hasUnsavedChanges — паттерн EntryCreateScreen

**Выбор:** `ExerciseCreateScreenContent` SHALL содержать
`BackHandler(enabled = state.uiState.hasUnsavedChanges) { attemptClose() }`
и `AlertDialog` через новый internal-компонент
`ExerciseCreateCloseConfirmDialog` (паттерн-близнец
`EntryCloseConfirmDialog` из `EntryCreateScreen.kt` L170–195).
`hasUnsavedChanges` — computed-свойство в `ExerciseCreateUiState`
(рядом с `canSave`); **полная формула — канон в требовании
«BackHandler + hasUnsavedChanges — подтверждение закрытия» спеки
(`specs/exercise-create-screen/spec.md`)**; это Design ссылается на
канон, а не дублирует формулу. Диалог переиспользует существующие
строки `R.string.entry_confirm_close_dialog_title`,
`R.string.entry_confirm_close_dialog_message`,
`R.string.entry_confirm_close`, `R.string.cancel` (`values/strings.xml`
L68–70, 72) — текст универсален для любой формы (прецедент —
`R.string.back` и `R.string.entry_save` в этой же форме).

**Обоснование:** Прямое требование пользователя. UX-симметрия двух
не-tab форм (`EntryCreateScreen`, `ExerciseCreateScreen`): обе формы
должны одинаково защищать введённые данные при системном back или
тапе стрелки `TopAppBar`. Без диалога пользователь теряет данные при
свайпе назад на Android 14+ — это регрессия по сравнению с
существующим экраном.

**Альтернативы рассмотрены:**

- Не добавлять BackHandler (non-goal, осознанное упрощение) —
  отклонено: UX-рассогласование с `EntryCreateScreen`, пользователь
  явно запросил симметрию.
- Извлечь `CloseConfirmDialog` в `ui/components/` для переиспользования
  между `EntryCreateScreen` и `ExerciseCreateScreen` — отложено как
  отдельный refactoring change (затрагивает `EntryCreateScreen.kt` и
  существующие тесты); в рамках этого change — близнец; рефакторинг
  откладывается, паттерн аналогичен Decision 6 про `CategoryDropdownField`.

### Decision 9: Без автоскролла каталога к новому упражнению

**Выбор:** После сохранения упражнения `ExercisesScreen` НЕ скроллится
автоматически к новой строке — позиция прокрутки `LazyColumn`
сохраняется (закрепление за первым видимым элементом), новое
упражнение вставляется первым и оказывается выше текущего viewport.
Чтобы увидеть его, пользователь проскролливает к началу списка.
Реактивность списка не нарушена: эмиссия со вставленной строкой
доходит до UI за ~100 мс (подтверждено логами `ExercisesViewModel`
и тестами `ExercisesLiveUpdateTest`,
`ExerciseCreateLiveUpdateIntegrationTest` — все зелёные).

**Обоснование:** Решение пользователя («оставляем как есть»).
Стандартное поведение `LazyColumn` — не дёргать позицию пользователя.
То же поведение и у списка записей на главном экране (`MainScreen`,
`EntryListItems`): при большом количестве записей новая запись
(секция «Сегодня», верх списка) тоже требует скролла наверх при
возврате с экрана создания. Оба списка согласованы; асимметричный
автоскролл только на одном из них — UX-рассогласование.

**Альтернатива отклонена:** Автоскролл к новой строке
(`rememberLazyListState` + `LaunchedEffect` + одноразовое
событие «вставка произошла» в `UiState` по конвенции проекта,
enum/sealed-kind) — добавляет состояние и тесты в двух местах
(`ExercisesScreen` + `MainScreen` для симметрии); польза мала,
YAGNI. Если позже потребуется — делать на обоих экранах одинаково.

## Risks / Trade-offs

- **[Risk]** Изменение `ORDER BY` в `ExerciseDao.observeExercises` ломает
  существующий `ExercisesViewModelTest`, если тест проверяет конкретный
  порядок списка. → **Mitigation:** `ExercisesViewModelTest` использует
  `ExercisesViewModelFakeRepository(initialCatalog = listOf(pullups, plank))`
  — `MutableStateFlow` с захардкоженным списком (файл теста, `private
  class ExercisesViewModelFakeRepository`, L36–39). Тест проверяет, что
  захардкоженный список доходит до `UiState.Success`, и косвенно
  фиксирует порядок через `assertEquals(listOf(pullups, plank), ...)`
  (L148–152) и `listOf(pullups, plank, squats)` (L179–194). После
  изменения `ORDER BY` в реальной БД `FakeRepository` продолжит
  эмитить тот же захардкоженный порядок — тест остаётся зелёным.
  Тем не менее, добавлена задача прогона регресса в `tasks.md` 7.1 на
  случай, если порядок проверяется в будущем (или при рефакторинге
  FakeRepository на реальный DAO в тестах) — 7.1 сама не добавляет
  новый сценарий, лишь запускает существующий `ExercisesViewModelTest`
  и при падении корректирует.

- **[Risk]** `createdAt` при множественной вставке за миллисекунды может
  дать одинаковое значение — порядок внутри группы кастомных при равных
  `createdAt` неопределён. → **Mitigation:** Не вводим дополнительный
  tie-break (`id DESC`) — YAGNI: кастомные упражнения добавляются
  пользователем вручную с паузой между вставками, риск коллизии в
  пределах миллисекунд чисто теоретический. Финальный `ORDER BY`
  (`is_custom DESC, created_at DESC, name COLLATE NOCASE`) фиксируется
  в `tasks.md` 2.2 и `specs/exercises-screen/spec.md` (требование
  «Сортировка каталога»); `design.md` и спека согласованы.

- **[Risk]** Использование `ExposedDropdownMenuBox` для `category` без
  подтверждения пользователем — он явно попросил сегментированную кнопку
  только для `measuredBy`, для `category` он не указал предпочтения. →
  **Mitigation:** Решение зафиксировано в `proposal.md` L22 («выпадающее
  меню `ExerciseCategory`») и в спеке `specs/exercise-create-screen/spec.md`,
  Requirement «Категория через ExposedDropdownMenuBox — обязательная»
  — пользователь явно одобрил `ExposedDropdownMenuBox` для `category`
  в требованиях к change.

- **[Risk]** Дублирование dropdown-компонента между `EntryCreateScreen` и
  новым экраном. → **Mitigation:** Минимально — близнец в пакете
  `exercises/` (рядом с `ExercisesScreen.kt`). Если извлечение в общий
  пакет окажется дешевле дублирования — извлекаем; решение принимается
  на этапе реализации. Тот же риск распространяется на
  `ExerciseCreateCloseConfirmDialog` — близнец `EntryCloseConfirmDialog`
  из `EntryCreateScreen.kt` (design Decision 8), извлечение в общий
  компонент также отложено.

## Migration Plan

Изменение одной строки `@Query` в `ExerciseDao.observeExercises` —
единственное место в data-слое. Схема Room (entities/indices) не
меняется — `ExerciseEntity` уже содержит `is_custom` и `created_at`,
а `@Query`-строки в экспортируемую схему не попадают. Версия БД и
JSON-схема в `app/schemas/` остаются прежними; bump не требуется.

Откат — откат одного коммита: `ORDER BY` возвращается к
`name COLLATE NOCASE`, `Screen.ExerciseCreate` удаляется, FAB —
обратно к заглушке. Поведение каталога и FAB-экрана полностью
восстанавливается.

## Open Questions

Нет. Все архитектурные решения зафиксированы, технические выборы
совпадают с явными требованиями пользователя или повторяют устоявшиеся
конвенции проекта.
