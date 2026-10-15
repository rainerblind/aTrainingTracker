package com.atrainingtracker.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architecture Audit Test enforcing REQ-STB-016 / TST-STB-016:
 * Hardening Service Launches and BroadcastReceiver Unregistration Against Lifecycle Exceptions.
 *
 * Verifies that zero unguarded startService or unregisterReceiver invocations exist across
 * app/src/main, ensuring all call sites route through [com.atrainingtracker.util.LifecycleUtils].
 */
class LifecycleServiceReceiverSafetyAuditTest {

    private val unsafePatterns = listOf(
        Pattern.compile("""(?<!safe)startService\s*\("""),
        Pattern.compile("""(?<!safe)unregisterReceiver\s*\(""")
    )

    @Test
    fun testNoUnguardedStartServiceOrUnregisterReceiverInMainSource() {
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
            .filter { !it.name.startsWith("LifecycleUtils") } // LifecycleUtils safely wraps platform calls
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, rawLine ->
                    val line = rawLine.trim()
                    // Skip comments
                    if (!line.startsWith("//") && !line.startsWith("*") && !line.startsWith("/*")) {
                        for (pattern in unsafePatterns) {
                            if (pattern.matcher(line).find()) {
                                violations.add("${file.relativeTo(mainDir)}:${index + 1} -> $line")
                            }
                        }
                    }
                }
            }

        assertTrue(
            "Found unguarded startService / unregisterReceiver call sites violating REQ-STB-016:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
