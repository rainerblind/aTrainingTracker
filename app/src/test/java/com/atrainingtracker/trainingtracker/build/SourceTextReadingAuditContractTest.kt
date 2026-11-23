/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural audit test enforcing prohibition of source-text reading tests
 * while exempting legitimate system architectural governance contract tests
 * (REQ-PRO-049, TST-PRO-049, docs/coding_standards.md §5.4, ATT-6186, ATT-6731).
 */
class SourceTextReadingAuditContractTest {

    companion object {
        /**
         * Legitimate system policy and architectural governance contract tests
         * preserved by docs/coding_standards.md §5.4 (REQ-PRO-049.2).
         */
        val LEGITIMATE_GOVERNANCE_GUARDS: Set<String> = setOf(
            "com/atrainingtracker/architecture/TryWithResourcesCursorAuditTest.kt",
            "com/atrainingtracker/architecture/SafeEnumDeserializationAuditTest.kt",
            "com/atrainingtracker/architecture/LifecycleServiceReceiverSafetyAuditTest.kt",
            "com/atrainingtracker/trainingtracker/build/ManifestAndBuildConfigContractTest.kt",
            "com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt",
            "com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt",
            "com/atrainingtracker/trainingtracker/build/ExceptionHandlingHygieneTest.kt",
            "com/atrainingtracker/trainingtracker/security/BackupRulesXmlValidationTest.kt",
            "com/atrainingtracker/trainingtracker/security/FileProviderHardeningContractTest.kt",
            "com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt",
            "com/atrainingtracker/trainingtracker/tracker/MergedManifestFgsAuditTest.kt",
            "com/atrainingtracker/trainingtracker/resources/NotificationIconContractTest.kt",
            "com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt",
            "com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt",
            "com/atrainingtracker/testing/LocalizationTestCache.kt",
            "com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt",
            "com/atrainingtracker/trainingtracker/ui/theme/ThemeColorCentralizationAuditTest.kt",
            "com/atrainingtracker/trainingtracker/ui/RepositoryEngineCleanlinessContractTest.kt",
            "com/atrainingtracker/trainingtracker/ui/RepositoryUiCleanlinessContractTest.kt",
            "com/atrainingtracker/trainingtracker/ui/GlobalDeleteContextMenuAuditTest.kt",
            "com/atrainingtracker/trainingtracker/ui/EditIconConsistencyTest.kt"
        )
        /**
         * Strips `//` line comments and `/* ... */` block comments while preserving string literals.
         */
        internal fun stripComments(source: String): String {
            val out = StringBuilder(source.length)
            var i = 0
            val n = source.length
            while (i < n) {
                val c = source[i]
                if (c == '"' && i + 2 < n && source[i + 1] == '"' && source[i + 2] == '"') {
                    out.append("\"\"\"")
                    i += 3
                    while (i < n) {
                        if (source[i] == '"' && i + 2 < n && source[i + 1] == '"' && source[i + 2] == '"') {
                            out.append("\"\"\"")
                            i += 3
                            break
                        }
                        out.append(source[i++])
                    }
                } else if (c == '"') {
                    out.append(c)
                    i++
                    while (i < n) {
                        val sc = source[i]
                        out.append(sc)
                        i++
                        if (sc == '\\' && i < n) {
                            out.append(source[i++])
                        } else if (sc == '"') {
                            break
                        }
                    }
                } else if (c == '/' && i + 1 < n && source[i + 1] == '/') {
                    i += 2
                    while (i < n && source[i] != '\n') i++
                } else if (c == '/' && i + 1 < n && source[i + 1] == '*') {
                    i += 2
                    while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                    i = minOf(n, i + 2)
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }

        private val RAW_STRING_REGEX = Regex("\"\"\"([\\s\\S]*?)\"\"\"")
        private val SIMPLE_VAL_REGEX = Regex("""\b(?:const\s+)?(?:val|var|String)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*String)?\s*=\s*"([^"\n]*)"""")
        private val CONCAT_REGEX = Regex(""""([^"\n]*)"\s*\+\s*"([^"\n]*)"""")
        private val JOINED_CALL_REGEX = Regex("""\b(?:listOf|arrayOf|Paths\.get|Path\.of|File)\s*\(([^()]*)\)""")
        private val QUOTED_ARG_REGEX = Regex(""""([^"\n]*)"""")
        private val RESOLVE_REGEX = Regex(""""([^"\n]*)"\s*\)\s*\.\s*resolve\s*\(\s*"([^"\n]*)"\s*\)""")
        private val STRING_RESOLVE_REGEX = Regex(""""([^"\n]*)"\s*\.\s*resolve\s*\(\s*"([^"\n]*)"\s*\)""")
        private val SB_APPEND_REGEX = Regex("""(?:StringBuilder\s*\(\s*"([^"\n]*)"\s*\)|"([^"\n]*)")\s*\.\s*append\s*\(\s*"([^"\n]*)"\s*\)""")
        private val READ_INVOCATION_REGEX = Regex(
            """(?:\.\s*(?:readText|readLines|readString|readAllLines|readAllBytes|bufferedReader)\s*\(|\.\s*useLines\s*(?:\(|\{)|\b(?:Files\.(?:readString|readAllLines|lines|readAllBytes|newBufferedReader)|FileReader|Scanner|InputStreamReader|FileInputStream)\s*\()"""
        )
        private val PROD_TARGET_REGEX = Regex(
            """(?:src/main|main/java|main/kotlin|main/res|src/main/AndroidManifest\.xml|main/AndroidManifest\.xml)"""
        )

        /**
         * Normalizes concatenated, templated, joined, StringBuilder, raw-string, and resolved path
         * expressions into canonical slash-separated literals so evasion tricks cannot bypass detection.
         */
        internal fun normalizePathExpressions(strippedSource: String): String {
            var code = strippedSource
                .replace("\${File.separator}", "/")
                .replace("File.separatorChar", "\"/\"")
                .replace("File.separator", "\"/\"")

            // Collapse raw multiline strings into single-line quoted literals
            code = RAW_STRING_REGEX.replace(code) { m ->
                val inner = m.groupValues[1].replace("\"", "\\\"").replace("\r", "").replace("\n", " ")
                "\"$inner\""
            }

            // Iteratively fold simple local string vars, templates, +, join/Path calls, resolve, and StringBuilder
            repeat(5) {
                val localVars = mutableMapOf<String, String>()
                SIMPLE_VAL_REGEX.findAll(code).forEach { m ->
                    localVars[m.groupValues[1]] = m.groupValues[2]
                }
                if (localVars.isNotEmpty()) {
                    code = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\}""").replace(code) { m ->
                        localVars[m.groupValues[1]] ?: m.value
                    }
                    code = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)""").replace(code) { m ->
                        localVars[m.groupValues[1]] ?: m.value
                    }
                    code = Regex("""\b([A-Za-z_][A-Za-z0-9_]*)\s*\+\s*"""").replace(code) { m ->
                        val v = localVars[m.groupValues[1]]
                        if (v != null) "\"$v" else m.value
                    }
                    code = Regex(""""\s*\+\s*([A-Za-z_][A-Za-z0-9_]*)\b""").replace(code) { m ->
                        val v = localVars[m.groupValues[1]]
                        if (v != null) "$v\"" else m.value
                    }
                }
                code = CONCAT_REGEX.replace(code) { m ->
                    "\"${m.groupValues[1]}${m.groupValues[2]}\""
                }
                code = JOINED_CALL_REGEX.replace(code) { m ->
                    val args = QUOTED_ARG_REGEX.findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
                    if (args.size >= 2 && m.groupValues[1].replace(QUOTED_ARG_REGEX, "").replace(",", "").isBlank()) {
                        val joined = args.joinToString("/").replace("//", "/")
                        "\"$joined\""
                    } else {
                        m.value
                    }
                }
                code = RESOLVE_REGEX.replace(code) { m ->
                    val joined = "${m.groupValues[1].trimEnd('/')}/${m.groupValues[2].trimStart('/')}"
                    "\"$joined\")"
                }
                code = STRING_RESOLVE_REGEX.replace(code) { m ->
                    val joined = "${m.groupValues[1].trimEnd('/')}/${m.groupValues[2].trimStart('/')}"
                    "\"$joined\""
                }
                code = SB_APPEND_REGEX.replace(code) { m ->
                    val first = m.groupValues[1].ifEmpty { m.groupValues[2] }
                    "\"$first${m.groupValues[3]}\""
                }
            }
            return code
        }

        private val DECL_START_REGEX = Regex("""^(?:(?:private|protected|internal|public|final|static|override)\s+)*(?:val|var|File|Path|String)\s+""")

        private fun groupLogicalStatements(normalizedCode: String): List<String> {
            val statements = mutableListOf<String>()
            val current = StringBuilder()
            var parenDepth = 0
            var rhsBraceDepth = 0
            var trackingRhsBraces = false
            for (rawLine in normalizedCode.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (current.isEmpty() && DECL_START_REGEX.containsMatchIn(line)) {
                    trackingRhsBraces = true
                    rhsBraceDepth = 0
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(line)
                for (ch in line) {
                    when (ch) {
                        '(' -> parenDepth++
                        ')' -> parenDepth = maxOf(0, parenDepth - 1)
                        '{' -> if (trackingRhsBraces) rhsBraceDepth++
                        '}' -> if (trackingRhsBraces) rhsBraceDepth = maxOf(0, rhsBraceDepth - 1)
                    }
                }
                if (parenDepth == 0 && rhsBraceDepth == 0 && !line.endsWith("+") && !line.endsWith(".") && !line.endsWith("=")) {
                    statements.add(current.toString())
                    current.clear()
                    trackingRhsBraces = false
                }
            }
            if (current.isNotEmpty()) statements.add(current.toString())
            return statements
        }

        /**
         * Returns detected production source-reading statements/targets in [source], pairing
         * read API invocations with resolved `src/main` targets at statement and helper scope
         * so non-source I/O (`docs/`, `tools/`, temp files) and non-reading path walks
         * (`walkTopDown()`, `.exists()`) produce zero false positives (REQ-PRO-133.1).
         */
        internal fun detectSourceReadingTargets(source: String): List<String> {
            val stripped = stripComments(source)
            val normalized = normalizePathExpressions(stripped)
            val statements = groupLogicalStatements(normalized)

            val taintedHelpers = mutableSetOf<String>()
            val readingHelpers = mutableSetOf<String>()
            val findings = mutableListOf<String>()

            // Pass 1: Identify helper functions whose bodies construct/return a production path or wrap file reads
            val funDeclRegex = Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
            var currentFun: String? = null
            var braceDepth = 0
            val currentFunStmts = mutableListOf<String>()
            for (stmt in statements) {
                val funMatch = if (currentFun == null) funDeclRegex.find(stmt) else null
                if (funMatch != null) {
                    currentFun = funMatch.groupValues[1]
                    currentFunStmts.clear()
                }
                if (currentFun != null) {
                    currentFunStmts.add(stmt)
                    braceDepth += stmt.count { it == '{' } - stmt.count { it == '}' }
                    if (braceDepth <= 0) {
                        val body = currentFunStmts.joinToString("\n")
                        val hasProd = PROD_TARGET_REGEX.containsMatchIn(body)
                        val hasRead = READ_INVOCATION_REGEX.containsMatchIn(body)
                        if (hasProd && !hasRead) {
                            taintedHelpers.add(currentFun)
                        } else if (hasRead && !currentFun.startsWith("test")) {
                            readingHelpers.add(currentFun)
                        }
                        currentFun = null
                        braceDepth = 0
                    }
                }
            }

            // Pass 2: Track statement-level target taint paired with read invocations (or reading-helper calls)
            val taintedVars = mutableSetOf<String>()
            val assignRegex = Regex("""^(?:(?:(?:private|protected|internal|public|final|static|override)\s+)*(?:val|var|File|Path|String)\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*(?::[^=]+)?(?:=|\bby\s+lazy\b)\s*(.+)$""")
            val loopRegex = Regex("""^(?:for\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s+in\s+(.+)\)|.*\b(?:forEach|walkTopDown|listFiles)\b.*)""")
            var loopTaintDepth = 0
            var loopBraceDepth = 0

            for (stmt in statements) {
                val hasProdLiteral = PROD_TARGET_REGEX.containsMatchIn(stmt)
                val callsTaintedHelper = taintedHelpers.any { h -> Regex("""\b$h\s*\(""").containsMatchIn(stmt) }
                val refsTaintedVar = taintedVars.any { v -> Regex("""\b$v\b""").containsMatchIn(stmt) }
                val stmtTainted = hasProdLiteral || callsTaintedHelper || refsTaintedVar || loopTaintDepth > 0

                val assignMatch = assignRegex.find(stmt)
                if (assignMatch != null) {
                    val varName = assignMatch.groupValues[1]
                    if (stmtTainted) {
                        taintedVars.add(varName)
                    } else {
                        taintedVars.remove(varName)
                    }
                }

                val loopMatch = loopRegex.find(stmt)
                if (loopMatch != null && stmtTainted) {
                    val loopVar = loopMatch.groupValues[1]
                    if (loopVar.isNotEmpty()) taintedVars.add(loopVar)
                    if (stmt.contains("{")) {
                        loopTaintDepth = 1
                        loopBraceDepth = stmt.count { it == '{' } - stmt.count { it == '}' }
                    }
                } else if (loopTaintDepth > 0) {
                    loopBraceDepth += stmt.count { it == '{' } - stmt.count { it == '}' }
                    if (loopBraceDepth <= 0) {
                        loopTaintDepth = 0
                        loopBraceDepth = 0
                    }
                }

                val callsReadingHelperWithTaint = readingHelpers.any { h ->
                    Regex("""\b$h\s*\(""").containsMatchIn(stmt)
                }
                if (stmtTainted && (READ_INVOCATION_REGEX.containsMatchIn(stmt) || callsReadingHelperWithTaint)) {
                    findings.add(stmt)
                }
            }
            return findings
        }
    }

    private fun findRepoRootFile(relativePath: String, appRelativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(appRelativePath),
            File("../$relativePath"),
            File("../$appRelativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    private fun findTestDir(): File {
        val candidates = listOf(
            File("app/src/test/java"),
            File("src/test/java"),
            File("../app/src/test/java"),
            File("../src/test/java")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not locate test directory in: ${File(".").absolutePath}")
    }

    private fun loadBaselineLegacyReaders(): Set<String> {
        val file = findRepoRootFile("app/source-reading-baseline.txt", "source-reading-baseline.txt")
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    private fun scanTestSourceFiles(testDir: File): Set<String> {
        val readers = mutableSetOf<String>()
        val thisClassName = "SourceTextReadingAuditContractTest.kt"

        testDir.walkTopDown().forEach { file ->
            if ((file.name.endsWith(".kt") || file.name.endsWith(".java")) && file.name != thisClassName) {
                val content = file.readText()
                if (detectSourceReadingTargets(content).isNotEmpty()) {
                    val relativePath = file.relativeTo(testDir).invariantSeparatorsPath
                    readers.add(relativePath)
                }
            }
        }
        return readers
    }

    @Test
    fun testSourceReadingAudit_noUnallowlistedSourceReaders() {
        val testDir = findTestDir()
        val detectedReaders = scanTestSourceFiles(testDir)
        val baseline = loadBaselineLegacyReaders()

        val allowedSet = LEGITIMATE_GOVERNANCE_GUARDS + baseline
        val unallowedReaders = detectedReaders - allowedSet

        assertTrue(
            "Found unallow-listed test files reading production source code (REQ-PRO-049.1, docs/coding_standards.md §5.4): " +
                unallowedReaders.joinToString("\n  "),
            unallowedReaders.isEmpty()
        )
    }

    @Test
    fun testGovernanceGuards_allExplicitlyAllowed() {
        assertTrue(
            "Expected <= 22 approved architectural governance guards (REQ-PRO-049.2, REQ-PRO-050.4): current=${LEGITIMATE_GOVERNANCE_GUARDS.size}",
            LEGITIMATE_GOVERNANCE_GUARDS.size <= 22
        )
        val testDir = findTestDir()
        LEGITIMATE_GOVERNANCE_GUARDS.forEach { guard ->
            assertTrue(
                "Governance guard $guard must end with Test.kt or Cache.kt",
                guard.endsWith("Test.kt") || guard.endsWith("Cache.kt")
            )
            val guardFile = File(testDir, guard)
            assertTrue(
                "Governance guard $guard must exist on disk at ${guardFile.path}",
                guardFile.exists()
            )
        }
    }

    @Test
    fun testBaseline_containsOnlyExpectedLegacyCount() {
        val testDir = findTestDir()
        val detectedReaders = scanTestSourceFiles(testDir)
        val baseline = loadBaselineLegacyReaders()

        val staleBaselineEntries = baseline - detectedReaders
        assertTrue(
            "Baseline contains stale entries that no longer read source files (REQ-PRO-049.3): " +
                staleBaselineEntries.joinToString("\n  "),
            staleBaselineEntries.isEmpty()
        )

        assertTrue(
            "Baseline legacy source-reading tests debt must not exceed ratcheted ceiling of 104 (REQ-PRO-049.3): current=${baseline.size}",
            baseline.size <= 104
        )
    }

    @Test
    fun testRatchetingTripwire_detectsUnallowlistedReader() {
        val mockDetected = setOf("com/atrainingtracker/trainingtracker/ui/fake/NewSourceReadingTest.kt")
        val baseline = loadBaselineLegacyReaders()
        val allowed = LEGITIMATE_GOVERNANCE_GUARDS + baseline

        val violations = mockDetected - allowed
        assertFalse(
            "Ratcheting tripwire must detect synthetic unallow-listed reader (REQ-PRO-049.3)",
            violations.isEmpty()
        )
        assertEquals("NewSourceReadingTest.kt", File(violations.first()).name)
    }

    @Test
    fun testEvasionTripwires_detectsConcatenationTemplatesJoinsPathsAndExpandedReadApis() {
        val evasions = mapOf(
            "string_concat" to """
                fun f(rel: String): String {
                    val p = "src" + "/main/" + rel
                    return File(p).readText()
                }
            """.trimIndent(),
            "manifest_concat" to """
                fun m() {
                    val mf = findRepoRootFile("app/src/" + "main/AndroidManifest.xml")
                    val txt = mf.readText()
                }
            """.trimIndent(),
            "kotlin_template" to """
                fun t() {
                    val dir = "src"
                    val f = File("${'$'}dir/main/java/Foo.kt")
                    val lines = f.readLines()
                }
            """.trimIndent(),
            "list_joinToString" to """
                fun j() {
                    val p = listOf("app", "src", "main", "res", "values", "strings.xml").joinToString(File.separator)
                    val s = File(p).useLines { it.count() }
                }
            """.trimIndent(),
            "paths_get_readAllLines" to """
                fun p() {
                    val lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get("app", "src", "main", "Foo.java"))
                }
            """.trimIndent(),
            "path_of_files_lines" to """
                fun po() {
                    val s = java.nio.file.Files.lines(java.nio.file.Path.of("src", "main", "kotlin", "Bar.kt"))
                }
            """.trimIndent(),
            "file_resolve_scanner" to """
                fun fr() {
                    val f = File("app").resolve("src").resolve("main").resolve("AndroidManifest.xml")
                    val sc = java.util.Scanner(f)
                }
            """.trimIndent(),
            "raw_string_readAllBytes" to """
                fun r() {
                    val p = java.nio.file.Paths.get(${"\"\"\""}app/src/main/Foo.kt${"\"\"\""})
                    val b = java.nio.file.Files.readAllBytes(p)
                }
            """.trimIndent(),
            "stringbuilder_filereader" to """
                fun sb() {
                    val path = StringBuilder("src").append("/main/").append("Foo.java").toString()
                    val r = java.io.FileReader(path)
                }
            """.trimIndent(),
            "helper_function_indirection" to """
                fun resolveProd(rel: String): File = File("src" + "/main/" + rel)
                fun test() {
                    val content = resolveProd("java/Foo.java").readText()
                }
            """.trimIndent()
        )

        evasions.forEach { (name, snippet) ->
            val hits = detectSourceReadingTargets(snippet)
            assertFalse(
                "TST-PRO-133.1 evasion tripwire '$name' must be detected",
                hits.isEmpty()
            )
        }
    }

    @Test
    fun testEvasionTripwires_zeroFalsePositivesOnDocsWalkAndTempFileTests() {
        val cleanDirectoryWalkWithDocsRead = """
            fun findMainSourceDir(): File {
                val relSubPath = "src" + "/main/java"
                return File("app", relSubPath)
            }
            fun testAdrAndWalk() {
                val adrFile = File("docs/architecture/adr_001.md")
                val content = adrFile.readText()
                val mainDir = findMainSourceDir()
                val javaFiles = mainDir.walkTopDown().filter { it.name.endsWith(".java") }.toList()
            }
        """.trimIndent()

        val cleanBackupExistsWithDocsRead = """
            fun testArch() {
                val content = File("docs/system/system_architecture.md").readText()
                val backupXml = File(listOf("app", "src", "main", "res", "xml", "full_backup_content.xml").joinToString(File.separator))
                assertTrue(backupXml.exists())
            }
        """.trimIndent()

        val cleanTempFileIo = """
            fun testExporter() {
                val out = File.createTempFile("export", ".gpx")
                val text = out.readText()
                val lines = out.readLines()
            }
        """.trimIndent()

        assertTrue(
            "Directory walk + docs readText() must produce zero false positives (TST-PRO-133.1)",
            detectSourceReadingTargets(cleanDirectoryWalkWithDocsRead).isEmpty()
        )
        assertTrue(
            "joinToString .exists() + docs readText() must produce zero false positives (TST-PRO-133.1)",
            detectSourceReadingTargets(cleanBackupExistsWithDocsRead).isEmpty()
        )
        assertTrue(
            "Temp-file I/O must produce zero false positives (TST-PRO-133.1)",
            detectSourceReadingTargets(cleanTempFileIo).isEmpty()
        )
    }
}
