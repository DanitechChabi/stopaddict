package com.stopaddict.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustedClockTest {

    private val anchor = ClockAnchor(
        serverNowUtcMillis = 1_700_000_000_000L,
        elapsedRealtimeAtSyncMillis = 10_000L,
    )

    @Test
    fun `avance du compteur monotone fait avancer le temps fiable`() {
        val t = TrustedClock.now(anchor, currentElapsedRealtimeMillis = 70_000L)
        assertEquals(TrustedTime.Known(1_700_000_000_000L + 60_000L), t)
    }

    @Test
    fun `compteur qui recule signifie redemarrage donc temps perime`() {
        val t = TrustedClock.now(anchor, currentElapsedRealtimeMillis = 5_000L)
        assertEquals(TrustedTime.Stale, t)
    }

    @Test
    fun `changement d'heure murale au-dela du seuil est detecte`() {
        val trusted = 1_700_000_000_000L
        assertTrue(TrustedClock.isWallClockTampered(trusted, trusted + 10 * 60 * 1000L))
        assertFalse(TrustedClock.isWallClockTampered(trusted, trusted + 60 * 1000L))
    }
}
