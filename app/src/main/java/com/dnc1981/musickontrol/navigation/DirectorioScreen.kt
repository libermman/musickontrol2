package com.dnc1981.musickontrol.navigation

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.dnc1981.musickontrol.database.MusicDatabaseHelper
import com.dnc1981.musickontrol.database.MusicScanner
import com.dnc1981.musickontrol.manager.UsbPersistenceManager
import com.dnc1981.musickontrol.ui.LocalFontSizeScale
import android.content.Context

private fun crearIntentParaUSB(context: android.content.Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

    return try {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val usbVolume = storageManager?.storageVolumes
            ?.firstOrNull { volume ->
                volume.isRemovable &&
                        !volume.isPrimary &&
                        volume.state == Environment.MEDIA_MOUNTED
            }

        usbVolume?.createOpenDocumentTreeIntent()
    } catch (e: Exception) {
        Log.e("DirectorioScreen", "❌ No se pudo preparar el selector USB: ${e.message}", e)
        null
    }
}

@Composable
fun DirectorioScreen(
    rootUri: Uri?,
    onRootUriChanged: (Uri?) -> Unit,
    navigationHistory: MutableList<DocumentFile>,
    currentElements: List<ElementoUsb>,
    onElementsChanged: (List<ElementoUsb>) -> Unit,
    onAudioSelected: (List<ElementoUsb>, Int) -> Unit,
    onShuffleAll: ((List<ElementoUsb>) -> Unit)? = null
) {

    val context = LocalContext.current
    val deepGreen = Color(0xFF0E501A)
    val accentCyan = Color(0xFF00FFFF)

    // ✅ OBTENER ESCALA DE FUENTE
    val fontSizeScale = LocalFontSizeScale.current.value

    val isLoading = remember { mutableStateOf(false) }
    val reloadKey = remember { mutableStateOf(0) }

    val usbPersistenceManager = remember { UsbPersistenceManager(context) }

    fun procesarUsbSeleccionado(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.w("DirectorioScreen", "⚠️ No se pudo persistir el permiso de lectura: ${e.message}")
        } catch (e: Exception) {
            Log.w("DirectorioScreen", "⚠️ Error persistiendo permiso: ${e.message}")
        }

        try {
            usbPersistenceManager.guardarUsbUri(uri)
            onRootUriChanged(uri)

            val rootDoc = DocumentFile.fromTreeUri(context, uri)
            if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
                isLoading.value = false
                Toast.makeText(context, "❌ No se pudo abrir la unidad USB", Toast.LENGTH_LONG).show()
                return
            }

            navigationHistory.clear()
            navigationHistory.add(rootDoc)
            isLoading.value = true

            Thread {
                try {
                    val elementos = listarElementosOptimizado(rootDoc)
                    (context as? Activity)?.runOnUiThread {
                        onElementsChanged(elementos)
                        isLoading.value = false
                    }

                    try {
                        Log.d("DirectorioScreen", "🔍 Iniciando escaneo de BD...")
                        val database = MusicDatabaseHelper(context)
                        val scanner = MusicScanner(context, database)
                        scanner.scan(rootDoc)
                    } catch (e: Exception) {
                        Log.e("DirectorioScreen", "❌ Error en escaneo: ${e.message}", e)
                    }
                } catch (e: Exception) {
                    Log.e("DirectorioScreen", "❌ Error leyendo USB: ${e.message}", e)
                    (context as? Activity)?.runOnUiThread {
                        isLoading.value = false
                        Toast.makeText(context, "❌ Error leyendo USB: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e("DirectorioScreen", "❌ Error procesando USB: ${e.message}", e)
            isLoading.value = false
            Toast.makeText(context, "❌ No se pudo abrir el USB: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val fallbackDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) procesarUsbSeleccionado(uri)
    }

    val usbDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            procesarUsbSeleccionado(uri)
        } else {
            Log.d("DirectorioScreen", "ℹ️ Selector USB cancelado o sin URI; usando selector general si el usuario lo vuelve a solicitar")
        }
    }

    fun lanzarSelectorUsb() {
        val usbIntent = crearIntentParaUSB(context)
        if (usbIntent != null) {
            Log.d("DirectorioScreen", "🔌 Abriendo selector específico de volumen USB")
            usbDirectoryLauncher.launch(usbIntent)
        } else {
            Log.d("DirectorioScreen", "📂 No se pudo detectar volumen USB montado; usando selector general")
            fallbackDirectoryLauncher.launch(null)
        }
    }

    // ✅ CARGAR USB GUARDADO AL INICIAR
    LaunchedEffect(Unit) {
        val savedUri = usbPersistenceManager.obtenerUsbUriGuardado()
        if (savedUri != null && usbPersistenceManager.verificarUsbDisponible(savedUri)) {
            onRootUriChanged(savedUri)

            val rootDoc = DocumentFile.fromTreeUri(context, savedUri)
            rootDoc?.let { doc ->
                navigationHistory.clear()
                navigationHistory.add(doc)

                isLoading.value = true
                Thread {
                    try {
                        val elementos = listarElementosOptimizado(doc)
                        onElementsChanged(elementos)
                        Log.d("DirectorioScreen", "✅ USB cargado automáticamente: $savedUri")
                    } finally {
                        isLoading.value = false
                    }
                }.start()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = "Unidad USB",
                color = Color.Green,
                fontSize = (32 * fontSizeScale).sp,
                fontWeight = FontWeight.Bold
            )

            if (rootUri != null) {

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    val allAudios = currentElements.filter { !it.isDirectory }

                    if (allAudios.isNotEmpty() && onShuffleAll != null) {

                        IconButton(
                            onClick = { onShuffleAll(allAudios) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Reproducir aleatoriamente",
                                tint = accentCyan,
                                modifier = Modifier.size((28 * fontSizeScale).dp)
                            )
                        }

                    }

                    IconButton(
                        onClick = { lanzarSelectorUsb() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "Cambiar unidad USB",
                            tint = Color.White,
                            modifier = Modifier.size((28 * fontSizeScale).dp)
                        )
                    }

                }

            }

        }

        if (rootUri == null) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Ninguna unidad USB conectada o indexada.",
                    color = Color.Gray,
                    fontSize = (18 * fontSizeScale).sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                    onClick = { lanzarSelectorUsb() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = deepGreen
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxHeight(0.1f)
                ) {

                    Icon(
                        Icons.Default.Usb,
                        contentDescription = null,
                        tint = Color.White
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        "SELECCIONAR UNIDAD USB / CARPETA",
                        color = Color.White,
                        fontSize = (16 * fontSizeScale).sp,
                        fontWeight = FontWeight.Bold
                    )

                }

            }

        } else {

            val currentFolder = navigationHistory.lastOrNull()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {

                if (navigationHistory.size > 1) {

                    IconButton(
                        onClick = {
                            try {
                                if (navigationHistory.size > 1) {
                                    navigationHistory.removeAt(navigationHistory.size - 1)

                                    if (navigationHistory.isNotEmpty()) {
                                        val parent = navigationHistory.last()

                                        isLoading.value = true
                                        Thread {
                                            try {
                                                val elementos = listarElementosOptimizado(parent)
                                                onElementsChanged(elementos)
                                            } finally {
                                                isLoading.value = false
                                            }
                                        }.start()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("DirectorioScreen", "❌ Error navegando atrás: ${e.message}")
                                e.printStackTrace()
                                isLoading.value = false
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }

                }

                Text(
                    text = currentFolder?.name ?: "Raíz",
                    color = Color.White,
                    fontSize = (18 * fontSizeScale).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )

                val audioCount = currentElements.count { !it.isDirectory }
                val folderCount = currentElements.count { it.isDirectory }

                if (audioCount > 0 || folderCount > 0) {
                    Text(
                        text = buildString {
                            if (folderCount > 0) append("$folderCount 📁  ")
                            if (audioCount > 0) append("$audioCount 🎵")
                        },
                        color = Color.Gray,
                        fontSize = (13 * fontSizeScale).sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                // ✅ BOTÓN DE RECARGA (CORREGIDO)
                IconButton(
                    onClick = {
                        reloadKey.value++

                        if (navigationHistory.isNotEmpty()) {
                            val currentFolder = navigationHistory.last()
                            isLoading.value = true

                            Toast.makeText(context, "🔄 Recargando...", Toast.LENGTH_SHORT).show()

                            Thread {
                                try {
                                    val elementos = listarElementosOptimizado(currentFolder)

                                    // ✅ CAMBIAR A MAIN THREAD PARA ACTUALIZAR UI
                                    (context as? Activity)?.runOnUiThread {
                                        onElementsChanged(elementos)
                                        isLoading.value = false
                                        Toast.makeText(context, "✅ Recarga completada", Toast.LENGTH_SHORT).show()
                                        Log.d("DirectorioScreen", "✅ Carpeta recargada: ${currentFolder.name}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("DirectorioScreen", "❌ Error recargando: ${e.message}", e)
                                    (context as? Activity)?.runOnUiThread {
                                        isLoading.value = false
                                        Toast.makeText(context, "❌ Error al recargar: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }.start()
                        } else {
                            Toast.makeText(context, "⚠️ No hay carpeta para recargar", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Recargar",
                        tint = accentCyan,
                        modifier = Modifier.size((24 * fontSizeScale).dp)
                    )
                }

            }

            if (isLoading.value) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "⏳ Cargando carpeta...",
                            color = accentCyan,
                            fontSize = (18 * fontSizeScale).sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Escaneando archivos...",
                            color = Color.Gray,
                            fontSize = (14 * fontSizeScale).sp
                        )
                    }
                }

            } else if (currentElements.isEmpty()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Carpeta vacía",
                        color = Color.Gray,
                        fontSize = (16 * fontSizeScale).sp
                    )
                }

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    itemsIndexed(currentElements) { _, elemento ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFF1E1E1E),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {

                                    try {

                                        if (elemento.isDirectory) {

                                            val targetDoc = currentFolder?.findFile(elemento.name)

                                            if (targetDoc != null && targetDoc.isDirectory) {

                                                navigationHistory.add(targetDoc)

                                                isLoading.value = true
                                                Thread {
                                                    try {
                                                        val elementos = listarElementosOptimizado(targetDoc)
                                                        onElementsChanged(elementos)
                                                    } finally {
                                                        isLoading.value = false
                                                    }
                                                }.start()

                                            }

                                        } else {

                                            val audioList = currentElements.filter { !it.isDirectory }
                                            val audioIndex = audioList.indexOfFirst { it.uri == elemento.uri }

                                            Log.d("DirectorioScreen", "🎵 Reproduciendo: ${elemento.name} (índice: $audioIndex)")

                                            if (audioIndex != -1) {
                                                onAudioSelected(audioList, audioIndex)
                                            }

                                        }

                                    } catch (e: Exception) {
                                        Log.e("DirectorioScreen", "❌ Error en click: ${e.message}")
                                        e.printStackTrace()
                                    }

                                }
                                .padding((16 * fontSizeScale).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector = if (elemento.isDirectory)
                                    Icons.Default.Folder
                                else
                                    Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (elemento.isDirectory)
                                    accentCyan
                                else
                                    Color(0xFF90EE90),
                                modifier = Modifier.size((28 * fontSizeScale).dp)
                            )

                            Spacer(modifier = Modifier.width((16 * fontSizeScale).dp))

                            Text(
                                text = elemento.name,
                                color = Color.White,
                                fontSize = (16 * fontSizeScale).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                        }

                    }

                }

            }

        }

    }

}

private fun listarElementosOptimizado(directory: DocumentFile): List<ElementoUsb> {

    val list = mutableListOf<ElementoUsb>()

    try {

        directory.listFiles().forEach { file ->

            if (file == null) return@forEach

            if (file.isDirectory) {

                list.add(
                    ElementoUsb(
                        name = file.name ?: "Carpeta sin nombre",
                        uri = file.uri,
                        isDirectory = true
                    )
                )

            } else {

                val name = file.name ?: return@forEach

                val esAudio = name.endsWith(".mp3", ignoreCase = true) ||
                        name.endsWith(".wav", ignoreCase = true) ||
                        name.endsWith(".flac", ignoreCase = true) ||
                        name.endsWith(".m4a", ignoreCase = true) ||
                        name.endsWith(".ogg", ignoreCase = true) ||
                        name.endsWith(".aac", ignoreCase = true)

                if (esAudio) {
                    list.add(
                        ElementoUsb(
                            name = name,
                            uri = file.uri,
                            isDirectory = false
                        )
                    )
                }

            }

        }

    } catch (e: Exception) {
        Log.e("DirectorioScreen", "❌ Error listando elementos: ${e.message}")
        e.printStackTrace()
    }

    return list.sortedWith(
        compareBy(
            { !it.isDirectory },
            { it.name.lowercase() }
        )
    )

}