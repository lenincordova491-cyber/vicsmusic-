package app.vicsmusic.feature.onboarding

data class ArtistItem(
    val id: String,
    val name: String,
    val genre: String,
    val imageUrl: String,
    val similarArtistIds: List<String> = emptyList()
)

object ArtistCatalog {
    val ALL_ARTISTS: List<ArtistItem> = listOf(
        // Cumbia
        ArtistItem(
            id = "grupo_5",
            name = "Grupo 5",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/w_LWtxTDr44/hqdefault.jpg",
            similarArtistIds = listOf("ke_personajes", "angeles_azules", "agua_marina", "armonia_10")
        ),
        ArtistItem(
            id = "ke_personajes",
            name = "Ke Personajes",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/E7wJTI-1dvQ/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "angeles_azules", "agua_marina")
        ),
        ArtistItem(
            id = "angeles_azules",
            name = "Los Ángeles Azules",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/k76BgIb89-s/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "ke_personajes", "agua_marina")
        ),
        ArtistItem(
            id = "agua_marina",
            name = "Agua Marina",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/MSFR0st6yJM/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "armonia_10")
        ),
        ArtistItem(
            id = "armonia_10",
            name = "Armonía 10",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/ROA6lTFZTbA/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "agua_marina")
        ),

        // Rock / Alternativo
        ArtistItem(
            id = "coldplay",
            name = "Coldplay",
            genre = "Rock",
            imageUrl = "https://i.ytimg.com/vi/dvgZkm1xWPE/hqdefault.jpg",
            similarArtistIds = listOf("imagine_dragons", "queen", "soda_stereo")
        ),
        ArtistItem(
            id = "imagine_dragons",
            name = "Imagine Dragons",
            genre = "Rock",
            imageUrl = "https://i.ytimg.com/vi/7wtfhZwyrcc/hqdefault.jpg",
            similarArtistIds = listOf("coldplay", "queen")
        ),
        ArtistItem(
            id = "queen",
            name = "Queen",
            genre = "Rock",
            imageUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
            similarArtistIds = listOf("coldplay", "soda_stereo")
        ),
        ArtistItem(
            id = "soda_stereo",
            name = "Soda Stereo",
            genre = "Rock",
            imageUrl = "https://i.ytimg.com/vi/T_FkEw27XJ0/hqdefault.jpg",
            similarArtistIds = listOf("queen", "coldplay")
        ),
        ArtistItem(
            id = "smashing_pumpkins",
            name = "The Smashing Pumpkins",
            genre = "Rock",
            imageUrl = "https://i.ytimg.com/vi/dvgZkm1xWPE/hqdefault.jpg",
            similarArtistIds = listOf("coldplay", "queen")
        ),

        // Pop
        ArtistItem(
            id = "the_weeknd",
            name = "The Weeknd",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
            similarArtistIds = listOf("bruno_mars", "dua_lipa", "billie_eilish")
        ),
        ArtistItem(
            id = "bruno_mars",
            name = "Bruno Mars",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/UqyT8IEBkvY/hqdefault.jpg",
            similarArtistIds = listOf("the_weeknd", "dua_lipa", "ed_sheeran")
        ),
        ArtistItem(
            id = "dua_lipa",
            name = "Dua Lipa",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
            similarArtistIds = listOf("the_weeknd", "sabrina_carpenter", "taylor_swift")
        ),
        ArtistItem(
            id = "billie_eilish",
            name = "Billie Eilish",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
            similarArtistIds = listOf("the_weeknd", "taylor_swift", "sabrina_carpenter")
        ),
        ArtistItem(
            id = "taylor_swift",
            name = "Taylor Swift",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/ic8j13piAhQ/hqdefault.jpg",
            similarArtistIds = listOf("billie_eilish", "sabrina_carpenter", "ed_sheeran")
        ),
        ArtistItem(
            id = "sabrina_carpenter",
            name = "Sabrina Carpenter",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/eVli-tstM5E/hqdefault.jpg",
            similarArtistIds = listOf("dua_lipa", "taylor_swift", "billie_eilish")
        ),

        // Urbano
        ArtistItem(
            id = "bad_bunny",
            name = "Bad Bunny",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/QpKkNIT84GY/hqdefault.jpg",
            similarArtistIds = listOf("feid", "karol_g")
        ),
        ArtistItem(
            id = "feid",
            name = "Feid",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/ZA7ZKB8Mo9k/hqdefault.jpg",
            similarArtistIds = listOf("bad_bunny", "karol_g")
        ),
        ArtistItem(
            id = "karol_g",
            name = "Karol G",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/QCZZwZQ4qNs/hqdefault.jpg",
            similarArtistIds = listOf("bad_bunny", "feid")
        )
    )
}
