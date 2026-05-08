package com.vunbo.watchtogether.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object Live : Screen("live")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data object RankList : Screen("rank_list/{rankKey}") {
        fun createRoute(rankKey: String) = "rank_list/${Uri.encode(rankKey)}"
    }

    data object Search : Screen("search?query={query}") {
        private const val baseRoute = "search"

        fun createRoute(query: String? = null): String {
            return if (query.isNullOrBlank()) {
                baseRoute
            } else {
                "$baseRoute?query=${Uri.encode(query)}"
            }
        }
    }

    data object ResourcePicker : Screen("resource_picker/{query}") {
        fun createRoute(query: String) = "resource_picker/${Uri.encode(query)}"
    }

    data object Detail : Screen("detail/{sourceKey}/{vodId}") {
        fun createRoute(sourceKey: String, vodId: String) =
            "detail/${Uri.encode(sourceKey)}/${Uri.encode(vodId)}"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "首页", Icons.Filled.Home),
    BottomNavItem(Screen.Live, "直播", Icons.Filled.LiveTv),
    BottomNavItem(Screen.Library, "历史/收藏", Icons.Filled.Bookmarks),
    BottomNavItem(Screen.Settings, "设置", Icons.Filled.Settings)
)
