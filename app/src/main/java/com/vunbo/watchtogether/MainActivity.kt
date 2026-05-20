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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.vunbo.watchtogether.navigation.WatchTogetherBottomBar
import com.vunbo.watchtogether.navigation.WatchTogetherNavGraph
import com.vunbo.watchtogether.ui.theme.WatchTogetherTheme
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
fun WatchTogetherMainScreen() {
    val navController = rememberNavController()

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
            modifier = Modifier.padding(innerPadding)
        )
    }
}
