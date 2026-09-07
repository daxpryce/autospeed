package io.pryce.android.autospeed.core.location

import io.pryce.android.autospeed.core.speed.SpeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOrchestratorTest {
    private val budget =
        ProviderQualityBudget(
            staleAfterMillis = 5000,
            maxSpeedAccuracyMetersPerSecond = 5f,
            recoveryStableIntervalMillis = 10_000,
        )

    private fun goodFix(
        tier: ProviderTier,
        atMillis: Long,
        speed: Float = 10f,
    ) = LocationFix(
        tier,
        speed,
        hasSpeed = true,
        speedAccuracyMetersPerSecond = 1f,
        hasSpeedAccuracy = true,
        timestampMillis = atMillis,
    )

    @Test
    fun `starts awaiting first fix with no data`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val result = orchestrator.evaluate(0, emptyMap())
        assertEquals(SpeedState.AwaitingFirstFix, result.speedState)
        assertNull(result.acceptedTier)
        assertTrue(result.shouldWarmDirectGnss)
        assertFalse(result.shouldRequestPlay)
    }

    @Test
    fun `accepts a good system fused fix immediately`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val fix = goodFix(ProviderTier.SYSTEM_FUSED, atMillis = 0)
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to fix))
        assertEquals(ProviderTier.SYSTEM_FUSED, result.acceptedTier)
        assertEquals(SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED), result.speedState)
        assertTrue(result.shouldWarmDirectGnss)
    }

    @Test
    fun `framework variant escalates directly to gnss when system fails`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val gnss = goodFix(ProviderTier.DIRECT_GNSS, atMillis = 0)
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.DIRECT_GNSS to gnss))
        assertEquals(ProviderTier.DIRECT_GNSS, result.acceptedTier)
        assertEquals(SpeedState.Available(10f, ProviderTier.DIRECT_GNSS), result.speedState)
        assertFalse(result.shouldWarmDirectGnss)
        assertFalse(result.shouldRequestPlay)
    }

    @Test
    fun `framework variant never requests play`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val result = orchestrator.evaluate(0, emptyMap())
        assertFalse(result.shouldRequestPlay)
    }

    @Test
    fun `play variant escalates to play when system fails`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = true, budget)
        val play = goodFix(ProviderTier.PLAY_FUSED, atMillis = 0)
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.PLAY_FUSED to play))
        assertEquals(ProviderTier.PLAY_FUSED, result.acceptedTier)
        assertTrue(result.shouldRequestPlay)
    }

    @Test
    fun `play variant escalates to gnss when system and play both fail`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = true, budget)
        val gnss = goodFix(ProviderTier.DIRECT_GNSS, atMillis = 0)
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.DIRECT_GNSS to gnss))
        assertEquals(ProviderTier.DIRECT_GNSS, result.acceptedTier)
    }

    @Test
    fun `stale fix does not qualify`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        // First accept a fix so the "ever accepted" flag is set, then let it go stale.
        orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 0)))
        val staleFix = goodFix(ProviderTier.SYSTEM_FUSED, atMillis = 0)
        val result = orchestrator.evaluate(5001, mapOf(ProviderTier.SYSTEM_FUSED to staleFix))
        assertEquals(SpeedState.Stale, result.speedState)
        assertNull(result.acceptedTier)
    }

    @Test
    fun `fix exactly at stale boundary still qualifies`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val fix = goodFix(ProviderTier.SYSTEM_FUSED, atMillis = 0)
        val result = orchestrator.evaluate(5000, mapOf(ProviderTier.SYSTEM_FUSED to fix))
        assertEquals(SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED), result.speedState)
    }

    @Test
    fun `fix without speed does not qualify and reports unavailable after acceptance`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 0)))
        val noSpeedFix =
            LocationFix(
                ProviderTier.SYSTEM_FUSED,
                0f,
                hasSpeed = false,
                speedAccuracyMetersPerSecond = 1f,
                hasSpeedAccuracy = true,
                timestampMillis = 0,
            )
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to noSpeedFix))
        assertEquals(SpeedState.Unavailable, result.speedState)
    }

    @Test
    fun `poor speed accuracy disqualifies a fix`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val poorAccuracy =
            LocationFix(
                ProviderTier.SYSTEM_FUSED,
                10f,
                hasSpeed = true,
                speedAccuracyMetersPerSecond = 5.01f,
                hasSpeedAccuracy = true,
                timestampMillis = 0,
            )
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to poorAccuracy))
        assertEquals(SpeedState.AwaitingFirstFix, result.speedState)
    }

    @Test
    fun `speed accuracy exactly at max still qualifies`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val fix =
            LocationFix(
                ProviderTier.SYSTEM_FUSED,
                10f,
                hasSpeed = true,
                speedAccuracyMetersPerSecond = 5f,
                hasSpeedAccuracy = true,
                timestampMillis = 0,
            )
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to fix))
        assertEquals(SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED), result.speedState)
    }

    @Test
    fun `unreported speed accuracy is ignored even when the raw value is poor`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val fix =
            LocationFix(
                ProviderTier.SYSTEM_FUSED,
                10f,
                hasSpeed = true,
                speedAccuracyMetersPerSecond = 99f,
                hasSpeedAccuracy = false,
                timestampMillis = 0,
            )
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to fix))
        assertEquals(SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED), result.speedState)
    }

    @Test
    fun `mismatched tier mapping never qualifies`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val mismatched = goodFix(ProviderTier.DIRECT_GNSS, atMillis = 0)
        val result = orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to mismatched))
        assertEquals(SpeedState.AwaitingFirstFix, result.speedState)
    }

    @Test
    fun `prefers a trustworthy gnss speed over a system fix lacking speed`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        val systemWithoutSpeed =
            LocationFix(
                ProviderTier.SYSTEM_FUSED,
                0f,
                hasSpeed = false,
                speedAccuracyMetersPerSecond = 0f,
                hasSpeedAccuracy = false,
                timestampMillis = 0,
            )
        val gnssWithSpeed = goodFix(ProviderTier.DIRECT_GNSS, atMillis = 0, speed = 8f)
        val result =
            orchestrator.evaluate(
                0,
                mapOf(ProviderTier.SYSTEM_FUSED to systemWithoutSpeed, ProviderTier.DIRECT_GNSS to gnssWithSpeed),
            )
        assertEquals(SpeedState.Available(8f, ProviderTier.DIRECT_GNSS), result.speedState)
    }

    @Test
    fun `no fix at all after acceptance reports unavailable`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 0)))
        val result = orchestrator.evaluate(100, emptyMap())
        assertEquals(SpeedState.Unavailable, result.speedState)
    }

    @Test
    fun `recovery to a more preferred tier requires sustained stability`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = true, budget)
        // Escalate to play first.
        orchestrator.evaluate(0, mapOf(ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 0)))

        // System fused starts qualifying again, but must not switch back immediately.
        val recovering =
            mapOf(
                ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 1000, speed = 5f),
                ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 1000),
            )
        val stillPlay = orchestrator.evaluate(1000, recovering)
        assertEquals(ProviderTier.PLAY_FUSED, stillPlay.acceptedTier)

        // Just before the recovery interval elapses, still on play.
        val almost =
            mapOf(
                ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 1000 + 9999, speed = 5f),
                ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 1000 + 9999),
            )
        val stillPlay2 = orchestrator.evaluate(1000 + 9999, almost)
        assertEquals(ProviderTier.PLAY_FUSED, stillPlay2.acceptedTier)

        // Once the recovery interval has fully elapsed, switch back to system fused.
        val recovered =
            mapOf(
                ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 1000 + 10_000, speed = 5f),
                ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 1000 + 10_000),
            )
        val backToSystem = orchestrator.evaluate(1000 + 10_000, recovered)
        assertEquals(ProviderTier.SYSTEM_FUSED, backToSystem.acceptedTier)
        assertEquals(SpeedState.Available(5f, ProviderTier.SYSTEM_FUSED), backToSystem.speedState)
    }

    @Test
    fun `flickering recovery candidate never switches back and resets its timer`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = true, budget)
        orchestrator.evaluate(0, mapOf(ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 0)))

        // System briefly recovers...
        orchestrator.evaluate(
            1000,
            mapOf(
                ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 1000),
                ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 1000),
            ),
        )

        // ...then fails again well before the recovery interval elapses. The pending candidate
        // must reset rather than accumulate stability time across the gap.
        orchestrator.evaluate(2000, mapOf(ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 2000)))

        // System recovers again; only 2000ms of *continuous* stability have elapsed from this
        // second attempt, which is under the 10,000ms interval, so it must still be on play.
        val stillPlay =
            orchestrator.evaluate(
                4000,
                mapOf(
                    ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 4000),
                    ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 4000),
                ),
            )
        assertEquals(ProviderTier.PLAY_FUSED, stillPlay.acceptedTier)
    }

    @Test
    fun `pending recovery candidate switches when a different tier becomes preferred`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = true, budget)
        // Escalate all the way to direct GNSS.
        orchestrator.evaluate(0, mapOf(ProviderTier.DIRECT_GNSS to goodFix(ProviderTier.DIRECT_GNSS, 0)))

        // System fused (the most preferred tier) becomes the pending recovery candidate.
        orchestrator.evaluate(
            1000,
            mapOf(
                ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 1000),
                ProviderTier.DIRECT_GNSS to goodFix(ProviderTier.DIRECT_GNSS, 1000),
            ),
        )

        // System fused stops qualifying, but play fused (also more preferred than GNSS) now
        // qualifies instead: the pending candidate must switch to play and restart its timer.
        val switched =
            orchestrator.evaluate(
                2000,
                mapOf(
                    ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 2000),
                    ProviderTier.DIRECT_GNSS to goodFix(ProviderTier.DIRECT_GNSS, 2000),
                ),
            )
        assertEquals(ProviderTier.DIRECT_GNSS, switched.acceptedTier)

        // Even after the original interval from t=1000 would have elapsed, the restarted timer
        // (from t=2000) means play has not yet been stable long enough.
        val stillGnss =
            orchestrator.evaluate(
                1000 + 10_000,
                mapOf(
                    ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 1000 + 10_000),
                    ProviderTier.DIRECT_GNSS to goodFix(ProviderTier.DIRECT_GNSS, 1000 + 10_000),
                ),
            )
        assertEquals(ProviderTier.DIRECT_GNSS, stillGnss.acceptedTier)

        // But once play has been stable for a full interval starting from its own restart, the
        // orchestrator switches to it.
        val switchedToPlay =
            orchestrator.evaluate(
                2000 + 10_000,
                mapOf(
                    ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 2000 + 10_000),
                    ProviderTier.DIRECT_GNSS to goodFix(ProviderTier.DIRECT_GNSS, 2000 + 10_000),
                ),
            )
        assertEquals(ProviderTier.PLAY_FUSED, switchedToPlay.acceptedTier)
    }

    @Test
    fun `escalation away from a failing active tier is immediate never delayed`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = true, budget)
        orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 0)))
        // System fails on the very next tick; play should be accepted immediately, not after
        // any hysteresis delay.
        val result = orchestrator.evaluate(1, mapOf(ProviderTier.PLAY_FUSED to goodFix(ProviderTier.PLAY_FUSED, 1)))
        assertEquals(ProviderTier.PLAY_FUSED, result.acceptedTier)
    }

    @Test
    fun `active tier vanishing while a more preferred candidate appears escalates immediately`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        // System fails first, so the orchestrator escalates to direct GNSS.
        orchestrator.evaluate(0, mapOf(ProviderTier.DIRECT_GNSS to goodFix(ProviderTier.DIRECT_GNSS, 0)))
        // On the very next tick, system fused starts qualifying again but the active GNSS fix
        // has vanished entirely (no entry at all, e.g. the provider was unregistered). Even
        // though system fused is a more-preferred tier that would normally have to wait out the
        // recovery hysteresis window, the vanished active fix must not be reported as accepted,
        // so the switch happens immediately instead of waiting.
        val result = orchestrator.evaluate(1, mapOf(ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 1)))
        assertEquals(ProviderTier.SYSTEM_FUSED, result.acceptedTier)
        assertEquals(SpeedState.Available(10f, ProviderTier.SYSTEM_FUSED), result.speedState)
    }

    @Test
    fun `active tier going stale while a more preferred candidate appears escalates immediately`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = false, budget)
        // System fails first, so the orchestrator escalates to direct GNSS.
        orchestrator.evaluate(0, mapOf(ProviderTier.DIRECT_GNSS to goodFix(ProviderTier.DIRECT_GNSS, 0)))
        // Much later, system fused starts qualifying again, but the last-known GNSS fix (still
        // present in the map, just old) is now stale rather than merely missing. This must not
        // be held up by the recovery hysteresis window either.
        val staleGnss = goodFix(ProviderTier.DIRECT_GNSS, atMillis = 0)
        val freshSystem = goodFix(ProviderTier.SYSTEM_FUSED, atMillis = 10_000)
        val result =
            orchestrator.evaluate(
                10_000,
                mapOf(ProviderTier.DIRECT_GNSS to staleGnss, ProviderTier.SYSTEM_FUSED to freshSystem),
            )
        assertEquals(ProviderTier.SYSTEM_FUSED, result.acceptedTier)
    }

    @Test
    fun `warms direct gnss whenever it is not the active tier`() {
        val orchestrator = ProviderOrchestrator(allowPlayFallback = true, budget)
        val onSystem =
            orchestrator.evaluate(0, mapOf(ProviderTier.SYSTEM_FUSED to goodFix(ProviderTier.SYSTEM_FUSED, 0)))
        assertTrue(onSystem.shouldWarmDirectGnss)

        val onGnss = orchestrator.evaluate(1, mapOf(ProviderTier.DIRECT_GNSS to goodFix(ProviderTier.DIRECT_GNSS, 1)))
        assertFalse(onGnss.shouldWarmDirectGnss)
    }
}
