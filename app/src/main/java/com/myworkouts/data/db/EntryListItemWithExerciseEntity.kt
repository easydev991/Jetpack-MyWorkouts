package com.myworkouts.data.db

/**
 * Единственная проекция JOIN записей с каталогом: 9 колонок строки
 * списка плюс колонки каталога (`exercise_id`, категория, тип измерения,
 * комментарий упражнения), нужные расчётам вкладок (overview-screen,
 * design Decision 1). Это НЕ `@Entity` — Room создаёт строки из
 * SQL-запроса, схема БД не меняется. Единственный источник —
 * [ExerciseEntryDao.observeEntryRows]; сужение до доменных видов —
 * мапперами `toListItem()` / `toEntryWithExercise()`.
 *
 * Категория и тип измерения приходят строками и переводятся в enum'ы
 * `valueOf`-ом в маппере (тот же контракт, что у [ExerciseEntity] —
 * испорченные данные падают громко). `created_at` в проекцию не
 * включается — расчётам не нужен. Комментарий упражнения назван
 * `exerciseComment`, чтобы не путать с комментарием записи.
 *
 * @property id Идентификатор записи
 * @property exerciseId Идентификатор упражнения каталога
 * @property exerciseName Название упражнения каталога
 * @property iconNumber Номер иконки упражнения (`null` — не назначена)
 * @property category Категория упражнения (имя константы [com.myworkouts.domain.model.ExerciseCategory])
 * @property measuredBy Тип измерения (имя константы [com.myworkouts.domain.model.ExerciseMeasuredBy])
 * @property exerciseComment Комментарий упражнения из каталога (`null` — без комментария)
 * @property sets Количество подходов
 * @property reps Повторы в подходе (`null` для упражнений «на время»)
 * @property durationSec Длительность в секундах (`null` для упражнений «на повторы»)
 * @property weightKg Дополнительный вес (`null` — без веса)
 * @property comment Комментарий к записи (`null` — без комментария)
 * @property date Дата/время записи (epoch millis)
 */
data class EntryListItemWithExerciseEntity(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val iconNumber: Int?,
    val category: String,
    val measuredBy: String,
    val exerciseComment: String?,
    val sets: Int,
    val reps: Int?,
    val durationSec: Int?,
    val weightKg: Double?,
    val comment: String?,
    val date: Long
)
