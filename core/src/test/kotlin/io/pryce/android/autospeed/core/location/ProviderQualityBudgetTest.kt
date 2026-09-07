package io.pryce.android.autospeed.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderQualityBudgetTest {
    @Test
    fun `accepts valid budget`() {
        val budget = ProviderQualityBudget(1000, 2f, 5000)
        assertEquals(1000, budget.staleAfterMillis)
        assertEquals(2f, budget.maxSpeedAccuracyMetersPerSecond)
        assertEquals(5000, budget.recoveryStableIntervalMillis)
    }

    @Test
    fun `rejects non positive stale after`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderQualityBudget(0, 2f, 5000)
        }
    }

    @Test
    fun `rejects non positive max speed accuracy`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderQualityBudget(1000, 0f, 5000)
        }
    }

    @Test
    fun `rejects negative recovery interval`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderQualityBudget(1000, 2f, -1)
        }
    }

    @Test
    fun `allows zero recovery interval`() {
        val budget = ProviderQualityBudget(1000, 2f, 0)
        assertEquals(0, budget.recoveryStableIntervalMillis)
    }
}
