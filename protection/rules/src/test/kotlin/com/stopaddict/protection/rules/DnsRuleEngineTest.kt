package com.stopaddict.protection.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DnsRuleEngineTest {

    @Test
    fun `un domaine bloqué et ses sous-domaines sont bloqués`() {
        val e = DnsRuleEngine(blocklist = listOf("adulte.example"))
        assertIs<DnsDecision.Block>(e.decide("adulte.example"))
        assertIs<DnsDecision.Block>(e.decide("www.adulte.example"))
        assertIs<DnsDecision.Block>(e.decide("cdn.media.adulte.example"))
    }

    @Test
    fun `un domaine ressemblant mais distinct n'est pas bloqué`() {
        val e = DnsRuleEngine(blocklist = listOf("example.com"))
        assertEquals(DnsDecision.Allow, e.decide("notexample.com"))
        assertEquals(DnsDecision.Allow, e.decide("example.com.autre.net"))
    }

    @Test
    fun `l'autorisation prime sur le blocage`() {
        val e = DnsRuleEngine(
            blocklist = listOf("example.com"),
            allowlist = listOf("ok.example.com"),
        )
        assertIs<DnsDecision.Block>(e.decide("bad.example.com"))
        assertEquals(DnsDecision.Allow, e.decide("ok.example.com"))
        assertEquals(DnsDecision.Allow, e.decide("sub.ok.example.com"))
    }

    @Test
    fun `blocage par catégorie seulement si la catégorie est activée`() {
        val categorie = mapOf("pornography" to listOf("porn.example"))
        val actif = DnsRuleEngine(blockedCategories = setOf("pornography"), categoryDomains = categorie)
        val inactif = DnsRuleEngine(blockedCategories = emptySet(), categoryDomains = categorie)

        val d = actif.decide("video.porn.example")
        assertIs<DnsDecision.Block>(d)
        assertEquals("pornography", d.category)
        assertEquals(DnsDecision.Allow, inactif.decide("video.porn.example"))
    }

    @Test
    fun `normalisation casse, espaces et point final`() {
        val e = DnsRuleEngine(blocklist = listOf("Example.Com"))
        assertIs<DnsDecision.Block>(e.decide("  WWW.EXAMPLE.COM.  "))
    }

    @Test
    fun `domaine neutre passe`() {
        val e = DnsRuleEngine(blocklist = listOf("porn.example"))
        assertEquals(DnsDecision.Allow, e.decide("wikipedia.org"))
    }
}
