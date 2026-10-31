package com.stopaddict.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectionSnapshotTest {

    @Test
    fun `tout actif et sync fraiche donne protege`() {
        val s = ProtectionSnapshot(
            vpnDns = ComponentState.ACTIVE,
            imageAi = ComponentState.DISABLED_BY_POLICY,
            videoAi = ComponentState.DISABLED_BY_POLICY,
            textAi = ComponentState.DISABLED_BY_POLICY,
            syncIsFresh = true,
        )
        assertEquals(OverallProtection.PROTECTED, s.overall())
    }

    @Test
    fun `un composant requis en echec interdit le statut protege`() {
        val s = ProtectionSnapshot(
            vpnDns = ComponentState.FAILED,
            imageAi = ComponentState.DISABLED_BY_POLICY,
            videoAi = ComponentState.DISABLED_BY_POLICY,
            textAi = ComponentState.DISABLED_BY_POLICY,
            syncIsFresh = true,
        )
        assertEquals(OverallProtection.NOT_PROTECTED, s.overall())
    }

    @Test
    fun `permission manquante degrade le statut`() {
        val s = ProtectionSnapshot(
            vpnDns = ComponentState.ACTIVE,
            imageAi = ComponentState.PERMISSION_MISSING,
            videoAi = ComponentState.DISABLED_BY_POLICY,
            textAi = ComponentState.DISABLED_BY_POLICY,
            syncIsFresh = true,
        )
        assertEquals(OverallProtection.DEGRADED, s.overall())
    }

    @Test
    fun `sync perimee met le statut en attente meme si tout tourne`() {
        val s = ProtectionSnapshot(
            vpnDns = ComponentState.ACTIVE,
            imageAi = ComponentState.ACTIVE,
            videoAi = ComponentState.DISABLED_BY_POLICY,
            textAi = ComponentState.DISABLED_BY_POLICY,
            syncIsFresh = false,
        )
        assertEquals(OverallProtection.SYNC_PENDING, s.overall())
    }
}
