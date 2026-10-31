package com.stopaddict.core.data

import com.stopaddict.core.network.SupabaseClient
import com.stopaddict.core.network.SupabaseConfig

/**
 * Session en mémoire de l'utilisateur authentifié. Pour la v1, elle vit le temps
 * du processus ; la persistance chiffrée (DataStore + Keystore) viendra ensuite.
 */
class SessionStore {
    var accessToken: String? = null
        private set
    var userId: String? = null
        private set
    var role: String? = null
        private set

    val isAuthenticated: Boolean get() = accessToken != null

    fun set(token: String, userId: String?, role: String?) {
        this.accessToken = token
        this.userId = userId
        this.role = role
    }

    fun clear() {
        accessToken = null
        userId = null
        role = null
    }

    /** Jeton requis pour un appel authentifié, sinon erreur explicite. */
    fun requireToken(): String = accessToken ?: error("session_absente")
}

/** Conteneur de dépendances minimal, partagé par les deux applications. */
class AppContainer(config: SupabaseConfig) {
    val client: SupabaseClient = SupabaseClient(config)
    val session: SessionStore = SessionStore()
}
