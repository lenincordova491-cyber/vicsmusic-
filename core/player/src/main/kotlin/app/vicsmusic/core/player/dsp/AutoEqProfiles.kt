package app.vicsmusic.core.player.dsp

/**
 * Perfil de ecualización AutoEQ calibrado contra la curva objetivo Harman Target.
 */
data class AutoEqProfile(
    val id: String,
    val name: String,
    val brand: String,
    val gainsMb: List<Short> // Ganancias relativas para 5 bandas estándar (60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz)
)

object AutoEqPresets {
    val PROFILES = listOf(
        AutoEqProfile(
            id = "flat",
            name = "Respuesta Plana / Estudio",
            brand = "Referencia",
            gainsMb = listOf(0, 0, 0, 0, 0)
        ),
        AutoEqProfile(
            id = "sony_wh1000xm4",
            name = "Sony WH-1000XM4",
            brand = "Sony",
            gainsMb = listOf(-250, -150, 100, 300, -100) // Control de graves excesivos y claridad vocal
        ),
        AutoEqProfile(
            id = "sony_wh1000xm5",
            name = "Sony WH-1000XM5",
            brand = "Sony",
            gainsMb = listOf(-200, -100, 150, 250, 50)
        ),
        AutoEqProfile(
            id = "airpods_pro_2",
            name = "Apple AirPods Pro (2ª Gen)",
            brand = "Apple",
            gainsMb = listOf(150, 50, -50, 200, 100) // Extensión sub-bass y compensación de agudos
        ),
        AutoEqProfile(
            id = "galaxy_buds_2_pro",
            name = "Samsung Galaxy Buds 2 Pro",
            brand = "Samsung",
            gainsMb = listOf(100, 0, -100, 150, 200)
        ),
        AutoEqProfile(
            id = "sennheiser_hd600",
            name = "Sennheiser HD 600 / 650",
            brand = "Sennheiser",
            gainsMb = listOf(350, 150, 0, 50, 150) // Refuerzo sub-grave natural para auriculares abiertos
        ),
        AutoEqProfile(
            id = "bose_qc45",
            name = "Bose QuietComfort 45",
            brand = "Bose",
            gainsMb = listOf(-100, -50, 150, -150, 100)
        ),
        AutoEqProfile(
            id = "moondrop_chu",
            name = "Moondrop Chu / Aria (IEM)",
            brand = "Moondrop",
            gainsMb = listOf(200, 100, 0, -150, 50)
        ),
        AutoEqProfile(
            id = "bass_boost_harman",
            name = "Firma Harman + Graves Profundos",
            brand = "Perfil Vicsmusic",
            gainsMb = listOf(450, 250, 0, 150, 200)
        )
    )
}
