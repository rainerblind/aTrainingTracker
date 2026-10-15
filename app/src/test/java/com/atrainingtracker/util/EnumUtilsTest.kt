package com.atrainingtracker.util

import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [EnumUtils] verifying crash immunity, whitespace trimming,
 * null/blank handling, enum evolution forward/backward compatibility, and observability.
 */
class EnumUtilsTest {

    private enum class SampleEnum {
        FIRST_OPTION,
        SECOND_OPTION,
        THIRD_OPTION
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testValidConstant_returnsExactEnum() {
        val result = EnumUtils.safeValueOf(SampleEnum::class.java, "SECOND_OPTION", SampleEnum.FIRST_OPTION, "TestTag")
        assertEquals(SampleEnum.SECOND_OPTION, result)

        val resultOrNull = EnumUtils.safeValueOfOrNull<SampleEnum>(SampleEnum::class.java, "THIRD_OPTION", "TestTag")
        assertEquals(SampleEnum.THIRD_OPTION, resultOrNull)
    }

    @Test
    fun testWhitespaceTrimming_returnsExactEnum() {
        val result = EnumUtils.safeValueOf(SampleEnum::class.java, "  SECOND_OPTION  \n", SampleEnum.FIRST_OPTION)
        assertEquals(SampleEnum.SECOND_OPTION, result)

        val resultOrNull = EnumUtils.safeValueOfOrNull<SampleEnum>(SampleEnum::class.java, "\tTHIRD_OPTION\t")
        assertEquals(SampleEnum.THIRD_OPTION, resultOrNull)
    }

    @Test
    fun testNullInput_returnsFallbackOrNull() {
        val result = EnumUtils.safeValueOf(SampleEnum::class.java, null, SampleEnum.FIRST_OPTION)
        assertEquals(SampleEnum.FIRST_OPTION, result)

        val resultOrNull = EnumUtils.safeValueOfOrNull<SampleEnum>(SampleEnum::class.java, null)
        assertNull(resultOrNull)

        verify { Log.w("EnumUtils", match<String> { it.contains("null or blank") }) }
    }

    @Test
    fun testEmptyAndBlankInput_returnsFallbackOrNull() {
        val emptyResult = EnumUtils.safeValueOf(SampleEnum::class.java, "", SampleEnum.FIRST_OPTION)
        assertEquals(SampleEnum.FIRST_OPTION, emptyResult)

        val blankResult = EnumUtils.safeValueOf(SampleEnum::class.java, "   ", SampleEnum.FIRST_OPTION)
        assertEquals(SampleEnum.FIRST_OPTION, blankResult)

        val nullResult1 = EnumUtils.safeValueOfOrNull<SampleEnum>(SampleEnum::class.java, "")
        assertNull(nullResult1)

        val nullResult2 = EnumUtils.safeValueOfOrNull<SampleEnum>(SampleEnum::class.java, "   ")
        assertNull(nullResult2)
    }

    @Test
    fun testUnrecognizedConstant_returnsFallbackOrNull() {
        val result = EnumUtils.safeValueOf(SampleEnum::class.java, "UNKNOWN_CONSTANT_XYZ", SampleEnum.FIRST_OPTION, "MyTag")
        assertEquals(SampleEnum.FIRST_OPTION, result)

        val resultOrNull = EnumUtils.safeValueOfOrNull<SampleEnum>(SampleEnum::class.java, "UNKNOWN_CONSTANT_XYZ", "MyTag")
        assertNull(resultOrNull)

        verify { Log.w("MyTag", match<String> { it.contains("Unrecognized or evolved") }, any()) }
    }

    @Test
    fun testEnumEvolution_futureConstant_returnsFallbackOrNull() {
        // Simulates future app version writing a new enum constant to DB / IPC
        val futureConstantName = "FOURTH_OPTION_FUTURE_V5"
        val result = EnumUtils.safeValueOf(SampleEnum::class.java, futureConstantName, SampleEnum.FIRST_OPTION)
        assertEquals(SampleEnum.FIRST_OPTION, result)

        val resultOrNull = EnumUtils.safeValueOfOrNull<SampleEnum>(SampleEnum::class.java, futureConstantName)
        assertNull(resultOrNull)
    }

    @Test
    fun testEnumEvolution_renamedConstant_returnsFallbackOrNull() {
        // Simulates an older app version writing a legacy enum constant name that was renamed
        val legacyConstantName = "LEGACY_FIRST"
        val result = EnumUtils.safeValueOf(SampleEnum::class.java, legacyConstantName, SampleEnum.FIRST_OPTION)
        assertEquals(SampleEnum.FIRST_OPTION, result)

        val resultOrNull = EnumUtils.safeValueOfOrNull<SampleEnum>(SampleEnum::class.java, legacyConstantName)
        assertNull(resultOrNull)
    }

    @Test
    fun testProductionEnums_BSportTypeAndPeriodType() {
        assertEquals(BSportType.RUN, safeEnumValueOf("RUN", BSportType.UNKNOWN))
        assertEquals(BSportType.UNKNOWN, safeEnumValueOf("FUTURE_EXTREME_SPORT", BSportType.UNKNOWN))
        assertNull(safeEnumValueOfOrNull<BSportType>("FUTURE_EXTREME_SPORT"))

        assertEquals(PeriodType.MONTH, safeEnumValueOf("MONTH", PeriodType.WEEK))
        assertEquals(PeriodType.WEEK, safeEnumValueOf(null, PeriodType.WEEK))
        assertNull(safeEnumValueOfOrNull<PeriodType>("INVALID_PERIOD"))
    }
}
