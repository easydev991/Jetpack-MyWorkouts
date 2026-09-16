package com.myworkouts.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.AppErrorOperation
import com.myworkouts.analytics.UserActionType
import com.myworkouts.crash.CrashlyticsHelper
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
 * @property analyticsProvider провайдер аналитики: user_action на вызовах
 *   операций и AppError в failure-ветках (design D4, D10)
 */
class AppDataViewModel(
    private val repository: WorkoutDataRepository,
    private val exportUseCase: ExportBackupUseCase,
    private val importUseCase: ImportBackupUseCase,
    private val analyticsProvider: AnalyticsProvider
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
         * @param analyticsProvider провайдер аналитики
         * @return Factory для создания [AppDataViewModel]
         */
        fun factory(
            repository: WorkoutDataRepository,
            context: Context,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val appContext = context.applicationContext
                    AppDataViewModel(
                        repository,
                        ExportBackupUseCase(repository, appContext),
                        ImportBackupUseCase(repository, appContext),
                        analyticsProvider
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

    /**
     * Экспорт резервной копии в файл `uri`. Действие логируется
     * независимо от исхода; сбой — в Crashlytics и `AppError`
     * (design D8, D10).
     */
    fun exportBackup(uri: Uri) {
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.CREATE_BACKUP))
        launchOperation {
            exportUseCase(uri).fold(
                onSuccess = { AppDataResult.EXPORTED },
                onFailure = {
                    CrashlyticsHelper.logException(it, "Экспорт резервной копии")
                    analyticsProvider.log(
                        AnalyticsEvent.AppError(AppErrorOperation.CREATE_BACKUP, it)
                    )
                    it.toAppDataResult(
                        operation = "экспорт",
                        fallback = AppDataResult.ERROR_EXPORT
                    )
                }
            )
        }
    }

    /**
     * Восстановление данных из копии `uri` слиянием. Действие логируется
     * независимо от исхода; сбой — в Crashlytics и `AppError`
     * (design D8, D10).
     */
    fun restoreFromBackup(uri: Uri) {
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.RESTORE_BACKUP))
        launchOperation {
            importUseCase(uri).fold(
                onSuccess = { AppDataResult.RESTORED },
                onFailure = {
                    CrashlyticsHelper.logException(it, "Восстановление резервной копии")
                    analyticsProvider.log(
                        AnalyticsEvent.AppError(AppErrorOperation.RESTORE_BACKUP, it)
                    )
                    it.toAppDataResult(
                        operation = "импорт",
                        fallback = AppDataResult.ERROR_IMPORT
                    )
                }
            )
        }
    }

    /**
     * Удаление всех данных с восстановлением стандартного набора.
     * Действие логируется независимо от исхода; сбой — в Crashlytics
     * и `AppError` (design D8, D10).
     */
    fun deleteAllData() {
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.DELETE_ALL_DATA))
        launchOperation {
            repository.deleteAllData().fold(
                onSuccess = { AppDataResult.DELETED },
                onFailure = {
                    CrashlyticsHelper.logException(it, "Удаление всех данных")
                    analyticsProvider.log(
                        AnalyticsEvent.AppError(AppErrorOperation.DELETE_ALL_DATA, it)
                    )
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
