package com.aesthetic.gym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aesthetic.gym.ui.nav.SymmetryRoot
import com.aesthetic.gym.ui.theme.SymmetryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SymmetryTheme {
                SymmetryRoot()
            }
        }
    }
}
