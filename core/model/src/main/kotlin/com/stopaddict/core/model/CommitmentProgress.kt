package com.stopaddict.core.model

/**
 * Avancement d'un engagement, calculé uniquement à partir d'un temps fiable
 * ([TrustedTime.Known]). Tant que le temps est [TrustedTime.Stale], aucune
 * affirmation sur les jours restants n'est faite : l'interface doit alors
 * afficher « synchronisation en attente », jamais un faux décompte.
 */
sealed interface CommitmentProgress {
    data object Unknown : CommitmentProgress
    data object NotActivated : CommitmentProgress
    data class Running(
        val elapsedMillis: Long,
        val remainingMillis: Long,
        val totalMillis: Long,
    ) : CommitmentProgress {
        /** Fraction écoulée dans [0.0, 1.0]. */
        val fraction: Double
            get() = if (totalMillis <= 0) 0.0 else (elapsedMillis.toDouble() / totalMillis).coerceIn(0.0, 1.0)

        /** Jours entiers restants, arrondis au supérieur (un reste de 2 h compte comme 1 jour). */
        val remainingDays: Int
            get() = Math.ceil(remainingMillis.toDouble() / MILLIS_PER_DAY).toInt()
    }
    data object Completed : CommitmentProgress

    companion object {
        const val MILLIS_PER_DAY: Long = 24 * 60 * 60 * 1000L

        fun of(commitment: Commitment, trusted: TrustedTime): CommitmentProgress {
            if (commitment.status != CommitmentStatus.ACTIVE) return NotActivated
            val start = commitment.startAtUtcMillis ?: return NotActivated
            val end = commitment.endAtUtcMillis ?: return NotActivated
            val nowUtc = when (trusted) {
                is TrustedTime.Known -> trusted.utcMillis
                TrustedTime.Stale -> return Unknown
            }
            if (nowUtc >= end) return Completed
            val elapsed = (nowUtc - start).coerceAtLeast(0)
            return Running(
                elapsedMillis = elapsed,
                remainingMillis = end - nowUtc,
                totalMillis = end - start,
            )
        }
    }
}
