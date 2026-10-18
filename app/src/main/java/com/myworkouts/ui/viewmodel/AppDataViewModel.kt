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
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.domain.crash.CrashReporter
import com.myworkouts.domain.model.BackupSettings
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.domain.usecase.BackupError
import com.myworkouts.domain.usecase.BackupException
import com.myworkouts.domain.usecase.ExportBackupUseCase
import com.myworkouts.domain.usecase.ImportBackupUseCase
import com.myworkouts.ui.state.AppDataResult
import com.myworkouts.ui.state.AppDataUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

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
 * Бэкап-путь без Android в домене: потоки SAF открываются здесь
 * (`contentResolver`), настройки вкладок читаются
 * из [dataStore] перед экспортом и применяются в него после импорта
 * ([AppSettingsDataStore.applyBackupSettings]); ошибка открытия потока —
 * [BackupException] (EXPORT/INVALID_FILE), прочие неперехваченные
 * исключения операций (отозванный SAF-грант, повреждённые preferences)
 * гасит guard [launchOperation] → `fallback`-результат, процесс
 * не падает.
 *
 * @property repository репозиторий данных тренировок (удаление — прямой
 *   вызов, без use-case: единственная строка без собственной логики)
 * @property dataStore хранилище настроек: настройки вкладок обзора
 *   входят в резервную копию (overview-screen, design D8)
 * @property context контекст приложения для SAF-потоков (замыкается
 *   `applicationContext` — Activity-контекст в ViewModel нельзя)
 * @property exportUseCase экспорт резервной копии
 * @property importUseCase восстановление из резервной копии
 * @property analyticsProvider провайдер аналитики: user_action на вызовах
 *   операций и AppError в failure-ветках (design D4, D10)
 * @property crashReporter репортер сбоев: failure-ветки операций бэкапа
 * @property ioDispatcher диспетчер IO-операций (открытие SAF-потока);
 *   дефолт — [Dispatchers.IO], в тестах подменяется тестовым
 *
 * `LongParameterList` подавлен осознанно: это полный граф зависимостей
 * экрана ручного DI (как у фабрик остальных ViewModel проекта).
 */
@Suppress("LongParameterList")
class AppDataViewModel(
    private val repository: WorkoutDataRepository,
    private val dataStore: AppSettingsDataStore,
    private val context: Context,
    private val exportUseCase: ExportBackupUseCase,
    private val importUseCase: ImportBackupUseCase,
    private val analyticsProvider: AnalyticsProvider,
    private val crashReporter: CrashReporter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    companion object {
        /**
         * Factory для ручного DI (образец — [AppearanceViewModel]):
         * сохраняет `context.applicationContext`, создаёт use-case'ы
         * design Decision 3.
         *
         * @param repository репозиторий данных тренировок
         * @param dataStore хранилище настроек: настройки вкладок обзора
         *   входят в резервную копию (overview-screen, design D8)
         * @param context контекст для SAF-потоков (замыкается
         *   `applicationContext` — Activity-контекст в ViewModel нельзя)
         * @param analyticsProvider провайдер аналитики
         * @param crashReporter репортер сбоев: пробрасывается в use-case'ы
         *   бэкапа и в failure-ветки VM
         * @return Factory для создания [AppDataViewModel]
         */
        fun factory(
            repository: WorkoutDataRepository,
            dataStore: AppSettingsDataStore,
            context: Context,
            analyticsProvider: AnalyticsProvider,
            crashReporter: CrashReporter
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val appContext = context.applicationContext
                    AppDataViewModel(
                        repository,
                        dataStore,
                        appContext,
                        ExportBackupUseCase(repository, crashReporter),
                        ImportBackupUseCase(repository, crashReporter),
                        analyticsProvider,
                        crashReporter
                    )
                }
            }
    }

    private val isBusy = MutableStateFlow(false)

    private val result = MutableStateFlow<AppDataResult?>(null)

    /**
     * UI State экрана. `hasData` — есть записи, пользовательские
     * упражнения или пользовательские категории (активные и архивные —
     * архивация не отменяет «данных пользователя» и попадает в экспорт,
     * custom-categories, spec «Создание резервной копии»); потоки
     * репозитория уже существуют (design Decision 7), отдельных снимков
     * не требуется.
     */
    val uiState: StateFlow<AppDataUiState> =
        combine(
            combine(
                repository.entries(),
                repository.exercises(""),
                repository.categories(),
                repository.archivedCategories()
            ) { entries, exercises, categories, archivedCategories ->
                entries.isNotEmpty() ||
                    exercises.any { it.isCustom } ||
                    categories.any { it.isCustom } ||
                    archivedCategories.any { it.isCustom }
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
            started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
            initialValue = AppDataUiState()
        )

    /**
     * Экспорт резервной копии в файл `uri`: настройки вкладок читаются
     * снапшотом из [dataStore] (design D8), открытие SAF-потока записи
     * передаётся в use-case лямбдой — файл открывается только после
     * успешного снимка и сборки JSON, при сбое существующий документ
     * не обнуляется. Действие логируется независимо от исхода; сбой —
     * в Crashlytics и `AppError` (design D8, D10).
     */
    fun exportBackup(uri: Uri) {
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.CREATE_BACKUP))
        launchOperation(fallback = AppDataResult.ERROR_EXPORT) {
            val settings =
                BackupSettings(
                    tabVisibility = dataStore.overviewTabVisibility.first(),
                    lastOverviewTab = dataStore.lastOverviewTab.first()
                )
            exportUseCase(settings) {
                openStream(uri, context.contentResolver::openOutputStream)
            }.fold(
                onSuccess = { AppDataResult.EXPORTED },
                onFailure = {
                    crashReporter.logException(it, "Экспорт резервной копии")
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
     * Восстановление данных из копии `uri` слиянием: настройки из файла
     * применяются к [dataStore] только после успешного восстановления
     * ([AppSettingsDataStore.applyBackupSettings], design D8); сбой
     * записи настроек не проваливает операцию — данные уже в БД
     * (см. `try` в `onSuccess`). Действие логируется независимо от
     * исхода; сбой — в Crashlytics и `AppError` (design D8, D10).
     *
     * `TooGenericExceptionCaught` подавлен осознанно: сбой применения
     * настроек (повреждённые preferences) гасится и логируется —
     * успешное восстановление не превращается в «импорт не удался».
     */
    @Suppress("TooGenericExceptionCaught")
    fun restoreFromBackup(uri: Uri) {
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.RESTORE_BACKUP))
        launchOperation(fallback = AppDataResult.ERROR_IMPORT) {
            val result =
                importUseCase {
                    openStream(uri, context.contentResolver::openInputStream)
                }
            result.fold(
                onSuccess = { settings ->
                    try {
                        dataStore.applyBackupSettings(settings)
                    } catch (e: CancellationException) {
                        // Отмена корутины — не ошибка: rethrow до логирования
                        throw e
                    } catch (e: Exception) {
                        crashReporter.logException(
                            e,
                            "Восстановление резервной копии: сбой применения настроек"
                        )
                    }
                    AppDataResult.RESTORED
                },
                onFailure = {
                    crashReporter.logException(it, "Восстановление резервной копии")
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
        launchOperation(fallback = AppDataResult.ERROR_DELETE) {
            repository.deleteAllData().fold(
                onSuccess = { AppDataResult.DELETED },
                onFailure = {
                    crashReporter.logException(it, "Удаление всех данных")
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
     * Открывает SAF-поток `contentResolver` на [Dispatchers.IO]
     * (`openInputStreamOrNull`-паттерн референса):
     * `IOException` (включая `FileNotFoundException`), `SecurityException`
     * (отозванный грант) и `IllegalArgumentException` (некорректный URI)
     * → `null`, вызывающий превращает его в [BackupException] (экспорт —
     * [BackupError.EXPORT], чтение — [BackupError.INVALID_FILE]).
     * Поток закрывает use-case.
     */
    private suspend fun <T : Closeable> openStream(
        uri: Uri,
        open: (Uri) -> T?
    ): T? =
        withContext(ioDispatcher) {
            try {
                open(uri)
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Не удалось открыть поток: ${e.message}")
                null
            } catch (e: SecurityException) {
                Log.e(LOG_TAG, "Нет доступа к документу: ${e.message}")
                null
            } catch (e: IllegalArgumentException) {
                Log.e(LOG_TAG, "Некорректный URI документа: ${e.message}")
                null
            }
        }

    /**
     * Единый каркас операции: busy на время выполнения, результат —
     * атомарно по завершении. Порядок записи значений исключает
     * состояние «результат есть, а busy ещё не сброшен».
     *
     * Неперехваченное исключение операции (чтение/запись preferences,
     * unchecked от ContentResolver мимо [openStream]) не уронит процесс:
     * сбой логируется в [crashReporter] и превращается в `fallback` —
     * тот же путь, что и у `Result.failure` внутри операций.
     *
     * `TooGenericExceptionCaught` подавлен осознанно: барьер «любой сбой
     * операции → пользовательский результат, не падение» требует общий
     * `catch`, как в use-case'ах бэкапа.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun launchOperation(
        fallback: AppDataResult,
        operation: suspend () -> AppDataResult
    ) {
        viewModelScope.launch {
            isBusy.value = true
            val outcome =
                try {
                    operation()
                } catch (e: CancellationException) {
                    // Отмена корутины — не ошибка: rethrow до логирования
                    throw e
                } catch (e: Exception) {
                    crashReporter.logException(e, "Сбой операции данных приложения")
                    fallback
                }
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
