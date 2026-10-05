package com.myworkouts.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room-база данных приложения: каталог упражнений, записи о выполнении
 * и категории.
 *
 * Версия `4` (в `2` добавилась колонка `exercises.is_archived` —
 * exercises-archive Decision 1: bump вместо миграции; в `3` — колонка
 * `exercises.icon_number`, exercise-icon-mvp Decision 1; в `4` — таблица
 * `categories` с prepopulate пяти стандартных, custom-categories
 * Decision 5), экспорт схемы в `app/schemas/` включён через
 * `ksp { arg("room.schemaLocation", ...) }` — JSON-схема коммитится
 * в репо.
 *
 * Изменения схемы — через `Migration`, зарегистрированные в
 * `addMigrations(...)`: при расхождении версий файла и схемы без
 * подходящей миграции Room бросает `IllegalStateException` при открытии,
 * файл не трогается (fail fast вместо тихой потери данных).
 *
 * Доступ к БД предоставляется через `create(...)` — единая точка
 * конфигурации, в т.ч. prepopulate через callback.
 */
@Database(
    entities = [ExerciseEntity::class, ExerciseEntryEntity::class, CategoryEntity::class],
    version = 4,
    exportSchema = true
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun exerciseEntryDao(): ExerciseEntryDao

    abstract fun categoryDao(): CategoryDao

    companion object {
        /**
         * Имя файла БД в директории приложения.
         */
        const val DATABASE_NAME: String = "workout_database"

        /**
         * Миграция v3 → v4 (custom-categories, design Decision 5): таблица
         * `categories` (DDL — копия createSql из экспортированной схемы
         * `app/schemas/…/4.json`, включая DEFAULT 0 у флагов), unique-индекс
         * `name` + индекс `created_at`, INSERT пяти стандартных категорий из
         * [standardCategories] с единым `created_at` — стабильная сортировка
         * по `name COLLATE NOCASE`. Рукописная, а не `@AutoMigration`:
         * требуется INSERT данных стандартного набора.
         */
        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `categories` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`color_argb` INTEGER NOT NULL, " +
                            "`is_custom` INTEGER NOT NULL DEFAULT 0, " +
                            "`is_archived` INTEGER NOT NULL DEFAULT 0, " +
                            "`created_at` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` " +
                            "ON `categories` (`name`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_categories_created_at` " +
                            "ON `categories` (`created_at`)"
                    )
                    val now = System.currentTimeMillis()
                    insertStandardCategories(db, now)
                }
            }

        /**
         * Создаёт БД и регистрирует prepopulate 13 стандартных упражнений.
         *
         * Названия резолвятся до регистрации callback'а (он не имеет доступа
         * к [Context], design.md Decision 4): `standardExercises(context)`
         * возвращает готовые записи «название + категория + тип измерения»,
         * которые замыкаются в callback и вставляются одной транзакцией
         * при первом создании БД (см. [seedCallback]).
         *
         * @param context Контекст приложения (должен быть `applicationContext`,
         *   иначе Room ругнётся на утечку при уничтожении Activity)
         * @param standardExercises Готовые записи для prepopulate
         *   (см. [standardExercises])
         */
        fun create(
            context: Context,
            standardExercises: List<StandardExercise>
        ): WorkoutDatabase =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java,
                    DATABASE_NAME
                ).addCallback(seedCallback(standardExercises))
                .addMigrations(MIGRATION_3_4)
                .build()

        /**
         * In-memory-вариант [create] для инструментальных тестов: та же
         * настройка и prepopulate, но без файла на диске — тесты не
         * затирают данные приложения на устройстве.
         */
        fun createInMemory(
            context: Context,
            standardExercises: List<StandardExercise>
        ): WorkoutDatabase =
            Room
                .inMemoryDatabaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java
                ).addCallback(seedCallback(standardExercises))
                .build()

        /**
         * Prepopulate стандартного набора при первом создании БД:
         * Room вызывает [Callback.onCreate] только при первом создании
         * файла, поэтому стандартный каталог появляется вместе с БД.
         */
        private fun seedCallback(standardExercises: List<StandardExercise>): Callback =
            object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedStandardData(db)
                }

                /**
                 * Вставляет стандартный набор — 13 упражнений и 5 категорий —
                 * одной транзакцией. Имена упражнений резолвятся до
                 * регистрации callback'а (замыкание на [standardExercises]) —
                 * callback не имеет доступа к Context; категории — константы
                 * [standardCategories] (custom-categories, спека
                 * category-data: предзаполнение в той же транзакции).
                 */
                private fun seedStandardData(db: SupportSQLiteDatabase) {
                    val now = System.currentTimeMillis()
                    db.beginTransaction()
                    try {
                        standardExercises.forEach { exercise ->
                            val args: Array<Any?> =
                                arrayOf(
                                    exercise.name,
                                    exercise.category,
                                    now,
                                    exercise.measuredBy.name,
                                    exercise.iconNumber
                                )
                            db.execSQL(
                                "INSERT INTO exercises " +
                                    "(name, category, is_custom, comment, created_at, measured_by, icon_number) " +
                                    "VALUES (?, ?, 0, NULL, ?, ?, ?)",
                                args
                            )
                        }
                        insertStandardCategories(db, now)
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }

        /**
         * Вставляет пять стандартных категорий с единым `created_at` —
         * стабильная сортировка по `name COLLATE NOCASE`. Общий код для
         * prepopulate (callback создания) и [MIGRATION_3_4].
         */
        private fun insertStandardCategories(
            db: SupportSQLiteDatabase,
            now: Long
        ) {
            standardCategories.forEach { category ->
                val args: Array<Any?> =
                    arrayOf(category.name, category.colorArgb, now)
                db.execSQL(
                    "INSERT INTO categories " +
                        "(name, color_argb, is_custom, is_archived, created_at) " +
                        "VALUES (?, ?, 0, 0, ?)",
                    args
                )
            }
        }
    }
}
