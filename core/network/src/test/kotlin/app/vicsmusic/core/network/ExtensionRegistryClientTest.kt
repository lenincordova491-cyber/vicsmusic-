package app.vicsmusic.core.network

import io.ktor.client.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionRegistryClientTest {

    private val sampleRegistryJson = """
    {
      "version": 1,
      "updated_at": "2026-11-05T00:00:00Z",
      "extensions": [
        {
          "id": "spotify-web",
          "name": "spotify-web",
          "display_name": "Spotify Web",
          "version": "1.10.6",
          "description": "Spotify metadata provider for Mornye",
          "download_url": "https://example.com/spotify.sflx",
          "category": "integration",
          "tags": ["spotify", "web", "streaming"],
          "downloads": 100,
          "min_app_version": "4.3.0"
        },
        {
          "id": "soundcloud",
          "name": "soundcloud",
          "display_name": "SoundCloud",
          "version": "1.0.14",
          "description": "SoundCloud audio streams",
          "category": "download",
          "tags": ["soundcloud", "aac"]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun parseRegistryJsonExtractsExtensionsProperly() {
        val client = ExtensionRegistryClient(HttpClient())
        val registry = client.parseRegistryJson(sampleRegistryJson)

        assertEquals(1, registry.version)
        assertEquals("2026-11-05T00:00:00Z", registry.updatedAt)
        assertEquals(2, registry.extensions.size)

        val spotify = registry.extensions[0]
        assertEquals("spotify-web", spotify.id)
        assertEquals("Spotify Web", spotify.displayName)
        assertEquals("1.10.6", spotify.version)
        assertEquals("integration", spotify.category)
        assertEquals(3, spotify.tags.size)

        val soundcloud = registry.extensions[1]
        assertEquals("soundcloud", soundcloud.id)
        assertEquals("SoundCloud", soundcloud.displayName)
        assertEquals("download", soundcloud.category)
    }
}
