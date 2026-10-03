package com.myworkouts.domain.usecase

import android.content.Context
import android.net.Uri
import com.myworkouts.crash.CrashlyticsHelper
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupSettings
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * [WorkoutDataRepository.getAllEntries]), настройки вкладок обзора —
 * из [AppSettingsDataStore] (overview-screen, design D8), JSON собирается
 * чистой функцией [buildBackupJson] и пишется в
 * `context.contentResolver.openOutputStream(uri)` на [Dispatchers.IO].
 * Поток открывается лениво — после успешного снимка и сборки: при сбое
 * снимка файл, созданный SAF, не трогается.
 *
 * Ошибки репозитория проходят в `Result.failure` как есть (design
 * Decision 3); ошибки сборки и записи файла оборачиваются в
 * [BackupException] с [BackupError.EXPORT]; [CancellationException]
 * прокидывается, не превращаясь в ошибку пользователя (design, Risks).
 *
 * @property repository доступ к данным приложения
 * @property dataStore хранилище настроек: видимость вкладок и последняя
 *   вкладка обзора входят в резервную копию (design D8)
 * @property context контекст приложения; потоки открываются через
 *   `contentResolver` (в [com.myworkouts.ui.viewmodel.AppDataViewModel]
 *   передаётся `context.applicationContext`)
 */
class ExportBackupUseCase(
    private val repository: WorkoutDataRepository,
    private val dataStore: AppSettingsDataStore,
    private val context: Context
) {
    /**
     * Выгружает все упражнения, записи и настройки вкладок в файл по [uri].
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
            val settings =
                BackupSettings(
                    tabVisibility = dataStore.overviewTabVisibility.first(),
                    lastOverviewTab = dataStore.lastOverviewTab.first()
                )
            val json = buildBackupJson(exercises, entries, settings)
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
            // Отмена корутины — не ошибка: rethrow строго до логирования
            // (спека «Отмена корутины не является ошибкой»).
            throw e
        } catch (e: BackupException) {
            CrashlyticsHelper.logException(e, "Экспорт резервной копии: сбой записи файла")
            Result.failure(e)
        } catch (e: Exception) {
            CrashlyticsHelper.logException(e, "Экспорт резервной копии: сбой записи файла")
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
 * Чистая функция сборки JSON резервной копии (design Decision 2,
 * overview-screen design D8): версия `2` — с блоком `settings`
 * (видимость вкладок обзора + последняя вкладка); массивы
 * `exercises`/`entries` в порядке переданных списков, запись с отступом 2
 * (`JSONObject.toString(2)`) — файл читается текстовым редактором и в
 * diff. Связь записи с упражнением — только по имени (`exerciseName`),
 * id строк БД и `modifiedAt` в файл не пишутся; `null`-поля
 * сериализуются явно.
 */
internal fun buildBackupJson(
    exercises: List<Exercise>,
    entries: List<ExerciseEntry>,
    settings: BackupSettings = BackupSettings.defaults()
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
                .put("isArchived", exercise.isArchived)
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
    val settingsVisibility = JSONObject()
    settings.tabVisibility.forEach { (tab, visible) ->
        settingsVisibility.put(tab.name, visible)
    }
    val settingsJson =
        JSONObject()
            .put("overviewTabVisibility", settingsVisibility)
            .put("lastOverviewTab", settings.lastOverviewTab.name)
    return JSONObject()
        .put("version", 1)
        .put("exercises", fileExercises)
        .put("entries", fileEntries)
        .put("settings", settingsJson)
        .toString(2)
}
