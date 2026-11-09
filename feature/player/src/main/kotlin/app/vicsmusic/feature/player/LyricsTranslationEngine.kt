package app.vicsmusic.feature.player

import app.vicsmusic.core.model.LyricLine
import app.vicsmusic.core.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class TranslationLanguage(val code: String, val displayName: String, val flag: String) {
    ORIGINAL("orig", "Original (sin traducir)", "🎵"),
    SPANISH("es", "Español", "🇪🇸"),
    ENGLISH("en", "Inglés (English)", "🇺🇸"),
    PORTUGUESE("pt", "Português", "🇧🇷"),
    FRENCH("fr", "Français", "🇫🇷"),
    GERMAN("de", "Deutsch", "🇩🇪"),
    ITALIAN("it", "Italiano", "🇮🇹"),
    TAGALOG("tl", "Tagalo (Filipino)", "🇵🇭"),
    TURKISH("tr", "Türkçe", "🇹🇷"),
    JAPANESE("ja", "Japonés (日本語)", "🇯🇵")
}

/**
 * Motor de traducción dinámica de letras en tiempo real.
 * Se apoya en el servicio global de Google Translate (gtx) para
 * traducir cualquier canción verso por verso preservando la sincronización temporal (timeMs).
 */
object LyricsTranslationEngine {

    private val translationCache = ConcurrentHashMap<String, Lyrics>()

    suspend fun translateLyrics(
        original: Lyrics,
        targetLanguage: TranslationLanguage,
        songTitle: String? = null,
        artistName: String? = null
    ): Lyrics = withContext(Dispatchers.IO) {
        if (targetLanguage == TranslationLanguage.ORIGINAL || original.lines.isEmpty()) {
            return@withContext original
        }

        val cacheKey = "${original.trackId}_${targetLanguage.code}"
        translationCache[cacheKey]?.let { return@withContext it }

        val translatedLines = try {
            fetchTranslation(original.lines, targetLanguage)
        } catch (e: Exception) {
            original.lines
        }

        val result = original.copy(lines = translatedLines)
        translationCache[cacheKey] = result
        result
    }

    private fun fetchTranslation(
        lines: List<LyricLine>,
        targetLanguage: TranslationLanguage
    ): List<LyricLine> {
        if (lines.isEmpty()) return lines

        // Procesar en bloques de 50 versos para no exceder límites de payload y mantener latencia baja (<300ms)
        return lines.chunked(50).flatMap { chunk ->
            translateChunk(chunk, targetLanguage)
        }
    }

    private fun translateChunk(
        chunk: List<LyricLine>,
        targetLanguage: TranslationLanguage
    ): List<LyricLine> {
        val fullText = chunk.joinToString("\n") { it.text.ifBlank { "..." } }
        val endpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${targetLanguage.code}&dt=t"
        val postData = "q=" + URLEncoder.encode(fullText, "UTF-8")

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 6000
            readTimeout = 8000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        }

        conn.outputStream.use { os ->
            OutputStreamWriter(os, "UTF-8").use { writer ->
                writer.write(postData)
                writer.flush()
            }
        }

        if (conn.responseCode != 200) {
            return chunk
        }

        val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val rootArray = JSONArray(responseText)
        val sentencesArray = rootArray.optJSONArray(0) ?: return chunk

        val translatedBuilder = StringBuilder()
        for (i in 0 until sentencesArray.length()) {
            val sentence = sentencesArray.optJSONArray(i)
            if (sentence != null) {
                translatedBuilder.append(sentence.optString(0, ""))
            }
        }

        val translatedLinesRaw = translatedBuilder.toString().split("\n")
        return chunk.mapIndexed { index, originalLine ->
            if (originalLine.text.isBlank()) {
                originalLine
            } else {
                val translatedLineText = translatedLinesRaw.getOrNull(index)?.trim()
                if (!translatedLineText.isNullOrBlank() && translatedLineText != "...") {
                    LyricLine(timeMs = originalLine.timeMs, text = translatedLineText)
                } else {
                    originalLine
                }
            }
        }
    }

    fun clearCache() {
        translationCache.clear()
    }
}
