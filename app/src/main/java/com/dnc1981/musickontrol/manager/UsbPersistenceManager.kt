package com.dnc1981.musickontrol.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class UsbPersistenceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("usb_persistence", Context.MODE_PRIVATE)

    fun guardarUsbUri(uri: Uri) {
        prefs.edit().putString("last_usb_uri", uri.toString()).apply()

        // El USB se abre en modo lectura. No pedimos escritura porque el selector
        // de Android puede conceder únicamente READ y un READ|WRITE posterior
        // provoca SecurityException en algunas implementaciones AAOS.
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun obtenerUsbUriGuardado(): Uri? {
        val uriString = prefs.getString("last_usb_uri", null) ?: return null
        return try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            null
        }
    }

    fun limpiarUsbUri() {
        prefs.edit().remove("last_usb_uri").apply()
    }

    fun verificarUsbDisponible(uri: Uri): Boolean {
        return try {
            val hasPersistedReadPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }

            if (!hasPersistedReadPermission) return false

            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.isDirectory
        } catch (e: Exception) {
            false
        }
    }
}