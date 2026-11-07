package app.vicsmusic.source.remote.declarative

import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

object TemplateEngine {

    /**
     * Resolves placeholders in the format `{{variable}}` using parameters map
     * and optional current JsonElement.
     */
    fun resolve(
        template: String,
        params: Map<String, String>,
        itemContext: JsonElement? = null,
        urlEncode: Boolean = false
    ): String {
        var result = template

        // 1. Resolve standard parameters (query, limit, offset, etc.)
        for ((key, value) in params) {
            val placeholder = "{{$key}}"
            if (result.contains(placeholder)) {
                val replacement = if (urlEncode) URLEncoder.encode(value, "UTF-8") else value
                result = result.replace(placeholder, replacement)
            }
        }

        // 2. Resolve JSONPath expressions in template, e.g. {{$.id}}
        if (itemContext != null && result.contains("{{$.")) {
            val regex = Regex("""\{\{(\$\.[^}]+)\}\}""")
            result = regex.replace(result) { matchResult ->
                val path = matchResult.groupValues[1]
                val extracted = JsonPathEvaluator.extractString(itemContext, path) ?: ""
                if (urlEncode) URLEncoder.encode(extracted, "UTF-8") else extracted
            }
        }

        return result
    }
}
