package com.stopaddict.core.crypto

/**
 * Sérialisation JSON canonique, miroir exact de `supabase/functions/_shared/canonical.ts` :
 * clés triées récursivement, pas d'espaces. Indispensable pour que l'app vérifie
 * la signature sur EXACTEMENT les mêmes octets que ceux signés par le serveur.
 *
 * Valeurs acceptées : null, String, Boolean, entiers (Int/Long), Map<String, *>,
 * List<*>. (Les payloads signés n'utilisent que ces types.)
 */
object CanonicalJson {

    fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Int, is Long -> value.toString()
        is Map<*, *> -> {
            val keys = value.keys.map { it as String }.sorted()
            keys.joinToString(separator = ",", prefix = "{", postfix = "}") { k ->
                quote(k) + ":" + stringify(value[k])
            }
        }
        is List<*> -> value.joinToString(separator = ",", prefix = "[", postfix = "]") { stringify(it) }
        else -> throw IllegalArgumentException("Type non supporté : ${value::class}")
    }

    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
