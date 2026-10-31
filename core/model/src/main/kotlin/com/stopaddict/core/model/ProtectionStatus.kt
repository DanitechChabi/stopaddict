package com.stopaddict.core.model

/**
 * État réel d'un composant de protection. Reflète ce qui fonctionne vraiment
 * sur l'appareil, jamais une intention. Un composant désactivé dans la
 * politique est [DISABLED_BY_POLICY] ; un composant censé tourner mais arrêté
 * est [FAILED] — ce qui doit empêcher l'affichage d'un statut global « actif ».
 */
enum class ComponentState {
    ACTIVE,
    DISABLED_BY_POLICY,
    PERMISSION_MISSING,
    FAILED,
}

/**
 * Statut global dérivé de l'état réel des composants et de la synchronisation.
 * Règle absolue : ne jamais afficher PROTECTED si un composant requis par la
 * politique n'est pas ACTIVE.
 */
enum class OverallProtection {
    PROTECTED,          // tous les composants requis sont actifs et la sync est fraîche
    DEGRADED,           // au moins un composant requis manque une permission
    NOT_PROTECTED,      // au moins un composant requis a échoué
    SYNC_PENDING,       // le temps n'est pas fiable / sync périmée
}

data class ProtectionSnapshot(
    val vpnDns: ComponentState,
    val imageAi: ComponentState,
    val videoAi: ComponentState,
    val textAi: ComponentState,
    val syncIsFresh: Boolean,
) {
    /**
     * Un composant « requis » est un composant que la politique active
     * (état différent de DISABLED_BY_POLICY). Le calcul est volontairement
     * pessimiste : la moindre défaillance dégrade le statut global.
     */
    fun overall(): OverallProtection {
        val required = listOf(vpnDns, imageAi, videoAi, textAi)
            .filter { it != ComponentState.DISABLED_BY_POLICY }

        if (required.any { it == ComponentState.FAILED }) return OverallProtection.NOT_PROTECTED
        if (required.any { it == ComponentState.PERMISSION_MISSING }) return OverallProtection.DEGRADED
        if (!syncIsFresh) return OverallProtection.SYNC_PENDING
        return OverallProtection.PROTECTED
    }
}
