# Анализ: редактирование/удаление стандартных упражнений

Дата: 2026-09-17. Контекст: после установки сборки `VERSION_CODE=3` пользователь
сообщил, что на экране «Упражнения» стало возможно редактировать и удалять
стандартные упражнения (prepopulated, `is_custom = 0`). Ранее это было
недоступно.

## Цель

Подтвердить или опровергнуть регрессию от коммита `f15ab66` (задача
`backup-is-archived-flag`), определить источник наблюдаемого поведения и
зафиксировать план закрытия пробела в тестовом покрытии.

## Метод

1. Сопоставление `VERSION_CODE` → коммит:
   - `VERSION_CODE=1` → коммит `98618c0` «Версия приложения на экране Ещё»
     (последний коммит с `VERSION_CODE=1` в `gradle.properties`).
   - `VERSION_CODE=2` → коммит `841d0a2` «Реализация задачи по добавлению
     firebase».
   - `VERSION_CODE=3` → коммит `f15ab66` «Реализация задачи» (наш коммит по
     `backup-is-archived-flag`).
2. Сравнение `git diff-tree -r f15ab66`: список изменённых файлов.
3. Поиск кода, отвечающего за `isCustom`-гейт в UI и репозитории.
4. Сопоставление с тестами `ImportBackupUseCaseTest` и
   `ExportBackupUseCaseTest`.

## Результаты

### `f15ab66` не трогает код, отвечающий за гейт `isCustom`

Изменены только 7 файлов:

| Файл | Что изменено |
|---|---|
| `ExportBackupUseCase.kt` | +1 строка: `.put("isArchived", exercise.isArchived)` |
| `ImportBackupUseCase.kt` | хелпер `optionalBoolean` + рефактор `when` |
| `ExportBackupUseCaseTest.kt` | +45 строк тестов |
| `ImportBackupUseCaseTest.kt` | +122 строки тестов |
| `gradle.properties` | `VERSION_CODE` 2 → 3 |
| `openspec/.../spec.md` | дельта спеки |
| `openspec/.../tasks.md` | отметки чекбоксов |

Ни UI (`ExercisesScreen.kt`), ни VM (`ExercisesViewModel.kt`,
`ExerciseCreateViewModel.kt`), ни репозиторий
(`RoomWorkoutDataRepository.kt`) не затронуты.

### Двойной барьер на `isCustom` остался на месте

1. **UI**: `ExercisesScreen.kt:632` и `:648` — пункты «Изменить» и
   «Удалить» в `ExerciseContextMenu` обёрнуты в `if (isCustom)`.
   Дополнительно: `applyLongPress` в `:511` создаёт `ExerciseContextMenuState`
   с конкретным `Exercise` (строка `:515`), и `isCustom` берётся в `:462`
   именно из `menu.item.isCustom`.
2. **Репозиторий**: `RoomWorkoutDataRepository.kt:236` —
   `if (!exercise.isCustom) return Result.failure(StandardExerciseDeletionException())`.

Оба файла последний раз менялись в коммитах, далёких от `f15ab66`
(`ExercisesScreen.kt` — `841d0a2` от 14 часов назад, без затрагивания гейта;
`RoomWorkoutDataRepository.kt` — `332fb63`).

### Пользовательский сценарий `deleteAllData` + restore из бэкапа

Prepoulate кладёт стандартные упражнения с `is_custom = 0`
(`WorkoutDatabase.kt:108`, raw SQL: `VALUES (?, ?, 0, NULL, ?, ?, ?)`).

`mergeBackup` в `ImportBackupUseCase.kt:142` принудительно выставляет
`isCustom = true` всем **вставленным** упражнениям (Decision 4:
«файл недоверенный»). Если стандартное упражнение уже есть в каталоге —
dedup по `name.trim().lowercase()` (строка `:140`) пропускает его. Если
каталог пустой (например, после `deleteAllData` на экране «Данные
приложения»), стандартные упражнения вставляются с `isCustom = true` и
становятся редактируемыми/удаляемыми.

Это задокументировано в `design.md` Decision 4 и `openspec/specs/
app-data-management/spec.md` (требование «Восстановление из резервной копии
слиянием», абзац про локали ru ↔ en). Для пользователя это валидный
сценарий, не регрессия.

### Покрытие тестами

| Сценарий | Тест | `isCustom` явно проверяется? |
|---|---|---|
| Новое упражнение из файла → вставлено | `import_when_new_exercise_then_inserted_with_isCustom_true_and_zero_id` (существовал до задачи) | да, `assertTrue(inserted.isCustom)` |
| Дубль имени → дедупликация | `import_when_name_matches_existing_ignoring_case_then_not_duplicated` (существовал до задачи) | **нет**: проверяет `exercisesSlot.captured.isEmpty()`, но не `isCustom` существующего |
| Существующий, дедуп, `isArchived` сохраняется | `import_when_duplicateExercise_then_existingIsArchivedUnchanged` (наш, задача `backup-is-archived-flag`) | **нет**: только `isArchived` |

**Пробел**: нет теста, который бы явно фиксировал «стандартное упражнение в
БД (`isCustom=false`) → бэкап содержит то же имя → существующее остаётся
`isCustom=false`, в `importData` не передаётся повторное упражнение».

## Гипотеза о причине бага на устройстве

Сценарий пользователя: между установками сборок 1 и 2 он выполнил
`deleteAllData` (или аналогичную операцию, обнулившую каталог) и восстановил
бэкап. После этого стандартные упражнения в БД имеют `is_custom = 1` —
ожидаемое поведение по Decision 4, но не то, что пользователь ожидает для
prepopulated набора.

Чтобы подтвердить гипотезу, нужно проверить реальные значения в БД на
устройстве:

```bash
adb shell run-as com.myworkouts sqlite3 databases/workout_database \
    "SELECT name, is_custom FROM exercises"
```

## Что предлагается

1. **Закрыть пробел покрытия**: добавить тест в
   `ImportBackupUseCaseTest`, который явно проверяет, что при дедупе по
   имени существующее стандартное упражнение остаётся с `isCustom = false`
   и **не** попадает в `exercisesToInsert`. Тест страхует от регрессии,
   если в будущем кто-то поменяет `mergeBackup`.
2. **Подтвердить на устройстве**, что у пользователя `is_custom = 1` для
   стандартных упражнений (см. команду выше). Если да — это ожидаемое
   поведение, не регрессия. Если `is_custom = 0` при этом UI показывает
   edit/delete — это уже настоящий регресс и нужно искать дальше
   (вероятность низкая по анализу кода, но не нулевая).

## Нужен ли новый OpenSpec-change для теста?

Нет. По прецеденту коммита `1d59cd0` («Исправлены warning'и exhaustiveness
в парсерах JSON»): пользователь подтвердил, что для рефакторинга без
изменения поведения OpenSpec-change не нужен. Добавление теста — это
фиксация существующего контракта без изменения поведения. Достаточно
обычного коммита с описанием «добавлен явный тест на дедуп `isCustom`».
