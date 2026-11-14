package com.myworkouts.domain

import org.junit.jupiter.api.Assertions.assertEquals
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
 * Обрабатываются: строки с escape (`\"`), raw strings — до закрывающих
 * `"""` (встроенные `"` в том числе), char-литералы — до закрывающей `'`
 * (`'\''`, `'\uXXXX'` и т.п.), вложенные блочные комментарии (счётчик глубины).
 */
private class CommentStripper {
    private var inLineComment = false

    /** Глубина вложенных блочных комментариев: Kotlin допускает `/* /* */ */`. */
    private var blockDepth = 0
    private var inString = false
    private var inRawString = false

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
            inRawString -> consumeRawString(source, i, out)
            inString -> consumeString(c, out)
            c == '/' && next == '/' -> startComment(out) { inLineComment = true }
            c == '/' && next == '*' -> startComment(out) { blockDepth = 1 }
            else -> consumeCode(source, i, out)
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

    private fun consumeRawString(
        source: String,
        i: Int,
        out: StringBuilder
    ): Int {
        if (source.startsWith("\"\"\"", i)) {
            // Хвост лишних кавычек (`"""abc""""`) съедается целиком: иначе
            // остаточная `"` включила бы строковый режим и маскировала код
            // до следующей `"` в файле.
            var run = 0
            while (i + run < source.length && source[i + run] == '"') run++
            inRawString = false
            repeat(run) { out.append(' ') }
            return run
        }
        out.append(if (source[i] == '\n') '\n' else ' ')
        return 1
    }

    // `ReturnCount` подавлен осознанно: по раннему выходу на каждый вид
    // литерала (`"""`, `'`, `"`) — нагляднее, чем `when` с переменной
    // потреблённой длины (прецедент — consumeBlockComment).
    @Suppress("ReturnCount")
    private fun consumeCode(
        source: String,
        i: Int,
        out: StringBuilder
    ): Int {
        val c = source[i]
        if (c == '"' && source.startsWith("\"\"\"", i)) {
            inRawString = true
            out.append("   ")
            return 3
        }
        if (c == '\'') return consumeCharLiteral(source, i, out)
        if (c == '"') inString = true
        out.append(c)
        return 1
    }

    /**
     * Char-литерал маскируется целиком до закрывающей `'`; escape-пары
     * (`\'`, `\\`, `\n` и т.п.) пропускаются одним шагом, поэтому закрывающая
     * кавычка не спутается с экранированной — `'\''` маскируется как один
     * литерал, и код на той же строке остаётся видимым. Незакрытая `'`
     * гасится до конца строки — иначе одиночная кавычка каскадом скрыла бы
     * весь последующий код.
     */
    private fun consumeCharLiteral(
        source: String,
        i: Int,
        out: StringBuilder
    ): Int {
        val lineEnd = source.indexOf('\n', i)
        var end = i + 1
        while (end < source.length && end != lineEnd && source[end] != '\'') {
            // Escape-пара занимает два символа; `\` у границы строки или у
            // перевода строки шагаем как обычный символ, чтобы не перескочить
            // `\n` и не сбить счёт строк.
            end += if (source[end] == '\\' && end + 1 < source.length && end + 1 != lineEnd) 2 else 1
        }
        if (end == source.length || end == lineEnd) {
            end = if (lineEnd >= 0) lineEnd else source.length - 1
        }
        for (k in i..end) {
            out.append(if (source[k] == '\n') '\n' else ' ')
        }
        return end - i + 1
    }
}

/**
 * Тесты стриппера: guard сканирует вырезанный текст, поэтому ложные
 * утечки (видимое содержимое литералов) и лишняя маскировка кода
 * ломают его вердикт — проверяются через результат `strip`.
 */
class CommentStripperTest {
    @Test
    fun masks_raw_string_with_embedded_quotes() {
        // Given — raw string со встроенными кавычками
        val source = "val s = \"\"\"x \"android.util.Log\" y\"\"\"\nval ok = 1"
        // When
        val stripped = CommentStripper().strip(source)
        // Then — содержимое скрыто, код после литерала виден
        assertTrue(stripped.contains("val ok = 1"))
        assertTrue(!stripped.contains("android."))
    }

    @Test
    fun masks_raw_string_with_trailing_extra_quote() {
        // Given — хвост из 4 кавычек: закрывающие `"""` плюс лишняя
        val source = "val s = \"\"\"abc\"\"\"\"\nval visible = 1"
        // When
        val stripped = CommentStripper().strip(source)
        // Then — лишняя кавычка не включает строковый режим и не гасит код дальше
        assertTrue(stripped.contains("val visible = 1"))
    }

    @Test
    fun masks_multiline_raw_string_and_preserves_lines() {
        // Given
        val source = "val a = 1\nval s = \"\"\"\nandroid.util.Log\n\"\"\"\nval b = 2"
        // When
        val stripped = CommentStripper().strip(source)
        // Then — переносы сохранены, позиции строк точны
        assertEquals(source.lines().size, stripped.lines().size)
        assertTrue(stripped.contains("val b = 2"))
        assertTrue(!stripped.contains("android."))
    }

    @Test
    fun masks_char_literal_with_quote_and_keeps_code_visible() {
        // Given — `'"'` не должен включать строковый режим и гасить код дальше
        val source = "val q = '\"'\nval visible = 1"
        // When
        val stripped = CommentStripper().strip(source)
        // Then
        assertTrue(stripped.contains("val visible = 1"))
    }

    @Test
    fun masks_char_literal_with_escaped_quote() {
        // Given
        val source = "val q = '\\''\nval visible = 2"
        // When
        val stripped = CommentStripper().strip(source)
        // Then
        assertTrue(stripped.contains("val visible = 2"))
    }

    @Test
    fun char_literal_with_escaped_quote_keeps_same_line_code_visible() {
        // Given — закрывающая кавычка не спутывается с экранированной:
        // остаточная `'` не гасит остаток строки
        val source = "val q = '\\'' ; val ok = 1"
        // When
        val stripped = CommentStripper().strip(source)
        // Then
        assertTrue(stripped.contains("val ok = 1"))
    }

    @Test
    fun masks_char_literal_with_escaped_backslash() {
        // Given — `'\\'`: экранированный обратный слэш, закрывающая `'` после него
        val source = "val q = '\\\\' ; val ok = 1"
        // When
        val stripped = CommentStripper().strip(source)
        // Then
        assertTrue(stripped.contains("val ok = 1"))
    }

    @Test
    fun still_masks_regular_strings() {
        // Given
        val source = "val s = \"android.util.Log\"\nval ok = 1"
        // When
        val stripped = CommentStripper().strip(source)
        // Then
        assertTrue(stripped.contains("val ok = 1"))
        assertTrue(!stripped.contains("android."))
    }
}
