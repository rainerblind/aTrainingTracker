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
}
