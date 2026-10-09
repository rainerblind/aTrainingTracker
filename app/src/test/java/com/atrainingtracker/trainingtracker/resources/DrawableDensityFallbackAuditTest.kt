package com.atrainingtracker.trainingtracker.resources

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit test enforcing REQ-UI-312 / TST-UI-272:
 * Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture.
 *
 * Verifies that all bitmap and raster assets residing in any screen density bucket
 * (drawable-mdpi, -hdpi, -xhdpi, -xxhdpi, -xxxhdpi, -nodpi, -ldpi) have default fallback
 * counterparts in the root app/src/main/res/drawable/ directory (either as .png, .jpg, .webp, or .xml vector).
 * This ensures Android's resource resolver never fails with Resources$NotFoundException on density-split APKs
 * or custom DPI devices.
 */
class DrawableDensityFallbackAuditTest {

    @Test
    fun testAllDensityDrawablesHaveRootFallback() {
        val currentDir = File(System.getProperty("user.dir") ?: ".")
        val resDir = when {
            File(currentDir, "src/main/res").exists() -> File(currentDir, "src/main/res")
            File(currentDir, "app/src/main/res").exists() -> File(currentDir, "app/src/main/res")
            else -> File(currentDir, "../app/src/main/res")
        }
        assertTrue("Resource directory must exist: ${resDir.absolutePath}", resDir.exists() && resDir.isDirectory)

        val rootDrawableDir = File(resDir, "drawable")
        assertTrue("Root drawable directory must exist", rootDrawableDir.exists() && rootDrawableDir.isDirectory)

        // In Android, R.drawable.<name> can be satisfied by .xml, .png, .jpg, or .webp in root drawable/
        val rootDrawableBases = rootDrawableDir.listFiles()
            ?.map { it.nameWithoutExtension }
            ?.toSet() ?: emptySet()

        val densityDirs = resDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("drawable-") }
            ?: emptyList()

        assertTrue("Density drawable directories must be present", densityDirs.isNotEmpty())

        val missingInRoot = mutableMapOf<String, MutableList<String>>()

        for (dir in densityDirs) {
            val files = dir.listFiles()?.filter {
                it.isFile && (it.name.endsWith(".png") || it.name.endsWith(".jpg") || it.name.endsWith(".webp") || it.name.endsWith(".xml"))
            } ?: emptyList()

            for (file in files) {
                val baseName = file.nameWithoutExtension
                if (baseName !in rootDrawableBases) {
                    missingInRoot.getOrPut(file.name) { mutableListOf() }.add(dir.name)
                }
            }
        }

        val errorMessage = buildString {
            appendLine("The following density-specific drawables are missing a default fallback in app/src/main/res/drawable/:")
            missingInRoot.forEach { (file, folders) ->
                appendLine("  $file (found in: ${folders.joinToString()})")
            }
            appendLine("All drawables must have a baseline asset in res/drawable/ to prevent Resources\$NotFoundException on split APKs (REQ-UI-312).")
        }

        assertTrue(errorMessage, missingInRoot.isEmpty())
    }
}
