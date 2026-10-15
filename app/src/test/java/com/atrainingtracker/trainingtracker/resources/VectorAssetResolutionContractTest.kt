package com.atrainingtracker.trainingtracker.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract test enforcing REQ-UI-375 / TST-UI-338:
 * Scalable Jetpack Compose Vector Asset Architecture & Multi-Density Raster Migration.
 */
class VectorAssetResolutionContractTest {

    private val migratedAssetNames = listOf(
        "bsport_bike",
        "bsport_run",
        "bsport_other",
        "hr",
        "bike_spd",
        "bike_cad",
        "bike_speed_and_cadence",
        "bike_pwr",
        "run_spd",
        "temp",
        "bt_hr",
        "bt_bike_spd",
        "bt_bike_cad",
        "bt_bike_speed_and_cadence",
        "bt_bike_pwr",
        "bt_run"
    )

    private fun getResDir(): File {
        val currentDir = File(System.getProperty("user.dir") ?: ".")
        val resDir = when {
            File(currentDir, "src/main/res").exists() -> File(currentDir, "src/main/res")
            File(currentDir, "app/src/main/res").exists() -> File(currentDir, "app/src/main/res")
            else -> File(currentDir, "../app/src/main/res")
        }
        assertTrue("Resource directory must exist: ${resDir.absolutePath}", resDir.exists() && resDir.isDirectory)
        return resDir
    }

    @Test
    fun testAllMigratedVectorAssetsExistAndParseValidVectorTag() {
        val resDir = getResDir()
        val rootDrawableDir = File(resDir, "drawable")
        assertTrue("Root drawable directory must exist", rootDrawableDir.exists() && rootDrawableDir.isDirectory)

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        for (assetName in migratedAssetNames) {
            val xmlFile = File(rootDrawableDir, "$assetName.xml")
            assertTrue("Vector XML file must exist: ${xmlFile.name}", xmlFile.exists() && xmlFile.isFile)

            val doc = builder.parse(xmlFile)
            val root = doc.documentElement
            assertEquals(
                "Root element of ${xmlFile.name} must be <vector>",
                "vector",
                root.tagName
            )
            assertTrue(
                "Vector ${xmlFile.name} must declare android:width",
                root.hasAttribute("android:width")
            )
            assertTrue(
                "Vector ${xmlFile.name} must declare android:height",
                root.hasAttribute("android:height")
            )
            assertTrue(
                "Vector ${xmlFile.name} must declare android:viewportWidth",
                root.hasAttribute("android:viewportWidth")
            )
            assertTrue(
                "Vector ${xmlFile.name} must declare android:viewportHeight",
                root.hasAttribute("android:viewportHeight")
            )
        }
    }

    @Test
    fun testNoLegacyRasterPngFilesRemainForMigratedAssets() {
        val resDir = getResDir()
        val allDrawableDirs = resDir.listFiles()?.filter {
            it.isDirectory && (it.name == "drawable" || it.name.startsWith("drawable-"))
        } ?: emptyList()

        for (dir in allDrawableDirs) {
            for (assetName in migratedAssetNames) {
                val legacyPng = File(dir, "$assetName.png")
                assertFalse(
                    "Legacy raster PNG must not exist: ${legacyPng.absolutePath}",
                    legacyPng.exists()
                )
            }
        }
    }

    @Test
    fun testDeadLegacyAssetsArePermanentlyEliminated() {
        val resDir = getResDir()
        val allDrawableDirs = resDir.listFiles()?.filter {
            it.isDirectory && (it.name == "drawable" || it.name.startsWith("drawable-"))
        } ?: emptyList()

        val deadAssetNames = listOf(
            "bsport_bike_gray.png",
            "bsport_run_gray.png",
            "bsport_other_gray.png",
            "btle_hr.png"
        )

        for (dir in allDrawableDirs) {
            for (deadFile in deadAssetNames) {
                val file = File(dir, deadFile)
                assertFalse(
                    "Dead unreferenced asset must not exist: ${file.absolutePath}",
                    file.exists()
                )
            }
        }
    }
}
