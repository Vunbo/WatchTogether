package com.vunbo.watchtogether

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vunbo.watchtogether.data.util.AppEvent
import com.vunbo.watchtogether.data.util.AppEventBus
import com.vunbo.watchtogether.navigation.Screen
import androidx.navigation.compose.rememberNavController
import com.vunbo.watchtogether.navigation.WatchTogetherBottomBar
import com.vunbo.watchtogether.navigation.WatchTogetherNavGraph
import com.vunbo.watchtogether.ui.theme.WatchTogetherTheme
import com.vunbo.watchtogether.ui.update.UpdateDialog
import com.vunbo.watchtogether.ui.update.UpdateMessageDialog
import com.vunbo.watchtogether.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    private val _pictureInPictureMode = MutableStateFlow(false)
    val pictureInPictureMode: StateFlow<Boolean> = _pictureInPictureMode.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatchTogetherTheme {
                WatchTogetherMainScreen()
            }
        }
    }

    fun enterPlayerPictureInPicture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        return runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _pictureInPictureMode.value = isInPictureInPictureMode
    }
}

@Composable
fun WatchTogetherMainScreen(
    updateViewModel: UpdateViewModel = viewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val updateState by updateViewModel.uiState.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    var previousRoute by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdates(auto = true)
    }

    LaunchedEffect(currentRoute) {
        if (previousRoute == Screen.Live.route && currentRoute != Screen.Live.route) {
            AppEventBus.post(AppEvent.LivePageExit)
        }
        previousRoute = currentRoute.orEmpty()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Vertical),
        bottomBar = {
            WatchTogetherBottomBar(navController)
        }
    ) { innerPadding ->
        WatchTogetherNavGraph(
            navController = navController,
            onCheckUpdate = { updateViewModel.checkForUpdates(auto = false) },
            modifier = Modifier.padding(innerPadding)
        )
    }

    UpdateDialog(
        state = updateState,
        onDismiss = { updateViewModel.dismissUpdate() },
        onDownload = { updateViewModel.downloadAndInstall(context) },
        onInstall = { updateViewModel.installDownloadedApk(context) }
    )
    UpdateMessageDialog(
        message = updateState.message,
        onDismiss = { updateViewModel.clearMessage() }
    )
}
