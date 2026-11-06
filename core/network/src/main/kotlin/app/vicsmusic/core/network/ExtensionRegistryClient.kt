package app.vicsmusic.core.network

import app.vicsmusic.core.model.ExtensionRegistry
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.io.IOException

class ExtensionRegistryClient(
    private val httpClient: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchRegistry(url: String): Result<ExtensionRegistry> = runCatching {
        val response = try {
            httpClient.get(url)
        } catch (e: Exception) {
            throw IOException("Network error fetching registry from $url", e)
        }

        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value} when fetching registry")
        }

        val text = response.bodyAsText()
        parseRegistryJson(text)
    }

    fun parseRegistryJson(jsonContent: String): ExtensionRegistry {
        return json.decodeFromString<ExtensionRegistry>(jsonContent)
    }
}
