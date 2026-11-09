package app.vicsmusic.core.ui.component

object ThumbnailUrlOptimizer {
    private val GOOGLE_IMAGE_PARAM_REGEX = Regex("=w\\d+-h\\d+.*|=s\\d+.*")

    fun optimize(url: String?): String? {
        if (url.isNullOrBlank()) return null

        var optimized = url

        // Normaliza thumbnails de i.ytimg.com o img.youtube.com a hqdefault.jpg
        if (optimized.contains("i.ytimg.com") || optimized.contains("img.youtube.com")) {
            if (optimized.endsWith("/default.jpg") || optimized.endsWith("/mqdefault.jpg")) {
                optimized = optimized.substringBeforeLast("/") + "/hqdefault.jpg"
            }
        }

        // Normaliza sufijos de resolución en URLs de Google (lh3.googleusercontent.com, etc.)
        if (optimized.contains("googleusercontent.com") || optimized.contains("ggpht.com")) {
            optimized = if (GOOGLE_IMAGE_PARAM_REGEX.containsMatchIn(optimized)) {
                optimized.replace(GOOGLE_IMAGE_PARAM_REGEX, "=w544-h544-l90-rj")
            } else if (!optimized.contains("=")) {
                "$optimized=w544-h544-l90-rj"
            } else {
                optimized
            }
        }

        return optimized
    }
}
