package com.dnc1981.musickontrol.navigation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnc1981.musickontrol.ui.LocalFontSizeScale

@Composable
fun TopBar(
    selectedTab: String,
    isCarMoving: Boolean,
    onTabSelected: (String) -> Unit
) {
    val context = LocalContext.current

    // ✅ OBTENER ESCALA DE FUENTE
    val fontSizeScale = LocalFontSizeScale.current.value

    val tabs = listOf("DIRECTORIO", "RADIO", "AHORA SUENA", "EQ", "PLAYLIST", "AJUSTES")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((8 * fontSizeScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val isRestricted = (tab == "PLAYLIST" || tab == "AJUSTES") && isCarMoving
                val deepGreen = if (isRestricted) Color(0xFF5A1A1A) else Color(0xFF0E501A)
                val neonCyan = if (isRestricted) Color.Red else Color(0xFF00FFFF)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(deepGreen)
                        .then(
                            if (isSelected) Modifier.border((2.5 * fontSizeScale).dp, neonCyan, RoundedCornerShape(50))
                            else Modifier
                        )
                        .clickable {
                            if ((tab == "PLAYLIST" || tab == "AJUSTES") && isCarMoving) {
                                Toast.makeText(context, "Bloqueado por seguridad: Vehículo en movimiento", Toast.LENGTH_SHORT).show()
                            } else {
                                onTabSelected(tab)
                            }
                        }
                        .padding(horizontal = (16 * fontSizeScale).dp, vertical = (10 * fontSizeScale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((4 * fontSizeScale).dp)
                    ) {
                        Text(
                            text = tab,
                            color = if (isRestricted) Color.LightGray else Color.White,
                            fontSize = (14 * fontSizeScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRestricted) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Bloqueado",
                                tint = Color.Red,
                                modifier = Modifier.size((16 * fontSizeScale).dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((8 * fontSizeScale).dp)
        ) {
            Text(
                text = "Music Kontrol",
                color = Color.White,
                fontSize = (16 * fontSizeScale).sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((24 * fontSizeScale).dp)
            )
        }
    }
}