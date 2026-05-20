package com.vunbo.watchtogether.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vunbo.watchtogether.ui.detail.DetailScreen
import com.vunbo.watchtogether.ui.home.HomeScreen
import com.vunbo.watchtogether.ui.home.RankListScreen
import com.vunbo.watchtogether.ui.library.LibraryScreen
import com.vunbo.watchtogether.ui.live.LiveScreen
import com.vunbo.watchtogether.ui.player.PlayerViewModel
import com.vunbo.watchtogether.ui.search.ResourcePickerScreen
import com.vunbo.watchtogether.ui.search.SearchScreen
import com.vunbo.watchtogether.ui.settings.SettingsScreen

@Composable
fun WatchTogetherNavGraph(
    navController: NavHostController,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sharedPlayerViewModel: PlayerViewModel = viewModel()
    val remoteTarget by sharedPlayerViewModel.remoteNavigationTarget.collectAsState()

    LaunchedEffect(remoteTarget) {
        val target = remoteTarget ?: return@LaunchedEffect
        navController.navigate(Screen.Detail.createRoute(target.sourceKey, target.vodId)) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onVideoClick = { query ->
                    navController.navigate(Screen.Search.createRoute(query))
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.createRoute())
                },
                onRankMoreClick = { rankKey ->
                    navController.navigate(Screen.RankList.createRoute(rankKey))
                }
            )
        }

        composable(
            route = Screen.RankList.route,
            arguments = listOf(navArgument("rankKey") { type = NavType.StringType })
        ) {
            RankListScreen(
                onBack = { navController.popBackStack() },
                onVideoClick = { query ->
                    navController.navigate(Screen.Search.createRoute(query))
                }
            )
        }

        composable(Screen.Live.route) {
            LiveScreen()
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onVideoClick = { sourceKey, vodId ->
                    navController.navigate(Screen.Detail.createRoute(sourceKey, vodId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onCheckUpdate = onCheckUpdate)
        }

        composable(
            route = Screen.Search.route,
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query").orEmpty()
            SearchScreen(
                onVideoClick = { sourceKey, vodId ->
                    navController.navigate(Screen.Detail.createRoute(sourceKey, vodId))
                },
                onBack = { navController.popBackStack() },
                initialQuery = query
            )
        }

        composable(
            route = Screen.ResourcePicker.route,
            arguments = listOf(navArgument("query") { type = NavType.StringType })
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query").orEmpty()
            ResourcePickerScreen(
                query = query,
                onVideoClick = { sourceKey, vodId ->
                    navController.navigate(Screen.Detail.createRoute(sourceKey, vodId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("sourceKey") { type = NavType.StringType },
                navArgument("vodId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sourceKey = backStackEntry.arguments?.getString("sourceKey") ?: ""
            val vodId = backStackEntry.arguments?.getString("vodId") ?: ""
            DetailScreen(
                sourceKey = sourceKey,
                vodId = vodId,
                onBack = { navController.popBackStack() },
                onQuickSearch = { query ->
                    navController.navigate(Screen.ResourcePicker.createRoute(query))
                },
                playerViewModel = sharedPlayerViewModel
            )
        }
    }
}
