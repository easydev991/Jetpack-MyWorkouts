package com.myworkouts.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.usecase.BackupError
import com.myworkouts.domain.usecase.BackupException
import com.myworkouts.domain.usecase.ExportBackupUseCase
import com.myworkouts.domain.usecase.ImportBackupUseCase
import com.myworkouts.ui.state.AppDataResult
import com.myworkouts.ui.state.AppDataUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Таймаут подписки StateFlow (5 секунд — стандарт для экранных ViewModel). */
private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

private const val LOG_TAG = "AppDataViewModel"

/**
 * ViewModel экрана «Данные приложения» (app-data-screen, design
 * Decision 7): экспорт и восстановление резервной копии, удаление всех
 * данных.
 *
 * Один [AppDataUiState.isBusy] на все операции: любая запущенная
 * блокирует все кнопки, параллельные операции исключены по построению —
 * детерминированный порядок и ровно один результат.
 *
 * @property repository репозиторий данных тренировок (удаление — прямой
 *   вызов, без use-case: единственная строка без собственной логики)
 * @property exportUseCase экспорт резервной копии
 * @property importUseCase восстановление из резервной копии
 */
class AppDataViewModel(
    private val repository: WorkoutDataRepository,
    private val exportUseCase: ExportBackupUseCase,
    private val importUseCase: ImportBackupUseCase
) : ViewModel() {
    companion object {
        /**
         * Factory для ручного DI (образец — [AppearanceViewModel]):
         * сохраняет `context.applicationContext` и создаёт use-case'ы
         * design Decision 3.
         *
         * @param repository репозиторий данных тренировок
         * @param context контекст для SAF-потоков (замыкается
         *   `applicationContext` — Activity-контекст в ViewModel нельзя)
         * @return Factory для создания [AppDataViewModel]
         */
        fun factory(
            repository: WorkoutDataRepository,
            context: Context
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val appContext = context.applicationContext
                    AppDataViewModel(
                        repository,
                        ExportBackupUseCase(repository, appContext),
                        ImportBackupUseCase(repository, appContext)
                    )
                }
            }
    }

    private val isBusy = MutableStateFlow(false)

    private val result = MutableStateFlow<AppDataResult?>(null)

    /**
     * UI State экрана. `hasData` — есть записи или пользовательские
     * упражнения; оба потока репозитория уже существуют (design
     * Decision 7), отдельных снимков не требуется.
     */
    val uiState: StateFlow<AppDataUiState> =
        combine(
            combine(
                repository.entries(),
                repository.exercises("")
            ) { entries, exercises ->
                entries.isNotEmpty() || exercises.any { it.isCustom }
            },
            isBusy,
            result
        ) { hasData, busy, pendingResult ->
            AppDataUiState(
                hasData = hasData,
                isBusy = busy,
                result = pendingResult
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = AppDataUiState()
        )

    /** Экспорт резервной копии в файл `uri`. */
    fun exportBackup(uri: Uri) {
        launchOperation {
            exportUseCase(uri).fold(
                onSuccess = { AppDataResult.EXPORTED },
                onFailure = {
                    it.toAppDataResult(
                        operation = "экспорт",
                        fallback = AppDataResult.ERROR_EXPORT
                    )
                }
            )
        }
    }

    /** Восстановление данных из копии `uri` слиянием. */
    fun restoreFromBackup(uri: Uri) {
        launchOperation {
            importUseCase(uri).fold(
                onSuccess = { AppDataResult.RESTORED },
                onFailure = {
                    it.toAppDataResult(
                        operation = "импорт",
                        fallback = AppDataResult.ERROR_IMPORT
                    )
                }
            )
        }
    }

    /** Удаление всех данных с восстановлением стандартного набора. */
    fun deleteAllData() {
        launchOperation {
            repository.deleteAllData().fold(
                onSuccess = { AppDataResult.DELETED },
                onFailure = {
                    it.toAppDataResult(
                        operation = "удаление",
                        fallback = AppDataResult.ERROR_DELETE
                    )
                }
            )
        }
    }

    /** Сброс одноразового результата после показа Toast. */
    fun clearResult() {
        result.value = null
    }

    /**
     * Единый каркас операции: busy на время выполнения, результат —
     * атомарно по завершении. Порядок записи значений исключает
     * состояние «результат есть, а busy ещё не сброшен».
     */
    private fun launchOperation(operation: suspend () -> AppDataResult) {
        viewModelScope.launch {
            isBusy.value = true
            val outcome = operation()
            isBusy.value = false
            result.value = outcome
        }
    }

    /**
     * Маппинг ошибки операции в [AppDataResult]: [BackupException]
     * типизирован по [BackupError], прочие (ошибки БД «как есть»,
     * design Decision 3) — обобщённый `fallback` операции. Техническое
     * сообщение — только в лог.
     */
    private fun Throwable.toAppDataResult(
        operation: String,
        fallback: AppDataResult
    ): AppDataResult {
        Log.e(LOG_TAG, "Не удался $operation: $message")
        return if (this is BackupException) {
            when (error) {
                BackupError.EXPORT -> AppDataResult.ERROR_EXPORT
                BackupError.INVALID_FILE -> AppDataResult.ERROR_INVALID_FILE
            }
        } else {
            fallback
        }
    }
}
