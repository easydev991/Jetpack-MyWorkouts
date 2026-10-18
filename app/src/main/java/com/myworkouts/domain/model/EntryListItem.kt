package com.myworkouts.domain.model

/**
 * Доменный элемент списка записей на главном экране.
 *
 * Содержит плоскую проекцию: id, название упражнения, подходы, одно
 * из значений (повторы или длительность), дополнительный вес,
 * комментарий к записи и дату/время. Без `createdAt` — строке списка
 * он не нужен (design Decision 2 change workout-entries-list). Маппинг
 * из `EntryListItemWithExerciseEntity` — `toListItem()`.
 *
 * @property id Идентификатор записи
 * @property exerciseName Название упражнения каталога
 * @property iconNumber Номер иконки упражнения из каталога (`null` —
 *   иконка не назначена; подтягивается тем же JOIN, дефолт держит
 *   фикстуры тестов/превью компилируемыми, exercise-icon-display
 *   design Decision 2)
 * @property sets Количество подходов
 * @property reps Повторы в подходе (`null` для упражнений «на время»)
 * @property durationSec Длительность в секундах (`null` для упражнений «на повторы»)
 * @property weightKg Дополнительный вес (`null` — без веса; дефолт держит
 *   фикстуры тестов/превью компилируемыми, design Decision 5)
 * @property comment Комментарий к записи (`null` — без комментария; дефолт
 *   держит фикстуры тестов/превью компилируемыми, design Decision 2 change
 *   mainscreen-itemrow-comment)
 * @property date Дата/время записи (epoch millis)
 */
data class EntryListItem(
    val id: Long,
    val exerciseName: String,
    val iconNumber: Int? = null,
    val sets: Int,
    val reps: Int?,
    val durationSec: Int?,
    val weightKg: Double? = null,
    val comment: String? = null,
    val date: Long
)
