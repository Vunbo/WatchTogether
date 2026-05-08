package com.vunbo.watchtogether.ui.player

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vunbo.watchtogether.ui.theme.WatchTogetherTheme

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceKey = intent.getStringExtra("sourceKey") ?: ""
        val vodId = intent.getStringExtra("vodId") ?: ""
        val playFlag = intent.getStringExtra("playFlag") ?: ""
        val playIndex = intent.getIntExtra("playIndex", 0)

        setContent {
            WatchTogetherTheme {
                PlayerScreen(
                    sourceKey = sourceKey,
                    vodId = vodId,
                    playFlag = playFlag,
                    playIndex = playIndex,
                    onBack = { finish() }
                )
            }
        }
    }
}
