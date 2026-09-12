# План: фикс «Сохранить» не активна при смене упражнения в режиме редактирования

## Цель

Устранить баг: на экране редактирования записи смена упражнения на
упражнение **того же типа** (REPS→REPS / TIME→TIME) делала форму
невалидной — кнопка «Сохранить» не активировалась до ручного повторного
ввода типозависимого поля.

## Анализ (источник бага)

`EntryCreateViewModel.onExerciseChange`
(`app/src/main/java/com/myworkouts/ui/viewmodel/EntryCreateViewModel.kt:111-119`)
безусловно стирает `repsText` и `durationSec` при любой смене упражнения:

```kotlin
it.copy(
    selectedExercise = exercise,
    repsText = "",        // ← стирается всегда
    durationSec = null,   // ← и это тоже
    isSaveError = false
)
```

В edit-режиме после предзаполнения (повторы = «10») смена упражнения
очищает обязательное поле → `formValid = false` (`EntryCreateUiState.kt:53-60`)
→ `canSave = false`. `hasChanges` при этом корректно становится `true` —
блокирует именно невалидность, а не отсутствие изменений.

**Отклонение от спеки** (`openspec/specs/workout-entry-screen/spec.md`):

- «Смена упражнения переключает поле» (:125-130) предписывает стирать
  значение только при смене **типа** измерения (REPS→TIME);
- «Изменение поля активирует кнопку» (:402-406): изменение любого поля
  при валидной форме → кнопка активна.

OpenSpec-артефакты не создаются: спека уже требует правильного поведения,
фикс приводит код в соответствие с ней.

## Ключевое решение реализации

Правило стирания — **по типу нового упражнения, а не по сравнению с
предыдущим выбранным**:

- `repsText` сохраняется, только если новое упражнение REPS;
- `durationSec` сохраняется, только если новое упражнение TIME.

Почему не «сравнить с предыдущим `selectedExercise`»: в edit-режиме
`selectedExercise` внутри `mutableUiState` остаётся `null` — его
подставляет `derivedState` только в выходной поток `combine`
(`EntryCreateViewModel.kt:98-108, 197-211`), обратно в `mutableUiState`
значение не пишется; `exercises` там тоже пуст. Правило по типу нового
упражнения читает только `repsText`/`durationSec`, которые в
`mutableUiState` есть, — и одинаково работает в обоих режимах без
ветвления по `isEditMode`.

Побочные эффекты правила (ожидаемые, покрываются тестами):

- create-режим: REPS→REPS сохраняет введённые повторы (улучшение UX,
  пинится новым тестом); REPS→TIME по-прежнему стирает — существующий
  тест `on_exercise_change_when_switching_reps_to_time_then_resets_reps`
  остаётся зелёным без правок;
- edit-режим REPS→TIME: поле стирается, кнопка ждёт выбора длительности —
  по спеке.

## Этап 1 — Red: тесты в `EntryCreateViewModelTest.kt`

Файл: `app/src/test/java/com/myworkouts/ui/viewmodel/EntryCreateViewModelTest.kt`.

### 1.1 Фикстура: второе REPS-упражнение в каталоге

В каталоге класса сейчас одно REPS-упражнение (`repsExercise`, id=1) —
для same-type смены нужно второе:

- [ ] Добавить `repsExercise2` (id=3, name=«Отжимания», category=PUSH,
  measuredBy=REPS) по образцу существующих полей класса (:112-132).
- [ ] Включить в каталог фейка: `FakeWorkoutDataRepository(listOf(repsExercise, timeExercise, repsExercise2))` (:134).
- [ ] **Правка существующего ассерта**: `initial_state_should_load_catalog_prefill_sets_and_disable_save`
  сравнивает каталог с точным списком `listOf(repsExercise, timeExercise)`
  (:200-204) — обновить ожидание, добавив `repsExercise2`.

### 1.2 Тесты (порядок Given/When/Then, имена snake_case без бэктиков, сообщения assert — на русском)

- [ ] `edit_mode_when_same_type_exercise_changed_then_save_enabled` —
  **главный красный тест бага**. Given: `repository.entryById = originalEntry`,
  VM с `entryId = originalEntry.id`, подписка `backgroundScope` +
  `advanceUntilIdle` (паттерн :595-598); пре-условия: `canSave == false`,
  `repsText == "10"`. When: `onExerciseChange(repsExercise2)`.
  Then: `selectedExercise == repsExercise2`, `repsText == "10"`
  (значение сохранено), `hasChanges == true`, `canSave == true`.
  Сейчас падает: `repsText` пуст, `canSave == false`.
- [ ] `edit_mode_when_type_switched_then_field_cleared_until_duration_picked`
  — guard-тест спек-сценария в edit-режиме. Given: тот же edit-режим
  REPS-записи. When: `onExerciseChange(timeExercise)`. Then: `repsText == ""`,
  `durationSec == null`, `hasChanges == true`, `canSave == false`;
  повторный When: `onDurationChange(60)` → `canSave == true`.
  Зелёный и до, и после фикса — защищает от перегиба в другую сторону.
- [ ] `on_exercise_change_same_type_then_keeps_entered_reps` — пин
  правила в create-режиме. Given: create-VM, `onExerciseChange(repsExercise)`,
  `onRepsChange("10")`. When: `onExerciseChange(repsExercise2)`.
  Then: `repsText == "10"`, `canSave == true`. Сейчас падает.

Размещение: тесты 1-2 — рядом с блоком edit-режимов (после
`edit_mode_when_invalid_change_then_can_save_false`, :661), тест 3 —
рядом с `on_exercise_change_when_switching_reps_to_time_then_resets_reps` (:326).

TIME→TIME не тестируется отдельно: правило типонезависимо по коду,
кейс симметричен REPS→REPS (YAGNI).

### Критерий этапа

`./gradlew test --tests "*EntryCreateViewModelTest"` — новые тесты падают,
остальные зелёные (кроме правки ассерта каталога в 1.1, она зелёная).

## Этап 2 — Green: реализация

- [ ] В `onExerciseChange` стирать поле только при несовпадении типа
  нового упражнения (суть фикса):

  ```kotlin
  it.copy(
      selectedExercise = exercise,
      repsText = if (exercise.measuredBy == ExerciseMeasuredBy.REPS) it.repsText else "",
      durationSec = if (exercise.measuredBy == ExerciseMeasuredBy.TIME) it.durationSec else null,
      isSaveError = false
  )
  ```

- [ ] Обновить KDoc `onExerciseChange` и упоминание «сброс
  типозависимого поля при смене упражнения» в KDoc класса (:36-37) —
  «сброс при смене типа измерения; same-type смена значение сохраняет».

`EntryCreateUiState` (`formValid`/`canSave`/`hasChanges`) и UI не
меняются — производные поля уже считают нужное.

### Критерий этапа

`./gradlew test --tests "*EntryCreateViewModelTest"` — все тесты зелёные.

## Этап 3 — проверка и завершение

- [ ] `make format && make lint` — detekt чист (KDoc-протяжка строк,
  ничего сверх).
- [ ] `make test` — вся JVM-пирамида зелёная.
- [ ] `make check` — финальный гейт перед коммитом.
- [ ] (Опционально, ручная проверка на эмуляторе) Открыть редактирование
  записи, сменить упражнение того же типа — кнопка «Сохранить» активна
  сразу; смена на другой тип — кнопка ждёт заполнения нового поля.
- [ ] Коммит по конвенции (skill `commit`, заголовок ≤ 50 символов).

androidTest не добавляются: активность кнопки выводится из `canSave`
чисто на уровне состояния, экран (`EntryCreateScreenContent`) уже покрыт
`EntryCreateScreenTest`; новая логика — целиком в ViewModel.

## Зависимости и порядок

Этап 1 → Этап 2 строго последовательно (red обязателен до green).
Этап 1.1 (фикстура) можно делать вместе с 1.2 — без второго упражнения
главный тест не выражается. Этап 3 — после зелёного green.

Активная openspec-задача `edit-delete-exercise` не затрагивается: она
правит экраны упражнений, `EntryCreateScreen` в её proposal помечен
«не затрагивается», конфликтов файлов нет.
