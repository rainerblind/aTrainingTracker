package com.atrainingtracker.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architecture Audit Test enforcing REQ-ARC-001 / TST-ARC-001:
 * Safe Enum Deserialization Across All External Boundaries.
 *
 * Verifies that zero unguarded Enum.valueOf invocations exist on external inputs
 * (Cursor, Intent, SharedPreferences, Bundle arguments, JSON payloads).
 */
class SafeEnumDeserializationAuditTest {

    private val unsafePatterns = listOf(
        Pattern.compile("""\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*cursor\.getString"""),
        Pattern.compile("""\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*(?:intent|getIntent\(\))\.(?:getStringExtra|getSerializableExtra)"""),
        Pattern.compile("""\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*(?:arguments|getArguments\(\))\.getString"""),
        Pattern.compile("""\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*(?:cSharedPreferences|sharedPreferences|prefs)\.getString"""),
        Pattern.compile("""\b[A-Z][a-zA-Z0-9_]*\.valueOf\s*\(\s*(?:json|obj|jsonObject)\.getString""")
    )

    @Test
    fun testNoUnguardedEnumDeserializationInMainSource() {
        val currentDir = File(System.getProperty("user.dir") ?: ".")
        val mainDir = when {
            File(currentDir, "src/main").exists() -> File(currentDir, "src/main")
            File(currentDir, "app/src/main").exists() -> File(currentDir, "app/src/main")
            else -> File(currentDir, "../app/src/main")
        }
        assertTrue("Main source directory must exist: ${mainDir.absolutePath}", mainDir.exists() && mainDir.isDirectory)

        val violations = mutableListOf<String>()

        mainDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { !it.name.startsWith("EnumUtils") } // EnumUtils safely wraps Enum.valueOf
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    for (pattern in unsafePatterns) {
                        if (pattern.matcher(line).find()) {
                            violations.add("${file.relativeTo(mainDir)}:${index + 1} -> ${line.trim()}")
                        }
                    }
                }
            }

        assertTrue(
            "Found unguarded Enum.valueOf call sites violating REQ-ARC-001:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
