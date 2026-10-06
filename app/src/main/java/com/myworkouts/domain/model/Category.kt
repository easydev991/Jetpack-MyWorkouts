package com.myworkouts.domain.model

/**
 * Категория упражнений (custom-categories, spec «Домен-модель Category»).
 *
 * Упражнения ссылаются на категорию по имени ([name], без FK — design
 * Decision 1); отображение стандартных категорий резолвится через
 * строковые ресурсы по имени-ключу, кастомных — по имени как есть.
 *
 * @property id Идентификатор; `0` — для новой, БД присвоит свой
 * @property name Имя-ключ категории (уникально по `trim().lowercase()`)
 * @property colorArgb Цвет ARGB — выбирается на экране категории (пикер:
 *   стартовый + refresh) и передаётся в операции создания/обновления
 *   готовым; ручной выбор из палитры вне scope
 *   (custom-categories-color-change, tasks 1.5)
 * @property isCustom `true` — создана пользователем, `false` — стандартная
 * @property isArchived `false` по умолчанию; архивация не меняет
 *   ссылающиеся упражнения
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val isCustom: Boolean,
    val isArchived: Boolean = false
)
