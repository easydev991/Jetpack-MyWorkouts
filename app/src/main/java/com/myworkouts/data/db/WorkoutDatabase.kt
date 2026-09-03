package com.myworkouts.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room-база данных приложения: каталог упражнений и записи о выполнении.
 *
 * Версия `1`, экспорт схемы в `app/schemas/` включён через
 * `ksp { arg("room.schemaLocation", ...) }` — JSON-схема коммитится в репо
 * для будущих миграций (`workouts` появится отдельной миграцией v1→v2,
 * design.md Decision 7).
 *
 * В MVP у базы нет прямого потребителя в UI: `MainActivity` не меняется,
 * интеграция — следующий change (design.md Decision 8). Доступ к БД
 * предоставляется через `create(...)` — единая точка конфигурации, в т.ч.
 * prepopulate через callback.
 */
@Database(
    entities = [ExerciseEntity::class, ExerciseEntryEntity::class],
    version = 1,
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
         * при первом создании БД.
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
                ).addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            val now = System.currentTimeMillis()
                            db.beginTransaction()
                            try {
                                standardExercises.forEach { exercise ->
                                    val args: Array<Any?> =
                                        arrayOf(
                                            exercise.name,
                                            exercise.category.name,
                                            now,
                                            exercise.measuredBy.name
                                        )
                                    db.execSQL(
                                        "INSERT INTO exercises " +
                                            "(name, category, is_custom, comment, created_at, measured_by) " +
                                            "VALUES (?, ?, 0, NULL, ?, ?)",
                                        args
                                    )
                                }
                                db.setTransactionSuccessful()
                            } finally {
                                db.endTransaction()
                            }
                        }
                    }
                ).build()
    }
}
