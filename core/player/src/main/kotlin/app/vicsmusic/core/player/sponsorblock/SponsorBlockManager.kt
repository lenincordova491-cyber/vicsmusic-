package app.vicsmusic.core.player.sponsorblock

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SkipSegment(
    val startMs: Long,
    val endMs: Long
)

/**
 * Gestor de SponsorBlock para saltar silencios, intros habladas y sketches (music_offtopic).
 * Consulta la API pública y gratuita de https://sponsor.ajay.app/.
 */
@Singleton
class SponsorBlockManager @Inject constructor() {

    private val httpClient: HttpClient by lazy {
        app.vicsmusic.core.network.VicsHttpClientFactory.create()
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = ConcurrentHashMap<String, List<SkipSegment>>()

    suspend fun getMusicOfftopicSegments(videoId: String): List<SkipSegment> {
        val cleanId = videoId.trim()
        if (cleanId.isEmpty()) return emptyList()

        cache[cleanId]?.let { return it }

        return runCatching {
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$cleanId&categories=[\"music_offtopic\"]"
            val response = httpClient.get(url)
            if (!response.status.isSuccess()) {
                cache[cleanId] = emptyList()
                return emptyList()
            }
            val text = response.bodyAsText()
            val root = json.parseToJsonElement(text).jsonArray
            val segments = mutableListOf<SkipSegment>()

            for (elem in root) {
                val obj = elem.jsonObject
                val category = obj["category"]?.jsonPrimitive?.content
                if (category == "music_offtopic") {
                    val segArr = obj["segment"]?.jsonArray
                    if (segArr != null && segArr.size >= 2) {
                        val startSec = segArr[0].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                        val endSec = segArr[1].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                        if (endSec > startSec) {
                            segments.add(
                                SkipSegment(
                                    startMs = (startSec * 1000.0).toLong(),
                                    endMs = (endSec * 1000.0).toLong()
                                )
                            )
                        }
                    }
                }
            }
            cache[cleanId] = segments
            segments
        }.getOrDefault(emptyList()).also {
            cache[cleanId] = it
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
