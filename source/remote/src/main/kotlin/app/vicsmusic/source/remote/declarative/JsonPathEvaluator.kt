package app.vicsmusic.source.remote.declarative

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object JsonPathEvaluator {

    /**
     * Navigates a JsonElement using a simplified JSONPath expression,
     * e.g. "$.results", "$.artist.name", "$.channels[0].title".
     */
    fun evaluate(root: JsonElement, path: String): JsonElement? {
        val cleanPath = path.trim().removePrefix("$").removePrefix(".")
        if (cleanPath.isEmpty()) return root

        val tokens = tokenizePath(cleanPath)
        var current: JsonElement? = root

        for (token in tokens) {
            if (current == null) return null
            current = when {
                token.startsWith("[") && token.endsWith("]") -> {
                    val index = token.substring(1, token.length - 1).toIntOrNull() ?: return null
                    if (current is JsonArray && index in 0 until current.size) {
                        current[index]
                    } else null
                }
                current is JsonObject -> {
                    current[token]
                }
                else -> null
            }
        }
        return current
    }

    fun extractString(root: JsonElement, path: String): String? {
        val element = evaluate(root, path) ?: return null
        return when (element) {
            is JsonPrimitive -> element.contentOrNull
            else -> null
        }
    }

    fun extractLong(root: JsonElement, path: String): Long? {
        val element = evaluate(root, path) ?: return null
        return when (element) {
            is JsonPrimitive -> element.longOrNull ?: element.doubleOrNull?.toLong()
            else -> null
        }
    }

    fun extractBoolean(root: JsonElement, path: String): Boolean? {
        val element = evaluate(root, path) ?: return null
        return when (element) {
            is JsonPrimitive -> element.booleanOrNull
            else -> null
        }
    }

    fun extractList(root: JsonElement, path: String): List<JsonElement> {
        val element = evaluate(root, path) ?: return emptyList()
        return when (element) {
            is JsonArray -> element.toList()
            else -> emptyList()
        }
    }

    private fun tokenizePath(path: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        var i = 0
        while (i < path.length) {
            val char = path[i]
            when (char) {
                '.' -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                '[' -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                    val closeBracket = path.indexOf(']', i)
                    if (closeBracket != -1) {
                        tokens.add(path.substring(i, closeBracket + 1))
                        i = closeBracket
                    }
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }
}
