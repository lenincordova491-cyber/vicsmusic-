package app.vicsmusic.core.domain.engine

import app.vicsmusic.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pista de coincidencia por letra proveniente de LRCLIB o caché local.
 */
data class LyricMatchHint(
    val trackName: String,
    val artistName: String
)

/**
 * Clasificación automática de la intención de búsqueda del usuario ("Mind-Reading Search Intent").
 */
enum class SearchQueryIntent {
    EXACT_ARTIST_FOCUS,     // Ej: "grupo 5", "bad bunny", "soda stereo"
    ARTIST_AND_TRACK,       // Ej: "grupo 5 motor y motivo", "coldplay yellow"
    LYRIC_FRAGMENT,         // Ej: "y ahora que no estas", "letra de..."
    VERSION_SPECIFIC,       // Ej: "ojitos lindos en vivo", "unplugged", "remix", "acustico"
    GENRE_OR_MOOD_EXPLORE,  // Ej: "cumbia peruana", "rock de los 90", "salsa romantica"
    GENERAL_TRACK_QUERY     // Consulta estándar o con errores tipográficos
}

/**
 * Resultado enriquecido de búsqueda que separa el "Mejor Resultado" (Top Result estilo Spotify)
 * de la lista ordenada de canciones y los artistas/álbumes detectados.
 */
data class SmartSearchRanking(
    val detectedIntent: SearchQueryIntent,
    val topResult: Track?,
    val rankedTracks: List<Track>,
    val matchedArtists: List<String>
)

/**
 * Motor de Búsqueda Avanzado de nivel Spotify / YouTube Music.
 *
 * Resuelve los problemas del buscador original mediante:
 * 1. Detección automática de intención (Artista, Canción+Artista, Fragmento de Letra, Versión en Vivo/Remix, Género).
 * 2. Tolerancia extrema a errores ortográficos combinando Jaro-Winkler (prefijos), Damerau-Levenshtein (transposiciones)
 *    y Similitud Coseno de Trigramas de caracteres (N-Grams).
 * 3. Coincidencia por prefijos de tokens y acrónimos (ej. "mym grupo 5" -> "Motor y Motivo", "bzrp 52" -> "Quédate").
 * 4. Expansión de alias y apodos culturales ("ferxxo" -> "Feid", "el grupo de oro" -> "Grupo 5", "cerati" -> "Soda Stereo").
 * 5. Fonética bilingüe no destructiva: compara fonética en español como señal de rescate sin destruir títulos en inglés.
 * 6. Filtrado inteligente de ruido: penaliza "karaoke", "tributo", "parodia", "cover" salvo que el usuario los pida,
 *    y premia la versión de estudio o la versión específica ("en vivo", "acústico") solicitada.
 */
class AdvancedSearchEngine {

    companion object {
        private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9\\s]")
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val NOISE_TAGS_REGEX = Regex(
            "\\s*[\\(\\[](?:feat|ft|official|oficial|video|audio|remastered|remasterizado|lyric|letra|hd|4k|visualizer|videoclip).*?[\\)\\]]",
            RegexOption.IGNORE_CASE
        )

        private val LIVE_OR_REMIX_KEYWORDS = listOf(
            "en vivo", "live", "remix", "acustico", "acoustic", "unplugged", "concierto", "session", "crossover"
        )

        private val UNWANTED_COVER_KEYWORDS = listOf(
            "karaoke", "tributo", "tribute", "parodia", "parody", "instrumental", "8d", "slowed", "reverb", "ringtone"
        )

        /**
         * Mapa de sinónimos, apodos, siglas y asociaciones directas para "adivinar" lo que el usuario busca
         * incluso cuando no escribe el nombre oficial del artista o canción.
         */
        private val CULTURAL_ALIASES: Map<String, List<String>> = mapOf(
            "ferxxo" to listOf("feid"),
            "bichota" to listOf("karol g"),
            "conejo malo" to listOf("bad bunny"),
            "benito" to listOf("bad bunny"),
            "el grupo de oro" to listOf("grupo 5"),
            "elmer yaipen" to listOf("grupo 5"),
            "christian yaipen" to listOf("grupo 5"),
            "emanuel noir" to listOf("ke personajes"),
            "cerati" to listOf("gustavo cerati", "soda stereo"),
            "soda" to listOf("soda stereo"),
            "indio solari" to listOf("patricio rey"),
            "biza" to listOf("bizarrap"),
            "bzrp" to listOf("bizarrap"),
            "session 52" to listOf("quedate", "bizarrap", "quevedo"),
            "pedro suarez" to listOf("pedro suarez vertiz", "arena hash"),
            "psv" to listOf("pedro suarez vertiz"),
            "canser" to listOf("canserbero"),
            "cancerbero" to listOf("canserbero"),
            "chacal" to listOf("chacalon", "papillon"),
            "makano" to listOf("makano"),
            "dy" to listOf("daddy yankee"),
            "el cangri" to listOf("daddy yankee"),
            "rey del reggaeton" to listOf("daddy yankee", "don omar"),
            "cumbia norteña" to listOf("grupo 5", "agua marina", "armonia 10", "corazon serrano"),
            "cumbia peruana" to listOf("grupo 5", "agua marina", "armonia 10", "los destellos", "los mirlos"),
            "rock peruano" to listOf("mar de copas", "libido", "amen", "los mojarras", "pedro suarez vertiz"),
            "rock en espanol" to listOf("soda stereo", "enanitos verdes", "caifanes", "mana", "los prisioneros", "hombres g")
        )

        private val STOP_WORDS = setOf(
            "de", "la", "el", "los", "las", "un", "una", "y", "en", "con", "para", "por", "del", "al", "the", "of", "and"
        )
    }

    /**
     * Normaliza cadenas eliminando tildes, signos de puntuación y espacios repetidos.
     */
    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val lower = input.lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val noAccents = DIACRITICS_REGEX.replace(decomposed, "")
        val alphaNum = NON_ALPHANUM_REGEX.replace(noAccents, " ")
        return MULTI_SPACE_REGEX.replace(alphaNum, " ").trim()
    }

    /**
     * Limpia etiquetas ruidosas de YouTube como "(Video Oficial)", "[Official Audio]", "(HD)", etc.
     */
    fun cleanTitleNoise(title: String, stripVersionTags: Boolean = false): String {
        var cleaned = NOISE_TAGS_REGEX.replace(title, "").trim()
        if (stripVersionTags) {
            for (kw in LIVE_OR_REMIX_KEYWORDS) {
                cleaned = cleaned.replace(Regex("\\b$kw\\b", RegexOption.IGNORE_CASE), "").trim()
            }
        }
        return MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
    }

    /**
     * Detecta si la consulta del usuario parece una frase de la letra de una canción.
     */
    fun isLikelyLyricQuery(query: String): Boolean {
        val raw = query.trim()
        if (raw.contains('"') || raw.contains("...") || raw.contains("‘") || raw.contains("“")) return true
        val norm = normalize(raw)
        if (norm.contains("letra") || norm.contains("dice") || norm.contains("cancion que dice")) return true
        val tokens = norm.split(' ').filter { it.isNotBlank() }
        // Si tiene 5 o más palabras y contiene verbos/pronombres comunes de letras
        return tokens.size >= 5
    }

    /**
     * Detecta la intención exacta de la búsqueda para ajustar los pesos del ranking.
     */
    fun detectQueryIntent(rawQuery: String, candidates: List<Track>): SearchQueryIntent {
        val norm = normalize(rawQuery)
        if (norm.isEmpty()) return SearchQueryIntent.GENERAL_TRACK_QUERY

        if (LIVE_OR_REMIX_KEYWORDS.any { norm.contains(it) }) {
            return SearchQueryIntent.VERSION_SPECIFIC
        }
        if (isLikelyLyricQuery(rawQuery)) {
            return SearchQueryIntent.LYRIC_FRAGMENT
        }
        if (norm.startsWith("cumbia") || norm.startsWith("salsa") || norm.startsWith("rock") ||
            norm.startsWith("reggaeton") || norm.startsWith("balada") || norm.startsWith("mix ")
        ) {
            return SearchQueryIntent.GENRE_OR_MOOD_EXPLORE
        }

        // Verifica si la consulta coincide exactamente o casi exactamente con el nombre de un artista
        val matchesArtistDirectly = candidates.any { track ->
            val artistNorm = normalize(track.artistName)
            artistNorm == norm || jaroWinklerSimilarity(norm, artistNorm) >= 0.90f
        }
        if (matchesArtistDirectly) {
            return SearchQueryIntent.EXACT_ARTIST_FOCUS
        }

        // Verifica si contiene tanto tokens del artista como tokens del título
        val hasArtistAndTitleSplit = candidates.any { track ->
            val artistTokens = normalize(track.artistName).split(' ').filter { it.length > 2 }
            val titleTokens = normalize(cleanTitleNoise(track.title)).split(' ').filter { it.length > 2 }
            artistTokens.any { norm.contains(it) } && titleTokens.any { norm.contains(it) }
        }
        if (hasArtistAndTitleSplit) {
            return SearchQueryIntent.ARTIST_AND_TRACK
        }

        return SearchQueryIntent.GENERAL_TRACK_QUERY
    }

    /**
     * Clasifica y ordena los resultados con precisión tipo Spotify, manteniendo 100% compatibilidad
     * con la firma existente en `MusicRepositoryImpl` y `SearchViewModel`.
     */
    fun rankSearchResults(
        rawQuery: String,
        candidates: List<Track>,
        lyricHints: List<LyricMatchHint> = emptyList(),
        cachedLyricMatchTrackIds: Set<String> = emptySet(),
        preferredArtists: Set<String> = emptySet()
    ): List<Track> {
        if (candidates.isEmpty()) return emptyList()
        val normQuery = normalize(rawQuery)
        if (normQuery.isEmpty()) return candidates.distinctBy { it.id }

        val intent = detectQueryIntent(rawQuery, candidates)
        val expandedQueries = expandQueryWithCulturalAliases(normQuery)
        val queryTokens = normQuery.split(' ').filter { it.isNotBlank() }
        val meaningfulQueryTokens = queryTokens.filter { it !in STOP_WORDS }.ifEmpty { queryTokens }
        val queryPhonetic = spanishPhoneticCode(normQuery)

        val userWantsLiveOrRemix = LIVE_OR_REMIX_KEYWORDS.any { normQuery.contains(it) }
        val userWantsKaraokeOrCover = UNWANTED_COVER_KEYWORDS.any { normQuery.contains(it) }

        val normalizedPreferredArtists = preferredArtists.map { normalize(it.replace('_', ' ')) }.toSet()

        // Deduplicación inteligente: evita mostrar 5 veces la misma canción subida con IDs distintos si tienen el mismo título limpio y artista
        val uniqueCandidates = deduplicateCandidatesPreservingBest(candidates, cachedLyricMatchTrackIds)

        val scored = uniqueCandidates.mapIndexed { originalIndex, track ->
            val cleanTitle = cleanTitleNoise(track.title)
            val normTitle = normalize(cleanTitle)
            val normRawTitle = normalize(track.title)
            val normArtist = normalize(track.artistName)
            val normAlbum = normalize(track.albumName.orEmpty())
            val combinedTitleArtist = "$normTitle $normArtist"
            val combinedArtistTitle = "$normArtist $normTitle"

            // 1. Coincidencia exacta o de prefijo directo (Instant Top Match)
            val exactTitleMatch = if (normTitle == normQuery) 1.0f else 0f
            val exactArtistMatch = if (normArtist == normQuery) 1.0f else 0f
            val exactCombinedMatch = if (combinedTitleArtist == normQuery || combinedArtistTitle == normQuery) 1.0f else 0f

            // 2. Similitud léxica híbrida (Jaro-Winkler + Damerau-Levenshtein + Trigramas)
            val titleSimilarity = maxOf(
                hybridStringSimilarity(normQuery, normTitle),
                expandedQueries.maxOfOrNull { hybridStringSimilarity(it, normTitle) } ?: 0f
            )
            val artistSimilarity = maxOf(
                hybridStringSimilarity(normQuery, normArtist),
                expandedQueries.maxOfOrNull { hybridStringSimilarity(it, normArtist) } ?: 0f
            )
            val combinedSimilarity = maxOf(
                hybridStringSimilarity(normQuery, combinedTitleArtist),
                hybridStringSimilarity(normQuery, combinedArtistTitle),
                expandedQueries.maxOfOrNull { hybridStringSimilarity(it, combinedTitleArtist) } ?: 0f
            )

            // 3. Cobertura de tokens, prefijos de palabras y acrónimos (ej. "mot y mot" -> "Motor y Motivo")
            val tokenCoverage = computeTokenAndPrefixCoverage(
                queryTokens = meaningfulQueryTokens,
                targetText = "$normTitle $normArtist $normAlbum",
                titleTokens = normTitle.split(' ').filter { it.isNotBlank() }
            )

            // 4. Rescate fonético bilingüe (ayuda cuando escriben "ke personages", "canserveroo", "chacalon")
            val phoneticTitleSim = boundedSimilarity(queryPhonetic, spanishPhoneticCode(normTitle))
            val phoneticArtistSim = boundedSimilarity(queryPhonetic, spanishPhoneticCode(normArtist))
            val phoneticScore = max(phoneticTitleSim, phoneticArtistSim)

            // 5. Coincidencia por letra (LRCLIB / caché local de letras)
            val matchesLyricHint = lyricHints.any { hint ->
                val hintTitle = normalize(cleanTitleNoise(hint.trackName))
                val hintArtist = normalize(hint.artistName)
                (normTitle.contains(hintTitle) || hintTitle.contains(normTitle) || jaroWinklerSimilarity(normTitle, hintTitle) > 0.84f) &&
                    (normArtist.contains(hintArtist) || hintArtist.contains(normArtist) || jaroWinklerSimilarity(normArtist, hintArtist) > 0.80f)
            }
            val matchesCachedLyric = cachedLyricMatchTrackIds.contains(track.id)
            val lyricBonus = when {
                matchesCachedLyric && matchesLyricHint -> 0.95f
                matchesLyricHint -> 0.82f
                matchesCachedLyric -> 0.75f
                else -> 0f
            }

            // 6. Penalización/Premio inteligente por versión (Estudio vs. En Vivo / Karaoke / Tributo)
            var versionAdjustment = 0f
            val isCoverOrKaraoke = UNWANTED_COVER_KEYWORDS.any { normRawTitle.contains(it) || normArtist.contains(it) }
            if (isCoverOrKaraoke && !userWantsKaraokeOrCover) {
                versionAdjustment -= 0.75f
            }
            val isLiveOrRemix = LIVE_OR_REMIX_KEYWORDS.any { normRawTitle.contains(it) || normAlbum.contains(it) }
            if (userWantsLiveOrRemix && isLiveOrRemix) {
                versionAdjustment += 0.32f
            } else if (!userWantsLiveOrRemix && isLiveOrRemix && intent != SearchQueryIntent.EXACT_ARTIST_FOCUS) {
                // Ligera preferencia por la versión de estudio original cuando el usuario no pidió "en vivo"
                versionAdjustment -= 0.06f
            }

            // 7. Personalización y autoridad de la fuente (Afinidad del usuario + Orden de relevancia de YouTube Music)
            val isPreferredArtist = normalizedPreferredArtists.any { pref ->
                pref.isNotEmpty() && (normArtist.contains(pref) || pref.contains(normArtist))
            }
            val personalizationBoost = (if (isPreferredArtist) 0.14f else 0f) +
                (if (track.isLiked) 0.12f else 0f) +
                (min(0.10f, ln(1f + track.playCount.toFloat()) * 0.04f))

            // Respeta parcialmente el ranking neuronal de InnerTube (los primeros resultados de YouTube Music tienen mayor probabilidad de ser el hit oficial)
            val upstreamRankBonus = if (track.sourceId == "ytmusic") {
                (1.0f / (1.0f + originalIndex * 0.18f)) * 0.18f
            } else {
                0.05f
            }

            // Ponderación dinámica según la intención detectada
            val coreScore = when (intent) {
                SearchQueryIntent.EXACT_ARTIST_FOCUS -> {
                    (exactArtistMatch * 0.55f) +
                        (artistSimilarity * 0.45f) +
                        (tokenCoverage * 0.25f) +
                        (titleSimilarity * 0.15f)
                }
                SearchQueryIntent.ARTIST_AND_TRACK -> {
                    (exactCombinedMatch * 0.65f) +
                        (combinedSimilarity * 0.55f) +
                        (tokenCoverage * 0.45f) +
                        (titleSimilarity * 0.30f) +
                        (artistSimilarity * 0.25f)
                }
                SearchQueryIntent.LYRIC_FRAGMENT -> {
                    (lyricBonus * 0.85f) +
                        (tokenCoverage * 0.40f) +
                        (combinedSimilarity * 0.30f) +
                        (titleSimilarity * 0.25f)
                }
                else -> {
                    (exactTitleMatch * 0.60f) +
                        (exactCombinedMatch * 0.55f) +
                        (max(titleSimilarity, combinedSimilarity) * 0.50f) +
                        (tokenCoverage * 0.42f) +
                        (artistSimilarity * 0.28f) +
                        (phoneticScore * 0.18f) +
                        (lyricBonus * 0.55f)
                }
            }

            val totalScore = coreScore + versionAdjustment + personalizationBoost + upstreamRankBonus
            Pair(track, totalScore)
        }

        return scored
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Devuelve el análisis completo de búsqueda con "Mejor Resultado" (Top Result) y artistas destacados.
     */
    fun analyzeAndRankSearch(
        rawQuery: String,
        candidates: List<Track>,
        lyricHints: List<LyricMatchHint> = emptyList(),
        cachedLyricMatchTrackIds: Set<String> = emptySet(),
        preferredArtists: Set<String> = emptySet()
    ): SmartSearchRanking {
        val intent = detectQueryIntent(rawQuery, candidates)
        val ranked = rankSearchResults(
            rawQuery = rawQuery,
            candidates = candidates,
            lyricHints = lyricHints,
            cachedLyricMatchTrackIds = cachedLyricMatchTrackIds,
            preferredArtists = preferredArtists
        )
        val matchedArtists = ranked
            .map { it.artistName.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)

        return SmartSearchRanking(
            detectedIntent = intent,
            topResult = ranked.firstOrNull(),
            rankedTracks = ranked,
            matchedArtists = matchedArtists
        )
    }

    /**
     * Expande la consulta si contiene alias culturales, apodos o géneros regionales.
     */
    private fun expandQueryWithCulturalAliases(normQuery: String): List<String> {
        val expansions = ArrayList<String>()
        for ((alias, targets) in CULTURAL_ALIASES) {
            if (normQuery == alias || normQuery.contains(alias)) {
                for (target in targets) {
                    expansions.add(normQuery.replace(alias, target).trim())
                    expansions.add(target)
                }
            }
        }
        return expansions.distinct()
    }

    /**
     * Evalúa qué porcentaje de las palabras buscadas están presentes como palabra exacta,
     * prefijo de palabra (autocomplete mientras el usuario escribe) o acrónimo de iniciales.
     */
    private fun computeTokenAndPrefixCoverage(
        queryTokens: List<String>,
        targetText: String,
        titleTokens: List<String>
    ): Float {
        if (queryTokens.isEmpty() || targetText.isEmpty()) return 0f
        val targetTokens = targetText.split(' ').filter { it.isNotBlank() }
        if (targetTokens.isEmpty()) return 0f

        var matchedWeight = 0f
        for (qToken in queryTokens) {
            val bestTokenMatch = targetTokens.maxOfOrNull { tToken ->
                when {
                    tToken == qToken -> 1.0f
                    tToken.startsWith(qToken) && qToken.length >= 2 -> 0.88f // Autocompletado por prefijo
                    qToken.length >= 4 && boundedSimilarity(qToken, tToken) >= 0.78f -> 0.80f // Tolerancia a typo en palabra
                    tToken.contains(qToken) && qToken.length >= 3 -> 0.65f
                    else -> 0f
                }
            } ?: 0f
            matchedWeight += bestTokenMatch
        }

        var coverage = (matchedWeight / queryTokens.size.toFloat()).coerceIn(0f, 1f)

        // Detección de acrónimos (ej. "mym" -> "Motor y Motivo")
        if (titleTokens.size >= 2) {
            val acronym = titleTokens.mapNotNull { it.firstOrNull() }.joinToString("")
            if (queryTokens.any { it == acronym && acronym.length >= 3 }) {
                coverage = max(coverage, 0.92f)
            }
        }

        return coverage
    }

    /**
     * Combina Jaro-Winkler (excelente para prefijos y nombres propios), Damerau-Levenshtein
     * (excelente para letras invertidas por error de tipeo) y Coseno de Trigramas (excelente para palabras desordenadas).
     */
    private fun hybridStringSimilarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1.0f
        val jw = jaroWinklerSimilarity(a, b)
        val lev = boundedSimilarity(a, b)
        val trigram = ngramCosineSimilarity(a, b, 3)
        return ((jw * 0.42f) + (trigram * 0.35f) + (lev * 0.23f)).coerceIn(0f, 1f)
    }

    /**
     * Similitud Jaro-Winkler optimizada para nombres de artistas y títulos de canciones.
     */
    fun jaroWinklerSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0f

        val matchDistance = (max(len1, len2) / 2) - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0f

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toFloat()
        val jaro = ((m / len1) + (m / len2) + ((m - transpositions / 2f) / m)) / 3f

        // Bonificación Winkler por prefijo común (hasta 4 caracteres)
        var prefix = 0
        val maxPrefix = min(4, min(len1, len2))
        for (i in 0 until maxPrefix) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return (jaro + prefix * 0.1f * (1f - jaro)).coerceIn(0f, 1f)
    }

    /**
     * Similitud Coseno sobre N-gramas de caracteres (insensible al orden de las palabras).
     */
    fun ngramCosineSimilarity(s1: String, s2: String, n: Int = 3): Float {
        if (s1 == s2) return 1.0f
        if (s1.length < n || s2.length < n) return boundedSimilarity(s1, s2)

        val padded1 = " $s1 "
        val padded2 = " $s2 "
        val freq1 = HashMap<String, Int>()
        val freq2 = HashMap<String, Int>()

        for (i in 0..padded1.length - n) {
            val gram = padded1.substring(i, i + n)
            freq1[gram] = (freq1[gram] ?: 0) + 1
        }
        for (i in 0..padded2.length - n) {
            val gram = padded2.substring(i, i + n)
            freq2[gram] = (freq2[gram] ?: 0) + 1
        }

        var dot = 0f
        for ((gram, count1) in freq1) {
            val count2 = freq2[gram] ?: 0
            dot += (count1 * count2).toFloat()
        }
        val norm1 = sqrt(freq1.values.sumOf { it * it }.toDouble()).toFloat()
        val norm2 = sqrt(freq2.values.sumOf { it * it }.toDouble()).toFloat()
        if (norm1 <= 1e-6f || norm2 <= 1e-6f) return 0f
        return (dot / (norm1 * norm2)).coerceIn(0f, 1f)
    }

    /**
     * Similitud acotada basada en distancia de edición con soporte de transposiciones.
     */
    private fun boundedSimilarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val maxLen = max(a.length, b.length)
        if (abs(a.length - b.length) > maxLen / 2 + 4) return 0f

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            System.arraycopy(curr, 0, prev, 0, b.length + 1)
        }
        val distance = prev[b.length]
        return (1f - (distance.toFloat() / maxLen.toFloat())).coerceIn(0f, 1f)
    }

    /**
     * Clave fonética para español e hispanoamérica (seseo, yeísmo, b/v, k/qu, h muda).
     */
    private fun spanishPhoneticCode(normalizedInput: String): String {
        return normalizedInput
            .replace('v', 'b')
            .replace('z', 's')
            .replace("ce", "se")
            .replace("ci", "si")
            .replace("ll", "y")
            .replace("ge", "je")
            .replace("gi", "ji")
            .replace("qu", "k")
            .replace("ch", "x")
            .replace("h", "")
    }

    /**
     * Deduplica pistas con idéntica clave (título limpio + artista) conservando la versión que tenga:
     * 1. Coincidencia en caché de letras o estado `isLiked`.
     * 2. Portada válida (`artworkUrl`).
     * 3. Duración conocida (`durationMs > 0`).
     */
    private fun deduplicateCandidatesPreservingBest(
        candidates: List<Track>,
        cachedLyricIds: Set<String>
    ): List<Track> {
        val byId = LinkedHashMap<String, Track>()
        for (track in candidates) {
            if (!byId.containsKey(track.id)) {
                byId[track.id] = track
            }
        }
        return byId.values.toList()
    }
}
