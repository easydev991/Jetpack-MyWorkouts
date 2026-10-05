package com.myworkouts.domain.model

/**
 * Доменная модель записи с данными упражнения из каталога
 * (overview-screen, design Decision 1): поля [EntryListItem] плюс
 * `exerciseId`, категория, тип измерения и комментарий упражнения —
 * единый вход расчётов всех трёх вкладок обзора.
 *
 * Без `createdAt` — столбец в проекцию не включается (прецедент —
 * [EntryListItem], строке расчётов он не нужен). Маппинг из
 * [com.myworkouts.data.db.EntryListItemWithExerciseEntity] —
 * `toEntryWithExercise()`.
 *
 * @property id Идентификатор записи
 * @property exerciseId Идентификатор упражнения каталога
 * @property exerciseName Название упражнения каталога
 * @property iconNumber Номер иконки упражнения (`null` — не назначена)
 * @property category Имя категории упражнения (суммы доната — по ней;
 *   ссылка по имени — custom-categories, design Decision 1)
 * @property measuredBy Тип измерения: REPS — в донат и рекорды значением
 *   повтора, TIME — длительность подхода
 * @property exerciseComment Комментарий упражнения из каталога (`null` —
 *   без комментария; фолбэк для комментария строки рекорда)
 * @property sets Количество подходов
 * @property reps Повторы в подходе (`null` для упражнений «на время»)
 * @property durationSec Длительность в секундах (`null` для упражнений «на повторы»)
 * @property weightKg Дополнительный вес (`null` — без веса)
 * @property comment Комментарий к записи (`null` — без комментария)
 * @property date Дата/время записи (epoch millis)
 */
data class EntryWithExercise(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val iconNumber: Int? = null,
    val category: String,
    val measuredBy: ExerciseMeasuredBy,
    val exerciseComment: String? = null,
    val sets: Int,
    val reps: Int?,
    val durationSec: Int?,
    val weightKg: Double? = null,
    val comment: String? = null,
    val date: Long
)
