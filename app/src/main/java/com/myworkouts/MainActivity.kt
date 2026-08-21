package com.myworkouts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myworkouts.ui.screens.root.RootScreen
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JetpackMyWorkoutsTheme {
                RootScreen()
            }
        }
    }
}
