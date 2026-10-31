package com.stopaddict.core.model

/**
 * Ancre temporelle fiable.
 *
 * On ne fait jamais confiance à l'horloge murale du téléphone (réglable par
 * l'utilisateur) pour mesurer l'avancement d'un engagement. On s'appuie plutôt
 * sur deux valeurs capturées lors de la dernière synchronisation réussie :
 *
 *  - [serverNowUtcMillis] : l'instant UTC donné par le serveur à ce moment-là ;
 *  - [elapsedRealtimeAtSyncMillis] : la valeur du compteur monotone de l'appareil
 *    (`SystemClock.elapsedRealtime()`) au même instant.
 *
 * Le compteur monotone ne recule jamais et n'est pas affecté par un changement
 * d'heure. Il est en revanche remis à zéro au redémarrage : si le compteur
 * courant est inférieur à celui de l'ancre, l'ancre est périmée et il faut
 * resynchroniser avant de pouvoir affirmer quoi que ce soit sur le temps.
 */
data class ClockAnchor(
    val serverNowUtcMillis: Long,
    val elapsedRealtimeAtSyncMillis: Long,
)

sealed interface TrustedTime {
    /** Temps UTC estimé de façon fiable. */
    data class Known(val utcMillis: Long) : TrustedTime
    /** L'ancre est inutilisable (redémarrage ou absence de sync) : resynchroniser. */
    data object Stale : TrustedTime
}

object TrustedClock {

    /** Au-delà de cet écart entre horloge murale et temps fiable, on soupçonne une manipulation. */
    const val CLOCK_TAMPER_THRESHOLD_MILLIS: Long = 5 * 60 * 1000L

    /**
     * Estime l'instant UTC courant à partir de l'ancre et du compteur monotone courant.
     * Renvoie [TrustedTime.Stale] si le compteur a reculé (redémarrage) : la durée
     * écoulée depuis la sync ne peut alors plus être calculée localement.
     */
    fun now(anchor: ClockAnchor, currentElapsedRealtimeMillis: Long): TrustedTime {
        val delta = currentElapsedRealtimeMillis - anchor.elapsedRealtimeAtSyncMillis
        if (delta < 0) return TrustedTime.Stale
        return TrustedTime.Known(anchor.serverNowUtcMillis + delta)
    }

    /**
     * Vrai si l'horloge murale diverge du temps fiable au-delà du seuil toléré.
     * Un résultat positif doit déclencher un `security_event` de type horloge.
     */
    fun isWallClockTampered(trustedUtcMillis: Long, wallClockUtcMillis: Long): Boolean =
        kotlin.math.abs(wallClockUtcMillis - trustedUtcMillis) > CLOCK_TAMPER_THRESHOLD_MILLIS
}
