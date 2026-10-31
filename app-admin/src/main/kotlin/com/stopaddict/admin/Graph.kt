package com.stopaddict.admin

import com.stopaddict.core.data.AppContainer
import com.stopaddict.core.network.SupabaseConfig

/** Graphe de dépendances minimal de l'app Admin (DI manuel pour la v1). */
object Graph {
    lateinit var container: AppContainer
        private set

    fun init(url: String, anonKey: String) {
        if (!::container.isInitialized) {
            container = AppContainer(SupabaseConfig(url, anonKey))
        }
    }
}
