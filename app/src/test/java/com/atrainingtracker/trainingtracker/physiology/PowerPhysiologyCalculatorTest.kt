package com.atrainingtracker.trainingtracker.physiology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/**
 * Unit tests for [PowerPhysiologyCalculator] verifying `REQ-UI-399` / `TST-UI-362`:
 * - 2-point work-time solver ($W = CP \cdot t + W'$)
 * - Skiba 1-Hz $W'_{\text{bal}}$ depletion ($P > CP$), zero floor, and exponential reconstitution ($P \le CP$)
 * - Series evaluation and empty-tank warning ($< 10\%$).
 */
class PowerPhysiologyCalculatorTest {

    @Test
    fun estimateCpAndWPrime_solvesTwoPointWorkTimeModelAccurately() {
        // 3 min (180s) at 420W -> W1 = 75,600 J
        // 12 min (720s) at 320W -> W2 = 230,400 J
        // CP = (230,400 - 75,600) / (720 - 180) = 154,800 / 540 = 286.6667 W (287 W)
        // W' = 75,600 - 286.6667 * 180 = 24,000 J = 24.0 kJ
        val estimate = PowerPhysiologyCalculator.estimateCpAndWPrime(
            p1Watts = 420.0,
            t1Seconds = 180,
            p2Watts = 320.0,
            t2Seconds = 720
        )
        assertNotNull(estimate)
        assertEquals(286.6667, estimate!!.cpWatts, 0.01)
        assertEquals(287, estimate.cpWattsRounded)
        assertEquals(24000.0, estimate.wPrimeJoules, 1.0)
        assertEquals(24.0, estimate.wPrimeKj, 0.01)
    }

    @Test
    fun estimateCpAndWPrime_handlesReversedDurationOrderAndRejectsInvalidInputs() {
        val reversed = PowerPhysiologyCalculator.estimateCpAndWPrime(
            p1Watts = 320.0,
            t1Seconds = 720,
            p2Watts = 420.0,
            t2Seconds = 180
        )
        assertNotNull(reversed)
        assertEquals(24.0, reversed!!.wPrimeKj, 0.01)

        // Invalid: longer duration has higher power than shorter duration -> negative W'
        assertNull(PowerPhysiologyCalculator.estimateCpAndWPrime(300.0, 180, 350.0, 720))
        // Invalid: identical durations
        assertNull(PowerPhysiologyCalculator.estimateCpAndWPrime(400.0, 180, 300.0, 180))
        // Invalid: non-positive power or duration
        assertNull(PowerPhysiologyCalculator.estimateCpAndWPrime(0.0, 180, 300.0, 720))
        assertNull(PowerPhysiologyCalculator.estimateCpAndWPrime(400.0, -10, 300.0, 720))
    }

    @Test
    fun stepWPrimeBalanceJoules_depletesLinearlyAboveCpAndRechargesExponentiallyBelowCp() {
        val cp = 300.0
        val wPrimeJ = 20_000.0
        var bal = wPrimeJ

        // 100 seconds at 400W (100W above CP -> 10,000 J expended)
        repeat(100) {
            bal = PowerPhysiologyCalculator.stepWPrimeBalanceJoules(bal, 400.0, cp, wPrimeJ, 1.0)
        }
        assertEquals(10_000.0, bal, 1e-6)

        // 100 seconds recovery at 200W (DCP = 100W -> tau = 546 * exp(-1.0) + 316)
        val expectedTau = 546.0 * exp(-1.0) + 316.0
        assertEquals(expectedTau, PowerPhysiologyCalculator.calculateSkibaTauSeconds(200.0, cp), 1e-6)

        repeat(100) {
            bal = PowerPhysiologyCalculator.stepWPrimeBalanceJoules(bal, 200.0, cp, wPrimeJ, 1.0)
        }
        val expectedRecharged = wPrimeJ - (wPrimeJ - 10_000.0) * exp(-100.0 / expectedTau)
        assertEquals(expectedRecharged, bal, 1e-3)
    }

    @Test
    fun stepWPrimeBalanceJoules_clampsAtZeroUnderExcessiveSupraCpEffort() {
        val cp = 300.0
        val wPrimeJ = 20_000.0
        var bal = wPrimeJ

        // 300 seconds at 400W (30,000 J demand > 20,000 J capacity)
        repeat(300) {
            bal = PowerPhysiologyCalculator.stepWPrimeBalanceJoules(bal, 400.0, cp, wPrimeJ, 1.0)
        }
        assertEquals(0.0, bal, 1e-9)
    }

    @Test
    fun computeWPrimeBalanceSeries_calculatesMinPctAndEmptyTankWarning() {
        // CP = 300W, W' = 20.0 kJ.
        // 185s at 400W expends 18.5 kJ -> min W'bal = 1.5 kJ (7.5% < 10% -> empty tank warning true)
        val samples = List(185) { 400.0 } + List(15) { 200.0 }
        val result = PowerPhysiologyCalculator.computeWPrimeBalanceSeries(
            powerSamplesWatts = samples,
            cpWatts = 300.0,
            wPrimeKj = 20.0
        )
        assertEquals(200, result.seriesKj.size)
        assertNotNull(result.minWPrimeBalKj)
        assertEquals(1.5, result.minWPrimeBalKj!!, 0.01)
        assertEquals(7.5, result.minWPrimeBalPct!!, 0.05)
        assertTrue(result.isEmptyTankWarning)

        // Moderate effort: 50s at 400W -> min W'bal = 15.0 kJ (75% >= 10% -> empty tank warning false)
        val moderate = PowerPhysiologyCalculator.computeWPrimeBalanceSeries(
            powerSamplesWatts = List(50) { 400.0 },
            cpWatts = 300.0,
            wPrimeKj = 20.0
        )
        assertEquals(15.0, moderate.minWPrimeBalKj!!, 0.01)
        assertEquals(75.0, moderate.minWPrimeBalPct!!, 0.05)
        assertFalse(moderate.isEmptyTankWarning)
    }

    @Test
    fun calculateNormalizedPower_and_cyclingEfficiencyFactor_and_PwHrDecoupling() {
        // AC-1: H1 = 60s at 240W, HR = 150 bpm -> EF1 = 240 / 150 = 1.60 W/bpm
        //       H2 = 60s at 240W, HR = 160 bpm -> EF2 = 240 / 160 = 1.50 W/bpm
        //       Decoupling % = (1.60 - 1.50) / 1.60 * 100 = 6.25% (>= 5.0% -> High Drift)
        val powerStream = List(120) { 240.0 }
        val hrStream = List(60) { 150.0 } + List(60) { 160.0 }

        val np = PowerPhysiologyCalculator.calculateNormalizedPower(powerStream)
        assertNotNull(np)
        assertEquals(240.0, np!!, 1e-4)

        val result = PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
            powerSamplesWatts = powerStream,
            speedSamplesMps = emptyList(),
            hrSamplesBpm = hrStream,
            bSportType = com.atrainingtracker.banalservice.BSportType.BIKE
        )
        assertTrue(result.isPowerBased)
        assertNotNull(result.efficiencyFactor)
        // Full-session NP = 240W, Avg HR = 155 bpm -> EF = 240 / 155 = 1.548387 W/bpm
        assertEquals(240.0 / 155.0, result.efficiencyFactor!!, 1e-4)
        assertEquals(1.60, result.efFirstHalf!!, 1e-4)
        assertEquals(1.50, result.efSecondHalf!!, 1e-4)
        assertEquals(6.25, result.decouplingPct!!, 1e-4)
        assertTrue(result.isHighDrift)
    }

    @Test
    fun computeAerobicEfficiencyAndDecoupling_runningPaHrCoupledResponseAndEdgeCases() {
        // AC-2: Running without power:
        // H1 = 60s at 3.5 m/s (210 m/min), HR = 150 bpm -> EF1 = 210 / 150 = 1.40 m/min/bpm
        // H2 = 60s at 3.5 m/s (210 m/min), HR = 153 bpm -> EF2 = 210 / 153 = 1.372549 m/min/bpm
        // Decoupling % = (1.40 - 1.372549) / 1.40 * 100 = 1.96078% (< 5.0% -> Coupled)
        val speedStream = List(120) { 3.5 }
        val hrStream = List(60) { 150.0 } + List(60) { 153.0 }

        val result = PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
            powerSamplesWatts = emptyList(),
            speedSamplesMps = speedStream,
            hrSamplesBpm = hrStream,
            bSportType = com.atrainingtracker.banalservice.BSportType.RUN
        )
        assertFalse(result.isPowerBased)
        assertEquals(1.40, result.efFirstHalf!!, 1e-4)
        assertEquals(210.0 / 153.0, result.efSecondHalf!!, 1e-4)
        assertEquals((3.0 / 153.0) * 100.0, result.decouplingPct!!, 1e-4)
        assertFalse(result.isHighDrift)

        // Missing HR returns null EF and decoupling safely
        val noHr = PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
            powerSamplesWatts = List(30) { 250.0 },
            speedSamplesMps = emptyList(),
            hrSamplesBpm = emptyList(),
            bSportType = com.atrainingtracker.banalservice.BSportType.BIKE
        )
        assertNull(noHr.efficiencyFactor)
        assertNull(noHr.decouplingPct)
        assertFalse(noHr.isHighDrift)
    }

    @Test
    fun computePowerTrainingMetrics_steadyAndVariablePower_matchesCogganFormulas() {
        // TST-MIG-036 (REQ-MIG-039.1): 1 hour (3600s) steady at 250W, FTP = 250W, weight = 75.0kg
        val steady1Hour = List(3600) { 250.0 }
        val steadyResult = PowerPhysiologyCalculator.computePowerTrainingMetrics(
            powerSamplesWatts = steady1Hour,
            activeTimeSeconds = 3600,
            ftpWatts = 250.0,
            athleteWeightKg = 75.0
        )
        assertEquals(250.0, steadyResult.normalizedPowerWatts!!, 1e-4)
        assertEquals(250.0, steadyResult.avgPowerWatts!!, 1e-4)
        assertEquals(250.0, steadyResult.maxPowerWatts!!, 1e-4)
        assertEquals(1.00, steadyResult.variabilityIndex!!, 1e-4)
        assertEquals(1.00, steadyResult.intensityFactor!!, 1e-4)
        assertEquals(100.0, steadyResult.tss!!, 1e-4)
        assertEquals(900.0, steadyResult.workKj!!, 1e-4)
        assertEquals(250.0 / 75.0, steadyResult.avgPowerToWeight!!, 1e-4)
        assertEquals(250.0 / 75.0, steadyResult.maxPowerToWeight!!, 1e-4)
        assertEquals(250.0 / 75.0, steadyResult.npPowerToWeight!!, 1e-4)

        // Variable interval workout: 30s at 300W + 30s at 100W (Avg = 200W, NP > 200W, VI > 1.0)
        val variableStream = List(30) { 300.0 } + List(30) { 100.0 }
        val varResult = PowerPhysiologyCalculator.computePowerTrainingMetrics(
            powerSamplesWatts = variableStream,
            activeTimeSeconds = 60,
            ftpWatts = 250.0,
            athleteWeightKg = 70.0
        )
        assertEquals(200.0, varResult.avgPowerWatts!!, 1e-4)
        assertEquals(300.0, varResult.maxPowerWatts!!, 1e-4)
        assertTrue(varResult.normalizedPowerWatts!! > 200.0)
        assertTrue(varResult.variabilityIndex!! > 1.0)
        assertEquals(12.0, varResult.workKj!!, 1e-4) // (30*300 + 30*100) / 1000 = 12.0 kJ

        // FIT session fallback seeds when sample stream is empty
        val fallbackResult = PowerPhysiologyCalculator.computePowerTrainingMetrics(
            powerSamplesWatts = emptyList(),
            activeTimeSeconds = 1800,
            ftpWatts = 250.0,
            athleteWeightKg = 75.0,
            fallbackNpWatts = 225.0,
            fallbackIf = 0.90,
            fallbackTss = 40.5,
            fallbackWorkKj = 380.0
        )
        assertEquals(225.0, fallbackResult.normalizedPowerWatts!!, 1e-4)
        assertEquals(0.90, fallbackResult.intensityFactor!!, 1e-4)
        assertEquals(40.5, fallbackResult.tss!!, 1e-4)
        assertEquals(380.0, fallbackResult.workKj!!, 1e-4)
        assertEquals(225.0 / 75.0, fallbackResult.npPowerToWeight!!, 1e-4)

        // Empty stream with no fallbacks returns null fields safely
        val emptyResult = PowerPhysiologyCalculator.computePowerTrainingMetrics(
            powerSamplesWatts = emptyList()
        )
        assertNull(emptyResult.normalizedPowerWatts)
        assertNull(emptyResult.intensityFactor)
        assertNull(emptyResult.tss)
        assertNull(emptyResult.workKj)
        assertNull(emptyResult.avgPowerToWeight)
    }

    @Test
    fun importAndHistoricalThresholdResolution_sourceContractsArePresent() {
        // TST-MIG-036 (REQ-MIG-039.2, REQ-MIG-039.3, REQ-MIG-039.4)
        val projectRoot = java.io.File(System.getProperty("user.dir") ?: ".")
        val baseDir = if (java.io.File(projectRoot, "app/src/main").exists()) {
            java.io.File(projectRoot, "app/src/main/java/com/atrainingtracker")
        } else {
            java.io.File(projectRoot, "src/main/java/com/atrainingtracker")
        }

        val dbManagerSrc = java.io.File(baseDir, "trainingtracker/database/AthleteMetricsDatabaseManager.kt").readText()
        assertTrue(dbManagerSrc.contains("fun getEntryEffectiveAt("))

        val settingsHelperSrc = java.io.File(baseDir, "trainingtracker/settings/SettingsDataStoreJavaHelper.kt").readText()
        assertTrue(settingsHelperSrc.contains("fun resolveEffectiveFtpAt("))
        assertTrue(settingsHelperSrc.contains("fun resolveEffectiveWeightKgAt("))

        val fitParserSrc = java.io.File(baseDir, "trainingtracker/migration/parsers/FitWorkoutParser.kt").readText()
        assertTrue(fitParserSrc.contains("foundSensors.add(SensorType.POWER)"))
        assertTrue(fitParserSrc.contains("sessionNormalizedPowerWatts = sessionNp"))

        val importEngineSrc = java.io.File(baseDir, "trainingtracker/migration/LegacyImportEngine.kt").readText()
        assertTrue(importEngineSrc.contains("computePowerTrainingMetrics("))
        assertTrue(importEngineSrc.contains("SensorType.NORMALIZED_POWER"))
        assertTrue(importEngineSrc.contains("SensorType.INTENSITY_FACTOR"))
        assertTrue(importEngineSrc.contains("SensorType.TSS"))
        assertTrue(importEngineSrc.contains("SensorType.WORK_KJ"))
        assertTrue(importEngineSrc.contains("SensorType.POWER_TO_WEIGHT"))
    }
}
