package com.myworkouts.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room-база данных приложения: каталог упражнений и записи о выполнении.
 *
 * Версия `3` (в `2` добавилась колонка `exercises.is_archived` —
 * exercises-archive Decision 1: bump вместо миграции; в `3` — колонка
 * `exercises.icon_number`, exercise-icon-mvp Decision 1), экспорт схемы
 * в `app/schemas/` включён через `ksp { arg("room.schemaLocation", ...) }` —
 * JSON-схема коммитится в репо
 * (`workouts` появится отдельным изменением схемы, design.md Decision 7).
 *
 * До первого релиза схема может меняться без миграций:
 * `fallbackToDestructiveMigration` пересоздаёт базу при расхождении версий
 * (в т.ч. откат с более новой версии). С первым релизом деструктивный
 * фоллбек убирается и изменения схемы идут через `Migration`.
 *
 * Доступ к БД предоставляется через `create(...)` — единая точка
 * конфигурации, в т.ч. prepopulate через callback.
 */
@Database(
    entities = [ExerciseEntity::class, ExerciseEntryEntity::class],
    version = 3,
    exportSchema = true
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun exerciseEntryDao(): ExerciseEntryDao

    companion object {
        /**
         * Имя файла БД в директории приложения.
         */
        const val DATABASE_NAME: String = "workout_database"

        /**
         * Создаёт БД и регистрирует prepopulate 13 стандартных упражнений.
         *
         * Названия резолвятся до регистрации callback'а (он не имеет доступа
         * к [Context], design.md Decision 4): `standardExercises(context)`
         * возвращает готовые записи «название + категория + тип измерения»,
         * которые замыкаются в callback и вставляются одной транзакцией
         * при первом создании БД и после каждого деструктивного
         * пересоздания таблиц (см. [seedCallback]).
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
                ).fallbackToDestructiveMigration()
                .addCallback(seedCallback(standardExercises))
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
         * Prepopulate стандартного набора при первом создании БД и после
         * каждого деструктивного пересоздания таблиц (design.md
         * seed-on-destructive-migration Decision 1/2): Room вызывает
         * [Callback.onCreate] только при первом создании файла, поэтому
         * деструктивная миграция без обработки оставляла каталог пустым.
         */
        private fun seedCallback(standardExercises: List<StandardExercise>): Callback =
            object : Callback() {
                // Room 2.8 (RoomOpenHelper.onUpgrade) вызывает
                // onDestructiveMigration ПОСЛЕ dropAllTables, но ДО
                // createAllTables — прямой seed в момент вызова падает с
                // «no such table». Переносим его в onOpen, который Room
                // вызывает уже после создания таблиц. Колбэки одного
                // Callback-объекта сериализованы lock'ом открытия БД —
                // plain var достаточен, атомарность не нужна.
                private var needsSeed = false

                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedStandardExercises(db)
                }

                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    needsSeed = true
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    if (needsSeed) {
                        needsSeed = false
                        seedStandardExercises(db)
                    }
                }

                /**
                 * Вставляет стандартный набор одной транзакцией. Имена
                 * резолвятся до регистрации callback'а (замыкание на
                 * [standardExercises]) — callback не имеет доступа к Context.
                 */
                private fun seedStandardExercises(db: SupportSQLiteDatabase) {
                    val now = System.currentTimeMillis()
                    db.beginTransaction()
                    try {
                        standardExercises.forEach { exercise ->
                            val args: Array<Any?> =
                                arrayOf(
                                    exercise.name,
                                    exercise.category.name,
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
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
    }
}
