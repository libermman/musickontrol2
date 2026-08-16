package com.dnc1981.musickontrol.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import com.dnc1981.musickontrol.manager.RadioStation


data class FavoriteTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String
)

data class ImportResult(
    val message: String,
    val stations: List<Pair<String, String>>
)

class FavoritesManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("musickontrol_favorites", Context.MODE_PRIVATE)
    private val favoritesKey = "favorites_list"
    private val onlineStationsKey = "online_stations"

    // ✅ OBTENER TODOS LOS FAVORITOS
    fun getAllFavorites(): List<FavoriteTrack> {
        val json = prefs.getString(favoritesKey, "[]") ?: "[]"
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<FavoriteTrack>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ✅ AGREGAR FAVORITO
    fun addFavorite(track: FavoriteTrack) {
        try {
            val currentFavorites = getAllFavorites().toMutableList()
            val exists = currentFavorites.any { it.uri == track.uri }
            if (!exists) {
                currentFavorites.add(track)
                saveFavorites(currentFavorites)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ ELIMINAR FAVORITO
    fun removeFavorite(uri: Uri) {
        try {
            val currentFavorites = getAllFavorites().toMutableList()
            currentFavorites.removeAll { it.uri == uri.toString() }
            saveFavorites(currentFavorites)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ LIMPIAR TODOS LOS FAVORITOS
    fun clearAllFavorites() {
        try {
            prefs.edit().putString(favoritesKey, "[]").apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ VERIFICAR SI ES FAVORITO
    fun isFavorite(uri: Uri): Boolean {
        return getAllFavorites().any { it.uri == uri.toString() }
    }

    // ✅ GUARDAR ESTACIONES DE RADIO ONLINE EN SHAREPREFERENCES
    fun saveRadioStations(stations: List<RadioStation>) {
        try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(stations)
            prefs.edit().putString(onlineStationsKey, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ CARGAR ESTACIONES DE RADIO ONLINE DESDE SHAREPREFERENCES
    fun getRadioStations(): List<RadioStation> {
        return try {
            val json = prefs.getString(onlineStationsKey, "[]") ?: "[]"
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<RadioStation>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ✅ AGREGAR ESTACIÓN DE RADIO Y GUARDAR
    fun addRadioStation(station: RadioStation) {
        try {
            val currentStations = getRadioStations().toMutableList()
            if (!currentStations.any { it.url == station.url }) {
                currentStations.add(station)
                saveRadioStations(currentStations)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ✅ ELIMINAR ESTACIÓN DE RADIO Y GUARDAR
    fun removeRadioStation(station: RadioStation) {
        try {
            val currentStations = getRadioStations().toMutableList()
            currentStations.removeAll { it.url == station.url }
            saveRadioStations(currentStations)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openFolderPicker(launcher: ActivityResultLauncher<Uri?>) {
        try {
            launcher.launch(null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al abrir selector: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ EXPORTAR FAVORITOS A CARPETA SELECCIONADA
    fun exportFavoritesToSelectedFolder(treeUri: Uri?) {
        if (treeUri == null) {
            Toast.makeText(context, "Exportación cancelada", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val resultMessage = withContext(Dispatchers.IO) {
                exportFavoritesInternal(treeUri)
            }
            Toast.makeText(context, resultMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportFavoritesInternal(treeUri: Uri): String {
        return try {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val favorites = getAllFavorites()

            if (favorites.isEmpty()) {
                return "❌ No hay favoritos para exportar"
            }

            val rootDocument = DocumentFile.fromTreeUri(context, treeUri)

            if (rootDocument == null || !rootDocument.exists() || !rootDocument.isDirectory) {
                return "❌ Carpeta no válida"
            }

            if (!rootDocument.canWrite()) {
                return "❌ No hay permiso de escritura en esta carpeta"
            }

            val timestamp = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "playlist_favoritos_$timestamp.m3u"

            val oldFile = rootDocument.findFile(fileName)
            try {
                oldFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val targetFile = rootDocument.createFile("audio/x-mpegurl", fileName)
                ?: return "❌ No se pudo crear el archivo M3U"

            val m3uContent = buildFavoritesM3U(favorites)

            context.contentResolver.openOutputStream(targetFile.uri, "w")?.use { output ->
                output.write(m3uContent.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: return "❌ No se pudo abrir el archivo para escribir"

            "✅ Favoritos exportados: $fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            "❌ Error al exportar: ${e.localizedMessage ?: e.message ?: "Error desconocido"}"
        }
    }

    fun exportFavoritesToDirectory(directoryUri: Uri) {
        try {
            val timestamp = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "playlist_favoritos_$timestamp.m3u"

            val contentResolver = context.contentResolver

            val fileUri = DocumentsContract.createDocument(
                contentResolver,
                directoryUri,
                "audio/x-mpegurl",
                fileName
            )

            if (fileUri != null) {
                contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write("#EXTM3U\n")

                        val favorites = getAllFavorites()
                        favorites.forEach { track ->
                            writer.write("#EXTINF:-1,${track.artist} - ${track.title}\n")
                            writer.write("${track.uri}\n")
                        }
                    }
                }

                Toast.makeText(context, "✅ Exportado: $fileName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "❌ Error al crear archivo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "❌ Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportFavoritesToDownloads(context: Context): Boolean {
        return try {
            val timestamp = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "playlist_favoritos_$timestamp.m3u"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return exportViaMediaStore(context, fileName)
            } else {
                return exportViaFile(fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "❌ Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportViaMediaStore(context: Context, fileName: String): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write("#EXTM3U\n")

                        val favorites = getAllFavorites()
                        favorites.forEach { track ->
                            writer.write("#EXTINF:-1,${track.artist} - ${track.title}\n")
                            writer.write("${track.uri}\n")
                        }
                    }
                }

                Toast.makeText(context, "✅ Exportado: $fileName", Toast.LENGTH_LONG).show()
                true
            } else {
                Toast.makeText(context, "❌ No se pudo crear el archivo", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "❌ Error MediaStore: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun exportViaFile(fileName: String): Boolean {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val file = File(downloadsDir, fileName)

            file.bufferedWriter().use { writer ->
                writer.write("#EXTM3U\n")

                val favorites = getAllFavorites()
                favorites.forEach { track ->
                    writer.write("#EXTINF:-1,${track.artist} - ${track.title}\n")
                    writer.write("${track.uri}\n")
                }
            }

            Toast.makeText(context, "✅ Exportado: $fileName", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "❌ Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun saveFavorites(favorites: List<FavoriteTrack>) {
        try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(favorites)
            prefs.edit().putString(favoritesKey, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildFavoritesM3U(favorites: List<FavoriteTrack>): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")

        favorites.forEach { track ->
            val artist = track.artist.ifBlank { "Desconocido" }
            val title = track.title.ifBlank { "Sin título" }

            builder.append("#EXTINF:-1,$artist - $title\n")
            builder.append(track.uri)
            builder.append("\n")
        }

        return builder.toString()
    }

    fun openFilePicker(launcher: ActivityResultLauncher<Uri?>) {
        try {
            launcher.launch(null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al abrir selector: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun importM3UFile(fileUri: Uri?, callback: (List<Pair<String, String>>) -> Unit) {
        if (fileUri == null) {
            Toast.makeText(context, "Importación cancelada", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                importM3UInternal(fileUri)
            }

            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            callback(result.stations)
        }
    }

    private suspend fun importM3UInternal(fileUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val stations = mutableListOf<Pair<String, String>>()
            var currentName = ""

            context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()

                    if (trimmed.startsWith("#EXTINF")) {
                        val parts = trimmed.split(",", limit = 2)
                        if (parts.size > 1) {
                            currentName = parts[1].ifBlank { "Radio" }
                        }
                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        var url = trimmed

                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://$url"
                        }

                        if (currentName.isNotEmpty()) {
                            stations.add(currentName to url)
                            currentName = ""
                        }
                    }
                }
            }

            if (stations.isEmpty()) {
                ImportResult("❌ No se encontraron estaciones en el archivo", emptyList())
            } else {
                ImportResult("✅ Se importaron ${stations.size} estaciones", stations)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult("❌ Error al importar: ${e.localizedMessage}", emptyList())
        }
    }

    fun openFolderPickerForRadios(launcher: ActivityResultLauncher<Uri?>) {
        try {
            launcher.launch(null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al abrir selector: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ EXPORTAR RADIOS/ONLINE A CARPETA SELECCIONADA
    fun exportRadiosToSelectedFolder(treeUri: Uri?, stations: List<RadioStation>) {
        if (treeUri == null) {
            Toast.makeText(context, "Exportación cancelada", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val resultMessage = withContext(Dispatchers.IO) {
                exportRadiosInternal(treeUri, stations)
            }

            Toast.makeText(context, resultMessage, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun exportRadiosInternal(treeUri: Uri, stations: List<RadioStation>): String = withContext(Dispatchers.IO) {
        return@withContext try {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (stations.isEmpty()) {
                return@withContext "❌ No hay radios para exportar"
            }

            val rootDocument = DocumentFile.fromTreeUri(context, treeUri)

            if (rootDocument == null || !rootDocument.exists() || !rootDocument.isDirectory) {
                return@withContext "❌ Carpeta no válida"
            }

            if (!rootDocument.canWrite()) {
                return@withContext "❌ No hay permiso de escritura"
            }

            val timestamp = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "playlist_online_$timestamp.m3u"

            val oldFile = rootDocument.findFile(fileName)

            try {
                oldFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val targetFile = rootDocument.createFile("audio/x-mpegurl", fileName)
                ?: return@withContext "❌ No se pudo crear el archivo M3U"

            val m3uContent = buildRadiosM3U(stations)

            context.contentResolver.openOutputStream(targetFile.uri, "w")?.use { output ->
                output.write(m3uContent.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: return@withContext "❌ No se pudo abrir el archivo para escribir"

            "✅ Radios exportadas: $fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            "❌ Error al exportar: ${e.localizedMessage ?: e.message ?: "Error desconocido"}"
        }
    }

    private fun buildRadiosM3U(stations: List<RadioStation>): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")

        stations.forEach { station ->
            builder.append("#EXTINF:-1,${station.name}\n")
            builder.append(station.url)
            builder.append("\n")
        }

        return builder.toString()
    }
}