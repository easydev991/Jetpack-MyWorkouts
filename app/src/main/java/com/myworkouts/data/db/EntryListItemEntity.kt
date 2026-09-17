package com.myworkouts.data.db

/**
 * Проекция списка записей: результат JOIN `exercise_entries`/`exercises`
 * для экрана списка (workout-entries-list, design Decision 1).
 *
 * Это НЕ `@Entity` — Room создаёт строки этого класса из SQL-запроса,
 * схема БД не меняется (миграция не нужна). Название упражнения
 * (`exerciseName`) подтягивается через JOIN и не требует отдельного
 * маппинга в ViewModel.
 *
 * @property id Идентификатор записи
 * @property exerciseName Название упражнения каталога
 * @property sets Количество подходов
 * @property reps Повторы в подходе (`null` для упражнений «на время»)
 * @property durationSec Длительность в секундах (`null` для упражнений «на повторы»)
 * @property weightKg Дополнительный вес (`null` — без веса; 0 — данные, не «нет веса»)
 * @property comment Комментарий к записи (`null` — без комментария;
 *   mainscreen-itemrow-comment)
 * @property date Дата/время записи (epoch millis)
 */
data class EntryListItemEntity(
    val id: Long,
    val exerciseName: String,
    val sets: Int,
    val reps: Int?,
    val durationSec: Int?,
    val weightKg: Double?,
    val comment: String?,
    val date: Long
)
