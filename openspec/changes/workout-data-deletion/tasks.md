## 1. DAO: методы удаления

- [ ] 1.1 В `data/db/ExerciseDao.kt` добавить
  `suspend fun deleteById(id: Long)` с аннотацией
  `@Query("DELETE FROM exercises WHERE id = :id")`. Верификация:
  `./gradlew :app:assembleDebug` зелёный.
- [ ] 1.2 В `data/db/ExerciseEntryDao.kt` добавить
  `suspend fun deleteById(id: Long)` с аннотацией
  `@Query("DELETE FROM exercise_entries WHERE id = :id")`. Верификация:
  `./gradlew :app:assembleDebug` зелёный.

## 2. Репозиторий: API удаления

- [ ] 2.1 В `RoomWorkoutDataRepository` добавить
  `suspend fun deleteEntry(id: Long): Result<Unit>` — прямой вызов DAO
  под `try/catch`, любое исключение → `Result.failure`. Верификация:
  `./gradlew :app:assembleDebug` зелёный.
- [ ] 2.2 В `RoomWorkoutDataRepository` добавить
  `suspend fun deleteExercise(id: Long): Result<Unit>` со следующей
  логикой: `getById(id)` → если `null` — `Result.failure(«Упражнение
  не найдено»)`; если `isCustom == false` — `Result.failure(«Стандартное
  упражнение нельзя удалить»)`; иначе `dao.deleteById(id)` под
  `try/catch`, `SQLiteConstraintException` → `Result.failure(«У этого
  упражнения есть записи, удалите их сначала»)`, прочее —
  generic `Result.failure`. Верификация: `./gradlew :app:assembleDebug`
  зелёный.

## 3. Тесты: репозиторий (androidTest)

- [ ] 3.1 В `RoomWorkoutDataRepositoryTest` добавить тест:
  `deleteEntry` существующей записи → `Result.isSuccess`, записи в БД
  нет. Верификация: `make android-test` зелёный.
- [ ] 3.2 В `RoomWorkoutDataRepositoryTest` добавить тест:
  `deleteExercise` пользовательского упражнения без записей →
  `Result.isSuccess`, упражнения в БД нет. Верификация:
  `make android-test` зелёный.
- [ ] 3.3 В `RoomWorkoutDataRepositoryTest` добавить тест:
  `deleteExercise` стандартного упражнения (`isCustom = false`) →
  `Result.isFailure` с сообщением о запрете, упражнение остаётся.
  Верификация: `make android-test` зелёный.
- [ ] 3.4 В `RoomWorkoutDataRepositoryTest` добавить тест:
  `deleteExercise` пользовательского упражнения с хотя бы одной
  записью → `Result.isFailure` с осмысленным сообщением, упражнение
  и записи остаются. Верификация: `make android-test` зелёный.

## 4. Финальная верификация

- [ ] 4.1 `make format && make lint && make test && make build` —
  все зелёные. Существующие тесты не сломаны, новые проходят.
- [ ] 4.2 `make android-test` — все существующие тесты не сломаны,
  4 новых теста из §3 проходят.
- [ ] 4.3 `app/schemas/com.myworkouts.data.db.WorkoutDatabase/1.json`
  без изменений (схема не менялась); `MainActivity` не затрагивается.
