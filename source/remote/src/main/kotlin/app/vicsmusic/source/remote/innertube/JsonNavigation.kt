package app.vicsmusic.source.remote.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Null-safe navigation helpers over InnerTube's deeply nested JSON.
 * Parsing against [JsonElement] instead of rigid DTOs keeps the client
 * tolerant to the frequent, unannounced schema changes of the API.
 */
internal fun JsonElement?.path(vararg keys: String): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) {
        current = (current as? JsonObject)?.get(key) ?: return null
    }
    return current
}

internal fun JsonElement?.obj(vararg keys: String): JsonObject? = path(*keys) as? JsonObject

internal fun JsonElement?.arr(vararg keys: String): JsonArray? = path(*keys) as? JsonArray

internal fun JsonElement?.str(vararg keys: String): String? =
    (path(*keys) as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Reads numeric values encoded either as JSON numbers or numeric strings. */
internal fun JsonElement?.long(vararg keys: String): Long? =
    (path(*keys) as? JsonPrimitive)?.content?.toLongOrNull()
