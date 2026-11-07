package app.vicsmusic.source.remote.declarative

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclarativeExtensionTest {

    private val sampleJson = """
    {
      "results": [
        {
          "id": "trk_101",
          "name": "Canción de Prueba",
          "artist_name": "Artista Indie",
          "album_name": "Álbum Indie",
          "duration": 210,
          "image": "https://example.com/cover.jpg",
          "audio": "https://example.com/stream.mp3"
        }
      ],
      "channels": [
        {
          "id": "defcon",
          "title": "DEF CON Radio",
          "genre": "Ambient / IDM"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun jsonPathEvaluator_extractsSimpleAndNestedFields() {
        val root = Json.parseToJsonElement(sampleJson)

        val resultsList = JsonPathEvaluator.extractList(root, "$.results")
        assertEquals(1, resultsList.size)

        val songName = JsonPathEvaluator.extractString(root, "$.results[0].name")
        assertEquals("Canción de Prueba", songName)

        val duration = JsonPathEvaluator.extractLong(root, "$.results[0].duration")
        assertEquals(210L, duration)

        val channelGenre = JsonPathEvaluator.extractString(root, "$.channels[0].genre")
        assertEquals("Ambient / IDM", channelGenre)

        val nonExistent = JsonPathEvaluator.extractString(root, "$.unknown.path")
        assertNull(nonExistent)
    }

    @Test
    fun templateEngine_resolvesParametersAndJsonPathExpressions() {
        val root = Json.parseToJsonElement(sampleJson)
        val channelItem = JsonPathEvaluator.evaluate(root, "$.channels[0]")!!

        val params = mapOf(
            "query" to "cumbia peruana",
            "limit" to "20",
            "appVersion" to "1.0.0"
        )

        val template1 = "https://api.example.com/search?q={{query}}&size={{limit}}"
        val resolved1 = TemplateEngine.resolve(template1, params, urlEncode = true)
        assertEquals("https://api.example.com/search?q=cumbia+peruana&size=20", resolved1)

        val template2 = "https://ice1.somafm.com/{{$.id}}-128-mp3"
        val resolved2 = TemplateEngine.resolve(template2, emptyMap(), itemContext = channelItem)
        assertEquals("https://ice1.somafm.com/defcon-128-mp3", resolved2)
    }
}
