package com.myworkouts.domain

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guard-тест чистоты домена (образец — JetpackDays `f45c78da`).
 *
 * **СТАТУС: зелёный**: домен не зависит от
 * `android`/`androidx`/`data`/`crash`/`analytics`/`ui`/`navigation`/`R`/
 * `BuildConfig`. Вспыхнувшее при регрессии падение служит картой работ.
 *
 * Сканирует текст всех `.kt` в `domain/` на запрещённые зависимости —
 * и `import X`, и голое FQN (`android.util.Log.e(...)` без import),
 * поэтому сканируется текст файла, а не только import-строки.
 * Комментарии (включая KDoc) и содержимое строковых литералов вырезаются —
 * упоминания платформенных типов там легальны или ложноположительны.
 *
 * Список запрещённых префиксов ниже должен оставаться синхронным
 * с правилом чистоты домена в `AGENTS.md`.
 */
class DomainPurityTest {
    @Test
    fun domain_does_not_depend_on_android_and_app_layers() {
        // Given
        val domainDir = findDomainDir()
        // When
        val violations =
            domainDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> violationsIn(file, domainDir) }
                .toList()
        // Then
        assertTrue(violations.isEmpty()) {
            "Домен протекает платформенными зависимостями: ${violations.size} нарушений\n" +
                violations.sorted().joinToString(separator = "\n") { "  $it" }
        }
    }

    private companion object {
        /**
         * Запрещённые префиксы в тексте доменных исходников — и как `import X`,
         * и как голое FQN. `kotlin*`/`kotlinx*`/`java.*`/`com.myworkouts.domain.*`
         * разрешены (см. план, роадмап фазы 2). Слои `data/`, `crash/`,
         * `analytics/`, `ui/`, `navigation/`, `di/` запрещены — домен не знает
         * ни о платформенных зависимостях, ни о слоях приложения.
         */
        val forbiddenPrefixes =
            listOf(
                "android.",
                "androidx.",
                "com.myworkouts.BuildConfig",
                "com.myworkouts.R",
                "com.myworkouts.data.",
                "com.myworkouts.crash.",
                "com.myworkouts.analytics.",
                "com.myworkouts.ui.",
                "com.myworkouts.navigation.",
                "com.myworkouts.di."
            )

        fun findDomainDir(): File {
            var dir: File? = File(System.getProperty("user.dir"))
            while (dir != null) {
                File(dir, "src/main/java/com/myworkouts/domain")
                    .takeIf { it.isDirectory }
                    ?.let { return it }
                File(dir, "app/src/main/java/com/myworkouts/domain")
                    .takeIf { it.isDirectory }
                    ?.let { return it }
                dir = dir.parentFile
            }
            error(
                "Каталог domain/ не найден вверх от " +
                    System.getProperty("user.dir")
            )
        }

        fun violationsIn(
            file: File,
            domainDir: File
        ): List<String> {
            val stripped = CommentStripper().strip(file.readText())
            val relativePath = file.relativeToDomain(domainDir)
            return stripped
                .lines()
                .flatMapIndexed { lineIndex, line ->
                    forbiddenPrefixes.flatMap { prefix ->
                        matchPositions(line, prefix).map { "$relativePath:${lineIndex + 1}" }
                    }
                }
        }

        private fun File.relativeToDomain(domainDir: File): String =
            relativeTo(domainDir).path.replace(File.separatorChar, '/')

        /**
         * Позиции вхождения [prefix], не являющиеся частью более длинного имени:
         * `kotlinx.android.` не должен ловиться как `android.`, поэтому вхождение
         * пропускается, если перед ним буква/цифра/`_`/`.`. Правая граница
         * (после префикса не буква/цифра/`_`) применяется только к префиксам
         * без хвостовой точки — защита от `com.myworkouts.R` → `…Repository`;
         * префиксы с хвостовой точкой матчатся как есть (после точки в реальном
         * коде всегда идентификатор).
         */
        fun matchPositions(
            line: String,
            prefix: String
        ): List<Int> {
            val checkRightBoundary = !prefix.endsWith(".")
            val positions = mutableListOf<Int>()
            var index = line.indexOf(prefix)
            while (index >= 0) {
                val prev = if (index == 0) ' ' else line[index - 1]
                val next = line.getOrElse(index + prefix.length) { ' ' }
                val insideLongerName = isIdentifierChar(prev) || prev == '.'
                val rightOk = !checkRightBoundary || !isIdentifierChar(next)
                if (!insideLongerName && rightOk) positions.add(index)
                index = line.indexOf(prefix, index + 1)
            }
            return positions
        }

        private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    }
}

/**
 * Конечный автомат вырезания комментариев и содержимого строковых литералов.
 * Заменяет вырезанное пробелами, сохраняя длину и переводы строк, —
 * номера строк в отчёте остаются точными.
 *
 * shortcut: тройные кавычки (`"""`) со встроенными `"` маскируются неточно —
 * фрагмент между внутренними кавычками остаётся видимым, и запрещённый
 * префикс там даст ложное срабатывание; чинить (отдельное состояние до
 * закрывающей `"""`), если в `domain/` появятся raw strings. Char-литералы
 * (`'a'`) не обрабатываются отдельным состоянием — в `domain/` их нет.
 * Вложенные блочные комментарии поддержаны счётчиком глубины.
 */
private class CommentStripper {
    private var inLineComment = false

    /** Глубина вложенных блочных комментариев: Kotlin допускает `/* /* */ */`. */
    private var blockDepth = 0
    private var inString = false

    fun strip(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            i += consume(source, i, out)
        }
        return out.toString()
    }

    private fun consume(
        source: String,
        i: Int,
        out: StringBuilder
    ): Int {
        val c = source[i]
        val next = source.getOrElse(i + 1) { ' ' }
        return when {
            inLineComment -> consumeLineComment(c, out)
            blockDepth > 0 -> consumeBlockComment(c, next, out)
            inString -> consumeString(c, out)
            c == '/' && next == '/' -> startComment(out) { inLineComment = true }
            c == '/' && next == '*' -> startComment(out) { blockDepth = 1 }
            else -> consumeCode(c, out)
        }
    }

    private fun consumeLineComment(
        c: Char,
        out: StringBuilder
    ): Int {
        if (c == '\n') inLineComment = false
        out.append(if (c == '\n') c else ' ')
        return 1
    }

    // `ReturnCount` подавлен осознанно: по раннему return на каждую пару
    // `/*`/`*/` — нагляднее, чем `when` с переменной потреблённой длины.
    @Suppress("ReturnCount")
    private fun consumeBlockComment(
        c: Char,
        next: Char,
        out: StringBuilder
    ): Int {
        if (c == '/' && next == '*') {
            blockDepth++
            out.append("  ")
            return 2
        }
        if (c == '*' && next == '/') {
            blockDepth--
            out.append("  ")
            return 2
        }
        out.append(if (c == '\n') c else ' ')
        return 1
    }

    private fun consumeString(
        c: Char,
        out: StringBuilder
    ): Int {
        if (c == '\\') {
            // Escape-последовательность (включая \") — тоже содержимое
            // литерала, гасим оба символа.
            out.append("  ")
            return 2
        }
        if (c == '"') inString = false
        // Содержимое литерала гасим пробелами: русский текст внутри строк
        // не должен ловиться как FQN-нарушение. Закрывающую кавычку гасим тоже.
        out.append(if (c == '\n') c else ' ')
        return 1
    }

    private fun startComment(
        out: StringBuilder,
        setFlag: () -> Unit
    ): Int {
        setFlag()
        out.append("  ")
        return 2
    }

    private fun consumeCode(
        c: Char,
        out: StringBuilder
    ): Int {
        if (c == '"') inString = true
        out.append(c)
        return 1
    }
}
