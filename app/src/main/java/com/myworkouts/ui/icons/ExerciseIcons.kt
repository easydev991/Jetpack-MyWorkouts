package com.myworkouts.ui.icons

import androidx.annotation.DrawableRes
import com.myworkouts.R

/**
 * Стабильные номера иконок упражнений (`Exercise.iconNumber`) и их маппинг
 * на ресурсы.
 *
 * Номера 1..19 — стабильные идентификаторы, не индексы в файловой системе:
 * переименование drawable-ресурса или смена порядка в списке не смещает
 * номера. Назначаются этой таблицей один раз и больше не меняются без
 * миграции БД (exercise-icon-display). Имена констант совпадают со slug'ом
 * ресурса без префикса `ICON_` — `ExerciseIcons.PULLUPS` уже говорит, что
 * это иконка.
 *
 * Светлые ресурсы — `res/drawable/<slug>.xml`; тёмные —
 * `res/drawable/<slug>_dark.xml` (инверсия яркости,
 * `scripts/generate_dark_icon_variants.py`). Пары ссылаются на оба варианта
 * сразу; UI выбирает между ними по теме приложения.
 *
 * Контракт [resolve]:
 * - `iconNumber == null` → `null` (иконка не назначена, ничего не рисуем);
 * - `iconNumber` не из таблицы (например, устаревшее значение из старого
 *   бэкапа или ручная правка БД) → `null`, без плейсхолдера и без
 *   предупреждения. Это сознательный MVP-выбор: тихий fallback на «нет
 *   иконки» безопаснее, чем падение или показ заглушки.
 */
internal object ExerciseIcons {
    const val ABS_HANGING_LEG_RAISES = 1
    const val BARBELL = 2
    const val BARBELL_1 = 3
    const val BARBELL_2 = 4
    const val BARBELL_3 = 5
    const val BARBELL_4 = 6
    const val BARS = 7
    const val CRUNCHES = 8
    const val DUMBBELLS = 9
    const val DUMBBELLS_1 = 10
    const val JUMPING_ROPE = 11
    const val LUNGES = 12
    const val PULLUPS = 13
    const val PULLUPS_AUSTRALIAN = 14
    const val PUSHUPS = 15
    const val PUSHUPS_KNEE = 16
    const val RUN = 17
    const val SQUATS = 18
    const val SQUATS_PISTOL = 19

    /** Пара ресурсов (светлый/тёмный вариант) для одной иконки. */
    data class Ref(
        @DrawableRes val light: Int,
        @DrawableRes val dark: Int
    )

    internal val byNumber: Map<Int, Ref> =
        mapOf(
            ABS_HANGING_LEG_RAISES to Ref(R.drawable.abs_hanging_leg_raises, R.drawable.abs_hanging_leg_raises_dark),
            BARBELL to Ref(R.drawable.barbell, R.drawable.barbell_dark),
            BARBELL_1 to Ref(R.drawable.barbell_1, R.drawable.barbell_1_dark),
            BARBELL_2 to Ref(R.drawable.barbell_2, R.drawable.barbell_2_dark),
            BARBELL_3 to Ref(R.drawable.barbell_3, R.drawable.barbell_3_dark),
            BARBELL_4 to Ref(R.drawable.barbell_4, R.drawable.barbell_4_dark),
            BARS to Ref(R.drawable.bars, R.drawable.bars_dark),
            CRUNCHES to Ref(R.drawable.crunches, R.drawable.crunches_dark),
            DUMBBELLS to Ref(R.drawable.dumbbells, R.drawable.dumbbells_dark),
            DUMBBELLS_1 to Ref(R.drawable.dumbbells_1, R.drawable.dumbbells_1_dark),
            JUMPING_ROPE to Ref(R.drawable.jumping_rope, R.drawable.jumping_rope_dark),
            LUNGES to Ref(R.drawable.lunges, R.drawable.lunges_dark),
            PULLUPS to Ref(R.drawable.pullups, R.drawable.pullups_dark),
            PULLUPS_AUSTRALIAN to Ref(R.drawable.pullups_australian, R.drawable.pullups_australian_dark),
            PUSHUPS to Ref(R.drawable.pushups, R.drawable.pushups_dark),
            PUSHUPS_KNEE to Ref(R.drawable.pushups_knee, R.drawable.pushups_knee_dark),
            RUN to Ref(R.drawable.run, R.drawable.run_dark),
            SQUATS to Ref(R.drawable.squats, R.drawable.squats_dark),
            SQUATS_PISTOL to Ref(R.drawable.squats_pistol, R.drawable.squats_pistol_dark)
        )

    /**
     * Резолвит номер иконки в пару ресурсов.
     *
     * @return `Ref` для известного номера, `null` для `null`/неизвестного —
     *   см. контракт в KDoc объекта.
     */
    fun resolve(iconNumber: Int?): Ref? = iconNumber?.let(byNumber::get)
}
