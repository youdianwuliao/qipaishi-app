package com.qipaishi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import com.qipaishi.ui.screens.LobbyScreen
import com.qipaishi.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Green600,
                    secondary = Gold,
                    surface = TableGreenDark,
                    background = TableGreen,
                    onPrimary = CardWhite,
                    onSurface = CardWhite,
                    error = ErrorRed
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LobbyScreen()
                }
            }
        }
    }
}