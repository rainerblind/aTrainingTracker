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

package com.atrainingtracker.trainingtracker.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates XML backup extraction rules and Android Auto Backup manifest configurations (REQ-DAT-032.2, TST-DAT-027).
 */
class BackupRulesXmlValidationTest {

    private fun loadXml(file: File): Element {
        assertTrue("XML file should exist: ${file.absolutePath}", file.exists())
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        return doc.documentElement
    }

    @Test
    fun testDataExtractionRules_excludesAuthCredentialsFromCloudAndTransfer() {
        val rulesFile = File("src/main/res/xml/data_extraction_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/data_extraction_rules.xml")
        }
        val root = loadXml(rulesFile)
        assertEquals("data-extraction-rules", root.tagName)

        val cloudBackupNodes = root.getElementsByTagName("cloud-backup")
        assertEquals(1, cloudBackupNodes.length)
        val cloudElement = cloudBackupNodes.item(0) as Element
        val cloudExcludes = cloudElement.getElementsByTagName("exclude")
        var cloudExclusionFound = false
        for (i in 0 until cloudExcludes.length) {
            val exclude = cloudExcludes.item(i) as Element
            if (exclude.getAttribute("domain") == "sharedpref" &&
                exclude.getAttribute("path") == AuthCredentialsStore.PREFS_FILE_NAME) {
                cloudExclusionFound = true
                break
            }
        }
        assertTrue("data_extraction_rules.xml must exclude auth_credentials_prefs.xml from cloud-backup", cloudExclusionFound)

        val transferNodes = root.getElementsByTagName("device-transfer")
        assertEquals(1, transferNodes.length)
        val transferElement = transferNodes.item(0) as Element
        val transferExcludes = transferElement.getElementsByTagName("exclude")
        var transferExclusionFound = false
        for (i in 0 until transferExcludes.length) {
            val exclude = transferExcludes.item(i) as Element
            if (exclude.getAttribute("domain") == "sharedpref" &&
                exclude.getAttribute("path") == AuthCredentialsStore.PREFS_FILE_NAME) {
                transferExclusionFound = true
                break
            }
        }
        assertTrue("data_extraction_rules.xml must exclude auth_credentials_prefs.xml from device-transfer", transferExclusionFound)
    }

    @Test
    fun testFullBackupContent_excludesAuthCredentials() {
        val backupFile = File("src/main/res/xml/full_backup_content.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/full_backup_content.xml")
        }
        val root = loadXml(backupFile)
        assertEquals("full-backup-content", root.tagName)

        val excludes = root.getElementsByTagName("exclude")
        var exclusionFound = false
        for (i in 0 until excludes.length) {
            val exclude = excludes.item(i) as Element
            if (exclude.getAttribute("domain") == "sharedpref" &&
                exclude.getAttribute("path") == AuthCredentialsStore.PREFS_FILE_NAME) {
                exclusionFound = true
                break
            }
        }
        assertTrue("full_backup_content.xml must exclude auth_credentials_prefs.xml", exclusionFound)
    }

    @Test
    fun testAndroidManifest_containsDataExtractionRulesAndFullBackupContent() {
        val manifestFile = File("src/main/AndroidManifest.xml").let {
            if (it.exists()) it else File("app/src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())
        val content = manifestFile.readText()

        assertTrue("Manifest must configure android:allowBackup=\"true\"", content.contains("android:allowBackup=\"true\""))
        assertTrue("Manifest must configure android:dataExtractionRules=\"@xml/data_extraction_rules\"",
            content.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue("Manifest must configure android:fullBackupContent=\"@xml/full_backup_content\"",
            content.contains("android:fullBackupContent=\"@xml/full_backup_content\""))
    }
}
