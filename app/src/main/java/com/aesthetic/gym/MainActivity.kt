package com.aesthetic.gym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aesthetic.gym.ui.nav.ZenitRoot
import com.aesthetic.gym.ui.theme.ZenitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZenitTheme {
                ZenitRoot()
            }
        }
    }
}
