package com.atrainingtracker.util

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [LifecycleUtils] enforcing REQ-STB-016 / TST-STB-016.
 *
 * Verifies crash immunity against IllegalStateException, SecurityException,
 * and IllegalArgumentException, null-safety, and observability.
 */
class LifecycleUtilsTest {

    private val mockContext = mockk<Context>()
    private val mockIntent = mockk<Intent>()
    private val mockReceiver = mockk<BroadcastReceiver>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        clearAllMocks()
    }

    @Test
    fun testSafeStartService_success() {
        val expected = ComponentName("com.atrainingtracker", "com.atrainingtracker.TestService")
        every { mockContext.startService(mockIntent) } returns expected

        val result = LifecycleUtils.safeStartService(mockContext, mockIntent, "TestTag")

        assertSame(expected, result)
        verify(exactly = 1) { mockContext.startService(mockIntent) }
    }

    @Test
    fun testSafeStartService_nullInputs_returnsNull() {
        assertNull(LifecycleUtils.safeStartService(null, mockIntent))
        assertNull(LifecycleUtils.safeStartService(mockContext, null))
        assertNull(LifecycleUtils.safeStartService(null, null))
    }

    @Test
    fun testSafeStartService_illegalStateException_returnsNullAndLogsWarning() {
        every { mockContext.startService(mockIntent) } throws IllegalStateException("Not allowed to start service Intent: app is in background")

        val result = LifecycleUtils.safeStartService(mockContext, mockIntent, "TestTag")

        assertNull(result)
        verify {
            Log.w("TestTag", match<String> { it.contains("background execution restricted") }, any())
        }
    }

    @Test
    fun testSafeStartService_securityException_returnsNullAndLogsWarning() {
        every { mockContext.startService(mockIntent) } throws SecurityException("Permission Denial: startService")

        val result = LifecycleUtils.safeStartService(mockContext, mockIntent, "TestTag")

        assertNull(result)
        verify {
            Log.w("TestTag", match<String> { it.contains("permission denied") }, any())
        }
    }

    @Test
    fun testSafeUnregisterReceiver_success() {
        every { mockContext.unregisterReceiver(mockReceiver) } just Runs

        val result = LifecycleUtils.safeUnregisterReceiver(mockContext, mockReceiver, "TestTag")

        assertTrue(result)
        verify(exactly = 1) { mockContext.unregisterReceiver(mockReceiver) }
    }

    @Test
    fun testSafeUnregisterReceiver_nullInputs_returnsFalse() {
        assertFalse(LifecycleUtils.safeUnregisterReceiver(null, mockReceiver))
        assertFalse(LifecycleUtils.safeUnregisterReceiver(mockContext, null))
        assertFalse(LifecycleUtils.safeUnregisterReceiver(null, null))
    }

    @Test
    fun testSafeUnregisterReceiver_illegalArgumentException_returnsFalseAndLogsWarning() {
        every { mockContext.unregisterReceiver(mockReceiver) } throws IllegalArgumentException("Receiver not registered: com.atrainingtracker.TestReceiver@12345")

        val result = LifecycleUtils.safeUnregisterReceiver(mockContext, mockReceiver, "TestTag")

        assertFalse(result)
        verify {
            Log.w("TestTag", match<String> { it.contains("receiver was not registered or already unregistered") }, any())
        }
    }

    @Test
    fun testSafeUnregisterReceiver_illegalStateException_returnsFalseAndLogsWarning() {
        every { mockContext.unregisterReceiver(mockReceiver) } throws IllegalStateException("Activity has been destroyed")

        val result = LifecycleUtils.safeUnregisterReceiver(mockContext, mockReceiver, "TestTag")

        assertFalse(result)
        verify {
            Log.w("TestTag", match<String> { it.contains("lifecycle state error") }, any())
        }
    }

    @Test
    fun testKotlinExtensions_delegateToLifecycleUtils() {
        val expected = ComponentName("com.atrainingtracker", "com.atrainingtracker.TestService")
        every { mockContext.startService(mockIntent) } returns expected
        every { mockContext.unregisterReceiver(mockReceiver) } just Runs

        val comp = mockContext.safeStartService(mockIntent)
        val unregistered = mockContext.safeUnregisterReceiver(mockReceiver)

        assertSame(expected, comp)
        assertTrue(unregistered)

        val nullContext: Context? = null
        assertNull(nullContext.safeStartService(mockIntent))
        assertFalse(nullContext.safeUnregisterReceiver(mockReceiver))
    }
}
