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

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.banalservice.BSportType
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for [FitSportMapper] verifying sport and subsport mapping to [BSportType] (TST-DAT-014).
 */
class FitSportMappingTest {

    @Test
    fun testNullSport_returnsUnknown() {
        assertEquals(BSportType.UNKNOWN, FitSportMapper.mapFitSport(null, null))
    }

    @Test
    fun testCycling_returnsBike() {
        assertEquals(BSportType.BIKE, FitSportMapper.mapFitSport(Sport.CYCLING, SubSport.GENERIC))
        assertEquals(BSportType.BIKE, FitSportMapper.mapFitSport(Sport.CYCLING, SubSport.ROAD))
        assertEquals(BSportType.BIKE, FitSportMapper.mapFitSport(Sport.CYCLING, SubSport.MOUNTAIN))
    }

    @Test
    fun testRunningAndWalking_returnsRun() {
        assertEquals(BSportType.RUN, FitSportMapper.mapFitSport(Sport.RUNNING, SubSport.GENERIC))
        assertEquals(BSportType.RUN, FitSportMapper.mapFitSport(Sport.RUNNING, SubSport.TRAIL))
        assertEquals(BSportType.RUN, FitSportMapper.mapFitSport(Sport.WALKING, SubSport.GENERIC))
    }

    @Test
    fun testFitnessEquipment_mapsCorrectlyBasedOnSubsport() {
        assertEquals(BSportType.BIKE, FitSportMapper.mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.INDOOR_CYCLING))
        assertEquals(BSportType.BIKE, FitSportMapper.mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.SPIN))
        assertEquals(BSportType.RUN, FitSportMapper.mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.INDOOR_RUNNING))
        assertEquals(BSportType.RUN, FitSportMapper.mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.TREADMILL))
        assertEquals(BSportType.RUN, FitSportMapper.mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.INDOOR_WALKING))
        assertEquals(BSportType.UNKNOWN, FitSportMapper.mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.ELLIPTICAL))
        assertEquals(BSportType.UNKNOWN, FitSportMapper.mapFitSport(Sport.FITNESS_EQUIPMENT, SubSport.INDOOR_ROWING))
    }

    @Test
    fun testTransition_returnsConflict() {
        assertEquals(BSportType.CONFLICT, FitSportMapper.mapFitSport(Sport.TRANSITION, SubSport.GENERIC))
    }

    @Test
    fun testOtherSports_returnsUnknown() {
        assertEquals(BSportType.UNKNOWN, FitSportMapper.mapFitSport(Sport.SWIMMING, SubSport.LAP_SWIMMING))
        assertEquals(BSportType.UNKNOWN, FitSportMapper.mapFitSport(Sport.GENERIC, SubSport.GENERIC))
    }
}
