package com.aesthetic.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet

/** Shared top bar used across the browsing screens: avatar · centered title · settings. */
@Composable
fun AppTopBar(title: String, modifier: Modifier = Modifier, onProfile: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Violet.copy(alpha = 0.22f))
                .clickable(onClick = onProfile),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, "Perfil", tint = Violet, modifier = Modifier.size(18.dp))
        }
        Text(
            title.uppercase(),
            color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, letterSpacing = 3.sp, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onProfile),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Settings, "Ajustes", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
