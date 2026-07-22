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
                // El deep link del arranque en frío lo procesa el NavHost automáticamente; los que
                // llegan con la app abierta (singleTop → onNewIntent) los engancha ZenitRoot.
                ZenitRoot()
            }
        }
    }
}
