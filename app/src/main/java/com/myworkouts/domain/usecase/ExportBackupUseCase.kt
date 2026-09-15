package com.myworkouts.domain.usecase

import android.content.Context
import android.net.Uri
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CancellationException

/**
 * Экспорт всех данных приложения в файл резервной копии
 * (app-data-screen, design Decision 3).
 *
 * Снимок каталога и записей берётся разовыми методами репозитория
 * ([WorkoutDataRepository.getAllExercises] /
 * [WorkoutDataRepository.getAllEntries]), JSON собирается чистой функцией
 * [buildBackupJson] и пишется в `context.contentResolver.openOutputStream(uri)`
 * на [Dispatchers.IO]. Поток открывается лениво — после успешного снимка и
 * сборки: при сбое снимка файл, созданный SAF, не трогается.
 *
 * Ошибки репозитория проходят в `Result.failure` как есть (design
 * Decision 3); ошибки сборки и записи файла оборачиваются в
 * [BackupException] с [BackupError.EXPORT]; [CancellationException]
 * прокидывается, не превращаясь в ошибку пользователя (design, Risks).
 *
 * @property repository доступ к данным приложения
 * @property context контекст приложения; потоки открываются через
 *   `contentResolver` (в [com.myworkouts.ui.viewmodel.AppDataViewModel]
 *   передаётся `context.applicationContext`)
 */
class ExportBackupUseCase(
    private val repository: WorkoutDataRepository,
    private val context: Context
) {
    /**
     * Выгружает все упражнения и записи в файл по [uri].
     *
     * `ReturnCount` и `TooGenericExceptionCaught` подавлены осознанно:
     * early-return на ошибке снимка репозитория («как есть», design
     * Decision 3) и барьер «любая ошибка записи файла → BackupException»
     * требуют общий `catch` и несколько точек выхода.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    suspend operator fun invoke(uri: Uri): Result<Unit> {
        val exercises =
            repository.getAllExercises().getOrElse { error ->
                return Result.failure(error)
            }
        val entries =
            repository.getAllEntries().getOrElse { error ->
                return Result.failure(error)
            }
        return try {
            val json = buildBackupJson(exercises, entries)
            withContext(Dispatchers.IO) {
                val stream =
                    context.contentResolver.openOutputStream(uri)
                        ?: throw BackupException(
                            "Поток записи недоступен: $uri",
                            BackupError.EXPORT
                        )
                stream.use { it.write(json.toByteArray()) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                BackupException(
                    "Сбой записи резервной копии: ${e.message}",
                    BackupError.EXPORT
                )
            )
        }
    }
}

/**
 * Чистая функция сборки JSON резервной копии (design Decision 2): всегда
 * `version: 1`, массивы `exercises`/`entries` в порядке переданных списков,
 * запись с отступом 2 (`JSONObject.toString(2)`) — файл читается текстовым
 * редактором и в diff. Связь записи с упражнением — только по имени
 * (`exerciseName`), id строк БД и `modifiedAt` в файл не пишутся;
 * `null`-поля сериализуются явно.
 */
internal fun buildBackupJson(
    exercises: List<Exercise>,
    entries: List<ExerciseEntry>
): String {
    val namesById = exercises.associate { it.id to it.name }
    val fileExercises = JSONArray()
    exercises.forEach { exercise ->
        fileExercises.put(
            JSONObject()
                .put("name", exercise.name)
                .put("category", exercise.category.name)
                .put("measuredBy", exercise.measuredBy.name)
                .put("isCustom", exercise.isCustom)
                .put("comment", exercise.comment ?: JSONObject.NULL)
                .put("iconNumber", exercise.iconNumber ?: JSONObject.NULL)
                .put("createdAt", exercise.createdAt)
        )
    }
    val fileEntries = JSONArray()
    entries.forEach { entry ->
        val exerciseName =
            checkNotNull(namesById[entry.exerciseId]) {
                "Запись id=${entry.id} ссылается на упражнение " +
                    "id=${entry.exerciseId}, которого нет в снимке каталога"
            }
        fileEntries.put(
            JSONObject()
                .put("exerciseName", exerciseName)
                .put("date", entry.date)
                .put("sets", entry.sets)
                .put("reps", entry.reps ?: JSONObject.NULL)
                .put("weightKg", entry.weightKg ?: JSONObject.NULL)
                .put("durationSec", entry.durationSec ?: JSONObject.NULL)
                .put("comment", entry.comment ?: JSONObject.NULL)
                .put("createdAt", entry.createdAt)
        )
    }
    return JSONObject()
        .put("version", 1)
        .put("exercises", fileExercises)
        .put("entries", fileEntries)
        .toString(2)
}
