package com.atrainingtracker.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.regex.Pattern

/**
 * Architecture Audit Test enforcing REQ-DAT-001 / TST-DAT-001:
 * Enforce Try-With-Resources and Cursor Bounds Checks Across Legacy Java Database Managers.
 *
 * Verifies that all SQLite query invocations across legacy Java database managers are:
 * 1. Enclosed in deterministic try-with-resources blocks OR
 * 2. Authorized as an explicit @CursorProvider transferring ownership to callers.
 * 3. Prohibits chained queries (e.g. db.query(...).moveToFirst()) which leak anonymous cursors.
 * 4. Ensures any @SuppressCursorAudit annotations have not expired.
 * 5. Contains "Poison Pill" negative tests proving the audit engine blocks violations.
 */
class TryWithResourcesCursorAuditTest {

    private val targetDirectories = listOf(
        "app/src/main/java/com/atrainingtracker/banalservice/database",
        "app/src/main/java/com/atrainingtracker/trainingtracker/database",
        "app/src/main/java/com/atrainingtracker/trainingtracker/exporter",
        "app/src/main/java/com/atrainingtracker/trainingtracker/segments",
        "app/src/main/java/com/atrainingtracker/trainingtracker/smartwatch/pebble"
    )

    private val queryPattern = Pattern.compile("""(?:db|database|getDatabase\(\)|getReadableDatabase\(\)|getWritableDatabase\(\))\s*\.\s*(?:rawQuery|query)\s*\(""")
    private val chainedPattern = Pattern.compile("""\.(?:rawQuery|query)\s*\([^\)]*\)\s*\.""")
    private val suppressPattern = Pattern.compile("""@SuppressCursorAudit\s*\(\s*ticketId\s*=\s*"([^"]+)"\s*,\s*reason\s*=\s*"([^"]+)"\s*,\s*expiryDate\s*=\s*"([^"]+)"\s*\)""")

    data class MethodChunk(
        val annotations: String,
        val body: String,
        val startLine: Int
    )

    private fun parseMethods(content: String): List<MethodChunk> {
        val lines = content.lines()
        val methods = mutableListOf<MethodChunk>()
        var braceDepth = 0
        var inMethod = false
        val currentMethodLines = mutableListOf<String>()
        var currentStartLine = 1
        val currentAnnotations = mutableListOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val stripped = line.trim()

            if (!inMethod && braceDepth == 1) {
                if (stripped.startsWith("@")) {
                    currentAnnotations.add(stripped)
                } else if (stripped.matches(Regex(""".*\b(public|protected|private|static|final|synchronized)\b.*\(.*\).*(?:throws\s+[^{]+)?\s*\{.*""")) ||
                    (stripped.endsWith("{") && stripped.contains("(") && !stripped.startsWith("class ") && !stripped.startsWith("interface "))
                ) {
                    inMethod = true
                    currentStartLine = i + 1
                    currentMethodLines.clear()
                    currentMethodLines.add(line)
                    braceDepth += line.count { it == '{' } - line.count { it == '}' }
                    i++
                    continue
                }
            }

            if (inMethod) {
                currentMethodLines.add(line)
                braceDepth += line.count { it == '{' } - line.count { it == '}' }
                if (braceDepth <= 1) {
                    methods.add(
                        MethodChunk(
                            annotations = currentAnnotations.joinToString("\n"),
                            body = currentMethodLines.joinToString("\n"),
                            startLine = currentStartLine
                        )
                    )
                    inMethod = false
                    currentMethodLines.clear()
                    currentAnnotations.clear()
                }
            } else {
                braceDepth += line.count { it == '{' } - line.count { it == '}' }
                if (braceDepth <= 1 && !stripped.startsWith("@")) {
                    currentAnnotations.clear()
                }
            }
            i++
        }
        return methods
    }

    fun auditContent(content: String, filename: String = "<test>", referenceDate: LocalDate = LocalDate.now()): List<String> {
        val violations = mutableListOf<String>()

        // 1. Expired suppressions check
        val suppressMatcher = suppressPattern.matcher(content)
        while (suppressMatcher.find()) {
            val ticketId = suppressMatcher.group(1) ?: ""
            val reason = suppressMatcher.group(2) ?: ""
            val expiryStr = suppressMatcher.group(3) ?: ""

            if (!ticketId.matches(Regex("""^ATT-\d+$"""))) {
                violations.add("$filename: Invalid ticketId '$ticketId' in @SuppressCursorAudit")
            }
            try {
                val expDate = LocalDate.parse(expiryStr)
                if (expDate.isBefore(referenceDate)) {
                    violations.add("$filename: Expired @SuppressCursorAudit (ticket: $ticketId, expired on $expiryStr, reason: '$reason')")
                }
            } catch (e: DateTimeParseException) {
                violations.add("$filename: Invalid expiryDate format '$expiryStr' in @SuppressCursorAudit")
            }
        }

        // 2. Chained call check (e.g. db.query(...).moveToFirst())
        val cleanContent = content
            .replace(Regex("""//.*"""), "")
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")

        val queryInvocationPattern = Pattern.compile("""\.(?:rawQuery|query)\s*\(""")
        val queryInvMatcher = queryInvocationPattern.matcher(cleanContent)
        while (queryInvMatcher.find()) {
            val start = queryInvMatcher.end() - 1
            var depth = 0
            var end = -1
            for (idx in start until cleanContent.length) {
                if (cleanContent[idx] == '(') {
                    depth++
                } else if (cleanContent[idx] == ')') {
                    depth--
                    if (depth == 0) {
                        end = idx
                        break
                    }
                }
            }
            if (end != -1 && end + 1 < cleanContent.length) {
                val remainder = cleanContent.substring(end + 1).trimStart()
                if (remainder.startsWith(".") && !remainder.startsWith("..")) {
                    val matchedText = cleanContent.substring(queryInvMatcher.start(), end + 1) + "."
                    violations.add("$filename: Prohibited chained cursor query call detected: '$matchedText'")
                }
            }
        }

        // 3. Method-level try-with-resources check
        val methods = parseMethods(cleanContent)
        for (m in methods) {
            if (m.annotations.contains("@CursorProvider")) {
                continue
            }

            val suppressMethodMatcher = suppressPattern.matcher(m.annotations)
            if (suppressMethodMatcher.find()) {
                val expiryStr = suppressMethodMatcher.group(3) ?: ""
                try {
                    val expDate = LocalDate.parse(expiryStr)
                    if (!expDate.isBefore(referenceDate)) {
                        continue
                    }
                } catch (ignored: Exception) {}
            }

            val lines = m.body.lines()
            for ((idx, line) in lines.withIndex()) {
                if (queryPattern.matcher(line).find() || (line.contains(".query(") && (line.contains("db") || line.contains("database")))) {
                    var enclosedInTry = false
                    val startBack = maxOf(0, idx - 4)
                    for (back in startBack..idx) {
                        val sub = lines.subList(back, idx + 1).joinToString("\n")
                        if (Regex("""try\s*\([^\)]*?(?:db|database|getDatabase\(\)|getReadableDatabase\(\)|getWritableDatabase\(\))\s*\.\s*(?:rawQuery|query)""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(sub)) {
                            enclosedInTry = true
                            break
                        }
                        if (lines[back].contains("try (") || lines[back].contains("try(")) {
                            val openCount = sub.count { it == '(' }
                            val closeCount = sub.count { it == ')' }
                            if (openCount > closeCount || (line.contains("{") && lines[back].contains("try"))) {
                                enclosedInTry = true
                                break
                            }
                        }
                    }
                    if (!enclosedInTry) {
                        violations.add("$filename:${m.startLine + idx}: Unshielded SQLite query not enclosed in try-with-resources: '${line.trim()}'")
                    }
                }
            }
        }

        return violations
    }

    // =========================================================================
    // Negative Tests ("Poison Pill" tests enforcing Gate 3 integrity)
    // =========================================================================

    @Test
    fun testPoisonPill_unshieldedAllocationFails() {
        val snippet = """
            package test;
            public class BadManager {
                public void doUnshieldedQuery(SQLiteDatabase db) {
                    Cursor cursor = db.query("users", null, null, null, null, null, null);
                    cursor.moveToFirst();
                    cursor.close();
                }
            }
        """.trimIndent()

        val violations = auditContent(snippet, "BadManager.java")
        assertFalse("Poison pill for unshielded query MUST produce violations", violations.isEmpty())
        assertTrue(violations.any { it.contains("Unshielded SQLite query not enclosed in try-with-resources") })
    }

    @Test
    fun testPoisonPill_chainedCallFails() {
        val snippet = """
            package test;
            public class ChainedManager {
                public boolean checkExists(SQLiteDatabase db) {
                    return db.query("users", null, null, null, null, null, null).moveToFirst();
                }
            }
        """.trimIndent()

        val violations = auditContent(snippet, "ChainedManager.java")
        assertFalse("Poison pill for chained call MUST produce violations", violations.isEmpty())
        assertTrue(violations.any { it.contains("Prohibited chained cursor query call detected") })
    }

    @Test
    fun testPoisonPill_expiredSuppressionFails() {
        val snippet = """
            package test;
            public class ExpiredManager {
                @SuppressCursorAudit(ticketId = "ATT-3550", reason = "Legacy adapter", expiryDate = "2020-01-01")
                public void oldMethod(SQLiteDatabase db) {
                    Cursor c = db.query("items", null, null, null, null, null, null);
                    c.close();
                }
            }
        """.trimIndent()

        val violations = auditContent(snippet, "ExpiredManager.java", referenceDate = LocalDate.of(2026, 10, 15))
        assertFalse("Poison pill for expired suppression MUST produce violations", violations.isEmpty())
        assertTrue(violations.any { it.contains("Expired @SuppressCursorAudit") })
    }

    @Test
    fun testPoisonPill_validSuppressionAndCursorProviderPass() {
        val snippet = """
            package test;
            public class CompliantManager {
                @CursorProvider
                public Cursor provideCursor(SQLiteDatabase db) {
                    return db.query("items", null, null, null, null, null, null);
                }

                @SuppressCursorAudit(ticketId = "ATT-3550", reason = "Legacy bridge", expiryDate = "2030-01-01")
                public void activeSuppression(SQLiteDatabase db) {
                    Cursor c = db.query("items", null, null, null, null, null, null);
                    c.close();
                }

                public void safeTryWithResources(SQLiteDatabase db) {
                    try (Cursor cursor = db.query("items", null, null, null, null, null, null)) {
                        cursor.moveToFirst();
                    }
                }
            }
        """.trimIndent()

        val violations = auditContent(snippet, "CompliantManager.java", referenceDate = LocalDate.of(2026, 10, 15))
        assertTrue("Compliant manager with CursorProvider, valid suppression, and try-with-resources must pass without violations, but got: $violations", violations.isEmpty())
    }

    // =========================================================================
    // Production Codebase Audit (Will pass once migration steps 2-5 are complete)
    // =========================================================================

    @Test
    fun testAllLegacyJavaQueriesUseTryWithResourcesOrCursorProvider() {
        val currentDir = File(System.getProperty("user.dir") ?: ".")
        val rootDir = when {
            File(currentDir, "app/src/main").exists() -> currentDir
            File(currentDir, "src/main").exists() -> currentDir.parentFile ?: currentDir
            else -> File(currentDir, "..")
        }

        val allViolations = mutableListOf<String>()

        for (relDir in targetDirectories) {
            val dir = File(rootDir, relDir)
            if (!dir.exists()) continue

            dir.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .filter { !it.name.startsWith("DatabaseConstants") && !it.name.startsWith("CursorUtils") && !it.name.startsWith("CursorProvider") && !it.name.startsWith("SuppressCursorAudit") }
                .forEach { file ->
                    val content = file.readText()
                    val violations = auditContent(content, file.relativeTo(rootDir).path)
                    allViolations.extend(violations)
                }
        }

        assertTrue(
            "Found legacy Java SQLite query call sites violating REQ-DAT-001 (must use try-with-resources or @CursorProvider):\n" +
                    allViolations.joinToString("\n"),
            allViolations.isEmpty()
        )
    }

    private fun MutableList<String>.extend(other: List<String>) {
        this.addAll(other)
    }
}
