package com.myworkouts.domain.model

/**
 * Доменный элемент списка записей на главном экране.
 *
 * Содержит плоскую проекцию: id, название упражнения, подходы, одно
 * из значений (повторы или длительность), дополнительный вес и
 * дату/время. Без `comment`/`createdAt` — строке списка они не нужны
 * (design Decision 2). Маппинг из
 * [com.myworkouts.data.db.EntryListItemEntity] — `toListItem()`.
 *
 * @property id Идентификатор записи
 * @property exerciseName Название упражнения каталога
 * @property sets Количество подходов
 * @property reps Повторы в подходе (`null` для упражнений «на время»)
 * @property durationSec Длительность в секундах (`null` для упражнений «на повторы»)
 * @property weightKg Дополнительный вес (`null` — без веса; дефолт держит
 *   фикстуры тестов/превью компилируемыми, design Decision 5)
 * @property date Дата/время записи (epoch millis)
 */
data class EntryListItem(
    val id: Long,
    val exerciseName: String,
    val sets: Int,
    val reps: Int?,
    val durationSec: Int?,
    val weightKg: Double? = null,
    val date: Long
)
