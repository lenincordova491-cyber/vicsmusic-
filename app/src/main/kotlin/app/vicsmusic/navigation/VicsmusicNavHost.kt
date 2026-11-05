package app.vicsmusic.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.vicsmusic.feature.home.HomeRoute
import app.vicsmusic.feature.library.LibraryRoute
import app.vicsmusic.feature.search.SearchRoute

@Composable
fun VicsmusicNavHost(
    navController: NavHostController,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = VicsmusicDestination.HOME.route,
        modifier = modifier
    ) {
        composable(VicsmusicDestination.HOME.route) {
            HomeRoute(onNavigateToPlayer = onNavigateToPlayer)
        }
        composable(VicsmusicDestination.SEARCH.route) {
            SearchRoute(onNavigateToPlayer = onNavigateToPlayer)
        }
        composable(VicsmusicDestination.LIBRARY.route) {
            LibraryRoute(onNavigateToPlayer = onNavigateToPlayer)
        }
    }
}
