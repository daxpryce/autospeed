package io.pryce.android.autospeed.core.location

import io.pryce.android.autospeed.core.speed.SpeedState

/**
 * The result of evaluating one round of provider fixes: which tier is currently accepted (if
 * any), the resulting [SpeedState] to display, and which sources the Android adapter layer
 * should keep subscribed to. Only one tier's fix ever feeds [speedState]; concurrent warming of
 * other tiers never blends into the displayed value.
 */
data class OrchestratorResult(
    val acceptedTier: ProviderTier?,
    val speedState: SpeedState,
    val shouldWarmDirectGnss: Boolean,
    val shouldRequestPlay: Boolean,
)

/**
 * Applies the Autospeed privacy-first provider ladder (design section 2.2.1) to the latest known
 * fix from each subscribed tier.
 *
 * The preference order is System fused, then Play fused (only when [allowPlayFallback] is true),
 * then direct GNSS. Escalating away from a currently active tier that has stopped qualifying is
 * immediate. Recovering back to a more-preferred tier requires that tier to continuously qualify
 * for [ProviderQualityBudget.recoveryStableIntervalMillis] first, which prevents rapid flapping
 * between providers. A trustworthy fix from a less-preferred tier is always chosen over a
 * higher-preference tier's fix that lacks usable speed.
 *
 * This class is a deterministic, side-effect-free state machine: it performs no I/O and holds no
 * platform reference, so it is fully exercised by JVM unit tests. Callers (Android location
 * adapters) supply the current time and the latest fix, if any, from each tier on every update.
 */
class ProviderOrchestrator(
    private val allowPlayFallback: Boolean,
    private val budget: ProviderQualityBudget,
) {
    private val precedence: List<ProviderTier> =
        if (allowPlayFallback) {
            listOf(ProviderTier.SYSTEM_FUSED, ProviderTier.PLAY_FUSED, ProviderTier.DIRECT_GNSS)
        } else {
            listOf(ProviderTier.SYSTEM_FUSED, ProviderTier.DIRECT_GNSS)
        }

    private var activeTier: ProviderTier = precedence.first()
    private var recoveryCandidate: ProviderTier? = null
    private var recoveryCandidateSinceMillis: Long = 0L
    private var everAccepted: Boolean = false

    /**
     * Evaluates the latest fix, if any, known for each tier and returns the tier/state Autospeed
     * should show. [fixes] need not contain an entry for every tier; a missing or null entry is
     * treated the same as a tier with no current fix.
     */
    fun evaluate(
        nowMillis: Long,
        fixes: Map<ProviderTier, LocationFix?>,
    ): OrchestratorResult {
        val bestQualifying =
            precedence.firstNotNullOfOrNull { tier ->
                fixes[tier]?.takeIf { qualifies(tier, it, nowMillis) }?.let { tier to it }
            }
        if (bestQualifying != null) {
            everAccepted = true
        }

        val resolved = resolveActiveTier(bestQualifying, fixes, nowMillis)
        val speedState = speedStateFor(resolved, fixes, nowMillis)

        return OrchestratorResult(
            acceptedTier = resolved?.first,
            speedState = speedState,
            shouldWarmDirectGnss = activeTier != ProviderTier.DIRECT_GNSS,
            shouldRequestPlay = allowPlayFallback,
        )
    }

    /**
     * Determines which tier (if any) is accepted this round, returning it paired with its
     * already-known-qualifying fix so [speedStateFor] never needs to re-look up or re-validate
     * it. Every branch that returns a non-null pair only ever does so for a tier/fix combination
     * that [qualifies] confirmed this same round, so there is no path that can hand back a stale
     * or missing fix and no need for an unreachable defensive null check.
     */
    private fun resolveActiveTier(
        bestQualifying: Pair<ProviderTier, LocationFix>?,
        fixes: Map<ProviderTier, LocationFix?>,
        nowMillis: Long,
    ): Pair<ProviderTier, LocationFix>? {
        if (bestQualifying == null) {
            resetRecovery()
            return null
        }
        val (bestTier, _) = bestQualifying
        val moreQualifiedThanActive = precedence.indexOf(bestTier) < precedence.indexOf(activeTier)
        if (moreQualifiedThanActive) {
            // A more-preferred tier now qualifies. Escalating away from the current tier is
            // only ever immediate when the current tier has itself stopped qualifying; if it is
            // still qualifying, the switch to the more-preferred tier waits out the recovery
            // hysteresis window below to avoid flapping.
            val activeFix = fixes[activeTier]
            if (activeFix != null && qualifies(activeTier, activeFix, nowMillis)) {
                return applyRecoveryHysteresis(bestQualifying, activeTier to activeFix, nowMillis)
            }
        }
        // Either the best-qualifying tier is already active, it is not more preferred than the
        // active tier (meaning the active tier itself no longer qualifies), or the active tier's
        // own fix just failed while a recovery candidate was pending: every one of these cases
        // switches (a no-op if already active) immediately, never delayed by hysteresis.
        resetRecovery()
        activeTier = bestTier
        return bestQualifying
    }

    private fun applyRecoveryHysteresis(
        candidate: Pair<ProviderTier, LocationFix>,
        current: Pair<ProviderTier, LocationFix>,
        nowMillis: Long,
    ): Pair<ProviderTier, LocationFix> {
        if (recoveryCandidate != candidate.first) {
            recoveryCandidate = candidate.first
            recoveryCandidateSinceMillis = nowMillis
            return current
        }
        val elapsed = nowMillis - recoveryCandidateSinceMillis
        if (elapsed >= budget.recoveryStableIntervalMillis) {
            activeTier = candidate.first
            resetRecovery()
            return candidate
        }
        return current
    }

    private fun resetRecovery() {
        recoveryCandidate = null
        recoveryCandidateSinceMillis = 0L
    }

    private fun qualifies(
        tier: ProviderTier,
        fix: LocationFix,
        nowMillis: Long,
    ): Boolean {
        if (!fix.hasSpeed) return false
        if (nowMillis - fix.timestampMillis > budget.staleAfterMillis) return false
        if (fix.hasSpeedAccuracy && fix.speedAccuracyMetersPerSecond > budget.maxSpeedAccuracyMetersPerSecond) {
            return false
        }
        return tier == fix.tier
    }

    private fun speedStateFor(
        resolved: Pair<ProviderTier, LocationFix>?,
        fixes: Map<ProviderTier, LocationFix?>,
        nowMillis: Long,
    ): SpeedState {
        if (resolved != null) {
            val (tier, fix) = resolved
            return SpeedState.Available(fix.speedMetersPerSecond, tier)
        }
        if (!everAccepted) {
            return SpeedState.AwaitingFirstFix
        }
        val representative = fixes[activeTier]
        return when {
            representative == null -> SpeedState.Unavailable
            nowMillis - representative.timestampMillis > budget.staleAfterMillis -> SpeedState.Stale
            else -> SpeedState.Unavailable
        }
    }
}
