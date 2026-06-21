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

package com.atrainingtracker.trainingtracker.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NumericalEncodingUtilsTest {

    @Test
    fun testEncodeDecodeDoubles() {
        val input = listOf(123.45, 67.89, 0.0, -12.34, 1000.5)
        val encoded = NumericalEncodingUtils.encodeDoubles(input)
        val decoded = NumericalEncodingUtils.decodeDoubles(encoded)

        assertEquals(input.size, decoded.size)
        for (i in input.indices) {
            assertEquals("Mismatch at index $i", input[i], decoded[i], 0.001)
        }
    }

    @Test
    fun testEmptyList() {
        val input = emptyList<Double>()
        val encoded = NumericalEncodingUtils.encodeDoubles(input)
        val decoded = NumericalEncodingUtils.decodeDoubles(encoded)
        assertEquals(0, decoded.size)
    }

    @Test
    fun testSingleValue() {
        val input = listOf(42.0)
        val encoded = NumericalEncodingUtils.encodeDoubles(input)
        val decoded = NumericalEncodingUtils.decodeDoubles(encoded)
        assertEquals(1, decoded.size)
        assertEquals(42.0, decoded[0], 0.001)
    }
}
