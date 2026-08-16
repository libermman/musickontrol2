package com.dnc1981.musickontrol.navigation

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnc1981.musickontrol.audio.AdaptiveVolumeManager
import com.dnc1981.musickontrol.audio.VolumeSensitivity
import com.dnc1981.musickontrol.audio.AutoplayManager
import com.dnc1981.musickontrol.audio.NightModeManager
import com.dnc1981.musickontrol.audio.AudioNormalizerProcessor
import androidx.compose.runtime.collectAsState
import com.dnc1981.musickontrol.manager.CrossfadeManagerSingleton
import com.dnc1981.musickontrol.manager.FontSizeManager
import com.dnc1981.musickontrol.ui.LocalFontSizeScale

private fun cargarAudioFocusEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("audio_focus_enabled", true)
}

private fun guardarAudioFocusEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("audio_focus_enabled", enabled).apply()
}

private fun cargarGaplessEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("gapless_enabled", false)
}

private fun guardarGaplessEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("gapless_enabled", enabled).apply()
}

private fun cargarCrossfadeDuration(context: Context): Float {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getLong("crossfade_duration", 2000L).toFloat() / 1000f
}

private fun guardarCrossfadeDuration(context: Context, duration: Float) {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    val durationMs = (duration * 1000).toLong()
    prefs.edit().putLong("crossfade_duration", durationMs).apply()
    Log.d("AjustesScreen", "💾 Crossfade Duration guardado: ${duration}s (${durationMs}ms)")
}

private fun cargarCrossfadeEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("crossfade_enabled", true)
}

private fun guardarCrossfadeEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
    Log.d("AjustesScreen", "💾 Crossfade guardado: $enabled")
}

private fun cargarResumeOnStartEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("resume_on_start_enabled", false)
}

private fun guardarResumeOnStartEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("resume_on_start_enabled", enabled).apply()
}

private fun cargarNormalizacionEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("normalization_enabled", false)
}

private fun guardarNormalizacionEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("normalization_enabled", enabled).apply()
}

private fun cargarNivelNormalizacion(context: Context): Float {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getFloat("normalization_level", -12f)
}

private fun guardarNivelNormalizacion(context: Context, level: Float) {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    prefs.edit().putFloat("normalization_level", level).apply()
}

private fun cargarAutoDetectEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("normalization_auto_detect", true)
}

private fun guardarAutoDetectEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("musickontrol_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("normalization_auto_detect", enabled).apply()
}
@Composable
fun AjustesScreen(
    backupExportLauncher: ActivityResultLauncher<String>? = null,
    backupImportLauncher: ActivityResultLauncher<Array<String>>? = null
) {
    val context = LocalContext.current
    val deepGreen = Color(0xFF0E501A)
    val neonCyan = Color(0xFF00FFFF)
    val cardBg = Color(0xFF1E1E1E)
    val scrollState = rememberScrollState()

    var audioFocusEnabled by remember { mutableStateOf(cargarAudioFocusEnabled(context)) }
    var gaplessEnabled by remember { mutableStateOf(cargarGaplessEnabled(context)) }
    var resumeOnStartEnabled by remember { mutableStateOf(cargarResumeOnStartEnabled(context)) }
    var normalizacionEnabled by remember { mutableStateOf(cargarNormalizacionEnabled(context)) }
    var nivelNormalizacion by remember { mutableStateOf(cargarNivelNormalizacion(context)) }
    var autoDetectEnabled by remember { mutableStateOf(cargarAutoDetectEnabled(context)) }

    var adaptiveVolumeEnabled by remember { mutableStateOf(false) }
    var currentSensitivity by remember { mutableStateOf(VolumeSensitivity.NORMAL) }

    var crossfadeEnabled by remember { mutableStateOf(cargarCrossfadeEnabled(context)) }
    var crossfadeDuration by remember { mutableStateOf(cargarCrossfadeDuration(context)) }

    val fontSizeManager = remember { FontSizeManager(context) }
    var currentFontSize by remember { mutableStateOf(fontSizeManager.loadFontSizeScale()) }

    val adaptiveVolumeManager = remember {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AdaptiveVolumeManager(context, audioManager)
    }

    val autoplayManager = remember {
        AutoplayManager(context)
    }

    val nightModeManager = remember {
        NightModeManager(context)
    }

    val crossfadeManager = remember {
        CrossfadeManagerSingleton.getInstance(context)
    }

    val audioNormalizer = remember {
        AudioNormalizerProcessor(context)
    }

    var autoplayEnabled by remember { mutableStateOf(autoplayManager.isEnabled()) }
    var nightModeEnabled by remember { mutableStateOf(true) }
    var nightModeLuxThreshold by remember { mutableStateOf(50f) }

    val currentLux by nightModeManager.currentLux.collectAsState()
    val hasSensor by nightModeManager.hasSensor.collectAsState()

    LaunchedEffect(adaptiveVolumeEnabled) {
        adaptiveVolumeManager.setEnabled(adaptiveVolumeEnabled)
    }

    LaunchedEffect(currentSensitivity) {
        adaptiveVolumeManager.setSensitivity(currentSensitivity)
    }

    LaunchedEffect(crossfadeDuration) {
        guardarCrossfadeDuration(context, crossfadeDuration)
        val durationMs = (crossfadeDuration * 1000).toLong()
        crossfadeManager.guardarCrossfadeDuration(durationMs)
        Log.d("AjustesScreen", "🔄 CrossfadeManager actualizado: ${crossfadeDuration}s (${durationMs}ms)")
    }

    LaunchedEffect(crossfadeEnabled) {
        guardarCrossfadeEnabled(context, crossfadeEnabled)
    }

    LaunchedEffect(normalizacionEnabled) {
        guardarNormalizacionEnabled(context, normalizacionEnabled)
        audioNormalizer.guardarNormalizacionEnabled(normalizacionEnabled)
        Log.d("AjustesScreen", "🔊 Normalización: $normalizacionEnabled")
    }

    LaunchedEffect(nivelNormalizacion) {
        guardarNivelNormalizacion(context, nivelNormalizacion)
        audioNormalizer.guardarNivelNormalizacion(nivelNormalizacion)
        Log.d("AjustesScreen", "🔊 Nivel de normalización: ${nivelNormalizacion}dB")
    }

    LaunchedEffect(autoDetectEnabled) {
        guardarAutoDetectEnabled(context, autoDetectEnabled)
        audioNormalizer.guardarAutoDetectEnabled(autoDetectEnabled)
        Log.d("AjustesScreen", "🔊 Auto-detect: $autoDetectEnabled")
    }

    fun actualizarAutoplay(newValue: Boolean) {
        autoplayEnabled = newValue
        autoplayManager.setEnabled(newValue)
        Toast.makeText(
            context,
            if (newValue) "✅ Autoplay ACTIVADO" else "❌ Autoplay DESACTIVADO",
            Toast.LENGTH_SHORT
        ).show()
        Log.d("AjustesScreen", if (newValue) "✅ Autoplay ACTIVADO" else "❌ Autoplay DESACTIVADO")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AJUSTES DE SISTEMA",
                    color = Color.Green,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "MusicKontrol v2.1.0 - Panel de Control",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            Text(
                text = "Rev: AAOS-12",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "VOLUMEN ADAPTATIVO",
                    color = neonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ajuste automático por velocidad",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Ajusta volumen según velocidad del vehículo",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = adaptiveVolumeEnabled,
                        onCheckedChange = { newValue ->
                            adaptiveVolumeEnabled = newValue
                            Toast.makeText(
                                context,
                                if (newValue) "✅ Volumen Adaptativo ACTIVADO" else "❌ Volumen Adaptativo DESACTIVADO",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = neonCyan,
                            checkedTrackColor = deepGreen
                        )
                    )
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📊 Tabla de velocidades (Curva Suave)",
                        color = neonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    listOf(
                        "0-15 km/h → 30%" to Color(0xFF4CAF50),
                        "15-30 km/h → 40%" to Color(0xFF8BC34A),
                        "30-45 km/h → 50%" to Color(0xFFFFC107),
                        "45-60 km/h → 60%" to Color(0xFFFF9800),
                        "60-80 km/h → 70%" to Color(0xFFFF7043),
                        "80-100 km/h → 85%" to Color(0xFFFF5722),
                        "100+ km/h → 100%" to Color(0xFFF44336)
                    ).forEach { (label, color) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(color, RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🎚️ Sensibilidad del cambio de volumen",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                currentSensitivity = VolumeSensitivity.SUAVE
                                Toast.makeText(context, "🌊 Modo SUAVE activado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentSensitivity == VolumeSensitivity.SUAVE) neonCyan else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                "SUAVE",
                                color = if (currentSensitivity == VolumeSensitivity.SUAVE) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                currentSensitivity = VolumeSensitivity.NORMAL
                                Toast.makeText(context, "⚖️ Modo NORMAL activado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentSensitivity == VolumeSensitivity.NORMAL) neonCyan else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                "NORMAL",
                                color = if (currentSensitivity == VolumeSensitivity.NORMAL) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                currentSensitivity = VolumeSensitivity.AGRESIVO
                                Toast.makeText(context, "⚡ Modo AGRESIVO activado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentSensitivity == VolumeSensitivity.AGRESIVO) neonCyan else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                "AGRESIVO",
                                color = if (currentSensitivity == VolumeSensitivity.AGRESIVO) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = when (currentSensitivity) {
                            VolumeSensitivity.SUAVE -> "🌊 Cambios lentos y suaves (Ideal para ciudad con retenciones)"
                            VolumeSensitivity.NORMAL -> "⚖️ Cambios moderados (Recomendado - Equilibrio perfecto)"
                            VolumeSensitivity.AGRESIVO -> "⚡ Cambios rápidos y directos (Ideal para autovía)"
                        },
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎵 Autoplay al arrancar",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Reproduce automáticamente al detectar movimiento",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoplayEnabled,
                        onCheckedChange = { newValue ->
                            actualizarAutoplay(newValue)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = neonCyan,
                            checkedTrackColor = deepGreen
                        )
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MODO NOCHE",
                    color = neonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasSensor) {
                                "🌙 Modo Noche Automático"
                            } else {
                                "🌙 Modo Noche Manual"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (hasSensor) {
                                "Detecta luz ambiente y adapta la interfaz"
                            } else {
                                "⚠️ Sin sensor de luz - Cambio manual"
                            },
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = nightModeEnabled,
                        onCheckedChange = { newValue ->
                            nightModeEnabled = newValue
                            if (hasSensor) {
                                nightModeManager.setEnabled(newValue)
                            } else {
                                nightModeManager.setManualNightMode(newValue)
                            }
                            Toast.makeText(
                                context,
                                if (newValue) "✅ Modo Noche ACTIVADO" else "❌ Modo Noche DESACTIVADO",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = neonCyan,
                            checkedTrackColor = deepGreen
                        )
                    )
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                if (hasSensor) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Umbral de Luz",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${String.format("%.0f", nightModeLuxThreshold)} lux",
                                color = neonCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = nightModeLuxThreshold,
                            onValueChange = { newValue ->
                                nightModeLuxThreshold = newValue
                                nightModeManager.setThreshold(newValue)
                            },
                            valueRange = 10f..200f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = neonCyan,
                                activeTrackColor = deepGreen,
                                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Muy sensible (10)", color = Color.Gray, fontSize = 11.sp)
                            Text(text = "Poco sensible (200)", color = Color.Gray, fontSize = 11.sp)
                        }

                        Text(
                            text = "📊 Luz actual: ${String.format("%.0f", currentLux)} lux",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1B3A1B), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⚠️ Sensor de luz no disponible",
                            color = Color(0xFFFFB74D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tu dispositivo no tiene sensor de luz. Usa el Switch arriba para cambiar manualmente entre día y noche.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "TAMAÑO DE FUENTE",
                    color = neonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Ajusta el tamaño de texto en toda la aplicación",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tamaño actual:",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${currentFontSize.label} (${String.format("%.1f", currentFontSize.scale)}x)",
                        color = neonCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                // ✅ OBTENER EL STATE DEL COMPOSITIONLOCAL
                val fontSizeScaleState = LocalFontSizeScale.current

                // ✅ BOTONES DE OPCIONES - ACTUALIZAR EN TIEMPO REAL
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FontSizeScale.entries.forEach { scale ->
                        Button(
                            onClick = {
                                // ✅ ACTUALIZAR EL STATE EN TIEMPO REAL
                                fontSizeScaleState.value = scale.scale

                                // Guardar en SharedPreferences
                                currentFontSize = scale
                                fontSizeManager.saveFontSizeScale(scale)

                                Toast.makeText(
                                    context,
                                    "📖 Tamaño: ${scale.label}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                Log.d("AjustesScreen", "📖 Tamaño de fuente: ${scale.label} (${scale.scale}x)")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentFontSize == scale) neonCyan else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = "📖 ${scale.label} (${String.format("%.1f", scale.scale)}x)",
                                color = if (currentFontSize == scale) Color.Black else Color.White,
                                fontSize = (14 * scale.scale).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                // ✅ PREVIEW DE TEXTO
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PREVIEW",
                        color = neonCyan,
                        fontSize = (12 * currentFontSize.scale).sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Este es el tamaño de fuente que verás en toda la app",
                        color = Color.White,
                        fontSize = (14 * currentFontSize.scale).sp
                    )
                    Text(
                        text = "Títulos grandes",
                        color = Color.Green,
                        fontSize = (20 * currentFontSize.scale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PREFERENCIAS DE AUDIO",
                    color = neonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audio Focus Manager",
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (audioFocusEnabled) "Pausa en llamadas/notificaciones" else "Desactivado",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = audioFocusEnabled,
                        onCheckedChange = { newValue ->
                            audioFocusEnabled = newValue
                            guardarAudioFocusEnabled(context, newValue)
                            Toast.makeText(
                                context,
                                if (newValue) "✅ Audio Focus activado" else "❌ Audio Focus desactivado",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = neonCyan,
                            checkedTrackColor = deepGreen
                        )
                    )
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reproducción sin pausas (Gapless)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Sin silencios entre canciones",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = gaplessEnabled,
                        onCheckedChange = { newValue ->
                            gaplessEnabled = newValue
                            guardarGaplessEnabled(context, newValue)
                            Toast.makeText(
                                context,
                                if (newValue) "✅ Gapless Playback activado" else "❌ Gapless Playback desactivado",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = neonCyan,
                            checkedTrackColor = deepGreen
                        )
                    )
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎵 Crossfade entre canciones",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Transición suave entre pistas",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = crossfadeEnabled,
                        onCheckedChange = { newValue ->
                            crossfadeEnabled = newValue
                            Log.d("AjustesScreen", "🎵 Crossfade: $newValue")
                            Toast.makeText(
                                context,
                                if (newValue) "✅ Crossfade ACTIVADO" else "❌ Crossfade DESACTIVADO",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = neonCyan,
                            checkedTrackColor = deepGreen
                        )
                    )
                }

                if (crossfadeEnabled) {
                    Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Duración del Crossfade",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${String.format("%.1f", crossfadeDuration)}s",
                                color = neonCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = crossfadeDuration,
                            onValueChange = { newValue ->
                                crossfadeDuration = newValue
                                Log.d("AjustesScreen", "🎚️ Slider movido: ${newValue}s")
                            },
                            valueRange = 0f..5f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = neonCyan,
                                activeTrackColor = deepGreen,
                                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Desactivado (0s)", color = Color.Gray, fontSize = 11.sp)
                            Text(text = "Máximo (5s)", color = Color.Gray, fontSize = 11.sp)
                        }

                        Text(
                            text = when {
                                crossfadeDuration < 1f -> "⚡ Muy rápido - Transición casi instantánea"
                                crossfadeDuration < 2f -> "🎵 Rápido - Transición fluida (Recomendado)"
                                crossfadeDuration < 3.5f -> "🌊 Normal - Transición suave"
                                else -> "🎼 Lento - Transición muy suave (Ideal para jazz/clásica)"
                            },
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Normalización de Audio",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Iguala volumen entre canciones",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = normalizacionEnabled,
                        onCheckedChange = { newValue ->
                            normalizacionEnabled = newValue
                            Toast.makeText(
                                context,
                                if (newValue) "✅ Normalización activada" else "❌ Normalización desactivada",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = neonCyan,
                            checkedTrackColor = deepGreen
                        )
                    )
                }

                if (normalizacionEnabled) {
                    Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nivel de Normalización",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${String.format("%.1f", nivelNormalizacion)} dB",
                                color = neonCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = nivelNormalizacion,
                            onValueChange = { newValue ->
                                nivelNormalizacion = newValue
                            },
                            valueRange = -20f..0f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = neonCyan,
                                activeTrackColor = deepGreen,
                                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "-20 dB", color = Color.Gray, fontSize = 11.sp)
                            Text(text = "0 dB", color = Color.Gray, fontSize = 11.sp)
                        }
                    }

                    Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Auto-detectar nivel", color = Color.White, fontSize = 14.sp)
                            Text(text = "Detecta automáticamente el nivel óptimo", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = autoDetectEnabled,
                            onCheckedChange = { newValue ->
                                autoDetectEnabled = newValue
                                Toast.makeText(context, if (newValue) "✅ Auto-detect activado" else "❌ Auto-detect desactivado", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = neonCyan, checkedTrackColor = deepGreen)
                        )
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Reanudar música al arrancar el vehículo", color = Color.White, fontSize = 15.sp)
                        Text(text = "Continúa desde donde se pausó", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = resumeOnStartEnabled,
                        onCheckedChange = { newValue ->
                            resumeOnStartEnabled = newValue
                            guardarResumeOnStartEnabled(context, newValue)
                            Toast.makeText(context, if (newValue) "✅ Resume on Start activado" else "❌ Resume on Start desactivado", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = neonCyan, checkedTrackColor = deepGreen)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "💾 COPIAS DE SEGURIDAD",
                    color = neonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Guarda o recupera la base de datos de MusicKontrol desde un USB.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            backupExportLauncher?.launch("musiclibrary_backup.db")
                                ?: Toast.makeText(context, "❌ Exportación no disponible", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("📤 Exportar BD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            backupImportLauncher?.launch(
                                arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")
                            ) ?: Toast.makeText(context, "❌ Importación no disponible", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("📥 Importar BD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Text(
                    text = "La copia contiene la base de datos de música indexada. No copia los archivos de música.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ℹ️ ACERCA DE",
                    color = neonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Aplicación:", color = Color.Gray, fontSize = 12.sp)
                        Text(text = "MusicKontrol", color = neonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Versión:", color = Color.Gray, fontSize = 12.sp)
                        Text(text = "v1.1.2", color = neonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Desarrollador:", color = Color.Gray, fontSize = 12.sp)
                        Text(text = "DNC1981", color = neonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Contacto:", color = Color.Gray, fontSize = 12.sp)
                        Text(text = "dnc1981@hotmail.com", color = neonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Copyright:", color = Color.Gray, fontSize = 12.sp)
                        Text(text = "© 2026 DNC1981", color = neonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Compositor:", color = Color.Gray, fontSize = 12.sp)
                        Text(text = "Jetpack Compose", color = neonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A2A0A), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    //Text(
                      //  text = "✨ CARACTERÍSTICAS PRINCIPALES",
                        //color = Color.Green,
                        //fontSize = 13.sp,
                        //fontWeight = FontWeight.Bold
                    //)

                    //listOf(
                      //  "🎵 Reproducción USB con navegación por carpetas",
                      //  "📻 Radios online con soporte HLS/ICY",
                      //  "🎚️ Ecualizador de 10 bandas",
                      //  "🚗 Volumen adaptativo por velocidad",
                      //  "🌙 Modo noche automático",
                      //  "🎵 Crossfade entre canciones",
                      //  "🔊 Normalización de audio",
                      //  "❤️ Gestor de favoritos",
                      //  "🎲 Reproducción aleatoria global",
                      //  "⚡ Interfaz optimizada para conducción"
                    //).forEach { feature ->
                      //  Text(
                        //    text = feature,
                        //    color = Color.Gray,
                        //    fontSize = 11.sp
                        //)
                    //}
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B1B1B), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "📋 LICENCIA Y CRÉDITOS",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "MusicKontrol es software propietario.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )

                    Text(
                        text = "Queda prohibida la reproducción, modificación, distribución, ingeniería inversa o comercialización de esta aplicación o de cualquiera de sus componentes, total o parcialmente, sin autorización expresa del titular de los derechos.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )

                    Text(
                        text = "MusicKontrol utiliza componentes y bibliotecas de terceros distribuidos bajo sus respectivas licencias.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }

                Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    //Text(
                     //   text = "⚙️ INFORMACIÓN DEL SISTEMA",
                      //  color = neonCyan,
                      //  fontSize = 12.sp,
                      //  fontWeight = FontWeight.Bold
                  //  )

                    //Row(
                    //    modifier = Modifier.fillMaxWidth(),
                    //    horizontalArrangement = Arrangement.SpaceBetween
                    //) {
                    //    Text(text = "Tamaño de fuente:", color = Color.Gray, fontSize = 11.sp)
                    //    Text(text = "${currentFontSize.label} (${String.format("%.1f", currentFontSize.scale)}x)", color = Color.Green, fontSize = 11.sp)
                    //}

                    //Row(
                     //   modifier = Modifier.fillMaxWidth(),
                     //   horizontalArrangement = Arrangement.SpaceBetween
                    //) {
                    //    Text(text = "Volumen adaptativo:", color = Color.Gray, fontSize = 11.sp)
                    //    Text(text = if (adaptiveVolumeEnabled) "✅ Activo" else "❌ Inactivo", color = if (adaptiveVolumeEnabled) Color.Green else Color.Red, fontSize = 11.sp)
                    //}

                    //Row(
                      //  modifier = Modifier.fillMaxWidth(),
                      //  horizontalArrangement = Arrangement.SpaceBetween
                    //) {
                      //  Text(text = "Modo noche:", color = Color.Gray, fontSize = 11.sp)
                      //  Text(text = if (nightModeEnabled) "🌙 Activo" else "☀️ Inactivo", color = if (nightModeEnabled) Color.Green else Color.Yellow, fontSize = 11.sp)
                    //}

                    //Row(
                      //  modifier = Modifier.fillMaxWidth(),
                      //  horizontalArrangement = Arrangement.SpaceBetween
                    //) {
                      //  Text(text = "Autoplay:", color = Color.Gray, fontSize = 11.sp)
                      //  Text(text = if (autoplayEnabled) "✅ Activo" else "❌ Inactivo", color = if (autoplayEnabled) Color.Green else Color.Red, fontSize = 11.sp)
                    //}

                    //Row(
                      //  modifier = Modifier.fillMaxWidth(),
                    //    horizontalArrangement = Arrangement.SpaceBetween
                    //) {
                      //  Text(text = "Crossfade:", color = Color.Gray, fontSize = 11.sp)
                      //  Text(text = if (crossfadeEnabled) "✅ ${String.format("%.1f", crossfadeDuration)}s" else "❌ Desactivado", color = if (crossfadeEnabled) Color.Green else Color.Red, fontSize = 11.sp)
                    //}

                    //Row(
                      //  modifier = Modifier.fillMaxWidth(),
                      //  horizontalArrangement = Arrangement.SpaceBetween
                    //) {
                      //  Text(text = "Normalización:", color = Color.Gray, fontSize = 11.sp)
                      //  Text(text = if (normalizacionEnabled) "✅ ${String.format("%.1f", nivelNormalizacion)}dB" else "❌ Desactivada", color = if (normalizacionEnabled) Color.Green else Color.Red, fontSize = 11.sp)
                    //}

                    //Row(
                      //  modifier = Modifier.fillMaxWidth(),
                      //  horizontalArrangement = Arrangement.SpaceBetween
                    //) {
                      //  Text(text = "Sensor de luz:", color = Color.Gray, fontSize = 11.sp)
                      //  Text(text = if (hasSensor) "✅ Disponible" else "❌ No disponible", color = if (hasSensor) Color.Green else Color.Red, fontSize = 11.sp)
                    //}

                    //if (hasSensor) {
                      //  Row(
                        //    modifier = Modifier.fillMaxWidth(),
                        //    horizontalArrangement = Arrangement.SpaceBetween
                        //) {
                          //  Text(text = "Luz actual:", color = Color.Gray, fontSize = 11.sp)
                          //  Text(text = "${String.format("%.0f", currentLux)} lux", color = neonCyan, fontSize = 11.sp)
                        //}
                    //}
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🚀 MusicKontrol v2.1.0 - Optimizado para Android Automotive OS",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}