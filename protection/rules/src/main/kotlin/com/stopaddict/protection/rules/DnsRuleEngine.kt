package com.stopaddict.protection.rules

/**
 * Décision de filtrage pour une requête DNS. BLOCK porte la catégorie, pour les
 * compteurs AGRÉGÉS (jamais le domaine lui-même n'est transmis à l'administrateur).
 */
sealed interface DnsDecision {
    data object Allow : DnsDecision
    data class Block(val category: String) : DnsDecision
}

/**
 * Moteur de règles du filtrage DNS (le « cerveau » du VpnService, testable sans
 * appareil). Principes (voir section 6 du cahier des charges) :
 *
 *  1. La liste d'autorisation (allowlist) prime : un domaine autorisé passe toujours.
 *  2. Sinon, un domaine présent dans la liste de blocage est bloqué.
 *  3. Sinon, un domaine rattaché à une catégorie activée est bloqué.
 *  4. Sinon, il passe.
 *
 * Le rattachement se fait par SUFFIXE : une règle sur `example.com` couvre aussi
 * `www.example.com` et `a.b.example.com`, mais pas `notexample.com`.
 */
class DnsRuleEngine(
    blocklist: Collection<String> = emptyList(),
    allowlist: Collection<String> = emptyList(),
    private val blockedCategories: Set<String> = emptySet(),
    categoryDomains: Map<String, Collection<String>> = emptyMap(),
) {
    private val blockSet = blocklist.map(::normalize).toHashSet()
    private val allowSet = allowlist.map(::normalize).toHashSet()
    // domaine normalisé -> catégorie
    private val domainCategory: Map<String, String> =
        categoryDomains.entries
            .flatMap { (cat, domains) -> domains.map { normalize(it) to cat } }
            .toMap()

    fun decide(host: String): DnsDecision {
        val domain = normalize(host)
        if (domain.isEmpty()) return DnsDecision.Allow

        // 1. Autorisation explicite (prime sur tout).
        if (matchesSuffix(domain, allowSet)) return DnsDecision.Allow

        // 2. Blocage explicite.
        if (matchesSuffix(domain, blockSet)) return DnsDecision.Block("blocklist")

        // 3. Blocage par catégorie activée.
        val cat = suffixCategory(domain)
        if (cat != null && cat in blockedCategories) return DnsDecision.Block(cat)

        return DnsDecision.Allow
    }

    /** Vrai si `domain` ou l'un de ses domaines parents est dans `set`. */
    private fun matchesSuffix(domain: String, set: Set<String>): Boolean {
        for (candidate in parents(domain)) {
            if (candidate in set) return true
        }
        return false
    }

    private fun suffixCategory(domain: String): String? {
        for (candidate in parents(domain)) {
            domainCategory[candidate]?.let { return it }
        }
        return null
    }

    /** `a.b.example.com` -> [a.b.example.com, b.example.com, example.com, com]. */
    private fun parents(domain: String): Sequence<String> = sequence {
        var current = domain
        while (true) {
            yield(current)
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
        }
    }

    private fun normalize(host: String): String =
        host.trim().lowercase().removeSuffix(".")
}
