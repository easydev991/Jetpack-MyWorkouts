package com.myworkouts.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented-тест миграции v3 → v4 (custom-categories, задачи 1.3–1.4,
 * design Decision 5) через `MigrationTestHelper`:
 * create v3 с данными (упражнение + запись) → применить
 * [WorkoutDatabase.MIGRATION_3_4] → валидация схемы v4, пять стандартных
 * категорий, данные упражнений целы.
 *
 * Схемы берутся из assets androidTest (`app/schemas` подключён
 * sourceSet'ом в `build.gradle.kts`).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            WorkoutDatabase::class.java
        )

    @Test
    fun migrate_3To4_thenFiveStandardCategories_andExerciseDataIntact() {
        // Given — файловая БД v3 (schema из 3.json) с пользовательским
        // упражнением и записью на него
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO exercises " +
                    "(name, category, is_custom, comment, created_at, measured_by, icon_number) " +
                    "VALUES ('Бёрпи', 'PUSH', 1, NULL, 42, 'REPS', NULL)"
            )
            execSQL(
                "INSERT INTO exercise_entries " +
                    "(exercise_id, workout_id, date, sets, reps, weight_kg, duration_sec, " +
                    "comment, created_at, modified_at) " +
                    "VALUES (1, NULL, 1700000000000, 4, 10, NULL, NULL, 'до миграции', 1, 1)"
            )
            close()
        }

        // When — миграция 3 → 4 с валидацией итоговой схемы
        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                4,
                true,
                WorkoutDatabase.MIGRATION_3_4
            )

        // Then — ровно 5 стандартных категорий, все активные, с цветами
        // из StandardCategories (контракт спеки category-data)
        assertEquals(5, longForQuery(db, "SELECT COUNT(*) FROM categories"))
        assertEquals(
            5,
            longForQuery(db, "SELECT COUNT(*) FROM categories WHERE is_custom = 0 AND is_archived = 0")
        )
        standardCategories.forEach { category ->
            assertEquals(
                "colorArgb категории ${category.name} должен совпадать со StandardCategories",
                category.colorArgb.toLong(),
                longForQuery(
                    db,
                    "SELECT color_argb FROM categories WHERE name = ?",
                    arrayOf(category.name)
                )
            )
        }

        // Then — данные упражнений и записей не тронуты миграцией
        assertEquals(
            1,
            longForQuery(db, "SELECT COUNT(*) FROM exercises WHERE name = 'Бёрпи' AND is_custom = 1")
        )
        assertEquals(
            1,
            longForQuery(db, "SELECT COUNT(*) FROM exercise_entries WHERE comment = 'до миграции'")
        )

        // Then — версия файла БД поднята до 4
        assertEquals(4, db.version)
    }

    private fun longForQuery(
        db: SupportSQLiteDatabase,
        sql: String,
        bindArgs: Array<out Any?> = emptyArray()
    ): Long =
        db.query(sql, bindArgs).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private companion object {
        const val TEST_DB: String = "migration-test.db"
    }
}
