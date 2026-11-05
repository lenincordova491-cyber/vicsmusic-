package app.vicsmusic.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import app.vicsmusic.core.ui.R

enum class VicsmusicDestination(
    val route: String,
    val labelResId: Int,
    val icon: ImageVector
) {
    HOME(
        route = "home",
        labelResId = R.string.nav_home,
        icon = Icons.Default.Home
    ),
    SEARCH(
        route = "search",
        labelResId = R.string.nav_search,
        icon = Icons.Default.Search
    ),
    LIBRARY(
        route = "library",
        labelResId = R.string.nav_library,
        icon = Icons.Default.LibraryMusic
    )
}
