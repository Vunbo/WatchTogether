package com.vunbo.watchtogether

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.vunbo.watchtogether.navigation.WatchTogetherBottomBar
import com.vunbo.watchtogether.navigation.WatchTogetherNavGraph
import com.vunbo.watchtogether.ui.theme.WatchTogetherTheme

class MainActivity : ComponentActivity() {
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
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        enterPictureInPictureMode(params)
        return true
    }
}

@Composable
fun WatchTogetherMainScreen() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
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
