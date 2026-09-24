package com.myworkouts.domain.model

import androidx.annotation.StringRes
import com.myworkouts.R

/**
 * Категория упражнения в каталоге тренировок.
 *
 * Используется для группировки и фильтрации упражнений, а также для будущих
 * агрегатов статистики (объём/нагрузка по категориям). Набор значений
 * фиксирован контрактом — полный список проверяется unit-тестом
 * (см. `ExerciseCategoryTest`), это же множество используется в prepopulate
 * БД (`StandardExercises`).
 */
enum class ExerciseCategory(
    @StringRes val labelResId: Int
) {
    /** Толкающие: отжимания, отжимания на брусьях и т.п. */
    PUSH(R.string.exercise_category_push),

    /** Тянущие: подтягивания, австралийские подтягивания. */
    PULL(R.string.exercise_category_pull),

    /** Ноги: приседания, выпады, пистолетик. */
    LEGS(R.string.exercise_category_legs),

    /** Кор: планка, скручивания, подъёмы ног. */
    CORE(R.string.exercise_category_core),

    /** Кардио: бег, скакалка. */
    CARDIO(R.string.exercise_category_cardio)
}
