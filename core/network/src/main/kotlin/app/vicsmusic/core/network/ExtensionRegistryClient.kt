package app.vicsmusic.core.network

import app.vicsmusic.core.model.ExtensionRegistry
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.io.IOException

class ExtensionRegistryClient(
    private val httpClient: HttpClient = VicsHttpClientFactory.create()
) {
    companion object {
        const val DEFAULT_REGISTRY_URL = "https://raw.githubusercontent.com/mornyeapp/Mornye-Extension/main/registry.json"

        const val FALLBACK_REGISTRY_JSON = """
        {
          "version": 1,
          "extensions": [
            {
              "id": "ytmusic-spotiflac",
              "name": "ytmusic-spotiflac",
              "display_name": "YouTube Music",
              "version": "1.0.8",
              "description": "InnerTube streaming sin anuncios",
              "category": "streaming",
              "tags": ["youtube", "streaming", "lossless"]
            },
            {
              "id": "spotify-web",
              "name": "spotify-web",
              "display_name": "Spotify Web",
              "version": "1.10.6",
              "description": "Catálogo y listas de Spotify",
              "category": "integration",
              "tags": ["spotify", "metadata"]
            },
            {
              "id": "soundcloud",
              "name": "soundcloud",
              "display_name": "SoundCloud",
              "version": "1.0.14",
              "description": "Audio streams y remixes de SoundCloud",
              "category": "streaming",
              "tags": ["soundcloud", "aac"]
            },
            {
              "id": "apple-music",
              "name": "apple-music",
              "display_name": "Apple Music",
              "version": "1.0.2",
              "description": "Álbumes y tops globales de Apple Music",
              "category": "integration",
              "tags": ["apple", "charts"]
            },
            {
              "id": "deezer",
              "name": "deezer",
              "display_name": "Deezer",
              "version": "1.2.0",
              "description": "Streaming y preescuchas de Deezer",
              "category": "streaming",
              "tags": ["deezer", "hifi"]
            },
            {
              "id": "jiosaavn",
              "name": "jiosaavn",
              "display_name": "JioSaavn",
              "version": "1.0.5",
              "description": "Música internacional en alta fidelidad (320 kbps)",
              "category": "streaming",
              "tags": ["jiosaavn", "320kbps"]
            },
            {
              "id": "tidal-web",
              "name": "tidal-web",
              "display_name": "Tidal Web",
              "version": "1.0.0",
              "description": "Catálogo master de Tidal",
              "category": "streaming",
              "tags": ["tidal", "master"]
            }
          ]
        }
        """
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchRegistry(url: String = DEFAULT_REGISTRY_URL): Result<ExtensionRegistry> = runCatching {
        val response = try {
            httpClient.get(url)
        } catch (e: Exception) {
            return@runCatching parseRegistryJson(FALLBACK_REGISTRY_JSON)
        }

        if (!response.status.isSuccess()) {
            return@runCatching parseRegistryJson(FALLBACK_REGISTRY_JSON)
        }

        val text = response.bodyAsText()
        parseRegistryJson(text)
    }

    fun parseRegistryJson(jsonContent: String): ExtensionRegistry {
        return json.decodeFromString<ExtensionRegistry>(jsonContent)
    }
}
