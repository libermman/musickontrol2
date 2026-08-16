package com.dnc1981.musickontrol.manager

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class FavoritesExporter(
    private val context: Context,
    private val activity: ComponentActivity
) {

    private lateinit var directoryPickerLauncher: ActivityResultLauncher<Uri?>

    init {
        setupDirectoryPicker()
    }

    /**
     * Configura el selector de carpeta
     */
    private fun setupDirectoryPicker() {
        directoryPickerLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                exportFavoritesToDirectory(uri)
            } else {
                Toast.makeText(context, "Operación cancelada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Abre el selector de carpeta
     */
    fun openDirectoryPicker() {
        try {
            directoryPickerLauncher.launch(null)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al abrir selector: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Exporta favoritos a la carpeta seleccionada
     */
    private fun exportFavoritesToDirectory(directoryUri: Uri) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "favoritos_$timestamp.m3u"

            // Obtener el URI del archivo a crear
            val fileUri = createFileInDirectory(directoryUri, fileName)

            if (fileUri != null) {
                writeM3uFile(fileUri)
                Toast.makeText(
                    context,
                    "✅ Favoritos exportados:\n$fileName",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "❌ Error al crear archivo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Crea un archivo en la carpeta seleccionada
     */
    private fun createFileInDirectory(directoryUri: Uri, fileName: String): Uri? {
        return try {
            val contentResolver = context.contentResolver
            val newFileUri = DocumentsContract.createDocument(
                contentResolver,
                directoryUri,
                "audio/x-mpegurl",
                fileName
            )
            newFileUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Escribe el contenido M3U en el archivo
     */
    private fun writeM3uFile(fileUri: Uri) {
        try {
            val contentResolver = context.contentResolver
            val outputStream = contentResolver.openOutputStream(fileUri)

            OutputStreamWriter(outputStream).use { writer ->
                writer.write("#EXTM3U\n")

                val favorites = getFavoritesFromDatabase()

                favorites.forEach { track ->
                    writer.write("#EXTINF:-1,${track.artist} - ${track.title}\n")
                    writer.write("${track.uri}\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al escribir archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Obtén tus favoritos desde FavoritesManager
     */
    private fun getFavoritesFromDatabase(): List<FavoriteTrack> {
        return try {
            val favoritesManager = FavoritesManager(context)
            favoritesManager.getAllFavorites()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}