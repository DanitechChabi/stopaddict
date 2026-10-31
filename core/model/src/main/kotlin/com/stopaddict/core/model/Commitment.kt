package com.stopaddict.core.model

/** État d'un engagement, aligné sur l'enum PostgreSQL `commitment_status`. */
enum class CommitmentStatus {
    PENDING_CONSENT,
    ACTIVE,
    COMPLETED,
    RELEASED_EARLY,
    CANCELLED,
}

/** Mode de gestion de l'appareil. Le mode renforcé exige le provisionnement Device Owner. */
enum class ManagementMode { STANDARD, DEVICE_OWNER }

/**
 * Engagement tel que renvoyé par le serveur. Les instants sont des epochs UTC
 * en millisecondes : la référence de temps est le serveur, jamais l'horloge locale.
 *
 * @property startAtUtcMillis début, null tant que l'engagement n'est pas activé.
 * @property endAtUtcMillis fin, null tant que l'engagement n'est pas activé.
 * @property signaturePresent vrai si le serveur a joint une signature Ed25519 vérifiable.
 */
data class Commitment(
    val id: String,
    val deviceId: String,
    val adminId: String,
    val userId: String,
    val durationDays: Int,
    val status: CommitmentStatus,
    val startAtUtcMillis: Long?,
    val endAtUtcMillis: Long?,
    val userConsentAtUtcMillis: Long?,
    val adminConfirmationAtUtcMillis: Long?,
    val signaturePresent: Boolean,
) {
    init {
        require(durationDays in 1..366) { "durationDays hors bornes : $durationDays" }
        if (startAtUtcMillis != null && endAtUtcMillis != null) {
            require(endAtUtcMillis > startAtUtcMillis) { "endAt doit suivre startAt" }
        }
    }

    /** Un engagement n'est activable que si les deux parties ont consenti. */
    val hasDualConsent: Boolean
        get() = userConsentAtUtcMillis != null && adminConfirmationAtUtcMillis != null
}
