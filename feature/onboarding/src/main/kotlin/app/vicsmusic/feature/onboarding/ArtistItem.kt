package app.vicsmusic.feature.onboarding

data class ArtistItem(
    val id: String,
    val name: String,
    val genre: String,
    val imageUrl: String,
    val similarArtistIds: List<String> = emptyList()
)

object ArtistGenres {
    val ALL = "Todos"
    val GENRES = listOf(
        "Todos",
        "Cumbia",
        "Salsa",
        "Urbano",
        "Rock en Español",
        "Rock & Metal",
        "Pop",
        "Regional Mexicano",
        "Trap & RKT",
        "Hip Hop",
        "Electrónica",
        "K-Pop",
        "R&B"
    )
}

object ArtistCatalog {
    val ALL_ARTISTS: List<ArtistItem> = listOf(
        // ==========================================
        // CUMBIA & TROPICAL (15)
        // ==========================================
        ArtistItem(
            id = "grupo_5",
            name = "Grupo 5",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/w_LWtxTDr44/hqdefault.jpg",
            similarArtistIds = listOf("ke_personajes", "angeles_azules", "agua_marina", "armonia_10", "corazon_serrano")
        ),
        ArtistItem(
            id = "ke_personajes",
            name = "Ke Personajes",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/E7wJTI-1dvQ/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "angeles_azules", "damas_gratis", "la_delio_valdez")
        ),
        ArtistItem(
            id = "angeles_azules",
            name = "Los Ángeles Azules",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/k76BgIb89-s/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "ke_personajes", "agua_marina", "los_palmeras")
        ),
        ArtistItem(
            id = "agua_marina",
            name = "Agua Marina",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/MSFR0st6yJM/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "armonia_10", "corazon_serrano")
        ),
        ArtistItem(
            id = "armonia_10",
            name = "Armonía 10",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/ROA6lTFZTbA/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "agua_marina", "caribenos_guadalupe")
        ),
        ArtistItem(
            id = "corazon_serrano",
            name = "Corazón Serrano",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/Hh0x6_X4-Zg/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "agua_marina", "papillon")
        ),
        ArtistItem(
            id = "la_delio_valdez",
            name = "La Delio Valdez",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/aZtF1pU671s/hqdefault.jpg",
            similarArtistIds = listOf("ke_personajes", "los_palmeras", "damas_gratis")
        ),
        ArtistItem(
            id = "damas_gratis",
            name = "Damas Gratis",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/H2gZvh3T3Jk/hqdefault.jpg",
            similarArtistIds = listOf("ke_personajes", "amar_azul", "los_palmeras")
        ),
        ArtistItem(
            id = "amar_azul",
            name = "Amar Azul",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/v5uL0vK-iJw/hqdefault.jpg",
            similarArtistIds = listOf("damas_gratis", "rafaga", "los_palmeras")
        ),
        ArtistItem(
            id = "los_palmeras",
            name = "Los Palmeras",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/p8U5m-lZ84s/hqdefault.jpg",
            similarArtistIds = listOf("angeles_azules", "ke_personajes", "la_delio_valdez")
        ),
        ArtistItem(
            id = "rafaga",
            name = "Ráfaga",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/F-ZpP1bS6qU/hqdefault.jpg",
            similarArtistIds = listOf("amar_azul", "los_palmeras", "grupo_5")
        ),
        ArtistItem(
            id = "hermanos_yaipen",
            name = "Hermanos Yaipén",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/gK9q0v0p67M/hqdefault.jpg",
            similarArtistIds = listOf("grupo_5", "armonia_10", "caribenos_guadalupe")
        ),
        ArtistItem(
            id = "caribenos_guadalupe",
            name = "Caribeños de Guadalupe",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/3N8b5zN0m8A/hqdefault.jpg",
            similarArtistIds = listOf("armonia_10", "agua_marina", "hermanos_yaipen")
        ),
        ArtistItem(
            id = "papillon",
            name = "Papillón",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/T8p8f5m_GZw/hqdefault.jpg",
            similarArtistIds = listOf("corazon_serrano", "agua_marina")
        ),
        ArtistItem(
            id = "rodrigo_tapari",
            name = "Rodrigo Tapari",
            genre = "Cumbia",
            imageUrl = "https://i.ytimg.com/vi/3R3T3U0uY8s/hqdefault.jpg",
            similarArtistIds = listOf("rafaga", "ke_personajes")
        ),

        // ==========================================
        // SALSA (15)
        // ==========================================
        ArtistItem(
            id = "hector_lavoe",
            name = "Héctor Lavoe",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/YwZ3R5z0G6A/hqdefault.jpg",
            similarArtistIds = listOf("willie_colon", "ruben_blades", "frankie_ruiz", "cheo_feliciano")
        ),
        ArtistItem(
            id = "marc_anthony",
            name = "Marc Anthony",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/yL_0C48p0Z0/hqdefault.jpg",
            similarArtistIds = listOf("gilberto_santa_rosa", "victor_manuelle", "jerry_rivera")
        ),
        ArtistItem(
            id = "ruben_blades",
            name = "Rubén Blades",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/N6eO8Y1ZqZs/hqdefault.jpg",
            similarArtistIds = listOf("hector_lavoe", "willie_colon", "cheo_feliciano")
        ),
        ArtistItem(
            id = "frankie_ruiz",
            name = "Frankie Ruiz",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/2sP8k0L3n3Y/hqdefault.jpg",
            similarArtistIds = listOf("hector_lavoe", "jerry_rivera", "eddie_santiago")
        ),
        ArtistItem(
            id = "gilberto_santa_rosa",
            name = "Gilberto Santa Rosa",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/7V8b9X1k9wQ/hqdefault.jpg",
            similarArtistIds = listOf("marc_anthony", "victor_manuelle", "el_gran_combo")
        ),
        ArtistItem(
            id = "willie_colon",
            name = "Willie Colón",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/3z2b1A9k8hY/hqdefault.jpg",
            similarArtistIds = listOf("hector_lavoe", "ruben_blades", "cheo_feliciano")
        ),
        ArtistItem(
            id = "victor_manuelle",
            name = "Victor Manuelle",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/5g1T0z9x8hY/hqdefault.jpg",
            similarArtistIds = listOf("marc_anthony", "gilberto_santa_rosa", "jerry_rivera")
        ),
        ArtistItem(
            id = "el_gran_combo",
            name = "El Gran Combo de Puerto Rico",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/4e8v7w6u5t4/hqdefault.jpg",
            similarArtistIds = listOf("gilberto_santa_rosa", "hector_lavoe", "grupo_niche")
        ),
        ArtistItem(
            id = "grupo_niche",
            name = "Grupo Niche",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/9x8y7z6w5v4/hqdefault.jpg",
            similarArtistIds = listOf("el_gran_combo", "joe_arroyo", "frankie_ruiz")
        ),
        ArtistItem(
            id = "jerry_rivera",
            name = "Jerry Rivera",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/1a2b3c4d5e6/hqdefault.jpg",
            similarArtistIds = listOf("frankie_ruiz", "marc_anthony", "victor_manuelle")
        ),
        ArtistItem(
            id = "celia_cruz",
            name = "Celia Cruz",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/7a6b5c4d3e2/hqdefault.jpg",
            similarArtistIds = listOf("hector_lavoe", "oscar_d_leon", "tito_puente")
        ),
        ArtistItem(
            id = "oscar_d_leon",
            name = "Oscar D'León",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/9k8j7h6g5f4/hqdefault.jpg",
            similarArtistIds = listOf("celia_cruz", "el_gran_combo", "grupo_niche")
        ),
        ArtistItem(
            id = "cheo_feliciano",
            name = "Cheo Feliciano",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/5t6y7u8i9o0/hqdefault.jpg",
            similarArtistIds = listOf("hector_lavoe", "ruben_blades", "willie_colon")
        ),
        ArtistItem(
            id = "tito_nieves",
            name = "Tito Nieves",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/0p9o8i7u6y5/hqdefault.jpg",
            similarArtistIds = listOf("marc_anthony", "gilberto_santa_rosa")
        ),
        ArtistItem(
            id = "la_india",
            name = "La India",
            genre = "Salsa",
            imageUrl = "https://i.ytimg.com/vi/4t3r2e1w0q9/hqdefault.jpg",
            similarArtistIds = listOf("celia_cruz", "marc_anthony", "victor_manuelle")
        ),

        // ==========================================
        // URBANO & REGGAETÓN (18)
        // ==========================================
        ArtistItem(
            id = "bad_bunny",
            name = "Bad Bunny",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/QpKkNIT84GY/hqdefault.jpg",
            similarArtistIds = listOf("feid", "karol_g", "rauw_alejandro", "mora", "myke_towers")
        ),
        ArtistItem(
            id = "feid",
            name = "Feid",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/ZA7ZKB8Mo9k/hqdefault.jpg",
            similarArtistIds = listOf("bad_bunny", "karol_g", "mora", "ryan_castro")
        ),
        ArtistItem(
            id = "karol_g",
            name = "Karol G",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/QCZZwZQ4qNs/hqdefault.jpg",
            similarArtistIds = listOf("bad_bunny", "feid", "shakira", "young_miko")
        ),
        ArtistItem(
            id = "rauw_alejandro",
            name = "Rauw Alejandro",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/bY_0Z9k8w7x/hqdefault.jpg",
            similarArtistIds = listOf("bad_bunny", "feid", "alvaro_diaz", "mora")
        ),
        ArtistItem(
            id = "myke_towers",
            name = "Myke Towers",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/GzN4-X3ePjU/hqdefault.jpg",
            similarArtistIds = listOf("bad_bunny", "quevedo", "eladio_carrion", "anuel_aa")
        ),
        ArtistItem(
            id = "daddy_yankee",
            name = "Daddy Yankee",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
            similarArtistIds = listOf("don_omar", "wisin_yandel", "ozuna", "bad_bunny")
        ),
        ArtistItem(
            id = "don_omar",
            name = "Don Omar",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/2c4P8z6w7eA/hqdefault.jpg",
            similarArtistIds = listOf("daddy_yankee", "wisin_yandel", "tito_el_bambino")
        ),
        ArtistItem(
            id = "wisin_yandel",
            name = "Wisin & Yandel",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/8s8A7g6f5d4/hqdefault.jpg",
            similarArtistIds = listOf("daddy_yankee", "don_omar", "ozuna")
        ),
        ArtistItem(
            id = "j_balvin",
            name = "J Balvin",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/zNpeK2w3g9A/hqdefault.jpg",
            similarArtistIds = listOf("maluma", "bad_bunny", "ozuna")
        ),
        ArtistItem(
            id = "maluma",
            name = "Maluma",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/4v5b6n7m8k9/hqdefault.jpg",
            similarArtistIds = listOf("j_balvin", "feid", "karol_g")
        ),
        ArtistItem(
            id = "ozuna",
            name = "Ozuna",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/9x8c7v6b5n4/hqdefault.jpg",
            similarArtistIds = listOf("bad_bunny", "anuel_aa", "daddy_yankee")
        ),
        ArtistItem(
            id = "anuel_aa",
            name = "Anuel AA",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/1q2w3e4r5t6/hqdefault.jpg",
            similarArtistIds = listOf("ozuna", "myke_towers", "eladio_carrion")
        ),
        ArtistItem(
            id = "quevedo",
            name = "Quevedo",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/A_g3lMcWKE0/hqdefault.jpg",
            similarArtistIds = listOf("bizarrap", "myke_towers", "mora")
        ),
        ArtistItem(
            id = "mora",
            name = "Mora",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/8u7y6t5r4e3/hqdefault.jpg",
            similarArtistIds = listOf("feid", "bad_bunny", "quevedo", "rauw_alejandro")
        ),
        ArtistItem(
            id = "eladio_carrion",
            name = "Eladio Carrión",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/3e4r5t6y7u8/hqdefault.jpg",
            similarArtistIds = listOf("myke_towers", "anuel_aa", "duki")
        ),
        ArtistItem(
            id = "young_miko",
            name = "Young Miko",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/6t7y8u9i0o1/hqdefault.jpg",
            similarArtistIds = listOf("karol_g", "feid", "bizarrap")
        ),
        ArtistItem(
            id = "ryan_castro",
            name = "Ryan Castro",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/0p1o2i3u4y5/hqdefault.jpg",
            similarArtistIds = listOf("blessd", "feid", "maluma")
        ),
        ArtistItem(
            id = "blessd",
            name = "Blessd",
            genre = "Urbano",
            imageUrl = "https://i.ytimg.com/vi/5t4r3e2w1q0/hqdefault.jpg",
            similarArtistIds = listOf("ryan_castro", "feid", "maluma")
        ),

        // ==========================================
        // TRAP & RKT (12)
        // ==========================================
        ArtistItem(
            id = "duki",
            name = "Duki",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/VqM1_R7F8Xo/hqdefault.jpg",
            similarArtistIds = listOf("bizarrap", "milo_j", "trueno", "ysy_a")
        ),
        ArtistItem(
            id = "bizarrap",
            name = "Bizarrap",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/A_g3lMcWKE0/hqdefault.jpg",
            similarArtistIds = listOf("duki", "milo_j", "quevedo", "tiago_pzk", "nicki_nicole")
        ),
        ArtistItem(
            id = "milo_j",
            name = "Milo J",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/K7kX8s3A9dM/hqdefault.jpg",
            similarArtistIds = listOf("bizarrap", "duki", "trueno", "tiago_pzk")
        ),
        ArtistItem(
            id = "trueno",
            name = "Trueno",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/2z9x8c7v6b5/hqdefault.jpg",
            similarArtistIds = listOf("duki", "milo_j", "tiago_pzk", "wos")
        ),
        ArtistItem(
            id = "nicki_nicole",
            name = "Nicki Nicole",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/7n8b9v0c1x2/hqdefault.jpg",
            similarArtistIds = listOf("bizarrap", "emilia", "trueno", "milo_j")
        ),
        ArtistItem(
            id = "tiago_pzk",
            name = "Tiago PZK",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/3m4k5j6h7g8/hqdefault.jpg",
            similarArtistIds = listOf("lit_killah", "duki", "milo_j", "emilia")
        ),
        ArtistItem(
            id = "emilia",
            name = "Emilia",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/9a8s7d6f5g4/hqdefault.jpg",
            similarArtistIds = listOf("tiago_pzk", "nicki_nicole", "duki")
        ),
        ArtistItem(
            id = "lit_killah",
            name = "Lit Killah",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/1q3e5r7t9y0/hqdefault.jpg",
            similarArtistIds = listOf("tiago_pzk", "duki", "trueno")
        ),
        ArtistItem(
            id = "ysy_a",
            name = "YSY A",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/2w4r6y8u0i1/hqdefault.jpg",
            similarArtistIds = listOf("duki", "neo_pistea", "trueno")
        ),
        ArtistItem(
            id = "callejero_fino",
            name = "Callejero Fino",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/4e6r8t0u2i3/hqdefault.jpg",
            similarArtistIds = listOf("l_gante", "ke_personajes")
        ),
        ArtistItem(
            id = "l_gante",
            name = "L-Gante",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/5r7t9y1u3i4/hqdefault.jpg",
            similarArtistIds = listOf("callejero_fino", "damas_gratis", "bizarrap")
        ),
        ArtistItem(
            id = "khea",
            name = "Khea",
            genre = "Trap & RKT",
            imageUrl = "https://i.ytimg.com/vi/6t8u0i2o4p5/hqdefault.jpg",
            similarArtistIds = listOf("duki", "tiago_pzk", "cazzu")
        ),

        // ==========================================
        // ROCK EN ESPAÑOL (15)
        // ==========================================
        ArtistItem(
            id = "soda_stereo",
            name = "Soda Stereo",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/T_FkEw27XJ0/hqdefault.jpg",
            similarArtistIds = listOf("gustavo_cerati", "los_prisioneros", "enanitos_verdes", "charly_garcia")
        ),
        ArtistItem(
            id = "gustavo_cerati",
            name = "Gustavo Cerati",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/eAO7CEcCd3s/hqdefault.jpg",
            similarArtistIds = listOf("soda_stereo", "charly_garcia", "fito_paez")
        ),
        ArtistItem(
            id = "los_prisioneros",
            name = "Los Prisioneros",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/B7kH8c1L3n4/hqdefault.jpg",
            similarArtistIds = listOf("soda_stereo", "cafe_tacvba", "enanitos_verdes")
        ),
        ArtistItem(
            id = "cafe_tacvba",
            name = "Café Tacvba",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/1t2y3u4i5o6/hqdefault.jpg",
            similarArtistIds = listOf("molotov", "zoe", "caifanes")
        ),
        ArtistItem(
            id = "enanitos_verdes",
            name = "Los Enanitos Verdes",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/5g7h8j9k0l1/hqdefault.jpg",
            similarArtistIds = listOf("soda_stereo", "heroes_del_silencio", "andres_calamaro")
        ),
        ArtistItem(
            id = "heroes_del_silencio",
            name = "Héroes del Silencio",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/9x8v7c6b5a4/hqdefault.jpg",
            similarArtistIds = listOf("soda_stereo", "bunbury", "enanitos_verdes")
        ),
        ArtistItem(
            id = "charly_garcia",
            name = "Charly García",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/3f4g5h6j7k8/hqdefault.jpg",
            similarArtistIds = listOf("fito_paez", "gustavo_cerati", "andres_calamaro")
        ),
        ArtistItem(
            id = "fito_paez",
            name = "Fito Páez",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/7j8k9l0z1x2/hqdefault.jpg",
            similarArtistIds = listOf("charly_garcia", "andres_calamaro", "gustavo_cerati")
        ),
        ArtistItem(
            id = "andres_calamaro",
            name = "Andrés Calamaro",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/2c3v4b5n6m7/hqdefault.jpg",
            similarArtistIds = listOf("fito_paez", "charly_garcia", "los_rodriguez")
        ),
        ArtistItem(
            id = "caifanes",
            name = "Caifanes",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/6b7n8m9l0k1/hqdefault.jpg",
            similarArtistIds = listOf("zoe", "cafe_tacvba", "heroes_del_silencio")
        ),
        ArtistItem(
            id = "zoe",
            name = "Zoé",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/0k1j2h3g4f5/hqdefault.jpg",
            similarArtistIds = listOf("caifanes", "cafe_tacvba", "babasonicos")
        ),
        ArtistItem(
            id = "babasonicos",
            name = "Babasónicos",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/4d5f6g7h8j9/hqdefault.jpg",
            similarArtistIds = listOf("zoe", "gustavo_cerati", "cuarteto_de_nos")
        ),
        ArtistItem(
            id = "molotov",
            name = "Molotov",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/8j9k0l1z2x3/hqdefault.jpg",
            similarArtistIds = listOf("cafe_tacvba", "los_prisioneros")
        ),
        ArtistItem(
            id = "cuarteto_de_nos",
            name = "El Cuarteto de Nos",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/2x3c4v5b6n7/hqdefault.jpg",
            similarArtistIds = listOf("babasonicos", "los_prisioneros")
        ),
        ArtistItem(
            id = "mana",
            name = "Maná",
            genre = "Rock en Español",
            imageUrl = "https://i.ytimg.com/vi/6n7m8l9k0j1/hqdefault.jpg",
            similarArtistIds = listOf("enanitos_verdes", "soda_stereo")
        ),

        // ==========================================
        // ROCK CLÁSICO & METAL (18)
        // ==========================================
        ArtistItem(
            id = "queen",
            name = "Queen",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
            similarArtistIds = listOf("beatles", "led_zeppelin", "pink_floyd")
        ),
        ArtistItem(
            id = "beatles",
            name = "The Beatles",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/NCtzkaL2t_Y/hqdefault.jpg",
            similarArtistIds = listOf("queen", "rolling_stones", "pink_floyd")
        ),
        ArtistItem(
            id = "pink_floyd",
            name = "Pink Floyd",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/y-E7_VHLvkE/hqdefault.jpg",
            similarArtistIds = listOf("led_zeppelin", "beatles", "queen")
        ),
        ArtistItem(
            id = "led_zeppelin",
            name = "Led Zeppelin",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/xbhCPt6PziU/hqdefault.jpg",
            similarArtistIds = listOf("pink_floyd", "queen", "ac_dc")
        ),
        ArtistItem(
            id = "ac_dc",
            name = "AC/DC",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/pAgnJDJN4VA/hqdefault.jpg",
            similarArtistIds = listOf("guns_n_roses", "metallica", "led_zeppelin")
        ),
        ArtistItem(
            id = "guns_n_roses",
            name = "Guns N' Roses",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/1w7OgIMMRc4/hqdefault.jpg",
            similarArtistIds = listOf("ac_dc", "aerosmith", "bon_jovi", "metallica")
        ),
        ArtistItem(
            id = "metallica",
            name = "Metallica",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/tAGnKpE4NCI/hqdefault.jpg",
            similarArtistIds = listOf("iron_maiden", "megadeth", "slipknot", "ac_dc")
        ),
        ArtistItem(
            id = "iron_maiden",
            name = "Iron Maiden",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/2G5rfPISIwo/hqdefault.jpg",
            similarArtistIds = listOf("metallica", "megadeth", "judas_priest")
        ),
        ArtistItem(
            id = "nirvana",
            name = "Nirvana",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/hTWKbfoikeg/hqdefault.jpg",
            similarArtistIds = listOf("linkin_park", "red_hot_chili_peppers", "green_day")
        ),
        ArtistItem(
            id = "linkin_park",
            name = "Linkin Park",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/eVTXPUF4Oz4/hqdefault.jpg",
            similarArtistIds = listOf("system_of_a_down", "nirvana", "slipknot")
        ),
        ArtistItem(
            id = "red_hot_chili_peppers",
            name = "Red Hot Chili Peppers",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/GLvohMXgcBo/hqdefault.jpg",
            similarArtistIds = listOf("nirvana", "green_day", "linkin_park")
        ),
        ArtistItem(
            id = "rolling_stones",
            name = "The Rolling Stones",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/O4irXQhgMqg/hqdefault.jpg",
            similarArtistIds = listOf("beatles", "led_zeppelin", "aerosmith")
        ),
        ArtistItem(
            id = "aerosmith",
            name = "Aerosmith",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/h3Yrhv33Ix8/hqdefault.jpg",
            similarArtistIds = listOf("guns_n_roses", "bon_jovi", "ac_dc")
        ),
        ArtistItem(
            id = "bon_jovi",
            name = "Bon Jovi",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/lDK9QqIzhwk/hqdefault.jpg",
            similarArtistIds = listOf("aerosmith", "guns_n_roses", "queen")
        ),
        ArtistItem(
            id = "green_day",
            name = "Green Day",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/NUTGr5t3Mo8/hqdefault.jpg",
            similarArtistIds = listOf("nirvana", "linkin_park", "red_hot_chili_peppers")
        ),
        ArtistItem(
            id = "system_of_a_down",
            name = "System of a Down",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/CSvFpBOe8eY/hqdefault.jpg",
            similarArtistIds = listOf("linkin_park", "slipknot", "metallica")
        ),
        ArtistItem(
            id = "megadeth",
            name = "Megadeth",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/7cxlpx3M3NQ/hqdefault.jpg",
            similarArtistIds = listOf("metallica", "iron_maiden")
        ),
        ArtistItem(
            id = "slipknot",
            name = "Slipknot",
            genre = "Rock & Metal",
            imageUrl = "https://i.ytimg.com/vi/5abamRO41fE/hqdefault.jpg",
            similarArtistIds = listOf("system_of_a_down", "metallica", "linkin_park")
        ),

        // ==========================================
        // POP GLOBAL (16)
        // ==========================================
        ArtistItem(
            id = "the_weeknd",
            name = "The Weeknd",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
            similarArtistIds = listOf("bruno_mars", "dua_lipa", "billie_eilish", "ariana_grande")
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
            similarArtistIds = listOf("the_weeknd", "sabrina_carpenter", "taylor_swift", "olivia_rodrigo")
        ),
        ArtistItem(
            id = "billie_eilish",
            name = "Billie Eilish",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
            similarArtistIds = listOf("the_weeknd", "taylor_swift", "sabrina_carpenter", "olivia_rodrigo")
        ),
        ArtistItem(
            id = "taylor_swift",
            name = "Taylor Swift",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/ic8j13piAhQ/hqdefault.jpg",
            similarArtistIds = listOf("billie_eilish", "sabrina_carpenter", "ed_sheeran", "olivia_rodrigo")
        ),
        ArtistItem(
            id = "sabrina_carpenter",
            name = "Sabrina Carpenter",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/eVli-tstM5E/hqdefault.jpg",
            similarArtistIds = listOf("dua_lipa", "taylor_swift", "billie_eilish", "olivia_rodrigo")
        ),
        ArtistItem(
            id = "ed_sheeran",
            name = "Ed Sheeran",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
            similarArtistIds = listOf("taylor_swift", "bruno_mars", "harry_styles")
        ),
        ArtistItem(
            id = "ariana_grande",
            name = "Ariana Grande",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/ffxKSjUwKdU/hqdefault.jpg",
            similarArtistIds = listOf("the_weeknd", "dua_lipa", "billie_eilish")
        ),
        ArtistItem(
            id = "olivia_rodrigo",
            name = "Olivia Rodrigo",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/gNi_6U5Pm_o/hqdefault.jpg",
            similarArtistIds = listOf("taylor_swift", "sabrina_carpenter", "billie_eilish")
        ),
        ArtistItem(
            id = "harry_styles",
            name = "Harry Styles",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/H5v3kku4y6Q/hqdefault.jpg",
            similarArtistIds = listOf("ed_sheeran", "taylor_swift", "dua_lipa")
        ),
        ArtistItem(
            id = "adele",
            name = "Adele",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg",
            similarArtistIds = listOf("ed_sheeran", "taylor_swift", "bruno_mars")
        ),
        ArtistItem(
            id = "lady_gaga",
            name = "Lady Gaga",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/bo_efYhAK2A/hqdefault.jpg",
            similarArtistIds = listOf("ariana_grande", "dua_lipa", "bruno_mars")
        ),
        ArtistItem(
            id = "justin_bieber",
            name = "Justin Bieber",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/kffacxfA7G4/hqdefault.jpg",
            similarArtistIds = listOf("ed_sheeran", "the_weeknd", "shawn_mendes")
        ),
        ArtistItem(
            id = "katy_perry",
            name = "Katy Perry",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/CevxZvSJLk8/hqdefault.jpg",
            similarArtistIds = listOf("taylor_swift", "lady_gaga", "rihanna")
        ),
        ArtistItem(
            id = "rihanna",
            name = "Rihanna",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/0RyInjfgNc4/hqdefault.jpg",
            similarArtistIds = listOf("the_weeknd", "katy_perry", "ariana_grande")
        ),
        ArtistItem(
            id = "coldplay",
            name = "Coldplay",
            genre = "Pop",
            imageUrl = "https://i.ytimg.com/vi/dvgZkm1xWPE/hqdefault.jpg",
            similarArtistIds = listOf("imagine_dragons", "ed_sheeran", "queen")
        ),

        // ==========================================
        // REGIONAL MEXICANO (12)
        // ==========================================
        ArtistItem(
            id = "peso_pluma",
            name = "Peso Pluma",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/h7UID2FkR4k/hqdefault.jpg",
            similarArtistIds = listOf("natanael_cano", "fuerza_regida", "junior_h", "grupo_frontera")
        ),
        ArtistItem(
            id = "natanael_cano",
            name = "Natanael Cano",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/K8g3lM8k9j0/hqdefault.jpg",
            similarArtistIds = listOf("peso_pluma", "junior_h", "fuerza_regida")
        ),
        ArtistItem(
            id = "fuerza_regida",
            name = "Fuerza Regida",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/2v3b4n5m6k7/hqdefault.jpg",
            similarArtistIds = listOf("peso_pluma", "grupo_frontera", "natanael_cano")
        ),
        ArtistItem(
            id = "junior_h",
            name = "Junior H",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/7j8k9l0p1o2/hqdefault.jpg",
            similarArtistIds = listOf("peso_pluma", "natanael_cano", "eslabon_armado")
        ),
        ArtistItem(
            id = "carin_leon",
            name = "Carín León",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/1q2w3e4r5t6/hqdefault.jpg",
            similarArtistIds = listOf("grupo_frontera", "christian_nodal", "banda_ms")
        ),
        ArtistItem(
            id = "grupo_frontera",
            name = "Grupo Frontera",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/9x8c7v6b5n4/hqdefault.jpg",
            similarArtistIds = listOf("carin_leon", "peso_pluma", "fuerza_regida")
        ),
        ArtistItem(
            id = "christian_nodal",
            name = "Christian Nodal",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/4e5r6t7y8u9/hqdefault.jpg",
            similarArtistIds = listOf("carin_leon", "banda_ms", "calibre_50")
        ),
        ArtistItem(
            id = "banda_ms",
            name = "Banda MS",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/0o1p2o3i4u5/hqdefault.jpg",
            similarArtistIds = listOf("calibre_50", "christian_nodal", "carin_leon")
        ),
        ArtistItem(
            id = "calibre_50",
            name = "Calibre 50",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/5y6u7i8o9p0/hqdefault.jpg",
            similarArtistIds = listOf("banda_ms", "los_dos_carnales")
        ),
        ArtistItem(
            id = "los_dos_carnales",
            name = "Los Dos Carnales",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/1a3s5d7f9g0/hqdefault.jpg",
            similarArtistIds = listOf("calibre_50", "banda_ms")
        ),
        ArtistItem(
            id = "eslabon_armado",
            name = "Eslabon Armado",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/2s4d6f8g0h1/hqdefault.jpg",
            similarArtistIds = listOf("peso_pluma", "junior_h")
        ),
        ArtistItem(
            id = "marca_registrada",
            name = "Marca Registrada",
            genre = "Regional Mexicano",
            imageUrl = "https://i.ytimg.com/vi/3d5f7g9h1j2/hqdefault.jpg",
            similarArtistIds = listOf("fuerza_regida", "grupo_frontera")
        ),

        // ==========================================
        // HIP HOP & RAP (14)
        // ==========================================
        ArtistItem(
            id = "kendrick_lamar",
            name = "Kendrick Lamar",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/tvTRZJ-4EyI/hqdefault.jpg",
            similarArtistIds = listOf("drake", "j_cole", "travis_scott", "kanye_west")
        ),
        ArtistItem(
            id = "drake",
            name = "Drake",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/uxpDa-c-4Mc/hqdefault.jpg",
            similarArtistIds = listOf("kendrick_lamar", "travis_scott", "future", "post_malone")
        ),
        ArtistItem(
            id = "eminem",
            name = "Eminem",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/_Yhyp-_hX2s/hqdefault.jpg",
            similarArtistIds = listOf("50_cent", "snoop_dogg", "tupac", "kanye_west")
        ),
        ArtistItem(
            id = "travis_scott",
            name = "Travis Scott",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/6ONRf7h3Mdk/hqdefault.jpg",
            similarArtistIds = listOf("kanye_west", "drake", "metro_boomin", "future")
        ),
        ArtistItem(
            id = "kanye_west",
            name = "Kanye West",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/PsO6Zn4V07g/hqdefault.jpg",
            similarArtistIds = listOf("travis_scott", "kendrick_lamar", "drake")
        ),
        ArtistItem(
            id = "post_malone",
            name = "Post Malone",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/UYwF-jdcVjY/hqdefault.jpg",
            similarArtistIds = listOf("drake", "travis_scott", "the_weeknd")
        ),
        ArtistItem(
            id = "j_cole",
            name = "J. Cole",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/e8CLsYzE5Vk/hqdefault.jpg",
            similarArtistIds = listOf("kendrick_lamar", "drake")
        ),
        ArtistItem(
            id = "future",
            name = "Future",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/4_iC0MyIykM/hqdefault.jpg",
            similarArtistIds = listOf("metro_boomin", "travis_scott", "drake")
        ),
        ArtistItem(
            id = "metro_boomin",
            name = "Metro Boomin",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/M4G1p6F4u4A/hqdefault.jpg",
            similarArtistIds = listOf("future", "travis_scott", "21_savage")
        ),
        ArtistItem(
            id = "snoop_dogg",
            name = "Snoop Dogg",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/0C58ttB2-Qg/hqdefault.jpg",
            similarArtistIds = listOf("eminem", "tupac", "50_cent")
        ),
        ArtistItem(
            id = "tupac",
            name = "2Pac",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/41qC3w3UUkU/hqdefault.jpg",
            similarArtistIds = listOf("snoop_dogg", "eminem", "notorious_big")
        ),
        ArtistItem(
            id = "50_cent",
            name = "50 Cent",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/5qm8PH4xAss/hqdefault.jpg",
            similarArtistIds = listOf("eminem", "snoop_dogg")
        ),
        ArtistItem(
            id = "tyler_the_creator",
            name = "Tyler, The Creator",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/HmAsUQEFYGI/hqdefault.jpg",
            similarArtistIds = listOf("kendrick_lamar", "frank_ocean")
        ),
        ArtistItem(
            id = "lil_nas_x",
            name = "Lil Nas X",
            genre = "Hip Hop",
            imageUrl = "https://i.ytimg.com/vi/w2Ov5jzm3j8/hqdefault.jpg",
            similarArtistIds = listOf("post_malone", "drake")
        ),

        // ==========================================
        // K-POP (10)
        // ==========================================
        ArtistItem(
            id = "bts",
            name = "BTS",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/gdZLi9oWNZg/hqdefault.jpg",
            similarArtistIds = listOf("blackpink", "stray_kids", "txt")
        ),
        ArtistItem(
            id = "blackpink",
            name = "BLACKPINK",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/2S24h707N3s/hqdefault.jpg",
            similarArtistIds = listOf("bts", "twice", "newjeans", "aespa")
        ),
        ArtistItem(
            id = "stray_kids",
            name = "Stray Kids",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/TQTlCHxyuu8/hqdefault.jpg",
            similarArtistIds = listOf("bts", "txt", "seventeen")
        ),
        ArtistItem(
            id = "newjeans",
            name = "NewJeans",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/js1CtxSY38I/hqdefault.jpg",
            similarArtistIds = listOf("blackpink", "twice", "le_sserafim", "aespa")
        ),
        ArtistItem(
            id = "twice",
            name = "TWICE",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/f5_wn8mexmM/hqdefault.jpg",
            similarArtistIds = listOf("blackpink", "newjeans", "itzy")
        ),
        ArtistItem(
            id = "txt",
            name = "TOMORROW X TOGETHER",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/Vd9QkWsd5p4/hqdefault.jpg",
            similarArtistIds = listOf("bts", "stray_kids", "enhypen")
        ),
        ArtistItem(
            id = "seventeen",
            name = "SEVENTEEN",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/R9VDPMk5HG0/hqdefault.jpg",
            similarArtistIds = listOf("stray_kids", "bts", "txt")
        ),
        ArtistItem(
            id = "aespa",
            name = "aespa",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/4TWR90KJl84/hqdefault.jpg",
            similarArtistIds = listOf("blackpink", "newjeans", "itzy")
        ),
        ArtistItem(
            id = "itzy",
            name = "ITZY",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/fE2h3lGlOsk/hqdefault.jpg",
            similarArtistIds = listOf("twice", "aespa", "le_sserafim")
        ),
        ArtistItem(
            id = "le_sserafim",
            name = "LE SSERAFIM",
            genre = "K-Pop",
            imageUrl = "https://i.ytimg.com/vi/pyf8cbqyfPs/hqdefault.jpg",
            similarArtistIds = listOf("newjeans", "itzy", "blackpink")
        ),

        // ==========================================
        // ELECTRÓNICA & EDM (12)
        // ==========================================
        ArtistItem(
            id = "avicii",
            name = "Avicii",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/IcrbM1l_BoI/hqdefault.jpg",
            similarArtistIds = listOf("david_guetta", "calvin_harris", "martin_garrix", "kygo")
        ),
        ArtistItem(
            id = "david_guetta",
            name = "David Guetta",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/JRfuAukYTKg/hqdefault.jpg",
            similarArtistIds = listOf("calvin_harris", "tiesto", "avicii")
        ),
        ArtistItem(
            id = "calvin_harris",
            name = "Calvin Harris",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/ebXbLfLAC34/hqdefault.jpg",
            similarArtistIds = listOf("david_guetta", "avicii", "tiesto")
        ),
        ArtistItem(
            id = "daft_punk",
            name = "Daft Punk",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/5NV6Rdv1a3I/hqdefault.jpg",
            similarArtistIds = listOf("avicii", "calvin_harris", "swedish_house_mafia")
        ),
        ArtistItem(
            id = "martin_garrix",
            name = "Martin Garrix",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/gCYcTmT9wx4/hqdefault.jpg",
            similarArtistIds = listOf("tiesto", "alok", "marshmello", "avicii")
        ),
        ArtistItem(
            id = "tiesto",
            name = "Tiësto",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/1_4ELAxKrDc/hqdefault.jpg",
            similarArtistIds = listOf("david_guetta", "martin_garrix", "alok")
        ),
        ArtistItem(
            id = "marshmello",
            name = "Marshmello",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/ALZHF5UqnU4/hqdefault.jpg",
            similarArtistIds = listOf("martin_garrix", "the_chainsmokers", "alan_walker")
        ),
        ArtistItem(
            id = "alok",
            name = "Alok",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/0p1q2w3e4r5/hqdefault.jpg",
            similarArtistIds = listOf("martin_garrix", "tiesto", "david_guetta")
        ),
        ArtistItem(
            id = "skrillex",
            name = "Skrillex",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/WSeNSzJ2-Jw/hqdefault.jpg",
            similarArtistIds = listOf("marshmello", "daft_punk", "diplo")
        ),
        ArtistItem(
            id = "swedish_house_mafia",
            name = "Swedish House Mafia",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/1y6smkh6c-0/hqdefault.jpg",
            similarArtistIds = listOf("avicii", "david_guetta", "tiesto")
        ),
        ArtistItem(
            id = "kygo",
            name = "Kygo",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/bM7SZ5SBzyY/hqdefault.jpg",
            similarArtistIds = listOf("avicii", "marshmello", "the_chainsmokers")
        ),
        ArtistItem(
            id = "the_chainsmokers",
            name = "The Chainsmokers",
            genre = "Electrónica",
            imageUrl = "https://i.ytimg.com/vi/PT2_F-1esPk/hqdefault.jpg",
            similarArtistIds = listOf("marshmello", "kygo", "calvin_harris")
        ),

        // ==========================================
        // R&B & SOUL (10)
        // ==========================================
        ArtistItem(
            id = "sza",
            name = "SZA",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/SQnc1Q3iFuo/hqdefault.jpg",
            similarArtistIds = listOf("frank_ocean", "giveon", "h_e_r", "daniel_caesar")
        ),
        ArtistItem(
            id = "frank_ocean",
            name = "Frank Ocean",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/TMfPJT4XjGQ/hqdefault.jpg",
            similarArtistIds = listOf("sza", "daniel_caesar", "steve_lacy")
        ),
        ArtistItem(
            id = "alicia_keys",
            name = "Alicia Keys",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/Ju8Hr50Ckwk/hqdefault.jpg",
            similarArtistIds = listOf("john_legend", "h_e_r", "sza")
        ),
        ArtistItem(
            id = "daniel_caesar",
            name = "Daniel Caesar",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/uQZX46328hs/hqdefault.jpg",
            similarArtistIds = listOf("frank_ocean", "giveon", "sza")
        ),
        ArtistItem(
            id = "giveon",
            name = "Giveon",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/waBF2P3oYQ8/hqdefault.jpg",
            similarArtistIds = listOf("daniel_caesar", "sza", "khalid")
        ),
        ArtistItem(
            id = "h_e_r",
            name = "H.E.R.",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/8s8z7x6c5v4/hqdefault.jpg",
            similarArtistIds = listOf("sza", "alicia_keys", "daniel_caesar")
        ),
        ArtistItem(
            id = "khalid",
            name = "Khalid",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/IPfJnp1guPc/hqdefault.jpg",
            similarArtistIds = listOf("giveon", "daniel_caesar", "post_malone")
        ),
        ArtistItem(
            id = "john_legend",
            name = "John Legend",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/450p7goxZqg/hqdefault.jpg",
            similarArtistIds = listOf("alicia_keys", "bruno_mars")
        ),
        ArtistItem(
            id = "chris_brown",
            name = "Chris Brown",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/zK0S3T7v6n0/hqdefault.jpg",
            similarArtistIds = listOf("the_weeknd", "bruno_mars")
        ),
        ArtistItem(
            id = "steve_lacy",
            name = "Steve Lacy",
            genre = "R&B",
            imageUrl = "https://i.ytimg.com/vi/VF-r5TtlT9w/hqdefault.jpg",
            similarArtistIds = listOf("frank_ocean", "sza", "tyler_the_creator")
        )
    )
}
