package com.myworkouts.data.repository

// ponytail: файловый комментарий — `//`, а не KDoc, чтобы избежать
// `no-consecutive-comments` (ktlint: KDoc не может идти под KDoc).
// Типизированные исключения операций с каталогом: удаление
// ([WorkoutDataRepository.deleteExercise], edit-delete-exercise;
// [WorkoutDataRepository.deleteCategory], custom-categories) и guard
// последнего активного элемента для архивации и удаления
// (prevent-from-archiving-all, design Decision 2). Заменяют
// generic `IllegalStateException` + парсинг текста
// `SQLiteConstraintException`: VM маппит подклассы в
// [com.myworkouts.domain.model.DeleteExerciseError], UI — в конкретный
// снекбар.
//
// Все наследуются от `IllegalStateException` — обратная совместимость с
// существующими вызывающими сторонами и unit-тестами репозитория
// (`result.isFailure` остаётся `true`); `instanceof`-проверки различают
// конкретный кейс.

/** Удаление отклонено дизайном: упражнение входит в стандартный набор. */
class StandardExerciseDeletionException : IllegalStateException("Стандартное упражнение нельзя удалить")

/**
 * Удаление отклонено: на упражнение ссылаются записи (FK). Pre-check
 * через [com.myworkouts.data.db.ExerciseEntryDao.countByExerciseId]
 * ПЕРЕД `exerciseDao.delete(...)` ловит race до попытки писать в БД
 * (design Decision 8).
 */
class LinkedEntriesDeletionException : IllegalStateException("У этого упражнения есть записи, удалите их сначала")

/**
 * Операция с категорией отклонена дизайном: категория стандартная
 * (custom-categories, design Decision 4). Бросается и удалением
 * ([WorkoutDataRepository.deleteCategory]), и обновлением
 * ([WorkoutDataRepository.updateCategory]) — стандартные имена-ключи
 * `PUSH`/`PULL`/… хранятся в БД и бэкапах и не меняются.
 */
class StandardCategoryDeletionException : IllegalStateException("Стандартную категорию нельзя удалить или обновить")

/**
 * Удаление категории отклонено: на неё ссылаются упражнения — активные
 * или архивные (custom-categories, design Decision 4). Pre-check через
 * [com.myworkouts.data.db.ExerciseDao.countByCategoryName] внутри
 * транзакции; в rename не возникает — ссылки упражнений обновляются
 * той же транзакцией.
 */
class CategoryInUseDeletionException : IllegalStateException("Эту категорию используют упражнения, удалите их сначала")

/**
 * Операция (архивация или удаление) отклонена: упражнение — последнее
 * активное в каталоге, операция опустошила бы активный список
 * (prevent-from-archiving-all, design Decision 2). По одному типу на
 * сущность, а не на операцию: и архивация, и удаление отклоняются одним
 * фактом, различие операции восстанавливается на стороне VM.
 */
class LastActiveExerciseException :
    IllegalStateException("Нельзя архивировать или удалять единственное активное упражнение")

/**
 * Операция (архивация или удаление) отклонена: категория — последняя
 * активная в списке (prevent-from-archiving-all, design Decision 2) —
 * по образцу [LastActiveExerciseException].
 */
class LastActiveCategoryException :
    IllegalStateException("Нельзя архивировать или удалять единственную активную категорию")
