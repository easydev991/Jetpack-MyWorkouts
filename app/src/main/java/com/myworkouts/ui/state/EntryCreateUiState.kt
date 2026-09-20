package com.myworkouts.ui.state

import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import java.time.LocalDate

/**
 * UI State экрана создания записи о выполнении.
 *
 * @property exercises Каталог упражнений для выпадающего списка
 * @property selectedExercise Выбранное упражнение (`null` — не выбрано)
 * @property setsText Текст поля «Подходы» (только цифры, префилл «1»)
 * @property repsText Текст поля «Повторы» (только для REPS-упражнений)
 * @property durationSec Выбранная длительность в секундах (только для
 *   TIME-упражнений, `null` — не выбрана)
 * @property weightText Текст поля «Доп. вес» (опционально, дефолт «0» —
 *   «без доп. веса»; десятичный ввод, разделитель — точка)
 * @property date Дата выполнения
 * @property hour Час времени выполнения (0–23)
 * @property minute Минута времени выполнения (0–59)
 * @property openMomentMillis Момент открытия экрана (epoch millis) —
 *   граница доступных даты/времени: даты позже дня открытия недоступны,
 *   время на дне открытия клэмпится на момент открытия
 * @property comment Текст комментария (пустой — без комментария)
 * @property isSaveError Признак провала последнего сохранения
 * @property isEditMode Истина, если экран открыт с `entryId != -1`
 *   (режим редактирования), — в том числе пока запись ещё не загружена или
 *   не найдена
 * @property hasChanges Есть отличия формы от загруженной записи
 *   (вычисляется ViewModel; всегда `false` в режиме создания)
 * @property isEntryMissing При открытии edit-режима запись отсутствует
 *   в БД (`getEntry` → `success(null)` или `failure`) — показать подсказку
 *   под кнопкой сохранения. Не меняется после `init {}` — отражает
 *   результат однократной загрузки при открытии экрана
 * @property isSetsFieldVisible Видимость опционального поля «Подходы»
 *   (entry-settings-screen; значение не связано с canSave/hasUnsavedChanges)
 * @property isWeightFieldVisible Видимость опционального поля «Доп. вес»
 * @property isDateFieldVisible Видимость опционального поля даты
 * @property isTimeFieldVisible Видимость опционального поля времени
 * @property isCommentFieldVisible Видимость опционального поля комментария
 */
data class EntryCreateUiState(
    val exercises: List<Exercise> = emptyList(),
    val selectedExercise: Exercise? = null,
    val setsText: String = "1",
    val repsText: String = "",
    val durationSec: Int? = null,
    val weightText: String = "0",
    val date: LocalDate = LocalDate.now(),
    val hour: Int = 0,
    val minute: Int = 0,
    val openMomentMillis: Long = System.currentTimeMillis(),
    val comment: String = "",
    val isSaveError: Boolean = false,
    val isEditMode: Boolean = false,
    val hasChanges: Boolean = false,
    val isEntryMissing: Boolean = false,
    val isSetsFieldVisible: Boolean = true,
    val isWeightFieldVisible: Boolean = true,
    val isDateFieldVisible: Boolean = true,
    val isTimeFieldVisible: Boolean = true,
    val isCommentFieldVisible: Boolean = true
) {
    private val formValid: Boolean
        get() =
            selectedExercise != null &&
                (setsText.toIntOrNull() ?: 0) >= 1 &&
                when (selectedExercise.measuredBy) {
                    ExerciseMeasuredBy.REPS -> repsText.toIntOrNull() != null
                    ExerciseMeasuredBy.TIME -> durationSec != null
                }

    /**
     * Вычисляемая доступность сохранения: в режиме создания — валидная
     * форма (spec: «запись не сохраняется, пока не выбрано упражнение /
     * не заполнены подходы ≥ 1 и типозависимое поле»); в режиме
     * редактирования — валидная форма И есть отличия от записи.
     */
    val canSave: Boolean
        get() = formValid && (!isEditMode || hasChanges)

    /**
     * Вычисляемое «есть что терять» при закрытии экрана без сохранения:
     * в режиме редактирования — [canSave] («валидная форма И есть отличия»);
     * в режиме создания — отличие любого поля от дефолта (дата/время не
     * учитываются — предзаполняются моментом открытия; вес сравнивается
     * численно: «0», «0.0» — дефолт «без веса»).
     *
     * Сравнение значений, а не факта ввода: откат поля до дефолта гасит
     * условие (add-entry-close-confirmation, design Decision 1).
     */
    val hasUnsavedChanges: Boolean
        get() = if (isEditMode) canSave else createModeChanges

    private val createModeChanges: Boolean
        get() =
            selectedExercise != null ||
                setsText.toIntOrNull() != 1 ||
                repsText.isNotEmpty() ||
                durationSec != null ||
                (weightText.toDoubleOrNull() ?: 0.0) != 0.0 ||
                comment.isNotEmpty()
}
