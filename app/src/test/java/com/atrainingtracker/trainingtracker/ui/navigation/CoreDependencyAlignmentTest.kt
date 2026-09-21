package com.atrainingtracker.trainingtracker.ui.navigation

import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression and invariant test for REQ-STB-007 / TST-STB-007 (ATT-1037).
 *
 * Verifies that the runtime classpath utilizes androidx.core:1.15.0 where
 * WindowInsetsCompat$TypeImpl34 (and its unguarded WindowInsets.Type.systemOverlays() call)
 * is absent, shielding Android 14 (API 34) devices from NoSuchMethodError.
 */
class CoreDependencyAlignmentTest {

    @Test
    fun testTypeImpl34IsAbsentInCore1150() {
        // WindowInsetsCompat$TypeImpl34 was introduced in androidx.core:1.16.0-1.19.0
        // and unconditionally calls WindowInsets.Type.systemOverlays() on API 34.
        // In androidx.core:1.15.0, this class does not exist.
        val typeImpl34Class = runCatching {
            Class.forName("androidx.core.view.WindowInsetsCompat\$TypeImpl34")
        }.getOrNull()

        assertNull(
            "WindowInsetsCompat\$TypeImpl34 must NOT exist in the runtime classpath to prevent " +
                    "fatal NoSuchMethodError on Android 14 (API 34) devices (ATT-1037 / Google Issue 558456603).",
            typeImpl34Class
        )
    }

    @Test
    fun testWindowInsetsCompatTypeConstantsAreAccessible() {
        // Verify standard insets type flags remain stable and functional in 1.15.0
        assertEquals(1, WindowInsetsCompat.Type.statusBars())
        assertEquals(2, WindowInsetsCompat.Type.navigationBars())
        assertEquals(4, WindowInsetsCompat.Type.captionBar())
        assertEquals(7, WindowInsetsCompat.Type.systemBars())
        assertEquals(8, WindowInsetsCompat.Type.ime())
        assertEquals(16, WindowInsetsCompat.Type.systemGestures())
        assertEquals(32, WindowInsetsCompat.Type.mandatorySystemGestures())
        assertEquals(64, WindowInsetsCompat.Type.tappableElement())
        assertEquals(128, WindowInsetsCompat.Type.displayCutout())
    }

    @Test
    fun testWindowInsetsCompatConsumedSentinel() {
        assertNotNull(WindowInsetsCompat.CONSUMED)
    }
}
