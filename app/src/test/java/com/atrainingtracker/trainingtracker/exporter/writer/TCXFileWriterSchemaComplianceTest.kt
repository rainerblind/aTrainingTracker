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

package com.atrainingtracker.trainingtracker.exporter.writer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.SampleConsumer
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutExportHeader
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSampleRecord
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests verifying TCX schema compliance for integer-only `<Cadence>`, `<Watts>`,
 * and `<RunCadence>` elements when exporting workouts containing fractional cadence and power
 * telemetry (REQ-EXP-001, TST-EXP-004).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TCXFileWriterSchemaComplianceTest {

    private lateinit var context: Context
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var mockSportRepo: SportTypesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockWorkoutRepo = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)

        mockSportRepo = mockk(relaxed = true)
        SportTypesRepository.resetForTesting(mockSportRepo)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
        File(context.filesDir, "tcx").deleteRecursively()
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        unmockkAll()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    private fun runExportAndParseTagTexts(
        fileBaseName: String,
        bSportType: BSportType,
        tcxSportName: String,
        samples: List<WorkoutSampleRecord>,
        tagNames: List<String>
    ): Map<String, List<String>> {
        val header = WorkoutExportHeader(
            workoutId = 42L,
            fileBaseName = fileBaseName,
            startTime = "2026-09-12 10:00:00",
            totalTime = "120",
            data = "-DSHCPNAG",
            totalDistance = "1000",
            workoutName = "Schema Audit",
            sportTypeId = 1L,
            indoorTrainerSession = false
        )
        every { mockWorkoutRepo.getWorkoutExportHeader(fileBaseName) } returns header
        every { mockWorkoutRepo.getLaps(42L) } returns listOf(
            LapData(
                id = 1L,
                workoutId = 42L,
                lapNr = 1L,
                timeStart = "2026-09-12 10:00:00",
                timeTotalS = 120,
                distanceTotalM = 1000.0,
                speedAverageMps = 8.33
            )
        )
        every { mockWorkoutRepo.streamSamples(fileBaseName, any()) } answers {
            val consumer = secondArg<SampleConsumer>()
            samples.forEach { consumer.accept(it) }
        }
        every { mockSportRepo.getBSportType(1L) } returns bSportType
        every { mockSportRepo.getTcxName(1L) } returns tcxSportName

        val writer = TCXFileWriter(context)
        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.COMMUNITY)
        val result = writer.export(exportInfo)
        if (!result.success()) {
            val detail = try {
                writer.doExport(exportInfo).toString()
            } catch (t: Throwable) {
                "${t.javaClass.name}: ${t.message} | cause=${t.cause?.javaClass?.name}: ${t.cause?.message}\n${t.cause?.stackTraceToString() ?: t.stackTraceToString()}"
            }
            org.junit.Assert.fail("TCX export must succeed: ${result.answer()} -> $detail")
        }

        val outFile = File(context.filesDir, exportInfo.shortPath)
        assertTrue("Exported TCX file must exist at ${outFile.absolutePath}", outFile.exists())

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(outFile)

        val resultMap = mutableMapOf<String, MutableList<String>>()
        for (tag in tagNames) {
            val nodes = doc.getElementsByTagNameNS("*", tag)
            val values = mutableListOf<String>()
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                values.add(el.textContent.trim())
            }
            resultMap[tag] = values
        }
        return resultMap
    }

    @Test
    fun testBikeTcxExport_formatsCadenceAndWattsStrictlyAsIntegers_TST_EXP_004() {
        val samples = listOf(
            WorkoutSampleRecord(
                time = "2026-09-12 10:00:00",
                lapNr = 1,
                latitude = 48.0,
                longitude = 11.0,
                distanceM = 0.0,
                speedMps = 8.5,
                hr = 140,
                cadence = 89.6,
                power = 245.7
            ),
            WorkoutSampleRecord(
                time = "2026-09-12 10:01:00",
                lapNr = 1,
                latitude = 48.005,
                longitude = 11.005,
                distanceM = 500.0,
                speedMps = 9.2,
                hr = 148,
                cadence = 92.2,
                power = 260.4
            )
        )

        val tags = runExportAndParseTagTexts(
            fileBaseName = "tst_exp_004_bike",
            bSportType = BSportType.BIKE,
            tcxSportName = "Biking",
            samples = samples,
            tagNames = listOf("Cadence", "Watts")
        )

        val cadences = tags.getValue("Cadence")
        val watts = tags.getValue("Watts")
        assertEquals(2, cadences.size)
        assertEquals(2, watts.size)
        assertEquals(listOf("90", "92"), cadences)
        assertEquals(listOf("246", "260"), watts)
        (cadences + watts).forEach { value ->
            assertTrue("Expected integer-only XML value but got '$value'", Regex("^\\d+$").matches(value))
        }
    }

    @Test
    fun testRunTcxExport_formatsRunCadenceStrictlyAsIntegers_TST_EXP_004() {
        val samples = listOf(
            WorkoutSampleRecord(
                time = "2026-09-12 10:00:00",
                lapNr = 1,
                latitude = 48.0,
                longitude = 11.0,
                distanceM = 0.0,
                speedMps = 3.5,
                hr = 152,
                cadence = 87.8
            ),
            WorkoutSampleRecord(
                time = "2026-09-12 10:01:00",
                lapNr = 1,
                latitude = 48.002,
                longitude = 11.002,
                distanceM = 210.0,
                speedMps = 3.6,
                hr = 156,
                cadence = 88.3
            )
        )

        val tags = runExportAndParseTagTexts(
            fileBaseName = "tst_exp_004_run",
            bSportType = BSportType.RUN,
            tcxSportName = "Running",
            samples = samples,
            tagNames = listOf("RunCadence")
        )

        val runCadences = tags.getValue("RunCadence")
        assertEquals(2, runCadences.size)
        assertEquals(listOf("88", "88"), runCadences)
        runCadences.forEach { value ->
            assertTrue("Expected integer-only <RunCadence> but got '$value'", Regex("^\\d+$").matches(value))
        }
    }
}
