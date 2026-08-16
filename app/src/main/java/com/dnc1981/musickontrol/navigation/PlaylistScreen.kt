package com.dnc1981.musickontrol.navigation

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dnc1981.musickontrol.manager.FavoriteTrack
import com.dnc1981.musickontrol.manager.FavoritesManager
import com.dnc1981.musickontrol.ui.LocalFontSizeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun PlaylistScreen(
    stations: SnapshotStateList<RadioStation>,
    onPlayItem: (Uri, String) -> Unit,
    onPlayFavorite: (Uri, String) -> Unit,
    onAddStation: (String, String) -> Unit,
    onDeleteStation: (RadioStation) -> Unit,
    exportLauncher: ActivityResultLauncher<Uri?>? = null,
    importLauncher: ActivityResultLauncher<Array<String>>? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val favoritesManager = remember { FavoritesManager(context) }

    // ✅ OBTENER ESCALA DE FUENTE
    val fontSizeScale = LocalFontSizeScale.current.value

    var selectedTab by remember { mutableStateOf("FAVORITOS") }
    var showDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    var inputName by remember { mutableStateOf("") }
    var inputUrl by remember { mutableStateOf("") }

    val favorites = remember { mutableStateListOf<FavoriteTrack>() }
    var favoritesCount by remember { mutableStateOf(0) }

    var importedStations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var importType by remember { mutableStateOf<String?>(null) }

    var pendingExportType by remember { mutableStateOf<String?>(null) }

    val m3uImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        cursor.moveToFirst()
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        cursor.getString(nameIndex)
                    } ?: "playlist.m3u"

                    val inputStream = context.contentResolver.openInputStream(it)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val lines = reader.use { it.readLines() }

                    val parsedStations = mutableListOf<RadioStation>()

                    var i = 0
                    while (i < lines.size) {
                        val line = lines[i].trim()

                        if (line.startsWith("#EXTINF:")) {
                            val name = line.substringAfterLast(",").trim()
                                .ifEmpty { "Radio ${parsedStations.size + 1}" }

                            if (i + 1 < lines.size) {
                                val url = lines[i + 1].trim()
                                if (url.isNotEmpty() && !url.startsWith("#")) {
                                    val station = RadioStation(name, url)
                                    parsedStations.add(station)
                                    i++
                                }
                            }
                        }
                        i++
                    }

                    val detectedType = when {
                        fileName.contains("favoritos", ignoreCase = true) -> "FAVORITOS"
                        fileName.contains("online", ignoreCase = true) -> "ONLINE"
                        else -> "ONLINE"
                    }

                    importedStations = parsedStations
                    importType = detectedType

                    when (detectedType) {
                        "ONLINE" -> {
                            parsedStations.forEach { station ->
                                if (!stations.any { it.url == station.url }) {
                                    stations.add(station)
                                }
                            }
                            // ✅ GUARDAR INMEDIATAMENTE AL IMPORTAR
                            guardarRadioStations(context, stations)
                        }
                        "FAVORITOS" -> {
                            parsedStations.forEach { station ->
                                val track = FavoriteTrack(
                                    uri = station.url,
                                    title = station.name,
                                    artist = "Importado",
                                    album = "M3U Import"
                                )
                                favoritesManager.addFavorite(track)
                            }
                            favorites.clear()
                            favorites.addAll(favoritesManager.getAllFavorites())
                            favoritesCount = favorites.size
                        }
                    }

                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "✅ Importado: ${parsedStations.size} radios en $detectedType",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "❌ Error al importar: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    val internalExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { directoryUri: Uri? ->
        if (directoryUri == null) {
            Toast.makeText(context, "Exportación cancelada", Toast.LENGTH_SHORT).show()
            pendingExportType = null
            return@rememberLauncherForActivityResult
        }

        when (pendingExportType) {
            "FAVORITOS" -> {
                favoritesManager.exportFavoritesToSelectedFolder(directoryUri)
            }

            "ONLINE" -> {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val m3uContent = buildString {
                            append("#EXTM3U\n")
                            stations.forEach { station ->
                                append("#EXTINF:-1,${station.name}\n")
                                append("${station.url}\n")
                            }
                        }

                        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(directoryUri, flags)

                        val rootDocument = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, directoryUri)

                        if (rootDocument != null && rootDocument.exists() && rootDocument.isDirectory && rootDocument.canWrite()) {
                            val timestamp = java.text.SimpleDateFormat("ddMMyyyy_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                            val fileName = "playlist_online_$timestamp.m3u"

                            val oldFile = rootDocument.findFile(fileName)
                            try {
                                oldFile?.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val targetFile = rootDocument.createFile("audio/x-mpegurl", fileName)
                            if (targetFile != null) {
                                context.contentResolver.openOutputStream(targetFile.uri, "w")?.use { output ->
                                    output.write(m3uContent.toByteArray(Charsets.UTF_8))
                                    output.flush()
                                }

                                coroutineScope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "✅ Radios exportadas: $fileName",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                coroutineScope.launch(Dispatchers.Main) {
                                    Toast.makeText(context, "❌ Error al crear archivo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "❌ Carpeta no válida", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        coroutineScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, "❌ Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            else -> {
                Toast.makeText(
                    context,
                    "No se ha seleccionado qué exportar",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        pendingExportType = null
    }

    LaunchedEffect(Unit) {
        favorites.clear()
        favorites.addAll(favoritesManager.getAllFavorites())
        favoritesCount = favorites.size
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Añadir Emisora de Radio / M3U8",
                    color = Color(0xFF00FFFF),
                    fontSize = (18 * fontSizeScale).sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy((10 * fontSizeScale).dp)) {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nombre de la Emisora", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FFFF),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("URL Stream (.m3u8, .mp3, .aac)", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FFFF),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputName.isNotBlank() && inputUrl.isNotBlank()) {
                            var formattedUrl = inputUrl.trim()

                            if (!formattedUrl.startsWith("http://") &&
                                !formattedUrl.startsWith("https://")
                            ) {
                                formattedUrl = "https://$formattedUrl"
                            }

                            onAddStation(inputName.trim(), formattedUrl)

                            inputName = ""
                            inputUrl = ""
                            showDialog = false
                        } else {
                            Toast.makeText(
                                context,
                                "Rellena nombre y URL",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E501A))
                ) {
                    Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Limpiar Favoritos",
                    color = Color(0xFFFF6B6B),
                    fontSize = (18 * fontSizeScale).sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que quieres eliminar todos los favoritos? Esta acción no se puede deshacer.",
                    color = Color.White,
                    fontSize = (16 * fontSizeScale).sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.Main) {
                            favoritesManager.clearAllFavorites()
                            favorites.clear()
                            favoritesCount = 0
                            showClearDialog = false

                            Toast.makeText(
                                context,
                                "Todos los favoritos han sido eliminados",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
                ) {
                    Text("Eliminar Todo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding((16 * fontSizeScale).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((8 * fontSizeScale).dp)
        ) {
            TabButton(
                title = "FAVORITOS ($favoritesCount)",
                isSelected = selectedTab == "FAVORITOS",
                onClick = { selectedTab = "FAVORITOS" },
                modifier = Modifier.weight(1f),
                fontSizeScale = fontSizeScale
            )

            TabButton(
                title = "IMPORTAR",
                isSelected = selectedTab == "IMPORTAR",
                onClick = { selectedTab = "IMPORTAR" },
                modifier = Modifier.weight(1f),
                fontSizeScale = fontSizeScale
            )

            TabButton(
                title = "ONLINE",
                isSelected = selectedTab == "ONLINE",
                onClick = { selectedTab = "ONLINE" },
                modifier = Modifier.weight(1f),
                fontSizeScale = fontSizeScale
            )
        }

        Spacer(modifier = Modifier.height((16 * fontSizeScale).dp))

        when (selectedTab) {
            "FAVORITOS" -> {
                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📭",
                                fontSize = (64 * fontSizeScale).sp,
                                modifier = Modifier.padding(bottom = (16 * fontSizeScale).dp)
                            )

                            Text(
                                text = "Sin favoritos",
                                color = Color.White,
                                fontSize = (24 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Pulsa el corazón en Ahora Suena para añadir canciones",
                                color = Color.Gray,
                                fontSize = (16 * fontSizeScale).sp,
                                modifier = Modifier.padding(top = (12 * fontSizeScale).dp)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy((12 * fontSizeScale).dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = (8 * fontSizeScale).dp),
                            horizontalArrangement = Arrangement.spacedBy((8 * fontSizeScale).dp)
                        ) {
                            Button(
                                onClick = { showClearDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height((45 * fontSizeScale).dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8B0000)
                                ),
                                shape = RoundedCornerShape((8 * fontSizeScale).dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size((20 * fontSizeScale).dp)
                                )

                                Spacer(modifier = Modifier.width((8 * fontSizeScale).dp))

                                Text(
                                    text = "LIMPIAR TODO",
                                    color = Color.White,
                                    fontSize = (13 * fontSizeScale).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    pendingExportType = "FAVORITOS"
                                    internalExportLauncher.launch(null)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height((45 * fontSizeScale).dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0E501A)
                                ),
                                shape = RoundedCornerShape((8 * fontSizeScale).dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size((20 * fontSizeScale).dp)
                                )

                                Spacer(modifier = Modifier.width((8 * fontSizeScale).dp))

                                Text(
                                    text = "EXPORTAR",
                                    color = Color.White,
                                    fontSize = (13 * fontSizeScale).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy((12 * fontSizeScale).dp)
                        ) {
                            items(favorites) { track ->
                                FavoriteTrackCard(
                                    track = track,
                                    onPlay = {
                                        Log.d("PlaylistScreen", "❤️ Play favorito: ${track.title}")
                                        onPlayFavorite(Uri.parse(track.uri), track.title)
                                    },
                                    onDelete = {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            favoritesManager.removeFavorite(Uri.parse(track.uri))
                                            favorites.remove(track)
                                            favoritesCount = favorites.size

                                            Toast.makeText(
                                                context,
                                                "Eliminado de favoritos",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    fontSizeScale = fontSizeScale
                                )
                            }
                        }
                    }
                }
            }

            "IMPORTAR" -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy((12 * fontSizeScale).dp)
                ) {
                    Button(
                        onClick = {
                            m3uImportLauncher.launch("*/*")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((50 * fontSizeScale).dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E501A)),
                        shape = RoundedCornerShape((8 * fontSizeScale).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size((24 * fontSizeScale).dp)
                        )

                        Spacer(modifier = Modifier.width((8 * fontSizeScale).dp))

                        Text(
                            text = "📥 IMPORTAR M3U",
                            color = Color.White,
                            fontSize = (15 * fontSizeScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height((16 * fontSizeScale).dp))

                    Text(
                        text = "ℹ️ Selecciona un archivo .m3u para importar radios\n\n💡 Usa nombres como:\n• playlist_favoritos.m3u\n• playlist_online.m3u",
                        color = Color.Gray,
                        fontSize = (14 * fontSizeScale).sp,
                        modifier = Modifier.padding(horizontal = (12 * fontSizeScale).dp)
                    )

                    if (importedStations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height((16 * fontSizeScale).dp))

                        Text(
                            text = "✅ Importadas: ${importedStations.size} radios en $importType",
                            color = Color(0xFF00E676),
                            fontSize = (14 * fontSizeScale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = (12 * fontSizeScale).dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy((8 * fontSizeScale).dp)
                        ) {
                            items(importedStations) { station ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape((8 * fontSizeScale).dp))
                                        .background(Color(0xFF1E1E1E))
                                        .padding((12 * fontSizeScale).dp)
                                ) {
                                    Column {
                                        Text(
                                            station.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (14 * fontSizeScale).sp
                                        )
                                        Text(
                                            station.url,
                                            color = Color.Gray,
                                            fontSize = (11 * fontSizeScale).sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "ONLINE" -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy((12 * fontSizeScale).dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((8 * fontSizeScale).dp)
                    ) {
                        Button(
                            onClick = { showDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height((50 * fontSizeScale).dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E501A)),
                            shape = RoundedCornerShape((8 * fontSizeScale).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size((24 * fontSizeScale).dp)
                            )

                            Spacer(modifier = Modifier.width((8 * fontSizeScale).dp))

                            Text(
                                text = "+ AÑADIR",
                                color = Color.White,
                                fontSize = (13 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (stations.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "No hay emisoras online para exportar",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    pendingExportType = "ONLINE"
                                    internalExportLauncher.launch(null)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height((50 * fontSizeScale).dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E501A)),
                            shape = RoundedCornerShape((8 * fontSizeScale).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size((24 * fontSizeScale).dp)
                            )

                            Spacer(modifier = Modifier.width((8 * fontSizeScale).dp))

                            Text(
                                text = "EXPORTAR",
                                color = Color.White,
                                fontSize = (13 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height((8 * fontSizeScale).dp))

                    if (stations.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📻",
                                    fontSize = (64 * fontSizeScale).sp
                                )

                                Spacer(modifier = Modifier.height((12 * fontSizeScale).dp))

                                Text(
                                    text = "Sin emisoras online",
                                    color = Color.White,
                                    fontSize = (22 * fontSizeScale).sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height((8 * fontSizeScale).dp))

                                Text(
                                    text = "Pulsa + AÑADIR para guardar una emisora",
                                    color = Color.Gray,
                                    fontSize = (15 * fontSizeScale).sp
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy((12 * fontSizeScale).dp),
                            verticalArrangement = Arrangement.spacedBy((12 * fontSizeScale).dp)
                        ) {
                            items(stations) { station ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((75 * fontSizeScale).dp)
                                        .clip(RoundedCornerShape((8 * fontSizeScale).dp))
                                        .background(Color(0xFF0E501A))
                                        .border((2 * fontSizeScale).dp, Color(0xFF00FFFF), RoundedCornerShape((8 * fontSizeScale).dp))
                                        .padding((12 * fontSizeScale).dp)
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
                                                    Log.d("PlaylistScreen", "📻 Pulsado ONLINE: ${station.name}")
                                                    onPlayItem(Uri.parse(station.url), station.name)
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Radio,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size((20 * fontSizeScale).dp)
                                            )

                                            Text(
                                                text = station.name,
                                                color = Color.White,
                                                fontSize = (14 * fontSizeScale).sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                Log.d("PlaylistScreen", "🗑️ Eliminar ONLINE: ${station.name}")
                                                onDeleteStation(station)
                                            },
                                            modifier = Modifier.size((28 * fontSizeScale).dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar",
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
        }
    }
}

@Composable
fun FavoriteTrackCard(
    track: FavoriteTrack,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    fontSizeScale: Float = 1f
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((12 * fontSizeScale).dp))
            .background(Color(0xFF0E501A))
            .border((2 * fontSizeScale).dp, Color(0xFF00FFFF), RoundedCornerShape((12 * fontSizeScale).dp))
            .padding((16 * fontSizeScale).dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = (12 * fontSizeScale).dp)
            ) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = (18 * fontSizeScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = track.artist,
                    color = Color.Gray,
                    fontSize = (14 * fontSizeScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = track.album,
                    color = Color.Gray,
                    fontSize = (12 * fontSizeScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy((8 * fontSizeScale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size((48 * fontSizeScale).dp)
                        .clip(RoundedCornerShape((8 * fontSizeScale).dp))
                        .background(Color(0xFF00FFFF))
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color(0xFF0E501A),
                        modifier = Modifier.size((28 * fontSizeScale).dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size((48 * fontSizeScale).dp)
                        .clip(RoundedCornerShape((8 * fontSizeScale).dp))
                        .background(Color(0xFF8B0000))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color.White,
                        modifier = Modifier.size((28 * fontSizeScale).dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSizeScale: Float = 1f
) {
    Button(
        onClick = onClick,
        modifier = modifier.height((45 * fontSizeScale).dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF0E501A) else Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape((8 * fontSizeScale).dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = (13 * fontSizeScale).sp,
            fontWeight = FontWeight.Bold
        )
    }
}