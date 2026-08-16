package com.dnc1981.musickontrol.navigation

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnc1981.musickontrol.utils.EqAudioController
import com.dnc1981.musickontrol.utils.EqPreferences
import com.dnc1981.musickontrol.utils.EqState
import kotlin.math.roundToInt

@Composable
fun EqScreen(
    eqController: EqAudioController,
    isDriving: Boolean = false
) {
    val context = LocalContext.current

    val initialState = remember {
        EqPreferences.loadEqState(context)
    }

    var isEqEnabled by remember {
        mutableStateOf(initialState.isEnabled)
    }

    var currentPreset by remember {
        mutableStateOf(initialState.presetName)
    }

    var use10Bands by remember {
        mutableStateOf(
            context.getSharedPreferences("eq_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("use_10_bands", false)
        )
    }

    val bands5List = remember {
        mutableStateListOf<Float>().apply {
            val safeBands = if (initialState.bands.size >= 5) {
                initialState.bands.take(5)
            } else {
                initialState.bands + List(5 - initialState.bands.size) { 0f }
            }
            addAll(safeBands.map { it.coerceIn(-12f, 12f) })
        }
    }

    val bands10List = remember {
        mutableStateListOf<Float>().apply {
            val savedBands10 = context.getSharedPreferences("eq_prefs", android.content.Context.MODE_PRIVATE)
                .getString("bands_10", "")
                ?.split(",")
                ?.mapNotNull { it.toFloatOrNull() }
                ?: emptyList()

            if (savedBands10.size == 10) {
                addAll(savedBands10)
            } else {
                addAll(List(10) { 0f })
            }
        }
    }

    var updateTrigger by remember { mutableStateOf(0) }

    val bands = if (use10Bands) bands10List else bands5List

    var bassBoostStrength by remember {
        mutableStateOf(initialState.bassBoostStrength.coerceIn(0f, 1000f))
    }

    var isBoost30Active by remember {
        mutableStateOf(initialState.isBoost30Active)
    }

    var isBoost50Active by remember {
        mutableStateOf(initialState.isBoost50Active)
    }

    var isSubgravesManuallyEnabled by remember {
        mutableStateOf(bassBoostStrength > 0f)
    }

    val isSubgravesActive = bassBoostStrength > 0f

    var showWarningDialog by remember {
        mutableStateOf(false)
    }

    var pendingBoostType by remember {
        mutableStateOf(0)
    }

    val presets5 = mapOf(
        "Normal" to listOf(0f, 0f, 0f, 0f, 0f),
        "Rock" to listOf(2f, 4f, -2f, 4f, 3f),
        "Pop" to listOf(2f, 0f, 3f, 5f, 4f),
        "Jazz" to listOf(2f, 4f, 2f, 1f, 3f),
        "Clásico" to listOf(1f, 1f, -1f, 2f, 4f),
        "Heavy Metal" to listOf(4f, 1f, -3f, 5f, 5f),
        "Techno" to listOf(5f, -1f, -2f, 4f, 6f),
        "Hip-Hop" to listOf(5f, 2f, 1f, 3f, 3f)
    )

    val presets10 = mapOf(
        "Normal" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Rock" to listOf(2f, 2f, 4f, 3f, 0f, -2f, 2f, 4f, 3f, 3f),
        "Pop" to listOf(1f, 2f, 1f, 0f, 1f, 3f, 4f, 5f, 4f, 4f),
        "Jazz" to listOf(3f, 2f, 4f, 3f, 1f, 2f, 1f, 1f, 2f, 3f),
        "Clásico" to listOf(2f, 1f, 1f, 0f, 0f, -1f, 1f, 2f, 3f, 4f),
        "Heavy Metal" to listOf(4f, 4f, 2f, 0f, -2f, -4f, 1f, 5f, 4f, 5f),
        "Techno" to listOf(6f, 5f, 2f, 0f, -2f, -2f, 2f, 4f, 5f, 6f),
        "Hip-Hop" to listOf(7f, 5f, 3f, 1f, 0f, 2f, 3f, 3f, 2f, 3f)
    )

    val subgravesForPreset = mapOf(
        "Normal" to 0f,
        "Rock" to 700f,
        "Pop" to 600f,
        "Jazz" to 650f,
        "Clásico" to 500f,
        "Heavy Metal" to 800f,
        "Techno" to 900f,
        "Hip-Hop" to 900f
    )

    val labels5 = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
    val labels10 = listOf("32Hz", "63Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

    val currentLabels = if (use10Bands) labels10 else labels5
    val currentPresets = if (use10Bands) presets10 else presets5

    fun buildCurrentEqState(): EqState {
        return EqState(
            isEnabled = isEqEnabled,
            presetName = currentPreset,
            bands = if (use10Bands) {
                bands10List.toList()
            } else {
                bands5List.toList()
            },
            bassBoostStrength = bassBoostStrength.coerceIn(0f, 1000f),
            isBoost30Active = isBoost30Active,
            isBoost50Active = isBoost50Active
        )
    }

    fun applyAndSave() {
        eqController.applyState(
            state = buildCurrentEqState(),
            save = true
        )
    }

    LaunchedEffect(bands5List.toList()) {
        if (!use10Bands) {
            applyAndSave()
        }
    }

    LaunchedEffect(bands10List.toList()) {
        val prefs = context.getSharedPreferences("eq_prefs", android.content.Context.MODE_PRIVATE)
        val bandsJson = bands10List.joinToString(",")
        prefs.edit().putString("bands_10", bandsJson).apply()

        if (use10Bands && isEqEnabled) {
            applyAndSave()
        }
    }

    LaunchedEffect(use10Bands) {
        val prefs = context.getSharedPreferences("eq_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("use_10_bands", use10Bands).apply()

        applyAndSave()
    }

    LaunchedEffect(
        isEqEnabled,
        currentPreset,
        bassBoostStrength,
        isBoost30Active,
        isBoost50Active
    ) {
        applyAndSave()
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = {
                showWarningDialog = false
            },
            title = {
                Text(
                    text = "⚠️ Advertencia de Seguridad",
                    color = Color.White,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "El desarrollador no se hace responsable si hay algún daño por forzar los altavoces del vehículo a niveles elevados de potencia.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false

                        when (pendingBoostType) {
                            30 -> {
                                if (!isBoost30Active) {
                                    if (isBoost50Active) {
                                        isBoost50Active = false

                                        for (i in bands.indices) {
                                            bands[i] = (bands[i] / 4.0f).coerceIn(-12f, 12f)
                                        }
                                    }

                                    isBoost30Active = true

                                    for (i in bands.indices) {
                                        bands[i] = (bands[i] * 2.5f).coerceIn(-12f, 12f)
                                    }

                                    bassBoostStrength = 900f
                                }
                            }

                            50 -> {
                                if (!isBoost50Active) {
                                    if (isBoost30Active) {
                                        isBoost30Active = false

                                        for (i in bands.indices) {
                                            bands[i] = (bands[i] / 2.5f).coerceIn(-12f, 12f)
                                        }
                                    }

                                    isBoost50Active = true

                                    for (i in bands.indices) {
                                        bands[i] = (bands[i] * 4.0f).coerceIn(-12f, 12f)
                                    }

                                    bassBoostStrength = 1000f
                                }
                            }
                        }

                        applyAndSave()

                        Toast.makeText(
                            context,
                            "Modo de potencia aplicado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(
                        text = "Aceptar",
                        color = Color(0xFF00E676)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false
                    }
                ) {
                    Text(
                        text = "Cancelar",
                        color = Color.Gray
                    )
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Ecualizador Universal",
                color = Color.White,
                fontSize = 20.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        use10Bands = !use10Bands

                        Toast.makeText(
                            context,
                            if (use10Bands) "🎚️ Modo: 10 BANDAS" else "🎚️ Modo: 5 BANDAS",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (use10Bands) Color(0xFFFFA500) else Color(0xFF2C2C2C),
                        contentColor = if (use10Bands) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = if (use10Bands) "10 BANDAS" else "5 BANDAS",
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = isEqEnabled,
                    onCheckedChange = {
                        isEqEnabled = it

                        Toast.makeText(
                            context,
                            if (it) {
                                "Ecualizador Activado"
                            } else {
                                "Ecualizador Desactivado"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF00E676)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val allPresetNames = currentPresets.keys.toList() + "Personalizado"

            allPresetNames.forEach { presetName ->
                val isSelected = currentPreset == presetName
                val isCustom = presetName == "Personalizado"
                val isDisabledInMotion = isDriving && isCustom

                Button(
                    onClick = {
                        if (isDisabledInMotion) {
                            Toast.makeText(
                                context,
                                "Modo Personalizado bloqueado en movimiento (Normas AAOS)",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            currentPreset = presetName

                            if (presetName != "Personalizado") {
                                currentPresets[presetName]?.let { values ->
                                    if (use10Bands) {
                                        bands10List.clear()
                                        bands10List.addAll(values.map { it.coerceIn(-12f, 12f) })
                                    } else {
                                        bands5List.clear()
                                        bands5List.addAll(values.map { it.coerceIn(-12f, 12f) })
                                    }
                                }

                                if (isSubgravesManuallyEnabled && !isBoost30Active && !isBoost50Active) {
                                    bassBoostStrength = subgravesForPreset[presetName] ?: 0f
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isDisabledInMotion -> Color(0xFF1A1A1A)
                            isSelected -> Color(0xFF00E676)
                            else -> Color(0xFF2C2C2C)
                        },
                        contentColor = when {
                            isDisabledInMotion -> Color.DarkGray
                            isSelected -> Color.Black
                            else -> Color.White
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 0.dp
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = if (isDisabledInMotion) {
                            "🔒 $presetName"
                        } else {
                            presetName
                        },
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (isDriving) {
                        Toast.makeText(
                            context,
                            "Boost bloqueado en movimiento (Normas AAOS)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        if (isBoost30Active) {
                            isBoost30Active = false

                            for (i in bands.indices) {
                                bands[i] = (bands[i] / 2.5f).coerceIn(-12f, 12f)
                            }

                            if (!isBoost50Active) {
                                bassBoostStrength = if (isSubgravesManuallyEnabled) {
                                    subgravesForPreset[currentPreset] ?: 0f
                                } else {
                                    0f
                                }
                            }

                            Toast.makeText(
                                context,
                                "Boost +30% Desactivado",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            pendingBoostType = 30
                            showWarningDialog = true
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isDriving -> Color(0xFF1A1A1A)
                        isBoost30Active -> Color(0xFF00E676)
                        else -> Color(0xFF2C2C2C)
                    },
                    contentColor = when {
                        isDriving -> Color.DarkGray
                        isBoost30Active -> Color.Black
                        else -> Color.White
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = if (isDriving) {
                        "🔒 Boost +30%"
                    } else {
                        "⚡ Boost +30%"
                    },
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            Button(
                onClick = {
                    if (isDriving) {
                        Toast.makeText(
                            context,
                            "Boost bloqueado en movimiento (Normas AAOS)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        if (isBoost50Active) {
                            isBoost50Active = false

                            for (i in bands.indices) {
                                bands[i] = (bands[i] / 4.0f).coerceIn(-12f, 12f)
                            }

                            if (!isBoost30Active) {
                                bassBoostStrength = if (isSubgravesManuallyEnabled) {
                                    subgravesForPreset[currentPreset] ?: 0f
                                } else {
                                    0f
                                }
                            }

                            Toast.makeText(
                                context,
                                "Boost +50% Desactivado",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            pendingBoostType = 50
                            showWarningDialog = true
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isDriving -> Color(0xFF1A1A1A)
                        isBoost50Active -> Color(0xFFFF5252)
                        else -> Color(0xFF2C2C2C)
                    },
                    contentColor = when {
                        isDriving -> Color.DarkGray
                        isBoost50Active -> Color.Black
                        else -> Color.White
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = if (isDriving) {
                        "🔒 Boost +50%"
                    } else {
                        "🔥 Boost +50%"
                    },
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            Button(
                onClick = {
                    if (isDriving) {
                        Toast.makeText(
                            context,
                            "Subgraves bloqueado en movimiento (Normas AAOS)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        isSubgravesManuallyEnabled = !isSubgravesManuallyEnabled

                        bassBoostStrength = if (isSubgravesManuallyEnabled) {
                            subgravesForPreset[currentPreset] ?: 700f
                        } else {
                            0f
                        }

                        Toast.makeText(
                            context,
                            if (isSubgravesManuallyEnabled) {
                                "Refuerzo de Graves Activado (${bassBoostStrength.toInt()})"
                            } else {
                                "Refuerzo de Graves Desactivado"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isDriving -> Color(0xFF1A1A1A)
                        isSubgravesActive -> Color(0xFF00E676)
                        else -> Color(0xFF2C2C2C)
                    },
                    contentColor = when {
                        isDriving -> Color.DarkGray
                        isSubgravesActive -> Color.Black
                        else -> Color.White
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = if (isDriving) {
                        "🔒 Subgraves"
                    } else {
                        "🔊 Subgraves ${if (isSubgravesActive) "(${bassBoostStrength.toInt()})" else "(OFF)"}"
                    },
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(bands.size) { bandIndex ->
                key(use10Bands, bandIndex) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(if (use10Bands) 48.dp else 64.dp)
                    ) {
                        val dbVal = bands[bandIndex]

                        val formattedVal = if (dbVal > 0) {
                            "+${dbVal.roundToInt()} dB"
                        } else {
                            "${dbVal.roundToInt()} dB"
                        }

                        Text(
                            text = formattedVal,
                            color = if (isDriving) {
                                Color.Gray.copy(alpha = 0.6f)
                            } else if (isEqEnabled) {
                                Color(0xFF00E676)
                            } else {
                                Color.Gray
                            },
                            fontSize = if (use10Bands) 10.sp else 12.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .width(if (use10Bands) 40.dp else 48.dp)
                                .height(160.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if (isDriving) {
                                        Color(0xFF161616)
                                    } else {
                                        Color(0xFF1E1E1E)
                                    }
                                )
                                .pointerInput(isEqEnabled, isDriving, bandIndex) {
                                    if (!isEqEnabled || isDriving) {
                                        return@pointerInput
                                    }

                                    detectVerticalDragGestures(
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()

                                            val trackHeightPx = size.height.toFloat()
                                            val deltaDb = (-dragAmount / trackHeightPx) * 24f
                                            val newDb = (bands[bandIndex] + deltaDb).coerceIn(-12f, 12f)

                                            bands[bandIndex] = newDb

                                            currentPreset = "Personalizado"
                                            isBoost30Active = false
                                            isBoost50Active = false

                                            updateTrigger++
                                            applyAndSave()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val width = size.width
                                val height = size.height

                                drawLine(
                                    color = if (isDriving) {
                                        Color.DarkGray.copy(alpha = 0.5f)
                                    } else {
                                        Color.DarkGray
                                    },
                                    start = Offset(width / 2f, 0f),
                                    end = Offset(width / 2f, height),
                                    strokeWidth = 4f
                                )

                                val normalized = (bands[bandIndex] + 12f) / 24f
                                val thumbY = height - (normalized * height)

                                if (isEqEnabled) {
                                    drawRect(
                                        color = if (isDriving) {
                                            Color.Gray
                                        } else {
                                            Color(0xFF00E676)
                                        },
                                        topLeft = Offset(0f, thumbY),
                                        size = Size(width, height - thumbY)
                                    )
                                }

                                drawCircle(
                                    color = if (isDriving) {
                                        Color.DarkGray
                                    } else {
                                        Color(0xFF00E676)
                                    },
                                    radius = 8f,
                                    center = Offset(width / 2f, thumbY)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentLabels[bandIndex],
                            color = Color.Gray,
                            fontSize = if (use10Bands) 9.sp else 10.sp
                        )
                    }
                }
            }
        }
    }
}