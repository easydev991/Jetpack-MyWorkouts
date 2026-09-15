# Proposal: exercise-icon-mvp

## Why

Иконки упражнений (отрисовка в списках, назначение стандартным, выбор для
пользовательских) — запланированная UI-фича, но её Data-слой ещё не готов:
ни доменная модель `Exercise`, ни БД, ни формат резервных копий не хранят
номер иконки. Проще добавить поле одним data-изменением сейчас (по
прецеденту `is_archived` — деструктивный фоллбек до релиза), чем тянуть
его внутри будущего UI-change вместе с ресурсами и экранами.

## What Changes

- Доменная модель `Exercise` получает опциональное поле `iconNumber: Int?`
  (`null` — иконки нет; значение по умолчанию `null` — существующий код
  и именованные вызовы не ломаются).
- Room-сущность `ExerciseEntity` — новая колонка `icon_number INTEGER`
  (nullable, без SQL DEFAULT), версия схемы БД 2 → 3
  (`fallbackToDestructiveMigration`, схема экспортируется в `app/schemas/`).
- `Mappers.kt` — поле прокидывается в `toDomain`/`toEntity`.
- `StandardExercise` — поле `iconNumber: Int? = null` (значения стандартным
  упражнениям пока не проставляются), raw SQL INSERT в seed-callback
  дополняется колонкой.
- Бэкап: экспорт пишет опциональное `iconNumber` (`null` → `JSONObject.NULL`),
  импорт читает его через существующий хелпер `optionalInt`; версия формата
  остаётся `1`. Старые бэкапы без поля продолжают импортироваться
  (`iconNumber = null`), новые бэкапы на старых версиях приложения
  переносят поле как неизвестное (уже гарантировано спекой
  app-data-management).
- ВАЖНО: сами иконки-ресурсы, назначение номеров стандартным упражнениям
  и UI-выбор иконки — ВНЕ скоупа (следующие изменения). Валидация
  диапазона номера в MVP не вводится (см. design.md Decision 2).

## Capabilities

### New Capabilities

(нет — фича целиком расширяет существующие возможности)

### Modified Capabilities

- `workout-data`: новое требование «Опциональный номер иконки упражнения» —
  модель и хранение `iconNumber` (`null` = иконки нет), прокидывание через
  мапперы, prepopulate со значением по умолчанию; сценарии roundtrip и
  стандартного набора.
- `app-data-management`: раздел «Формат файла резервной копии» —
  `iconNumber` как опциональное поле элемента `exercises` (Int);
  уточнение сценариев обратной совместимости (старый бэкап без поля
  импортируется; новый бэкап на старой версии приложения — неизвестное
  поле игнорируется), а также раздел «Восстановление из резервной копии
  слиянием» — сохранение `iconNumber` вставленного упражнения и
  неизменность `iconNumber` существующего при дедупликации.

## Impact

- Код: `domain/model/Exercise.kt`, `data/db/ExerciseEntity.kt`,
  `data/db/WorkoutDatabase.kt` (версия + seedCallback),
  `data/db/StandardExercise.kt`, `data/db/Mappers.kt`,
  `domain/usecase/ExportBackupUseCase.kt`, `domain/usecase/ImportBackupUseCase.kt`.
- Схема БД: bump версии 2 → 3, экспорт схемы в `app/schemas/`.
- Формат бэкапа v1: обратно совместимое расширение (новое опциональное поле).
- Тесты: JVM (ExportBackupUseCaseTest, ImportBackupUseCaseTest, Mappers
  roundtrip) и androidTest (репозиторий, prepopulate `WorkoutDatabaseTest`).
- Не затрагивается: UI-слой, строки, навигация.
