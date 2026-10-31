package com.stopaddict.app

import com.stopaddict.core.data.AppContainer
import com.stopaddict.core.network.SupabaseConfig

/** Graphe de dépendances minimal de l'app utilisateur (DI manuel pour la v1). */
object Graph {
    lateinit var container: AppContainer
        private set

    /** Clé publique de vérification des signatures (vide si non configurée). */
    var signingPublicKey: String = ""
        private set

    fun init(url: String, anonKey: String, signingPublicKey: String) {
        this.signingPublicKey = signingPublicKey
        if (!::container.isInitialized) {
            container = AppContainer(SupabaseConfig(url, anonKey))
        }
    }
}
