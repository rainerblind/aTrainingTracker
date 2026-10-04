/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.routes

import android.media.ToneGenerator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class NavigationAudioAlertManagerTest {

    @Test
    fun testAlertChimes_invokeToneGeneratorWithExpectedParameters() {
        val mockToneGen = mockk<ToneGenerator>(relaxed = true)
        val manager = NavigationAudioAlertManager { mockToneGen }

        manager.playApproachAlert()
        verify(exactly = 1) { mockToneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }

        manager.playTurnNowAlert()
        verify(exactly = 1) { mockToneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 250) }

        manager.playOffRouteAlert()
        verify(exactly = 1) { mockToneGen.startTone(ToneGenerator.TONE_PROP_NACK, 300) }

        manager.playBackOnRouteAlert()
        verify(exactly = 1) { mockToneGen.startTone(ToneGenerator.TONE_PROP_ACK, 200) }

        manager.release()
        verify(exactly = 1) { mockToneGen.release() }
    }

    @Test
    fun testNullToneGenerator_doesNotThrow() {
        val manager = NavigationAudioAlertManager { null }
        manager.playApproachAlert()
        manager.playTurnNowAlert()
        manager.playOffRouteAlert()
        manager.playBackOnRouteAlert()
        manager.release()
    }
}
