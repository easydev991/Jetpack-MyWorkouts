## 1. Тесты на репозиторий (red, androidTest)

> TDD: задачи с тестами идут раньше задач с реализацией (AGENTS.md).
> Stub-методы из 1.0 бросают `TODO()` — благодаря этому все 4 теста
> красные на stub-фазе (исключение пробивает assertion независимо от
> ожидаемого `isSuccess`/`isFailure`).

- [ ] 1.0 В `RoomWorkoutDataRepository` добавить stub-методы
  `deleteEntry(id: Long): Result<Unit>` и
  `deleteExercise(id: Long): Result<Unit>`, тела — `TODO(...)`.
  Без stub'ов тесты 1.1–1.4 не компилируются. Stub'ы заменяются в §3.
- [ ] 1.1 В `RoomWorkoutDataRepositoryTest` добавить тест
  `deleteEntry` существующей записи → `Result.isSuccess`, записи в БД
  нет; затем повторный `deleteEntry` с тем же id → `Result.isSuccess`
  (идемпотентность, spec-сценарий «Удаление несуществующей записи»).
  Верификация: `make android-test` — тест красный (stub бросает
  `TODO`).
- [ ] 1.2 Тест `deleteExercise` пользовательского упражнения без
  записей → `Result.isSuccess`, упражнения в БД нет; затем
  `deleteExercise` с идентификатором, отсутствующим в каталоге,
  → `Result.isFailure` (spec-сценарий «Удаление несуществующего
  упражнения отклоняется»). Верификация: красный.
- [ ] 1.3 Тест `deleteExercise` стандартного упражнения
  (`isCustom = false`) → `Result.isFailure`, упражнение остаётся.
  Верификация: красный.
- [ ] 1.4 Тест `deleteExercise` пользовательского упражнения с хотя бы
  одной записью → `Result.isFailure`, упражнение и записи остаются.
  Верификация: красный.

## 2. Реализация: DAO

- [ ] 2.1 В `data/db/ExerciseDao.kt` добавить
  `suspend fun deleteById(id: Long)` с аннотацией
  `@Query("DELETE FROM exercises WHERE id = :id")`. Верификация:
  `./gradlew :app:assembleDebug` зелёный.
- [ ] 2.2 В `data/db/ExerciseEntryDao.kt` добавить
  `suspend fun deleteById(id: Long)` с аннотацией
  `@Query("DELETE FROM exercise_entries WHERE id = :id")`. Верификация:
  `./gradlew :app:assembleDebug` зелёный.

## 3. Реализация: репозиторий (green)

- [ ] 3.1 В `RoomWorkoutDataRepository` заменить stub `deleteEntry`
  на реализацию: прямой вызов `dao.deleteById(id)` под `try/catch`,
  любое исключение → `Result.failure`. Верификация: тест 1.1
  зелёный (`make android-test`).
- [ ] 3.2 В `RoomWorkoutDataRepository` заменить stub `deleteExercise`
  на реализацию со следующей логикой: `getById(id)` → если `null` —
  `Result.failure`; если `isCustom == false` — `Result.failure`;
  иначе `dao.deleteById(id)` под `try/catch`, `SQLiteConstraintException`
  → `Result.failure`. Верификация: тесты 1.2, 1.3, 1.4 зелёные
  (`make android-test`).

## 4. Финальная верификация

- [ ] 4.1 `make format && make lint && make test && make build` —
  все зелёные. Существующие тесты не сломаны, новые проходят.
- [ ] 4.2 `make android-test` — все существующие тесты не сломаны,
  4 новых теста из §1 проходят.
- [ ] 4.3 `app/schemas/com.myworkouts.data.db.WorkoutDatabase/1.json`
  без изменений (схема не менялась); `MainActivity` не затрагивается.