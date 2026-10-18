package com.myworkouts.domain.usecase

import com.myworkouts.domain.crash.CrashReporter
import com.myworkouts.domain.model.BackupSettings
import com.myworkouts.domain.repository.WorkoutDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Экспорт всех данных приложения в поток резервной копии
 * (app-data-screen, design Decision 3 — сигнатуры без Android: поток
 * открывает вызывающий, настройки — снапшотом параметром).
 *
 * Снимок каталога, записей и категорий берётся разовыми методами
 * репозитория ([WorkoutDataRepository.getAllExercises] /
 * [WorkoutDataRepository.getAllEntries] /
 * [WorkoutDataRepository.getAllCategories]), JSON собирается чистой
 * функцией [buildBackupJson] и только затем открывается файл — через
 * [openStream]: при сбое снимка или сборки документ не открывается,
 * а значит и не обнуляется провайдером. Запись идёт на
 * [Dispatchers.IO]; поток закрывается здесь (`use`).
 *
 * Ошибки репозитория проходят в `Result.failure` как есть (design
 * Decision 3); недоступность потока, ошибки сборки и записи файла
 * оборачиваются в [BackupException] с [BackupError.EXPORT];
 * [CancellationException] прокидывается, не превращаясь в ошибку
 * пользователя (design, Risks).
 *
 * @property repository доступ к данным приложения
 * @property crashReporter репортер сбоев: failure-ветки записи файла
 */
class ExportBackupUseCase(
    private val repository: WorkoutDataRepository,
    private val crashReporter: CrashReporter
) {
    /**
     * Выгружает все упражнения, категории, записи и настройки вкладок
     * в поток, открытый [openStream], и закрывает его. [openStream]
     * вызывается после успешного снимка и сборки JSON; `null` —
     * [BackupException] с [BackupError.EXPORT].
     *
     * `ReturnCount` и `TooGenericExceptionCaught` подавлены осознанно:
     * early-return на ошибке снимка репозитория («как есть», design
     * Decision 3) и барьер «любая ошибка записи файла → BackupException»
     * требуют общий `catch` и несколько точек выхода.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    suspend operator fun invoke(
        settings: BackupSettings,
        openStream: suspend () -> OutputStream?
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val exercises =
                repository.getAllExercises().getOrElse { error ->
                    return@withContext Result.failure(error)
                }
            val entries =
                repository.getAllEntries().getOrElse { error ->
                    return@withContext Result.failure(error)
                }
            val categories =
                repository.getAllCategories().getOrElse { error ->
                    return@withContext Result.failure(error)
                }
            try {
                val json = buildBackupJson(exercises, entries, settings, categories)
                val stream =
                    openStream() ?: return@withContext Result.failure(
                        BackupException("Поток записи недоступен", BackupError.EXPORT)
                    )
                stream.use { it.write(json.toByteArray()) }
                Result.success(Unit)
            } catch (e: CancellationException) {
                // Отмена корутины — не ошибка: rethrow строго до логирования
                // (спека «Отмена корутины не является ошибкой»)
                throw e
            } catch (e: BackupException) {
                crashReporter.logException(e, "Экспорт резервной копии: сбой записи файла")
                Result.failure(e)
            } catch (e: Exception) {
                crashReporter.logException(e, "Экспорт резервной копии: сбой записи файла")
                Result.failure(
                    BackupException(
                        "Сбой записи резервной копии: ${e.message}",
                        BackupError.EXPORT
                    )
                )
            }
        }
}
