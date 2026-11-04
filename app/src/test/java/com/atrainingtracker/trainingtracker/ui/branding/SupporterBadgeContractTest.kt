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

package com.atrainingtracker.trainingtracker.ui.branding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot

/**
 * Architectural contract tests validating Option A launcher icons, safe-zone containment,
 * authentic brand color preservation, manifest activity-alias declarations, and 24dp emblems (REQ-UI-445 / TST-UI-445).
 */
class SupporterBadgeContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in ${File(".").absolutePath}")
    }

    private fun getResDir(): File = findRepoRootFile("app/src/main/res")

    private fun getManifestFile(): File = findRepoRootFile("app/src/main/AndroidManifest.xml")

    @Test
    fun testActivityAliasesInManifest() {
        val manifestDoc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(getManifestFile())

        val aliases = manifestDoc.getElementsByTagName("activity-alias")
        assertTrue("Manifest must declare at least 6 <activity-alias> entries", aliases.length >= 6)

        val aliasMap = mutableMapOf<String, org.w3c.dom.Element>()
        for (i in 0 until aliases.length) {
            val el = aliases.item(i) as org.w3c.dom.Element
            val name = el.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            aliasMap[name] = el
        }

        // Verify each variant alias
        for (variant in LauncherIconVariant.entries) {
            val shortName = variant.aliasName.substringAfterLast(".")
            val foundElement = aliasMap.entries.firstOrNull { it.key.endsWith(shortName) }?.value
            assertNotNull("Manifest must declare activity-alias for $shortName", foundElement)

            val target = foundElement!!.getAttributeNS("http://schemas.android.com/apk/res/android", "targetActivity")
            assertEquals(".trainingtracker.activities.MainActivityWithNavigation", target)

            val enabledAttr = foundElement.getAttributeNS("http://schemas.android.com/apk/res/android", "enabled")
            if (variant == LauncherIconVariant.STANDARD) {
                assertEquals("Standard alias must be enabled by default", "true", enabledAttr)
            } else {
                assertEquals("${variant.name} alias must be disabled by default", "false", enabledAttr)
            }
        }
    }

    @Test
    fun testAllAdaptiveMipmapsExist() {
        val resDir = getResDir()
        val anyDpiDir = File(resDir, "mipmap-anydpi-v26")
        assertTrue("mipmap-anydpi-v26 directory must exist", anyDpiDir.exists())

        val expectedMipmaps = listOf(
            "ic_launcher.xml",
            "ic_launcher_round.xml",
            "ic_launcher_pioneer.xml",
            "ic_launcher_pioneer_round.xml",
            "ic_launcher_bronze.xml",
            "ic_launcher_bronze_round.xml",
            "ic_launcher_silver.xml",
            "ic_launcher_silver_round.xml",
            "ic_launcher_gold.xml",
            "ic_launcher_gold_round.xml",
            "ic_launcher_diamond.xml",
            "ic_launcher_diamond_round.xml"
        )

        for (xmlName in expectedMipmaps) {
            val xmlFile = File(anyDpiDir, xmlName)
            assertTrue("$xmlName must exist in mipmap-anydpi-v26", xmlFile.exists())

            val doc = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }.newDocumentBuilder().parse(xmlFile)
            val root = doc.documentElement
            assertEquals("Root element must be <adaptive-icon>", "adaptive-icon", root.tagName)
            assertTrue("Must contain <background>", root.getElementsByTagName("background").length > 0)
            assertTrue("Must contain <foreground>", root.getElementsByTagName("foreground").length > 0)
            assertTrue("Must contain <monochrome>", root.getElementsByTagName("monochrome").length > 0)
        }
    }

    @Test
    fun testSafeZoneContainmentAcrossAllTiers() {
        val resDir = getResDir()
        val fgFiles = listOf(
            "drawable/ic_launcher_foreground.xml",
            "drawable/ic_launcher_foreground_pioneer.xml",
            "drawable/ic_launcher_foreground_bronze.xml",
            "drawable/ic_launcher_foreground_silver.xml",
            "drawable/ic_launcher_foreground_gold.xml",
            "drawable/ic_launcher_foreground_diamond.xml"
        )

        val pathPattern = Pattern.compile("android:pathData=\"([^\"]+)\"")
        val cmdPattern = Pattern.compile("([MLAZCSQmlazcsq])([^MLAZCSQmlazcsq]*)")
        val numPattern = Pattern.compile("[-+]?\\d*\\.?\\d+")
        val center = 54.0

        for (fgRelPath in fgFiles) {
            val fgFile = File(resDir, fgRelPath)
            assertTrue("$fgRelPath must exist", fgFile.exists())

            val content = fgFile.readText()
            val matcher = pathPattern.matcher(content)
            var maxRadialDistance = 0.0

            while (matcher.find()) {
                val pathData = matcher.group(1) ?: continue
                val cmdMatcher = cmdPattern.matcher(pathData)

                while (cmdMatcher.find()) {
                    val cmd = cmdMatcher.group(1)?.uppercase() ?: ""
                    val args = cmdMatcher.group(2) ?: ""

                    val numMatcher = numPattern.matcher(args)
                    val nums = mutableListOf<Double>()
                    while (numMatcher.find()) {
                        nums.add(numMatcher.group().toDouble())
                    }

                    when (cmd) {
                        "M", "L", "C", "S", "Q" -> {
                            var i = 0
                            while (i < nums.size - 1) {
                                val dist = hypot(nums[i] - center, nums[i + 1] - center)
                                if (dist > maxRadialDistance) maxRadialDistance = dist
                                i += 2
                            }
                        }
                        "A" -> {
                            var i = 0
                            while (i + 6 < nums.size) {
                                val x = nums[i + 5]
                                val y = nums[i + 6]
                                val dist = hypot(x - center, y - center)
                                if (dist > maxRadialDistance) maxRadialDistance = dist
                                i += 7
                            }
                        }
                    }
                }
            }

            val maxAllowedRadius = if (fgRelPath == "drawable/ic_launcher_foreground.xml") 34.5 else 33.0
            assertTrue(
                "$fgRelPath geometry must fit inside safe zone (r <= ${maxAllowedRadius}dp), found $maxRadialDistance",
                maxRadialDistance <= maxAllowedRadius
            )
        }
    }

    @Test
    fun testAuthenticBrandColorPreservationAcrossAllTiers() {
        val resDir = getResDir()
        val fgFiles = listOf(
            "drawable/ic_launcher_foreground.xml",
            "drawable/ic_launcher_foreground_pioneer.xml",
            "drawable/ic_launcher_foreground_bronze.xml",
            "drawable/ic_launcher_foreground_silver.xml",
            "drawable/ic_launcher_foreground_gold.xml",
            "drawable/ic_launcher_foreground_diamond.xml"
        )

        val mandatoryColors = listOf(
            "#FF0000", // Cervélo P3C frame & center pusher
            "#000000", // Carbon wheels & saddle
            "#FFC700", // Golden ring & pusher stems
            "#F0F0F0", // Stopwatch dial
            "#000090", // Navy pusher
            "#009000", // Green pusher
            "#0000FF"  // Royal blue cyclist & runner
        )

        for (fgRelPath in fgFiles) {
            val content = File(resDir, fgRelPath).readText().uppercase()
            for (color in mandatoryColors) {
                assertTrue(
                    "$fgRelPath must strictly preserve authentic brand color $color",
                    content.contains(color)
                )
            }
        }
    }

    @Test
    fun testStandalone24dpEmblemsExistAndValid() {
        val resDir = getResDir()
        val emblemFiles = listOf(
            "drawable/ic_supporter_emblem_pioneer.xml",
            "drawable/ic_supporter_emblem_bronze.xml",
            "drawable/ic_supporter_emblem_silver.xml",
            "drawable/ic_supporter_emblem_gold.xml",
            "drawable/ic_supporter_emblem_diamond.xml"
        )

        for (emblemPath in emblemFiles) {
            val file = File(resDir, emblemPath)
            assertTrue("$emblemPath must exist", file.exists())

            val doc = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }.newDocumentBuilder().parse(file)
            val root = doc.documentElement
            assertEquals("Root element must be <vector>", "vector", root.tagName)
            assertEquals("24dp", root.getAttributeNS("http://schemas.android.com/apk/res/android", "width"))
            assertEquals("24dp", root.getAttributeNS("http://schemas.android.com/apk/res/android", "height"))
        }
    }
}
