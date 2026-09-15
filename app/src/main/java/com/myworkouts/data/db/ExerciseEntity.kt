package com.myworkouts.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-сущность каталога упражнений.
 *
 * Каталог общий для стандартных (prepopulate) и пользовательских упражнений
 * (design.md Decision 1, spec: «Единый каталог»). Категория хранится строкой
 * — `name` доменного enum [com.myworkouts.domain.model.ExerciseCategory];
 * маппинг в Mappers.kt. Тип `category` намеренно не типизирован в entity —
 * enum остаётся в доменном слое (design.md Decision 2). Тип измерения
 * `measured_by` хранится так же — строкой имени
 * [com.myworkouts.domain.model.ExerciseMeasuredBy].
 *
 * @property id Автогенерируемый идентификатор
 * @property name Название упражнения
 * @property category Имя категории (`PUSH`/`PULL`/`LEGS`/`CORE`/`CARDIO`)
 * @property isCustom `true` — создано пользователем; `false` — стандартное
 * @property comment Опциональный комментарий к упражнению (техника, хват)
 * @property createdAt Момент создания записи каталога (epoch millis)
 * @property measuredBy Имя типа измерения (`REPS`/`TIME`); дефолт `"REPS"` —
 *   Kotlin-дефолт конструктора (не SQL DEFAULT), именованные вызовы
 *   без поля продолжают компилироваться
 * @property isArchived Флаг архивации: `true` — скрыто из каталога,
 *   `false` — активно. SQL DEFAULT `0` — существующие строки при
 *   деструктивном пересоздании схемы остаются активными (design.md
 *   exercises-archive Decision 1)
 * @property iconNumber Опциональный номер иконки (`icon_number` INTEGER,
 *   nullable, без SQL DEFAULT — деструктивное пересоздание и так даёт
 *   `NULL` в существующих строках; exercise-icon-mvp)
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val category: String,
    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean,
    val comment: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "measured_by")
    val measuredBy: String = "REPS",
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "icon_number")
    val iconNumber: Int? = null
)
