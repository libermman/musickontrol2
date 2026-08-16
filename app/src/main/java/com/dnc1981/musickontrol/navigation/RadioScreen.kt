package com.dnc1981.musickontrol.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.dnc1981.musickontrol.ui.LocalFontSizeScale

@Composable
fun RadioScreen(
    stations: List<RadioStation>,
    exoPlayer: ExoPlayer,
    onNavigateToAhoraSuena: () -> Unit,
    onDeleteStation: (RadioStation) -> Unit,
    onPlayRadio: (Uri, String) -> Unit
) {
    // ✅ OBTENER ESCALA DE FUENTE
    val fontSizeScale = LocalFontSizeScale.current.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding((16 * fontSizeScale).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "EMISORAS DE RADIO & STREAMING",
            color = Color(0xFF00FFFF),
            fontSize = (22 * fontSizeScale).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = (16 * fontSizeScale).dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy((12 * fontSizeScale).dp),
            verticalArrangement = Arrangement.spacedBy((12 * fontSizeScale).dp)
        ) {
            items(stations) { station ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((80 * fontSizeScale).dp)
                        .clip(RoundedCornerShape((8 * fontSizeScale).dp))
                        .background(Color(0xFF0E501A))
                        .border((2 * fontSizeScale).dp, Color(0xFF00FFFF), RoundedCornerShape((8 * fontSizeScale).dp))
                        .padding((10 * fontSizeScale).dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((8 * fontSizeScale).dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    Log.d("RadioScreen", "🎙️ Pulsado: ${station.name}")
                                    val radioUri = Uri.parse(station.url)
                                    // ✅ LLAMAR A onPlayRadio PRIMERO
                                    onPlayRadio(radioUri, station.name)
                                    // ✅ NAVEGAR DESPUÉS
                                    onNavigateToAhoraSuena()
                                }
                        ) {
                            Icon(
                                Icons.Default.Radio,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size((22 * fontSizeScale).dp)
                            )
                            Text(
                                station.name,
                                color = Color.White,
                                fontSize = (13 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy((4 * fontSizeScale).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ✅ BOTÓN DE PLAY EXPLÍCITO
                            IconButton(
                                onClick = {
                                    Log.d("RadioScreen", "▶️ Play pulsado: ${station.name}")
                                    val radioUri = Uri.parse(station.url)
                                    onPlayRadio(radioUri, station.name)
                                    onNavigateToAhoraSuena()
                                },
                                modifier = Modifier.size((28 * fontSizeScale).dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    "Reproducir",
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size((18 * fontSizeScale).dp)
                                )
                            }

                            // ✅ BOTÓN DE ELIMINAR
                            IconButton(
                                onClick = {
                                    Log.d("RadioScreen", "🗑️ Eliminar: ${station.name}")
                                    onDeleteStation(station)
                                },
                                modifier = Modifier.size((28 * fontSizeScale).dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    "Eliminar",
                                    tint = Color.Red,
                                    modifier = Modifier.size((18 * fontSizeScale).dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}