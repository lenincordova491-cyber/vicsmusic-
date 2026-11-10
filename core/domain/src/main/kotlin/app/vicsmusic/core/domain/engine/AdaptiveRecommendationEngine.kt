package app.vicsmusic.core.domain.engine

import app.vicsmusic.core.model.Track
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Franjas horarias con sesgo acústico multidimensional (Energía, Valencia y Bailabilidad).
 */
enum class DayPart(
    val label: String,
    val energyBias: Float,
    val valenceBias: Float = 0.60f,
    val danceabilityBias: Float = 0.60f
) {
    MORNING(
        label = "Energía Matutina",
        energyBias = 0.72f,
        valenceBias = 0.75f,
        danceabilityBias = 0.65f
    ),
    AFTERNOON(
        label = "Ritmo de Tarde",
        energyBias = 0.80f,
        valenceBias = 0.78f,
        danceabilityBias = 0.82f
    ),
    EVENING(
        label = "Sesión Nocturna",
        energyBias = 0.85f,
        valenceBias = 0.70f,
        danceabilityBias = 0.88f
    ),
    LATE_NIGHT(
        label = "Modo Chill & Relax",
        energyBias = 0.38f,
        valenceBias = 0.45f,
        danceabilityBias = 0.42f
    )
}

/**
 * Evento de reproducción con porcentaje de retención para distinguir entre
 * omisiones rápidas (skips < 22%), escuchas parciales y repeticiones en bucle.
 */
data class ListeningEvent(
    val trackId: String,
    val artistName: String,
    val completionPercentage: Float,
    val timestamp: Long
)

/**
 * Perfil de afinidad musical del usuario calculado en tiempo real.
 */
data class UserTasteProfile(
    val topArtists: List<String> = emptyList(),
    val dominantGenre: String = "Pop Global",
    val tasteSummaryLabel: String = "Adaptado a tu gusto musical",
    val artistScores: Map<String, Float> = emptyMap(),
    val genreScores: Map<String, Float> = emptyMap()
)

/**
 * Resultado estructurado de recomendaciones para poblar Home y Mixes personalizados.
 */
data class RecommendationResult(
    val dailyMix: List<Track>,
    val recommendedForYou: List<Track>,
    val jumpBackIn: List<Track>,
    val topArtistAffinity: String? = null,
    val tasteProfile: UserTasteProfile = UserTasteProfile()
)

/**
 * Vector acústico de 6 dimensiones inspirado en los Audio Features de Spotify:
 * [energy, valence, danceability, acousticness, instrumentalness, tempoNorm]
 */
data class AcousticVector(
    val energy: Float,
    val valence: Float,
    val danceability: Float,
    val acousticness: Float,
    val instrumentalness: Float,
    val tempoNorm: Float
) {
    fun cosineSimilarity(other: AcousticVector): Float {
        val dot = (energy * other.energy) +
            (valence * other.valence) +
            (danceability * other.danceability) +
            (acousticness * other.acousticness) +
            (instrumentalness * other.instrumentalness) +
            (tempoNorm * other.tempoNorm)
        val normA = sqrt(
            energy * energy + valence * valence + danceability * danceability +
                acousticness * acousticness + instrumentalness * instrumentalness +
                tempoNorm * tempoNorm
        )
        val normB = sqrt(
            other.energy * other.energy + other.valence * other.valence +
                other.danceability * other.danceability + other.acousticness * other.acousticness +
                other.instrumentalness * other.instrumentalness + other.tempoNorm * other.tempoNorm
        )
        if (normA <= 1e-6f || normB <= 1e-6f) return 0.5f
        return (dot / (normA * normB)).coerceIn(0f, 1f)
    }

    fun blendWith(other: AcousticVector, weightOther: Float): AcousticVector {
        val w = weightOther.coerceIn(0f, 1f)
        val selfW = 1f - w
        return AcousticVector(
            energy = (energy * selfW + other.energy * w).coerceIn(0f, 1f),
            valence = (valence * selfW + other.valence * w).coerceIn(0f, 1f),
            danceability = (danceability * selfW + other.danceability * w).coerceIn(0f, 1f),
            acousticness = (acousticness * selfW + other.acousticness * w).coerceIn(0f, 1f),
            instrumentalness = (instrumentalness * selfW + other.instrumentalness * w).coerceIn(0f, 1f),
            tempoNorm = (tempoNorm * selfW + other.tempoNorm * w).coerceIn(0f, 1f)
        )
    }
}

/**
 * Motor de Recomendaciones Adaptativo de nivel industrial (estilo Spotify BaRT + YouTube Music).
 *
 * Combina:
 * 1. Vectores acústicos de 6 dimensiones (Energía, Valencia, Bailabilidad, Acústica, Instrumental, Tempo).
 * 2. Inercia de sesión en corto plazo (Session Momentum Vector de los últimos 45 minutos) para "leer la mente"
 *    según el estado de ánimo inmediato del usuario.
 * 3. Procesamiento de señales implícitas: penalización severa de skips (< 22%), premio por escuchas completas (> 88%)
 *    y detección de canciones en bucle (Repeat-Loop Boost).
 * 4. Grafo colaborativo de co-ocurrencia de artistas (clusters culturales/regionales + transiciones del usuario).
 * 5. Selección mediante Maximal Marginal Relevance (MMR) y transiciones suaves de energía tipo DJ.
 */
class AdaptiveRecommendationEngine {

    companion object {
        private const val HALF_LIFE_DAYS = 10.0
        private const val SESSION_WINDOW_MS = 45L * 60L * 1000L // 45 minutos para detectar el mood actual
        private const val COOLDOWN_WINDOW_MS = 2L * 60L * 60L * 1000L // 2 horas para no repetir exactamente la misma pista en Mix
        private const val SKIP_PENALTY_WINDOW_MS = 3L * 24L * 60L * 60L * 1000L // 3 días de enfriamiento tras un skip
        private const val EXPLORATION_RATIO = 0.22f
        private const val MMR_LAMBDA = 0.74f // Balance entre relevancia (74%) y diversidad/sorpresa (26%)

        private val GENRE_KEYWORDS: Map<String, List<String>> = mapOf(
            "Cumbia y Fiesta" to listOf(
                "grupo 5", "angeles azules", "destellos", "hijos del sol", "chacal", "maroyu",
                "amar azul", "rafaga", "cumbia", "chicha", "huayno", "yarita", "agua marina",
                "armonia 10", "corazon serrano", "ke personajes", "onda sabanera", "damas gratis",
                "los mirlos", "bareto", "gran orquesta", "hermanos yaipen", "papillon", "pintura roja"
            ),
            "Salsa y Tropical" to listOf(
                "marc anthony", "frankie ruiz", "eddie santiago", "salsa", "romeo santos",
                "aventura", "juan luis guerra", "bachata", "merengue", "daniela darcourt",
                "oscar d'leon", "oscar dleon", "ruben blades", "hector lavoe", "willie colon",
                "gilberto santa rosa", "jerry rivera", "adolescentes", "grupo niche", "joe arroyo",
                "yahaira", "combinacion de la habana", "zaperoko", "prince royce", "manuel turizo"
            ),
            "Rock & Alternativo" to listOf(
                "soda stereo", "mana", "caifanes", "jarabe de palo", "mojarras", "hombres g",
                "libido", "amen", "mar de copas", "imagine dragons", "smashing pumpkins",
                "arctic monkeys", "linkin park", "nirvana", "strokes", "cerati", "rock", "indie",
                "los prisioneros", "enanitos verdes", "charly garcia", "fito paez", "spinetta",
                "autenticos decadentes", "fabulosos cadillacs", "vilma palma", "pedro suarez",
                "arena hash", "rio", "nose quien", "zen", "tk", "queen", "coldplay", "radiohead",
                "oasis", "muse", "red hot chili peppers", "metallica", "guns n roses", "ac/dc"
            ),
            "Urbano Latino" to listOf(
                "bad bunny", "bizarrap", "maluma", "daddy yankee", "don omar", "ozuna",
                "j balvin", "myke towers", "young miko", "mora", "reggaeton", "trap",
                "feid", "karol g", "rauw alejandro", "quevedo", "rosalia", "canserbero",
                "anuel", "nicky jam", "wisin", "yandel", "arcangel", "de la ghetto",
                "tego calderon", "residente", "calle 13", "trueno", "duki", "peso pluma"
            ),
            "Electrónica & Synth" to listOf(
                "daft punk", "david guetta", "tiesto", "skrillex", "fred again", "remix",
                "edm", "house", "techno", "avicii", "calvin harris", "marshmello", "kygo",
                "martin garrix", "alan walker", "deadmau5", "disclosure", "rüfüs du sol",
                "electronic", "synthwave", "trance", "biza"
            ),
            "Baladas & Acústico" to listOf(
                "luis miguel", "sin bandera", "natalia lafourcade", "balada", "acustico",
                "unplugged", "camila", "reik", "jesse & joy", "pablo alboran", "alejandro sanz",
                "ricardo arjona", "chayanne", "cristian castro", "jose jose", "juan gabriel",
                "mon laferte", "camilo", "sebastian yatra", "morat", "lasso", "piano", "acoustic"
            ),
            "Pop Global" to listOf(
                "the weeknd", "billie eilish", "ed sheeran", "miley cyrus", "ariana grande",
                "harry styles", "taylor swift", "dua lipa", "bruno mars", "sabrina carpenter",
                "lady gaga", "justin bieber", "post malone", "olivia rodrigo", "lana del rey",
                "adele", "rihanna", "michael jackson", "madonna", "shakira", "pop"
            )
        )

        /**
         * Grafo de afinidad colaborativa entre artistas (similitud de audiencia estilo "Los fans también escuchan").
         */
        private val ARTIST_COLLABORATIVE_CLUSTERS: List<Set<String>> = listOf(
            // Cluster Cumbia Peruana Norteña
            setOf("grupo 5", "agua marina", "armonia 10", "corazon serrano", "hermanos yaipen", "gran orquesta"),
            // Cluster Chicha / Cumbia Psicodélica y Clásica
            setOf("los destellos", "los hijos del sol", "los mirlos", "chacal", "pintura roja", "bareto"),
            // Cluster Cumbia Latinoamericana / Romántica / Argentina
            setOf("ke personajes", "los angeles azules", "rafaga", "amar azul", "onda sabanera", "maroyu"),
            // Cluster Rock Peruano
            setOf("mar de copas", "libido", "amen", "pedro suarez-vertiz", "los mojarras", "arena hash", "rio"),
            // Cluster Rock Latinoamericano Clásico
            setOf("soda stereo", "gustavo cerati", "los enanitos verdes", "caifanes", "los prisioneros", "mana", "hombres g", "jarabe de palo"),
            // Cluster Rock / Alternativo Internacional
            setOf("coldplay", "queen", "imagine dragons", "the smashing pumpkins", "arctic monkeys", "the strokes", "nirvana", "linkin park"),
            // Cluster Urbano / Reggaetón Actual
            setOf("bad bunny", "feid", "karol g", "rauw alejandro", "mora", "myke towers", "young miko", "bizarrap", "quevedo", "rosalia"),
            // Cluster Reggaetón Clásico
            setOf("daddy yankee", "don omar", "wisin & yandel", "tego calderon", "nicky jam", "maluma", "j balvin", "ozuna"),
            // Cluster Rap Consciente / Hip Hop Latino
            setOf("canserbero", "residente", "calle 13", "trueno", "nach", "al2 el aldeano", "cancerbero"),
            // Cluster Salsa Clásica y Romántica
            setOf("marc anthony", "ruben blades", "oscar d'leon", "frankie ruiz", "eddie santiago", "hector lavoe", "grupo niche", "daniela darcourt"),
            // Cluster Bachata & Tropical Moderno
            setOf("romeo santos", "aventura", "juan luis guerra", "prince royce", "manuel turizo"),
            // Cluster Pop Global Contemporáneo
            setOf("the weeknd", "dua lipa", "bruno mars", "billie eilish", "taylor swift", "sabrina carpenter", "ed sheeran", "miley cyrus")
        )

        /**
         * Vectores base por género [energy, valence, danceability, acousticness, instrumentalness, tempoNorm].
         */
        private val GENRE_BASE_VECTORS: Map<String, AcousticVector> = mapOf(
            "Cumbia y Fiesta" to AcousticVector(0.84f, 0.88f, 0.90f, 0.35f, 0.08f, 0.68f),
            "Salsa y Tropical" to AcousticVector(0.82f, 0.84f, 0.89f, 0.42f, 0.10f, 0.72f),
            "Urbano Latino" to AcousticVector(0.80f, 0.72f, 0.88f, 0.18f, 0.04f, 0.65f),
            "Rock & Alternativo" to AcousticVector(0.76f, 0.58f, 0.54f, 0.28f, 0.15f, 0.70f),
            "Electrónica & Synth" to AcousticVector(0.90f, 0.70f, 0.86f, 0.08f, 0.45f, 0.82f),
            "Baladas & Acústico" to AcousticVector(0.36f, 0.42f, 0.38f, 0.82f, 0.12f, 0.44f),
            "Pop Global" to AcousticVector(0.70f, 0.68f, 0.74f, 0.25f, 0.06f, 0.66f)
        )
    }

    /**
     * Determina la franja horaria actual para ajustar la curva de energía y valencia.
     */
    fun currentDayPart(
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): DayPart = when (hourOfDay) {
        in 5..11 -> DayPart.MORNING
        in 12..17 -> DayPart.AFTERNOON
        in 18..22 -> DayPart.EVENING
        else -> DayPart.LATE_NIGHT
    }

    /**
     * Infiere el género principal de una canción evaluando artista, título y álbum con pesos diferenciados.
     */
    fun inferTrackGenre(track: Track): String {
        val artistNorm = normalizeText(track.artistName)
        val titleNorm = normalizeText(track.title)
        val albumNorm = normalizeText(track.albumName ?: "")
        val combined = "$artistNorm ${track.artistId.lowercase(Locale.ROOT)} $albumNorm $titleNorm"

        var bestGenre = "Pop Global"
        var maxScore = 0

        for ((genre, keywords) in GENRE_KEYWORDS) {
            var score = 0
            for (kw in keywords) {
                val cleanKw = normalizeText(kw)
                if (artistNorm.contains(cleanKw)) score += 5
                else if (albumNorm.contains(cleanKw)) score += 3
                else if (combined.contains(cleanKw)) score += 2
            }
            if (score > maxScore) {
                maxScore = score
                bestGenre = genre
            }
        }
        return bestGenre
    }

    /**
     * Calcula el vector acústico de 6 dimensiones de una pista a partir de su género,
     * modificadores semánticos del título/álbum (en vivo, acústico, remix, triste, fiesta) y duración.
     */
    fun computeTrackAcousticVector(track: Track): AcousticVector {
        val genre = inferTrackGenre(track)
        val base = GENRE_BASE_VECTORS[genre] ?: GENRE_BASE_VECTORS.getValue("Pop Global")
        val meta = normalizeText("${track.title} ${track.albumName.orEmpty()} ${track.artistName}")

        var energy = base.energy
        var valence = base.valence
        var danceability = base.danceability
        var acousticness = base.acousticness
        var instrumentalness = base.instrumentalness
        var tempo = base.tempoNorm

        // Ajustes semánticos finos según versión o estilo de la canción
        if (meta.contains("acustico") || meta.contains("acoustic") || meta.contains("unplugged") || meta.contains("piano")) {
            energy -= 0.24f
            acousticness += 0.42f
            tempo -= 0.10f
        }
        if (meta.contains("en vivo") || meta.contains("live") || meta.contains("concierto") || meta.contains("parranda")) {
            energy += 0.14f
            valence += 0.10f
        }
        if (meta.contains("remix") || meta.contains("session") || meta.contains("crossover") || meta.contains("festa")) {
            energy += 0.12f
            danceability += 0.12f
            tempo += 0.08f
        }
        if (meta.contains("triste") || meta.contains("llorar") || meta.contains("adios") ||
            meta.contains("muerte") || meta.contains("traicion") || meta.contains("pobre corazon") ||
            meta.contains("lamento")
        ) {
            valence -= 0.22f
        }
        if (meta.contains("canserbero")) {
            energy = 0.64f
            valence = 0.32f
            danceability = 0.58f
            acousticness = 0.45f
        }

        return AcousticVector(
            energy = energy.coerceIn(0.05f, 0.99f),
            valence = valence.coerceIn(0.05f, 0.99f),
            danceability = danceability.coerceIn(0.05f, 0.99f),
            acousticness = acousticness.coerceIn(0.02f, 0.98f),
            instrumentalness = instrumentalness.coerceIn(0.01f, 0.95f),
            tempoNorm = tempo.coerceIn(0.10f, 0.98f)
        )
    }

    /**
     * Analiza el perfil de gustos del usuario integrando:
     * - Decaimiento temporal exponencial (Half-Life)
     * - Penalización de Skips (< 22%) y bonificación de escuchas completas (> 88%)
     * - Detección de obsesión temporal (Repeat-Loop en las últimas 24h)
     * - Propagación de afinidad hacia artistas similares (Collaborative Graph Propagation)
     */
    fun analyzeUserTaste(
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent>,
        favoriteTrackIds: Set<String> = emptySet(),
        favoriteArtistNames: Set<String> = emptySet()
    ): UserTasteProfile {
        val now = System.currentTimeMillis()
        val trackById = allAvailableTracks.associateBy { it.id }

        val rawArtistScores = LinkedHashMap<String, Float>()
        val rawGenreScores = LinkedHashMap<String, Float>()

        // 1. Semilla explícita: Artistas favoritos seleccionados en Onboarding/Ajustes
        for (favArtist in favoriteArtistNames) {
            val clean = favArtist.replace('_', ' ').trim()
            if (clean.isNotEmpty()) {
                rawArtistScores[clean] = (rawArtistScores[clean] ?: 0f) + 6.5f
            }
        }

        // 2. Semilla explícita: Canciones con "Me gusta" y contador histórico de reproducciones
        for (track in allAvailableTracks) {
            val artistKey = track.artistName.trim()
            val genre = inferTrackGenre(track)
            if (track.isLiked || favoriteTrackIds.contains(track.id)) {
                rawArtistScores[artistKey] = (rawArtistScores[artistKey] ?: 0f) + 4.5f
                rawGenreScores[genre] = (rawGenreScores[genre] ?: 0f) + 3.2f
            }
            if (track.playCount > 0) {
                // Escala logarítmica para evitar que una sola canción monopolice todo el perfil
                val playBoost = (ln(1.0 + track.playCount.toDouble()) * 1.8).toFloat()
                rawArtistScores[artistKey] = (rawArtistScores[artistKey] ?: 0f) + playBoost
                rawGenreScores[genre] = (rawGenreScores[genre] ?: 0f) + (playBoost * 0.7f)
            }
        }

        // 3. Procesamiento de señales implícitas del historial (Skips, Completions, Repeat-Loops)
        val recentTrackPlayCounts24h = HashMap<String, Int>()
        val oneDayMs = 24L * 60L * 60L * 1000L

        for (event in listeningHistory) {
            val ageMs = max(0L, now - event.timestamp)
            val ageDays = ageMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
            val recencyDecay = exp(-ln(2.0) * (ageDays / HALF_LIFE_DAYS)).toFloat().coerceIn(0.15f, 1.0f)

            // Curva no lineal de recompensa/penalización según porcentaje escuchado
            val completionSignal = when {
                event.completionPercentage < 0.22f -> {
                    // Skip rápido: voto negativo fuerte si ocurrió en los últimos 3 días
                    val skipRecency = if (ageMs < SKIP_PENALTY_WINDOW_MS) 1.0f else 0.35f
                    -1.45f * skipRecency
                }
                event.completionPercentage < 0.50f -> 0.20f
                event.completionPercentage < 0.85f -> 1.05f
                else -> 1.75f // Escucha completa hasta el final
            }

            if (ageMs <= oneDayMs && event.completionPercentage >= 0.70f) {
                recentTrackPlayCounts24h[event.trackId] =
                    (recentTrackPlayCounts24h[event.trackId] ?: 0) + 1
            }

            val weightedDelta = completionSignal * recencyDecay
            val artistKey = event.artistName.trim()
            if (artistKey.isNotEmpty()) {
                rawArtistScores[artistKey] = max(0f, (rawArtistScores[artistKey] ?: 0f) + weightedDelta)
            }

            val matchedTrack = trackById[event.trackId]
            val genre = matchedTrack?.let { inferTrackGenre(it) } ?: inferGenreFromArtistName(artistKey)
            rawGenreScores[genre] = max(0f, (rawGenreScores[genre] ?: 0f) + (weightedDelta * 0.85f))
        }

        // 4. Bonificación por obsesión temporal (canciones escuchadas en bucle hoy)
        for ((trackId, count24h) in recentTrackPlayCounts24h) {
            if (count24h >= 2) {
                val track = trackById[trackId] ?: continue
                val loopBonus = min(5.0f, (count24h - 1) * 1.6f)
                val artistKey = track.artistName.trim()
                val genre = inferTrackGenre(track)
                rawArtistScores[artistKey] = (rawArtistScores[artistKey] ?: 0f) + loopBonus
                rawGenreScores[genre] = (rawGenreScores[genre] ?: 0f) + loopBonus
            }
        }

        // 5. Propagación en el grafo colaborativo de artistas ("Los fans de X también aman Y")
        val propagatedArtistScores = LinkedHashMap<String, Float>(rawArtistScores)
        for ((artist, score) in rawArtistScores) {
            if (score <= 0.5f) continue
            val neighbors = getRelatedArtists(artist)
            for (neighbor in neighbors) {
                val propagatedBoost = score * 0.28f
                propagatedArtistScores[neighbor] =
                    (propagatedArtistScores[neighbor] ?: 0f) + propagatedBoost
            }
        }

        // Normalización de puntuaciones [0.0 .. 1.0]
        val maxArtist = (propagatedArtistScores.values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val normalizedArtists = propagatedArtistScores
            .entries
            .sortedByDescending { it.value }
            .associate { it.key to (it.value / maxArtist).coerceIn(0f, 1f) }

        val maxGenre = (rawGenreScores.values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val normalizedGenres = rawGenreScores
            .entries
            .sortedByDescending { it.value }
            .associate { it.key to (it.value / maxGenre).coerceIn(0f, 1f) }

        val topArtists = normalizedArtists.keys.take(5)
        val dominantGenre = normalizedGenres.keys.firstOrNull() ?: "Pop Global"
        val topArtistsPreview = topArtists.take(2).joinToString(", ").ifEmpty { "Tus artistas" }
        val summaryLabel = "$dominantGenre • $topArtistsPreview • Explora tu mezcla personalizada"

        return UserTasteProfile(
            topArtists = topArtists,
            dominantGenre = dominantGenre,
            tasteSummaryLabel = summaryLabel,
            artistScores = normalizedArtists,
            genreScores = normalizedGenres
        )
    }

    /**
     * Genera las 3 colecciones principales de Home:
     * - `dailyMix`: Mezcla de alta precisión (Exploitation + Session Momentum + transición suave de energía).
     * - `recommendedForYou`: Descubrimiento inteligente (Artistas co-escuchados en el grafo colaborativo y joyas ocultas).
     * - `jumpBackIn`: Canciones con mayor retención reciente o que el usuario suele repetir a esta hora.
     */
    fun computeRecommendations(
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent>,
        favoriteTrackIds: Set<String> = emptySet(),
        favoriteArtistNames: Set<String> = emptySet(),
        limit: Int = 20
    ): RecommendationResult {
        if (allAvailableTracks.isEmpty()) {
            return RecommendationResult(
                dailyMix = emptyList(),
                recommendedForYou = emptyList(),
                jumpBackIn = emptyList()
            )
        }

        val now = System.currentTimeMillis()
        val tasteProfile = analyzeUserTaste(
            allAvailableTracks = allAvailableTracks,
            listeningHistory = listeningHistory,
            favoriteTrackIds = favoriteTrackIds,
            favoriteArtistNames = favoriteArtistNames
        )
        val dayPart = currentDayPart()
        val sessionVector = computeSessionMomentumVector(allAvailableTracks, listeningHistory, dayPart, now)

        // Pistas escuchadas hace muy poco (< 2h) o saltadas recientemente
        val recentlyPlayedIds = listeningHistory
            .filter { (now - it.timestamp) in 0..COOLDOWN_WINDOW_MS && it.completionPercentage >= 0.5f }
            .map { it.trackId }
            .toSet()

        val recentlySkippedIds = listeningHistory
            .filter { (now - it.timestamp) in 0..SKIP_PENALTY_WINDOW_MS && it.completionPercentage < 0.22f }
            .map { it.trackId }
            .toSet()

        val historicallyPlayedIds = listeningHistory
            .filter { it.completionPercentage >= 0.55f }
            .map { it.trackId }
            .toSet()

        // 1. Puntuación para Daily Mix (alta afinidad + estado de ánimo actual)
        val dailyScored = allAvailableTracks.map { track ->
            val artistAffinity = resolveArtistScore(track, tasteProfile.artistScores)
            val genre = inferTrackGenre(track)
            val genreAffinity = tasteProfile.genreScores[genre] ?: 0.25f
            val trackVector = computeTrackAcousticVector(track)
            val moodAlignment = trackVector.cosineSimilarity(sessionVector)

            val likedBonus = if (track.isLiked || favoriteTrackIds.contains(track.id)) 0.22f else 0f
            val cooldownPenalty = if (recentlyPlayedIds.contains(track.id)) -0.35f else 0f
            val skipPenalty = if (recentlySkippedIds.contains(track.id)) -0.65f else 0f

            val totalScore = (artistAffinity * 0.42f) +
                (genreAffinity * 0.26f) +
                (moodAlignment * 0.24f) +
                likedBonus + cooldownPenalty + skipPenalty

            ScoredTrack(track, totalScore, trackVector)
        }.sortedByDescending { it.score }

        val dailyMix = selectWithMmrAndFlow(
            candidates = dailyScored,
            limit = limit,
            maxPerArtist = 2,
            lambda = MMR_LAMBDA
        )

        // 2. Puntuación para Descubrimiento ("Recomendado para ti": prioriza canciones no quemadas pero de alta compatibilidad)
        val discoveryScored = allAvailableTracks.map { track ->
            val artistAffinity = resolveArtistScore(track, tasteProfile.artistScores)
            val genre = inferTrackGenre(track)
            val genreAffinity = tasteProfile.genreScores[genre] ?: 0.20f
            val trackVector = computeTrackAcousticVector(track)
            val moodAlignment = trackVector.cosineSimilarity(sessionVector)

            // Premia pistas que aún no se han reproducido muchas veces pero pertenecen a artistas/géneros afines
            val noveltyBonus = when {
                !historicallyPlayedIds.contains(track.id) && track.playCount == 0 -> 0.32f
                track.playCount in 1..2 -> 0.16f
                else -> -0.10f
            }
            val skipPenalty = if (recentlySkippedIds.contains(track.id)) -0.70f else 0f

            val discoveryScore = (artistAffinity * 0.34f) +
                (genreAffinity * 0.28f) +
                (moodAlignment * 0.22f) +
                (noveltyBonus * (1f + EXPLORATION_RATIO)) +
                skipPenalty

            ScoredTrack(track, discoveryScore, trackVector)
        }.sortedByDescending { it.score }

        val recommendedForYou = selectWithMmrAndFlow(
            candidates = discoveryScored,
            limit = limit,
            maxPerArtist = 2,
            lambda = 0.65f // Mayor diversidad en descubrimiento
        )

        // 3. "Vuelve a escuchar" (Jump Back In): Solo pistas que el usuario efectivamente ya escuchó o tiene en favoritos
        val jumpBackCandidates = allAvailableTracks.filter { track ->
            historicallyPlayedIds.contains(track.id) || track.playCount > 0 || track.isLiked || favoriteTrackIds.contains(track.id)
        }

        val jumpBackScored = jumpBackCandidates.map { track ->
            val artistAffinity = resolveArtistScore(track, tasteProfile.artistScores)
            val likedBonus = if (track.isLiked || favoriteTrackIds.contains(track.id)) 0.30f else 0f
            val skipPenalty = if (recentlySkippedIds.contains(track.id)) -0.60f else 0f
            val vector = computeTrackAcousticVector(track)

            ScoredTrack(
                track = track,
                score = 0.45f + (artistAffinity * 0.35f) + likedBonus + skipPenalty,
                vector = vector
            )
        }.sortedByDescending { it.score }

        val jumpBackIn = selectWithDiversity(
            tracks = jumpBackScored.map { it.track },
            limit = min(12, limit),
            maxPerArtist = 2
        )

        return RecommendationResult(
            dailyMix = dailyMix,
            recommendedForYou = recommendedForYou,
            jumpBackIn = jumpBackIn,
            topArtistAffinity = tasteProfile.topArtists.firstOrNull(),
            tasteProfile = tasteProfile
        )
    }

    /**
     * Genera una cola de Autoplay continua que mantiene la atmósfera exacta de `currentTrack`
     * ("Radio de la canción") usando similitud coseno en 6D, co-ocurrencia de artistas y MMR.
     */
    fun computeAutoplayQueue(
        currentTrack: Track,
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent> = emptyList(),
        favoriteArtistNames: Set<String> = emptySet(),
        favoriteTrackIds: Set<String> = emptySet(),
        excludedTrackIds: Set<String> = emptySet(),
        limit: Int = 15,
        count: Int = limit
    ): List<Track> {
        val effectiveLimit = if (count != 15) count else limit
        if (allAvailableTracks.isEmpty()) return emptyList()

        val tasteProfile = analyzeUserTaste(
            allAvailableTracks = allAvailableTracks,
            listeningHistory = listeningHistory,
            favoriteTrackIds = favoriteTrackIds,
            favoriteArtistNames = favoriteArtistNames
        )

        val seedGenre = inferTrackGenre(currentTrack)
        val seedVector = computeTrackAcousticVector(currentTrack)
        val seedNeighbors = getRelatedArtists(currentTrack.artistName)
        val blockedIds = excludedTrackIds + currentTrack.id

        val candidates = allAvailableTracks.filter { it.id !in blockedIds }
        if (candidates.isEmpty()) return emptyList()

        val scoredCandidates = candidates.map { candidate ->
            val candGenre = inferTrackGenre(candidate)
            val candVector = computeTrackAcousticVector(candidate)

            // 1. Continuidad acústica directa con la canción semilla (evita cambios bruscos de ritmo)
            val acousticSim = seedVector.cosineSimilarity(candVector)

            // 2. Relación de artista (mismo artista o artista hermano en el grafo colaborativo)
            val sameArtist = candidate.artistId.equals(currentTrack.artistId, ignoreCase = true) ||
                candidate.artistName.equals(currentTrack.artistName, ignoreCase = true)
            val isNeighborArtist = seedNeighbors.any { matchesArtist(candidate, it) }

            val artistContinuity = when {
                sameArtist -> 0.88f
                isNeighborArtist -> 0.95f // Prioriza ligeramente artistas hermanos para que la radio sea variada pero coherente
                else -> resolveArtistScore(candidate, tasteProfile.artistScores) * 0.65f
            }

            // 3. Coherencia de género
            val genreContinuity = if (candGenre == seedGenre) 1.0f else (tasteProfile.genreScores[candGenre] ?: 0.2f) * 0.5f

            // 4. Mismo álbum (si no es un nombre genérico como "Hits" o "En Vivo")
            val sameAlbum = !currentTrack.albumName.isNullOrBlank() &&
                !currentTrack.albumName.equals("Hits", ignoreCase = true) &&
                currentTrack.albumName.equals(candidate.albumName, ignoreCase = true)
            val albumBonus = if (sameAlbum) 0.12f else 0f

            val score = (acousticSim * 0.40f) +
                (artistContinuity * 0.35f) +
                (genreContinuity * 0.25f) +
                albumBonus

            ScoredTrack(candidate, score, candVector)
        }.sortedByDescending { it.score }

        return selectWithMmrAndFlow(
            candidates = scoredCandidates,
            limit = effectiveLimit,
            maxPerArtist = 2,
            lambda = 0.78f,
            seedVector = seedVector
        )
    }

    /**
     * Calcula el vector de inercia de la sesión actual (Session Momentum Vector).
     * Si el usuario escuchó música en los últimos 45 minutos, el algoritmo "lee su mente"
     * promediando el vector acústico de las últimas pistas con peso exponencial por orden.
     */
    private fun computeSessionMomentumVector(
        allAvailableTracks: List<Track>,
        listeningHistory: List<ListeningEvent>,
        dayPart: DayPart,
        nowMs: Long
    ): AcousticVector {
        val defaultDayVector = AcousticVector(
            energy = dayPart.energyBias,
            valence = dayPart.valenceBias,
            danceability = dayPart.danceabilityBias,
            acousticness = if (dayPart == DayPart.LATE_NIGHT) 0.65f else 0.30f,
            instrumentalness = 0.10f,
            tempoNorm = dayPart.energyBias
        )

        val recentEvents = listeningHistory
            .filter { (nowMs - it.timestamp) in 0..SESSION_WINDOW_MS && it.completionPercentage >= 0.40f }
            .sortedByDescending { it.timestamp }
            .take(5)

        if (recentEvents.isEmpty()) return defaultDayVector

        val trackMap = allAvailableTracks.associateBy { it.id }
        var currentMomentum = defaultDayVector

        // Aplicamos desde el evento más antiguo de la ventana hasta el más reciente
        for (event in recentEvents.asReversed()) {
            val track = trackMap[event.trackId] ?: continue
            val vec = computeTrackAcousticVector(track)
            val weight = (event.completionPercentage.coerceIn(0.4f, 1.0f)) * 0.55f
            currentMomentum = currentMomentum.blendWith(vec, weight)
        }
        return currentMomentum
    }

    /**
     * Selección mediante Maximal Marginal Relevance (MMR) + suavizado de transición acústica.
     * Evita que dos canciones del mismo artista suenen pegadas y garantiza que cada pista
     * conecte armónicamente con la anterior como un DJ profesional.
     */
    private fun selectWithMmrAndFlow(
        candidates: List<ScoredTrack>,
        limit: Int,
        maxPerArtist: Int,
        lambda: Float,
        seedVector: AcousticVector? = null
    ): List<Track> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val selected = ArrayList<ScoredTrack>(min(limit, candidates.size))
        val selectedIds = HashSet<String>()
        val artistCounts = HashMap<String, Int>()
        val pool = ArrayList(candidates)

        var previousVector = seedVector

        while (selected.size < limit && pool.isNotEmpty()) {
            var bestCandidate: ScoredTrack? = null
            var bestMmrScore = Float.NEGATIVE_INFINITY
            var bestIndex = -1

            val lastArtistKey = selected.lastOrNull()?.track?.artistName?.trim()?.lowercase(Locale.ROOT)

            for (i in pool.indices) {
                val cand = pool[i]
                if (selectedIds.contains(cand.track.id)) continue

                val artistKey = cand.track.artistName.trim().lowercase(Locale.ROOT)
                val countForArtist = artistCounts[artistKey] ?: 0
                if (countForArtist >= maxPerArtist) continue

                // Penalización fuerte si es exactamente el mismo artista que la canción inmediatamente anterior
                val consecutiveArtistPenalty = if (artistKey == lastArtistKey) 0.35f else 0f

                // Similitud de flujo suave con la pista inmediatamente anterior (DJ Transition)
                val transitionSmoothness = previousVector?.cosineSimilarity(cand.vector) ?: 0.75f

                // Penalización de redundancia frente a lo ya seleccionado en la lista
                val maxRedundancy = if (selected.isEmpty()) {
                    0f
                } else {
                    selected.maxOf { sel ->
                        val sameArtistSim = if (sel.track.artistName.equals(cand.track.artistName, ignoreCase = true)) 0.85f else 0f
                        max(sameArtistSim, sel.vector.cosineSimilarity(cand.vector) * 0.6f)
                    }
                }

                val mmrScore = (lambda * cand.score) +
                    ((1f - lambda) * (1f - maxRedundancy)) +
                    (transitionSmoothness * 0.15f) -
                    consecutiveArtistPenalty

                if (mmrScore > bestMmrScore) {
                    bestMmrScore = mmrScore
                    bestCandidate = cand
                    bestIndex = i
                }
            }

            if (bestCandidate != null && bestIndex >= 0) {
                pool.removeAt(bestIndex)
                selected.add(bestCandidate)
                selectedIds.add(bestCandidate.track.id)
                val artistKey = bestCandidate.track.artistName.trim().lowercase(Locale.ROOT)
                artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
                previousVector = bestCandidate.vector
            } else {
                break
            }
        }

        // Si por restricciones de artista aún faltan elementos para llegar al límite, rellenar sin duplicar IDs
        if (selected.size < limit) {
            for (cand in candidates) {
                if (selected.size >= limit) break
                if (selectedIds.add(cand.track.id)) {
                    selected.add(cand)
                }
            }
        }

        return selected.map { it.track }
    }

    private fun selectWithDiversity(
        tracks: List<Track>,
        limit: Int,
        maxPerArtist: Int
    ): List<Track> {
        val result = ArrayList<Track>()
        val seenIds = HashSet<String>()
        val artistCount = LinkedHashMap<String, Int>()

        for (track in tracks) {
            if (result.size >= limit) break
            if (seenIds.contains(track.id)) continue
            val artistKey = track.artistName.trim().lowercase(Locale.ROOT)
            val currentCount = artistCount.getOrDefault(artistKey, 0)
            if (currentCount < maxPerArtist) {
                result.add(track)
                seenIds.add(track.id)
                artistCount[artistKey] = currentCount + 1
            }
        }

        if (result.size < limit) {
            for (track in tracks) {
                if (result.size >= limit) break
                addIfAbsent(result, track, seenIds)
            }
        }
        return result
    }

    private fun addIfAbsent(
        list: MutableList<Track>,
        track: Track,
        seenIds: HashSet<String>
    ): Boolean {
        if (seenIds.add(track.id)) {
            list.add(track)
            return true
        }
        return false
    }

    private fun resolveArtistScore(track: Track, artistScores: Map<String, Float>): Float {
        artistScores[track.artistName]?.let { return it }
        artistScores[track.artistId]?.let { return it }
        for ((artistKey, score) in artistScores) {
            if (matchesArtist(track, artistKey)) return score
        }
        return 0.08f
    }

    private fun matchesArtist(track: Track, queryArtist: String): Boolean {
        val cleanQuery = normalizeText(queryArtist.replace('_', ' ').trim())
        if (cleanQuery.isEmpty()) return false
        val cleanId = normalizeText(track.artistId.replace('_', ' '))
        val cleanName = normalizeText(track.artistName)
        return cleanId == cleanQuery ||
            cleanName == cleanQuery ||
            cleanName.contains(cleanQuery) ||
            cleanQuery.contains(cleanName)
    }

    private fun getRelatedArtists(artistName: String): Set<String> {
        val norm = normalizeText(artistName)
        val related = LinkedHashSet<String>()
        for (cluster in ARTIST_COLLABORATIVE_CLUSTERS) {
            if (cluster.any { norm.contains(it) || it.contains(norm) }) {
                for (member in cluster) {
                    if (member != norm) related.add(member)
                }
            }
        }
        return related
    }

    private fun inferGenreFromArtistName(artistName: String): String {
        val norm = normalizeText(artistName)
        for ((genre, keywords) in GENRE_KEYWORDS) {
            if (keywords.any { norm.contains(normalizeText(it)) }) {
                return genre
            }
        }
        return "Pop Global"
    }

    private fun normalizeText(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')
            .trim()
    }

    private data class ScoredTrack(
        val track: Track,
        val score: Float,
        val vector: AcousticVector
    )
}
