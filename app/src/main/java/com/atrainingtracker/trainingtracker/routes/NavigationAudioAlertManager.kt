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

package com.atrainingtracker.trainingtracker.routes

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Auditory feedback controller for turn-by-turn navigation alerts (REQ-MAP-028 / ATT-1450).
 *
 * Emits distinct audio chimes for approaching turns, immediate execution, off-route warnings,
 * and corridor re-entry using Android standard [ToneGenerator] without external asset dependencies.
 */
class NavigationAudioAlertManager(
    toneGeneratorProvider: (() -> ToneGenerator?)? = null
) {
    companion object {
        private const val TAG = "NavAudioAlertManager"
    }

    private var toneGenerator: ToneGenerator? = if (toneGeneratorProvider != null) {
        toneGeneratorProvider.invoke()
    } else {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize ToneGenerator", e)
            null
        }
    }

    /**
     * Plays two-tone chime when athlete approaches an upcoming turn cue within countdown distance.
     */
    fun playApproachAlert() {
        playTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    /**
     * Plays assertive chime when athlete reaches execution zone (<= 25m).
     */
    fun playTurnNowAlert() {
        playTone(ToneGenerator.TONE_PROP_BEEP2, 250)
    }

    /**
     * Plays low warning tone when athlete deviates outside corridor threshold for 3 consecutive fixes.
     */
    fun playOffRouteAlert() {
        playTone(ToneGenerator.TONE_PROP_NACK, 300)
    }

    /**
     * Plays confirmation tone when athlete re-enters route corridor.
     */
    fun playBackOnRouteAlert() {
        playTone(ToneGenerator.TONE_PROP_ACK, 200)
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "Error playing navigation tone $toneType", e)
        }
    }

    /**
     * Releases audio hardware resources.
     */
    fun release() {
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing ToneGenerator", e)
        } finally {
            toneGenerator = null
        }
    }
}
