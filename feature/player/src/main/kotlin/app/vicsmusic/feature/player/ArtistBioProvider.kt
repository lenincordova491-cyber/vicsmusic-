package app.vicsmusic.feature.player

import kotlin.math.absoluteValue

data class ArtistBioInfo(
    val artistName: String,
    val monthlyListeners: String,
    val bio: String,
    val bannerUrl: String,
    val origin: String = "Perú",
    val formedYear: String = "1976",
    val genres: List<String> = listOf("Cumbia", "Música Latina")
)

object ArtistBioProvider {

    fun getArtistInfo(artistName: String?, fallbackArtworkUrl: String? = null): ArtistBioInfo {
        val name = artistName?.trim().orEmpty().ifBlank { "Artista Destacado" }
        val lower = name.lowercase()

        return when {
            lower.contains("agua marina") -> ArtistBioInfo(
                artistName = "Agua Marina",
                monthlyListeners = "2,485,320 oyentes mensuales",
                bio = "Agua Marina es la orquesta insignia y leyenda viva de la cumbia peruana, fundada el 30 de agosto de 1976 en Sechura, Piura, por los hermanos José y Manuel Quiroga Querevalú. Conocidos como «El agua más rica del Perú», revolucionaron la música popular con su estilo elegante, romántico y guitarras melódicas inconfundibles. Con más de 45 años de trayectoria y conciertos multitudinarios en América y Europa, han dejado himnos intergeneracionales como «Tu traición se acabó», «Paloma del alma mía», «Basta ya mi amor», «Así es el amor» y «Cenizas».",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1000&auto=format&fit=crop",
                origin = "Sechura, Piura, Perú",
                formedYear = "1976",
                genres = listOf("Cumbia Elegante", "Cumbia Peruana", "Salsa")
            )

            lower.contains("grupo 5") -> ArtistBioInfo(
                artistName = "Grupo 5",
                monthlyListeners = "3,120,450 oyentes mensuales",
                bio = "Fundado en Monsefú, Chiclayo en 1973 por Elmer y Víctor Yaipén, Grupo 5 es considerado «El Grupo de Oro del Perú». Liderado hoy por Christian Yaipén, ha llevado la cumbia peruana a escenarios masivos llenando estadios históricos con una impecable producción de nivel internacional y éxitos como «Motor y Motivo», «El Ritmo de mi Corazón» y «Cambio mi Corazón».",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1000&auto=format&fit=crop",
                origin = "Monsefú, Lambayeque, Perú",
                formedYear = "1973",
                genres = listOf("Cumbia Norteña", "Música Tropical")
            )

            lower.contains("ke personajes") -> ArtistBioInfo(
                artistName = "Ke Personajes",
                monthlyListeners = "8,950,200 oyentes mensuales",
                bio = "Formada en Concepción del Uruguay, Entre Ríos (Argentina) en 2020 y liderada por Emanuel Noir, Ke Personajes se convirtió en un fenómeno global de la cumbia villera y romántica con su voz apasionada y versiones virales de clásicos que han roto récords mundiales en streaming.",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1000&auto=format&fit=crop",
                origin = "Entre Ríos, Argentina",
                formedYear = "2020",
                genres = listOf("Cumbia Romántica", "Urbano")
            )

            lower.contains("ángeles azules") || lower.contains("angeles azules") -> ArtistBioInfo(
                artistName = "Los Ángeles Azules",
                monthlyListeners = "14,200,800 oyentes mensuales",
                bio = "Provenientes de Iztapalapa, Ciudad de México, Los Ángeles Azules son los máximos embajadores mundiales de la cumbia sonidera. Fundada por la familia Mejía Avante, su fusión con orquestas sinfónicas y grandes estrellas pop los consolidó como un referente indiscutible de la música latina.",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=1000&auto=format&fit=crop",
                origin = "Iztapalapa, CDMX, México",
                formedYear = "1976",
                genres = listOf("Cumbia Sonidera", "Pop Latino")
            )

            lower.contains("soda stereo") -> ArtistBioInfo(
                artistName = "Soda Stereo",
                monthlyListeners = "9,840,100 oyentes mensuales",
                bio = "Banda legendaria del rock en español formada en Buenos Aires en 1982 por Gustavo Cerati, Zeta Bosio y Charly Alberti. Considerada la banda más influyente y venerada del rock latinoamericano de todos los tiempos.",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1000&auto=format&fit=crop",
                origin = "Buenos Aires, Argentina",
                formedYear = "1982",
                genres = listOf("Rock en Español", "Post-Punk", "Pop Rock")
            )

            lower.contains("jósean log") || lower.contains("josean log") -> ArtistBioInfo(
                artistName = "Jósean Log",
                monthlyListeners = "2,110,600 oyentes mensuales",
                bio = "Cantautor mexicano independiente reconocido por su sonido acústico íntimo dominado por el ukelele, combinando pop tropical y reflexiones poéticas en himnos generacionales como «Chachachá» y «Doma».",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1000&auto=format&fit=crop",
                origin = "Cholula, Puebla, México",
                formedYear = "2015",
                genres = listOf("Indie Pop", "Folk Tropical")
            )

            lower.contains("coldplay") -> ArtistBioInfo(
                artistName = "Coldplay",
                monthlyListeners = "82,450,000 oyentes mensuales",
                bio = "Banda británica de pop rock formada en Londres en 1997 por Chris Martin, Jonny Buckland, Guy Berryman y Will Champion. Con más de 100 millones de discos vendidos y giras de estadios que han redefinido el espectáculo en vivo en todo el mundo.",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1000&auto=format&fit=crop",
                origin = "Londres, Reino Unido",
                formedYear = "1997",
                genres = listOf("Pop Rock", "Alternative")
            )

            lower.contains("armonía 10") || lower.contains("armonia 10") -> ArtistBioInfo(
                artistName = "Armonía 10",
                monthlyListeners = "1,780,000 oyentes mensuales",
                bio = "Conocida como «La Universidad de la Cumbia», fundada en Piura en 1972 por Juan de Dios Lozada Naquiche. Pioneros de la cumbia con vientos y percusión potente que forjaron clásicos absolutos como «El Cervecero» y «Siempre pierdo en el amor».",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1000&auto=format&fit=crop",
                origin = "Piura, Perú",
                formedYear = "1972",
                genres = listOf("Cumbia Piurana", "Cumbia Peruana")
            )

            lower.contains("corazón serrano") || lower.contains("corazon serrano") -> ArtistBioInfo(
                artistName = "Corazón Serrano",
                monthlyListeners = "2,650,000 oyentes mensuales",
                bio = "Orquesta de cumbia sanjuanera fundada en Bellavista de Cachiaco, Piura en 1993 por los hermanos Guerrero Neira. Han conquistado el continente entero gracias a sus armonías vocales y un sonido andino-norteño inconfundible.",
                bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1000&auto=format&fit=crop",
                origin = "Piura, Perú",
                formedYear = "1993",
                genres = listOf("Cumbia Sanjuanera", "Música Andina Tropical")
            )

            else -> {
                val seed = name.hashCode().absoluteValue
                val listenersCount = (120 + (seed % 850)) / 100f
                ArtistBioInfo(
                    artistName = name,
                    monthlyListeners = String.format("%.2fM oyentes mensuales", listenersCount),
                    bio = "$name es un artista destacado en el catálogo de streaming de Vicsmusic. Descubre su discografía oficial, sencillos más populares y canciones similares seleccionadas con audio en alta definición.",
                    bannerUrl = fallbackArtworkUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1000&auto=format&fit=crop",
                    origin = "Música Internacional",
                    formedYear = "2020",
                    genres = listOf("Éxitos Populares", "Streaming HD")
                )
            }
        }
    }
}
