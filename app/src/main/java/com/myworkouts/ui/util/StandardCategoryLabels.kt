package com.myworkouts.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myworkouts.R

/**
 * Резолвит имя-ключ стандартной категории в строковый ресурс
 * локализованного названия (`exercise_category_*`). `null` — имя не из
 * стандартного набора (кастомная категория): отображается по `name`
 * как есть (custom-categories, design Decision 2).
 *
 * Единственная связь «имя-ключ → label-ресурс»; зависит только от
 * констант и `R.string`, enum не использует. Контракт соответствия —
 * `StandardCategoriesTest`.
 */
@StringRes
fun standardCategoryLabelResId(name: String): Int? =
    when (name) {
        "PUSH" -> R.string.exercise_category_push
        "PULL" -> R.string.exercise_category_pull
        "LEGS" -> R.string.exercise_category_legs
        "CORE" -> R.string.exercise_category_core
        "CARDIO" -> R.string.exercise_category_cardio
        else -> null
    }

/**
 * Отображаемое имя категории (единое для строки списка, дропдауна
 * выбора категории и легенды статистики — реюз трёх приватных копий):
 * стандартная — локализованный `exercise_category_*`, кастомная и
 * неизвестная — имя как есть.
 */
@Composable
internal fun categoryLabel(name: String): String = standardCategoryLabelResId(name)?.let { stringResource(it) } ?: name
