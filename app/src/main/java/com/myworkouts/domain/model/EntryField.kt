package com.myworkouts.domain.model

import androidx.annotation.StringRes
import com.myworkouts.R

/**
 * Опциональные поля формы записи о выполнении, видимость которых
 * настраивается пользователем (entry-settings-screen, design D1).
 * Упражнение и типозависимое поле (повторы/длительность) сюда
 * не входят — они обязательные и отображаются всегда.
 *
 * Добавление нового опционального поля = одна строка в этом enum
 * + пара строк локализации `entry_visibility_*` + флаг/условие рендера
 * в форме по существующему паттерну; экран настроек и DataStore при
 * этом не меняются (ключи деривируются из [name]).
 *
 * @property labelResId Ресурс названия поля для экрана настроек
 * @property hintResId Ресурс подсказки под названием (`null` — без
 *   подсказки)
 */
enum class EntryField(
    @StringRes val labelResId: Int,
    val hintResId: Int? = null
) {
    SETS(R.string.entry_visibility_sets_label, R.string.entry_visibility_sets_hint),
    WEIGHT(R.string.entry_visibility_weight_label),
    DATE(R.string.entry_visibility_date_label, R.string.entry_visibility_date_hint),
    TIME(R.string.entry_visibility_time_label, R.string.entry_visibility_time_hint),
    COMMENT(R.string.entry_visibility_comment_label)
}
