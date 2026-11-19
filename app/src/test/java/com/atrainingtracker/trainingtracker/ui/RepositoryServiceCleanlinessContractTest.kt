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

package com.atrainingtracker.trainingtracker.ui

import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.ui.devices.devicedata.PairedRemoteDeviceDto
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Architectural contract test verifying database manager decoupling across Phase C recording
 * services and device connection management (REQ-TRK-066, TST-TRK-066.1, REQ-PRO-133.2).
 *
 * Inspects compiled JVM `.class` bytecode constant pools (main class and `$*` inner/anonymous
 * classes) with zero `app/src/main/` source-text reads (REQ-PRO-049.1).
 */
class RepositoryServiceCleanlinessContractTest {

    private data class MethodCallRef(val ownerClass: String, val methodName: String)

    private data class ClassPoolData(
        val utf8Strings: Set<String>,
        val methodRefs: List<MethodCallRef>
    )

    private fun readClassPoolData(classBytes: ByteArray): ClassPoolData {
        val dis = DataInputStream(ByteArrayInputStream(classBytes))
        val magic = dis.readInt()
        require(magic == 0xCAFEBABE.toInt()) { "Invalid classfile magic: $magic" }
        dis.readUnsignedShort() // minor
        dis.readUnsignedShort() // major
        val cpCount = dis.readUnsignedShort()

        val utf8ByIdx = mutableMapOf<Int, String>()
        val classNameIdxByIdx = mutableMapOf<Int, Int>()
        val natNameIdxByIdx = mutableMapOf<Int, Int>()
        val rawMethodRefs = mutableListOf<Pair<Int, Int>>()

        var idx = 1
        while (idx < cpCount) {
            when (val tag = dis.readUnsignedByte()) {
                1 -> utf8ByIdx[idx] = dis.readUTF()
                3, 4 -> dis.skipBytes(4)
                5, 6 -> {
                    dis.skipBytes(8)
                    idx++ // Long and Double consume 2 constant-pool entries
                }
                7 -> classNameIdxByIdx[idx] = dis.readUnsignedShort()
                8, 16, 19, 20 -> dis.skipBytes(2)
                9, 17, 18 -> dis.skipBytes(4)
                10, 11 -> {
                    val classIdx = dis.readUnsignedShort()
                    val natIdx = dis.readUnsignedShort()
                    rawMethodRefs.add(classIdx to natIdx)
                }
                12 -> {
                    val nameIdx = dis.readUnsignedShort()
                    dis.readUnsignedShort() // descriptor
                    natNameIdxByIdx[idx] = nameIdx
                }
                15 -> dis.skipBytes(3)
                else -> error("Unsupported JVM constant pool tag $tag at index $idx")
            }
            idx++
        }

        val resolvedMethodRefs = rawMethodRefs.mapNotNull { (classIdx, natIdx) ->
            val owner = classNameIdxByIdx[classIdx]?.let { utf8ByIdx[it] }
            val name = natNameIdxByIdx[natIdx]?.let { utf8ByIdx[it] }
            if (owner != null && name != null) MethodCallRef(owner, name) else null
        }
        return ClassPoolData(utf8ByIdx.values.toSet(), resolvedMethodRefs)
    }

    private fun collectClassPoolData(clazz: Class<*>): ClassPoolData {
        val classLoader = clazz.classLoader ?: ClassLoader.getSystemClassLoader()
        val internalName = clazz.name.replace('.', '/')
        val packagePath = internalName.substringBeforeLast('/')
        val simpleName = internalName.substringAfterLast('/')
        val strings = mutableSetOf<String>()
        val methodRefs = mutableListOf<MethodCallRef>()

        val mainUrl = classLoader.getResource("$internalName.class")
            ?: error("Could not load compiled class resource for ${clazz.name}")
        mainUrl.openStream().use {
            val data = readClassPoolData(it.readBytes())
            strings.addAll(data.utf8Strings)
            methodRefs.addAll(data.methodRefs)
        }

        if (mainUrl.protocol == "file") {
            val classFile = File(URLDecoder.decode(mainUrl.path, StandardCharsets.UTF_8))
            val dir = classFile.parentFile
            dir?.listFiles { _, name ->
                name.startsWith("$simpleName$") && name.endsWith(".class")
            }?.forEach { innerFile ->
                innerFile.inputStream().use {
                    val data = readClassPoolData(it.readBytes())
                    strings.addAll(data.utf8Strings)
                    methodRefs.addAll(data.methodRefs)
                }
            }
        }
        assertTrue(packagePath.isNotEmpty())
        return ClassPoolData(strings, methodRefs)
    }

    private fun hasDirectDbGetInstanceReference(pool: ClassPoolData): List<String> {
        val dbClassPattern = Regex("""^com/atrainingtracker/.*(?:DatabaseManager|DbHelper)(?:\$.*)?$""")
        return pool.methodRefs
            .filter { dbClassPattern.matches(it.ownerClass) && it.methodName == "getInstance" }
            .map { "${it.ownerClass}.${it.methodName}" }
            .distinct()
    }

    @Test
    fun testTrackerService_zeroDatabaseManagerInstantiation() {
        val pool = collectClassPoolData(TrackerService::class.java)
        val poolStrings = pool.utf8Strings

        val directDbRefs = hasDirectDbGetInstanceReference(pool)
        assertTrue(
            "TrackerService bytecode must contain zero DatabaseManager/DbHelper getInstance references (REQ-TRK-066.1), found: $directDbRefs",
            directDbRefs.isEmpty()
        )
        assertTrue(
            "TrackerService must route summary, lap, and live sample persistence through WorkoutRepository",
            "getWorkoutRepository" in poolStrings
        )
        assertTrue(
            "TrackerService must route sport type lookups through SportTypesRepository",
            "getSportTypesRepository" in poolStrings
        )
        assertTrue(
            "TrackerService must route active device recording through DeviceDataRepository",
            "getDeviceDataRepository" in poolStrings
        )
        assertTrue(
            "TrackerService must route favorite location geocoding through KnownLocationsRepository",
            "getKnownLocationsRepository" in poolStrings
        )
        assertTrue(
            "TrackerService must route beat-to-beat RR intervals through WorkoutRrRepository",
            "getWorkoutRrRepository" in poolStrings
        )
    }

    @Test
    fun testBanalService_zeroDatabaseManagerInstantiation() {
        val pool = collectClassPoolData(BANALService::class.java)
        val poolStrings = pool.utf8Strings

        val directDbRefs = hasDirectDbGetInstanceReference(pool)
        assertTrue(
            "BANALService bytecode must contain zero DatabaseManager/DbHelper getInstance references (REQ-TRK-066.2), found: $directDbRefs",
            directDbRefs.isEmpty()
        )
        assertTrue(
            "BANALService must route sport queries through SportTypesRepository",
            poolStrings.any { it.contains("SportTypesRepository") }
        )
    }

    @Test
    fun testDeviceManager_zeroDatabaseManagerInstantiationAndZeroCursorLeak() {
        val pool = collectClassPoolData(DeviceManager::class.java)
        val poolStrings = pool.utf8Strings

        val directDbRefs = hasDirectDbGetInstanceReference(pool)
        assertTrue(
            "DeviceManager bytecode must contain zero DatabaseManager/DbHelper getInstance references (REQ-TRK-066.3), found: $directDbRefs",
            directDbRefs.isEmpty()
        )
        assertFalse(
            "DeviceManager bytecode must not reference android.database.Cursor (zero Cursor leaks)",
            poolStrings.any { it.contains("android/database/Cursor") }
        )
        assertFalse(
            "DeviceManager bytecode must not reference android.database.sqlite.SQLiteDatabase",
            poolStrings.any { it.contains("android/database/sqlite/SQLiteDatabase") }
        )
        assertFalse(
            "DeviceManager bytecode must not directly reference DevicesDbHelper",
            poolStrings.any { it.contains("DevicesDbHelper") }
        )
        assertTrue(
            "DeviceManager must query paired devices via DeviceDataRepository.getPairedRemoteDevices",
            "getPairedRemoteDevices" in poolStrings
        )
    }

    @Test
    fun testPairedRemoteDeviceDto_contractValidation() {
        val dto = PairedRemoteDeviceDto(
            id = 42L,
            isPaired = true,
            deviceType = com.atrainingtracker.banalservice.devices.DeviceType.HRM,
            antDeviceNumber = 12345,
            btAddress = "00:11:22:33:44:55"
        )
        assertEquals(42L, dto.id)
        assertTrue(dto.isPaired)
        assertEquals(com.atrainingtracker.banalservice.devices.DeviceType.HRM, dto.deviceType)
        assertEquals(12345, dto.antDeviceNumber)
        assertEquals("00:11:22:33:44:55", dto.btAddress)
    }
}
