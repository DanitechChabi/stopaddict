package com.stopaddict.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommitmentProgressTest {

    private val day = CommitmentProgress.MILLIS_PER_DAY

    private fun active(start: Long, end: Long) = Commitment(
        id = "c1", deviceId = "d1", adminId = "a1", userId = "u1",
        durationDays = ((end - start) / day).toInt(),
        status = CommitmentStatus.ACTIVE,
        startAtUtcMillis = start, endAtUtcMillis = end,
        userConsentAtUtcMillis = start, adminConfirmationAtUtcMillis = start,
        signaturePresent = true,
    )

    @Test
    fun `temps perime donne un avancement inconnu, jamais un faux decompte`() {
        val c = active(0, 90 * day)
        assertIs<CommitmentProgress.Unknown>(CommitmentProgress.of(c, TrustedTime.Stale))
    }

    @Test
    fun `jours restants arrondis au superieur`() {
        val c = active(0, 90 * day)
        val p = CommitmentProgress.of(c, TrustedTime.Known(23 * day))
        assertIs<CommitmentProgress.Running>(p)
        assertEquals(67, p.remainingDays)
    }

    @Test
    fun `depassement de la fin donne Completed`() {
        val c = active(0, 90 * day)
        assertIs<CommitmentProgress.Completed>(CommitmentProgress.of(c, TrustedTime.Known(91 * day)))
    }

    @Test
    fun `engagement non actif n'est pas en cours`() {
        val c = active(0, 90 * day).copy(status = CommitmentStatus.PENDING_CONSENT)
        assertIs<CommitmentProgress.NotActivated>(CommitmentProgress.of(c, TrustedTime.Known(day)))
    }
}
