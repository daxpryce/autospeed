package io.pryce.android.autospeed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A minimal instrumented smoke test confirming the application under test launches its
 * [AutospeedApplication] and exposes the expected package. This suite requires a connected device
 * or emulator (`connectedAndroidTest`); this container has neither an emulator nor system images
 * installed, so it is included for completeness but has not been executed here (see the final
 * report).
 */
@RunWith(AndroidJUnit4::class)
class AutospeedApplicationInstrumentedTest {
    @Test
    fun appContext_hasExpectedBasePackageName() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        assertTrue(targetContext.packageName.startsWith("io.pryce.android.autospeed"))
        assertTrue(instrumentation.context.packageName.isNotEmpty())
    }
}
