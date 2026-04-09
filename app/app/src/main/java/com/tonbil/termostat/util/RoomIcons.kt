package com.tonbil.termostat.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bathroom
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Oda icon string'inden ImageVector'e donusturur.
 * Tum oda bilesenlerinde bu fonksiyon kullanilmali (tekrar yazilmamali).
 */
fun roomIcon(icon: String?): ImageVector = when (icon?.lowercase()) {
    "bedroom", "yatak" -> Icons.Default.Bed
    "kitchen", "mutfak" -> Icons.Default.Kitchen
    "bathroom", "banyo" -> Icons.Default.Bathroom
    "living", "salon" -> Icons.Default.Chair
    else -> Icons.Default.MeetingRoom
}
