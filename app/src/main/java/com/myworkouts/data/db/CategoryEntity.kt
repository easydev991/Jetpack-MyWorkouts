package com.myworkouts.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-сущность категории упражнения (custom-categories, design
 * Decision 1).
 *
 * Упражнения ссылаются на категорию по `name` (без FK): бэкап v1
 * переносит категорию именем, ссылка по имени сохраняет текущую модель
 * `ExerciseEntity.category` — целостность поддерживается приложением
 * (транзакционное переименование, пре-чек удаления).
 *
 * Уникальность `name` — unique-индекс (точное совпадение); дедуп
 * с точностью до `name.trim().lowercase()` — валидация в репозитории,
 * БД-ограничение — запасная страховка (как у дедупа упражнений).
 *
 * @property id Автогенерируемый идентификатор
 * @property name Имя-ключ категории (стандартные: `PUSH`/`PULL`/`LEGS`/
 *   `CORE`/`CARDIO`, кастомные — произвольное имя пользователя)
 * @property colorArgb Цвет ARGB (`StandardCategories` — для стандартных,
 *   генерация hue — для кастомных)
 * @property isCustom `true` — создана пользователем; `false` —
 *   стандартная (переименование/удаление запрещены)
 * @property isArchived Флаг архивации: `true` — скрыта из активного
 *   каталога. SQL DEFAULT `0` — существующие строки остаются активными
 * @property createdAt Момент создания (epoch millis) — сортировка
 *   списков (`created_at DESC`)
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["created_at"])
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    @ColumnInfo(name = "is_custom", defaultValue = "0")
    val isCustom: Boolean,
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
