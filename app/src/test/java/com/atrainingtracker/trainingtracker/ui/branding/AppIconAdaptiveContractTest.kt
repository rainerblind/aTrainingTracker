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
 * Contract test validating Android Adaptive Icon architecture, safe-zone radius containment,
 * authentic Cervélo P3C / stopwatch brand color preservation, and manifest configuration (REQ-UI-444 / TST-UI-444).
 */
class AppIconAdaptiveContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    private fun getResDir(): File = findRepoRootFile("app/src/main/res")

    private fun getManifestFile(): File = findRepoRootFile("app/src/main/AndroidManifest.xml")

    @Test
    fun testAdaptiveIconResourcesAndManifest() {
        val manifestDoc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(getManifestFile())

        val appElements = manifestDoc.getElementsByTagName("application")
        assertTrue("Manifest must declare <application>", appElements.length > 0)
        val appElement = appElements.item(0) as org.w3c.dom.Element

        val iconAttr = appElement.getAttributeNS("http://schemas.android.com/apk/res/android", "icon")
        val roundIconAttr = appElement.getAttributeNS("http://schemas.android.com/apk/res/android", "roundIcon")

        assertEquals("Manifest android:icon must point to @mipmap/ic_launcher", "@mipmap/ic_launcher", iconAttr)
        assertEquals("Manifest android:roundIcon must point to @mipmap/ic_launcher_round", "@mipmap/ic_launcher_round", roundIconAttr)

        // Verify adaptive icon mipmap-anydpi-v26 XML definitions
        val resDir = getResDir()
        val anyDpiDir = File(resDir, "mipmap-anydpi-v26")
        assertTrue("mipmap-anydpi-v26 directory must exist", anyDpiDir.exists())

        for (xmlName in listOf("ic_launcher.xml", "ic_launcher_round.xml")) {
            val xmlFile = File(anyDpiDir, xmlName)
            assertTrue("$xmlName must exist in mipmap-anydpi-v26", xmlFile.exists())
            val doc = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }.newDocumentBuilder().parse(xmlFile)

            val root = doc.documentElement
            assertEquals("Root element must be <adaptive-icon>", "adaptive-icon", root.tagName)

            val backgrounds = root.getElementsByTagName("background")
            assertTrue("Must contain <background>", backgrounds.length > 0)
            val bgDrawable = (backgrounds.item(0) as org.w3c.dom.Element).getAttributeNS("http://schemas.android.com/apk/res/android", "drawable")
            assertEquals("@drawable/ic_launcher_background", bgDrawable)

            val foregrounds = root.getElementsByTagName("foreground")
            assertTrue("Must contain <foreground>", foregrounds.length > 0)
            val fgDrawable = (foregrounds.item(0) as org.w3c.dom.Element).getAttributeNS("http://schemas.android.com/apk/res/android", "drawable")
            assertEquals("@drawable/ic_launcher_foreground", fgDrawable)

            val monoman = root.getElementsByTagName("monochrome")
            assertTrue("Must contain <monochrome>", monoman.length > 0)
            val monoDrawable = (monoman.item(0) as org.w3c.dom.Element).getAttributeNS("http://schemas.android.com/apk/res/android", "drawable")
            assertEquals("@drawable/ic_launcher_monochrome", monoDrawable)
        }

        // Verify legacy mipmap density fallbacks
        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        for (density in densities) {
            val dDir = File(resDir, "mipmap-$density")
            assertTrue("mipmap-$density directory must exist", dDir.exists())
            assertTrue("ic_launcher.png must exist in mipmap-$density", File(dDir, "ic_launcher.png").exists())
            assertTrue("ic_launcher_round.png must exist in mipmap-$density", File(dDir, "ic_launcher_round.png").exists())
        }
    }

    @Test
    fun testSafeZoneContainment() {
        val resDir = getResDir()
        val fgFile = File(resDir, "drawable/ic_launcher_foreground.xml")
        assertTrue("ic_launcher_foreground.xml must exist", fgFile.exists())

        val content = fgFile.readText()
        val pathPattern = Pattern.compile("android:pathData=\"([^\"]+)\"")
        val cmdPattern = Pattern.compile("([MLAZmlaz])([^MLAZmlaz]*)")
        val numPattern = Pattern.compile("[-+]?\\d*\\.?\\d+")

        val matcher = pathPattern.matcher(content)
        var maxRadialDistance = 0.0
        val center = 54.0 // 108dp canvas center

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
                    "M", "L" -> {
                        var i = 0
                        while (i < nums.size - 1) {
                            val dist = hypot(nums[i] - center, nums[i + 1] - center)
                            if (dist > maxRadialDistance) maxRadialDistance = dist
                            i += 2
                        }
                    }
                    "A" -> {
                        // Arc syntax: rx, ry, rot, large, sweep, x, y
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

        assertTrue(
            "All foreground vector geometry must fit within 66dp safe zone (r <= 33.0dp), found max r = $maxRadialDistance",
            maxRadialDistance <= 33.0
        )
    }

    @Test
    fun testBrandColorIntegrity() {
        val resDir = getResDir()
        val fgFile = File(resDir, "drawable/ic_launcher_foreground.xml")
        val content = fgFile.readText().uppercase()

        // Required brand color tokens
        val mandatoryColors = listOf(
            "#FF0000", // Cervélo P3C frame & center pusher
            "#000000", // Carbon wheels & saddle
            "#FFC700", // Golden ring & pusher stems
            "#F0F0F0", // Stopwatch dial
            "#000090", // Navy pusher
            "#009000", // Green pusher
            "#0000FF"  // Royal blue cyclist & runner
        )

        for (color in mandatoryColors) {
            assertTrue("Foreground vector must contain authentic brand color $color", content.contains(color))
        }

        // Verify monochrome layer exists and is single-color (#000000)
        val monoFile = File(resDir, "drawable/ic_launcher_monochrome.xml")
        assertTrue("ic_launcher_monochrome.xml must exist", monoFile.exists())
        val monoContent = monoFile.readText().uppercase()
        assertTrue("Monochrome layer must use #000000 silhouette", monoContent.contains("#000000"))
        // Monochrome layer must not contain multiple distinct theme colors
        assertTrue("Monochrome layer must not contain #FFC700", !monoContent.contains("#FFC700"))
        assertTrue("Monochrome layer must not contain #0000FF", !monoContent.contains("#0000FF"))
    }

    @Test
    fun testInAppCallerParityAndMasterAssets() {
        val resDir = getResDir()

        // Legacy notification logo drawables MUST remain untouched
        val legacyLogo = File(resDir, "drawable/logo.png")
        assertTrue("Legacy drawable/logo.png must remain intact for notification callers", legacyLogo.exists())

        val legacyLogoXxxhdpi = File(resDir, "drawable-xxxhdpi/logo.png")
        assertTrue("Legacy drawable-xxxhdpi/logo.png must remain intact", legacyLogoXxxhdpi.exists())

        // Play Store 512x512 master assets
        val playStoreLogo = findRepoRootFile("images/logo_512.png")
        assertTrue("Play Store 512x512 master asset must exist in images/", playStoreLogo.exists())
        assertTrue("images/logo_512.png size must be > 10KB", playStoreLogo.length() > 10000)
    }
}
