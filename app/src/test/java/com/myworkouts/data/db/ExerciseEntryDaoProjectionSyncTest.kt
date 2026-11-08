package com.myworkouts.data.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guard-тест синхронности JOIN-проекций DAO записей (чеклист в KDoc
 * [ExerciseEntryDao.observeEntryRows] и [ExerciseEntryDao.observeEntryRowById],
 * workout-entry-detail-screen design Decision 2).
 *
 * SQL-проекция из 13 колонок скопирована 1-в-1 между двумя запросами —
 * осознанное решение дизайна («единая копия SQL, чтобы она не дрейфовала»).
 * Текст `@Query` в рантайме недоступен (retention BINARY), поэтому
 * сверяется исходник DAO: из текста каждого метода извлекается конкатенация
 * строковых литералов его аннотации, из неё — проекция между `SELECT`
 * и первым `FROM`. Расхождение проекций даёт падение с картой работ:
 * новую/переименованную колонку нужно внести в оба запроса.
 *
 * Добавление колонки синхронно в оба запроса тест не ломает — он требует
 * синхронности, а не фиксированного набора колонок.
 */
class ExerciseEntryDaoProjectionSyncTest {
    @Test
    fun projections_of_list_and_by_id_queries_are_identical() {
        // Given
        val daoSource = findDaoSource().readText()
        // When
        val listProjection = projectionOf(daoSource, "observeEntryRows")
        val byIdProjection = projectionOf(daoSource, "observeEntryRowById")
        // Then
        assertTrue(listProjection.isNotEmpty()) {
            "Проекция observeEntryRows не извлечена — парсер теста устарел"
        }
        assertTrue(byIdProjection.isNotEmpty()) {
            "Проекция observeEntryRowById не извлечена — парсер теста устарел"
        }
        assertEquals(listProjection, byIdProjection) {
            "JOIN-проекции observeEntryRows и observeEntryRowById разошлись — " +
                "синхронизируйте оба запроса (чеклист в KDoc DAO)\n" +
                "observeEntryRows:     $listProjection\n" +
                "observeEntryRowById:  $byIdProjection"
        }
    }

    private companion object {
        /**
         * Исходник DAO: файл ищется подъёмом вверх от рабочего каталога —
         * idiom DomainPurityTest (рабочий каталог Gradle-тестов зависит
         * от точки запуска).
         */
        fun findDaoSource(): File {
            var dir: File? = File(System.getProperty("user.dir"))
            while (dir != null) {
                File(dir, "src/main/java/com/myworkouts/data/db/ExerciseEntryDao.kt")
                    .takeIf { it.isFile }
                    ?.let { return it }
                File(dir, "app/src/main/java/com/myworkouts/data/db/ExerciseEntryDao.kt")
                    .takeIf { it.isFile }
                    ?.let { return it }
                dir = dir.parentFile
            }
            error(
                "ExerciseEntryDao.kt не найден вверх от " +
                    System.getProperty("user.dir")
            )
        }

        /**
         * Нормализованная проекция `SELECT … FROM` метода [method]: от
         * последнего `@Query(` перед объявлением fun извлекаются строковые
         * литералы (SQL разбит конкатенацией), из склейки — участок между
         * `SELECT` и первым `FROM` со сжатыми пробелами. Если аннотация или
         * литералы не найдены — loud error: парсер теста устарел, тишина
         * недопустима.
         */
        fun projectionOf(
            source: String,
            method: String
        ): String {
            val funIndex = source.indexOf("fun $method(")
            assertTrue(funIndex >= 0) { "Метод $method не найден в DAO" }
            val annotationIndex = source.lastIndexOf("@Query(", funIndex)
            assertTrue(annotationIndex >= 0) { "@Query метода $method не найден" }
            val sql =
                stringLiterals(source.substring(annotationIndex, funIndex))
                    .joinToString(separator = "")
            val selectIndex = sql.indexOf("SELECT")
            val fromIndex = sql.indexOf("FROM")
            assertTrue(selectIndex >= 0 && fromIndex > selectIndex) {
                "В SQL метода $method нет пары SELECT…FROM"
            }
            return sql
                .substring(selectIndex, fromIndex)
                .trim()
                .replace(Regex("\\s+"), " ")
        }

        /** Содержимое всех `"…"`-литералов [text] с раскрытием escape-последовательностей. */
        fun stringLiterals(text: String): List<String> =
            Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
                .findAll(text)
                .map { it.groupValues[1].replace("\\\"", "\"") }
                .toList()
    }
}
