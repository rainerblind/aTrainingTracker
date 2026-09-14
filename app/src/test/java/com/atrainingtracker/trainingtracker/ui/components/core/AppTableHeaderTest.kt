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

package com.atrainingtracker.trainingtracker.ui.components.core

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying [AppTableHeader] typography and styling rules
 * (REQ-UI-148, TST-UI-101, ATT-939).
 */
class AppTableHeaderTest {

    @Test
    fun testWithBottomBaselineAlignment_disablesFontPaddingAndAlignsBottom() {
        val baseStyle = TextStyle()
        val styled = baseStyle.withBottomBaselineAlignment()

        assertNotNull("PlatformStyle must be defined", styled.platformStyle)
        assertNotNull("ParagraphStyle must be defined", styled.platformStyle?.paragraphStyle)
        assertFalse("includeFontPadding must be false for baseline alignment", styled.platformStyle!!.paragraphStyle!!.includeFontPadding)

        assertNotNull("LineHeightStyle must be defined", styled.lineHeightStyle)
        assertEquals(
            "LineHeightStyle alignment must be Bottom",
            LineHeightStyle.Alignment.Bottom,
            styled.lineHeightStyle!!.alignment
        )
        assertEquals(
            "LineHeightStyle trim must be Both",
            LineHeightStyle.Trim.Both,
            styled.lineHeightStyle!!.trim
        )
    }

    @Test
    fun testWithBottomBaselineAlignment_preservesExistingProperties() {
        val customStyle = TextStyle(
            color = androidx.compose.ui.graphics.Color.Red,
            fontSize = 14.sp
        )
        val result = customStyle.withBottomBaselineAlignment()

        assertEquals(androidx.compose.ui.graphics.Color.Red, result.color)
        assertEquals(14.sp, result.fontSize)
        assertFalse(result.platformStyle!!.paragraphStyle!!.includeFontPadding)
        assertEquals(LineHeightStyle.Alignment.Bottom, result.lineHeightStyle!!.alignment)
    }
}
