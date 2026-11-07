package app.vicsmusic.feature.search

data class SearchCategory(
    val id: String,
    val title: String,
    val searchQuery: String,
    val startColor: Long,
    val endColor: Long,
    val iconEmoji: String
)

object SearchCategoryProvider {

    val categories = listOf(
        SearchCategory(
            id = "cumbia",
            title = "Cumbia",
            searchQuery = "Cumbia",
            startColor = 0xFFE64A19,
            endColor = 0xFFFF8A65,
            iconEmoji = "🪗"
        ),
        SearchCategory(
            id = "pop",
            title = "Pop Latino",
            searchQuery = "Pop Latino",
            startColor = 0xFF8E24AA,
            endColor = 0xFFBA68C8,
            iconEmoji = "🎤"
        ),
        SearchCategory(
            id = "electro",
            title = "Electrónica",
            searchQuery = "Electrónica",
            startColor = 0xFF00897B,
            endColor = 0xFF4DB6AC,
            iconEmoji = "⚡"
        ),
        SearchCategory(
            id = "rock",
            title = "Rock en Español",
            searchQuery = "Rock en Español",
            startColor = 0xFFC62828,
            endColor = 0xFFEF5350,
            iconEmoji = "🎸"
        ),
        SearchCategory(
            id = "salsa",
            title = "Salsa & Tropical",
            searchQuery = "Salsa",
            startColor = 0xFFF57C00,
            endColor = 0xFFFFB74D,
            iconEmoji = "🎺"
        ),
        SearchCategory(
            id = "top50",
            title = "Éxitos Top 50",
            searchQuery = "Top 50",
            startColor = 0xFF303F9F,
            endColor = 0xFF7986CB,
            iconEmoji = "🏆"
        ),
        SearchCategory(
            id = "podcasts",
            title = "Podcasts",
            searchQuery = "Podcast",
            startColor = 0xFF2E7D32,
            endColor = 0xFF81C784,
            iconEmoji = "🎙️"
        ),
        SearchCategory(
            id = "audiobooks",
            title = "Audiolibros",
            searchQuery = "Audiolibro",
            startColor = 0xFF455A64,
            endColor = 0xFF90A4AE,
            iconEmoji = "📚"
        ),
        SearchCategory(
            id = "urbano",
            title = "Urbano & Trap",
            searchQuery = "Urbano",
            startColor = 0xFFAD1457,
            endColor = 0xFFF06292,
            iconEmoji = "🔥"
        ),
        SearchCategory(
            id = "electrocumbia",
            title = "Electro Cumbia",
            searchQuery = "Electro Cumbia",
            startColor = 0xFF0097A7,
            endColor = 0xFF4DD0E1,
            iconEmoji = "🎛️"
        ),
        SearchCategory(
            id = "baladas",
            title = "Baladas del Recuerdo",
            searchQuery = "Baladas",
            startColor = 0xFF1565C0,
            endColor = 0xFF64B5F6,
            iconEmoji = "💫"
        ),
        SearchCategory(
            id = "indie",
            title = "Indie Alternativo",
            searchQuery = "Indie",
            startColor = 0xFF5D4037,
            endColor = 0xFFA1887F,
            iconEmoji = "🌙"
        )
    )

    val personalizedHashtags = listOf(
        "#Cumbia",
        "#ElectroCumbia",
        "#Electrónica",
        "#Rock",
        "#PopLatino",
        "#Salsa",
        "#Urbano",
        "#Top50",
        "#Indie"
    )
}
