package com.dnc1981.musickontrol.activity

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import com.dnc1981.musickontrol.manager.FavoritesManager
import com.dnc1981.musickontrol.manager.DatabaseBackupManager
import com.dnc1981.musickontrol.manager.ExoPlayerManager
import com.dnc1981.musickontrol.manager.FontSizeManager
import com.dnc1981.musickontrol.navigation.MainLayout
import com.dnc1981.musickontrol.service.MediaSessionService
import com.dnc1981.musickontrol.ui.LocalFontSizeScale

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TAG = "MusicKontrol"
    }

    private lateinit var exportLauncher: ActivityResultLauncher<Uri?>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backupExportLauncher: ActivityResultLauncher<String>
    private lateinit var backupImportLauncher: ActivityResultLauncher<Array<String>>
    private val favoritesManager by lazy { FavoritesManager(this) }

    private var mediaSessionServiceBound = false

    private fun isAndroidAutomotive(): Boolean {
        return packageManager.hasSystemFeature("android.hardware.type.automotive")
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mediaSessionServiceBound = true
            Log.d(TAG, "✅ MediaSessionService conectado")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaSessionServiceBound = false
            Log.d(TAG, "❌ MediaSessionService desconectado")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val deniedPermissions = permissions.filterValues { !it }.keys.toList()

        if (deniedPermissions.isNotEmpty()) {
            val deniedList = deniedPermissions.joinToString("\n") { permission ->
                when (permission) {
                    Manifest.permission.READ_EXTERNAL_STORAGE -> "📁 Lectura de almacenamiento"
                    Manifest.permission.WRITE_EXTERNAL_STORAGE -> "📁 Escritura de almacenamiento"
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE -> "📁 Gestión de almacenamiento"
                    Manifest.permission.ACCESS_FINE_LOCATION -> "📍 Ubicación precisa (GPS)"
                    Manifest.permission.ACCESS_COARSE_LOCATION -> "📍 Ubicación aproximada"
                    Manifest.permission.MODIFY_AUDIO_SETTINGS -> "🔊 Configuración de audio"
                    Manifest.permission.INTERNET -> "🌐 Acceso a internet"
                    Manifest.permission.POST_NOTIFICATIONS -> "🔔 Notificaciones"
                    Manifest.permission.READ_MEDIA_AUDIO -> "🎵 Lectura de audio"
                    else -> permission
                }
            }

            Toast.makeText(
                this,
                "⚠️ Permisos denegados:\n$deniedList\n\nLa aplicación puede no funcionar correctamente.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "✅ Todos los permisos han sido otorgados",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "🚀 MainActivity creada")
            Log.d(TAG, "🚗 ¿Es AAOS? ${isAndroidAutomotive()}")
            Log.d(TAG, "📱 API Level: ${Build.VERSION.SDK_INT}")

            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )

            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )

            // ✅ REGISTRAR LAUNCHERS ANTES DE setContent
            exportLauncher = registerForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { directoryUri ->
                if (directoryUri != null) {
                    try {
                        favoritesManager.exportFavoritesToSelectedFolder(directoryUri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            this,
                            "Error al exportar: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(this, "Operación cancelada", Toast.LENGTH_SHORT).show()
                }
            }

            importLauncher = registerForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    try {
                        val fileUri = uris[0]
                        favoritesManager.importM3UFile(fileUri) { stations ->
                            Toast.makeText(
                                this,
                                "✅ Se importaron ${stations.size} estaciones",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            this,
                            "Error al importar: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(this, "Importación cancelada", Toast.LENGTH_SHORT).show()
                }
            }

            backupExportLauncher = registerForActivityResult(
                ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                if (uri != null) {
                    val success = DatabaseBackupManager.exportDatabase(this, uri)
                    Toast.makeText(
                        this,
                        if (success) "✅ Base de datos exportada correctamente" else "❌ Error al exportar la base de datos",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "Exportación cancelada", Toast.LENGTH_SHORT).show()
                }
            }

            backupImportLauncher = registerForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val success = DatabaseBackupManager.importDatabase(this, uri)
                    if (success) {
                        Toast.makeText(this, "✅ Base de datos importada. Reiniciando MusicKontrol...", Toast.LENGTH_LONG).show()
                        window.decorView.postDelayed({ recreate() }, 700L)
                    } else {
                        Toast.makeText(this, "❌ No se pudo importar la base de datos", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Importación cancelada", Toast.LENGTH_SHORT).show()
                }
            }

            // ✅ INICIALIZAR EXOPLAYER SINGLETON
            ExoPlayerManager.getInstance(this).apply {
                repeatMode = Player.REPEAT_MODE_OFF
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                playWhenReady = true
            }

            Log.d(TAG, "✅ ExoPlayer Singleton inicializado")

            requestRequiredPermissions()

            startMediaSessionService()

            // ✅ INICIALIZAR FONT SIZE MANAGER CON LIMPIEZA DE DATOS CORRUPTOS
            val fontSizeManager = FontSizeManager(this)

            val fontSizeScale = try {
                fontSizeManager.loadFontSizeScale()
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Datos corruptos en SharedPreferences, limpiando...", e)
                fontSizeManager.clearCorruptedData()
                fontSizeManager.loadFontSizeScale()
            }

            // ✅ CREAR MUTABLESTATE REACTIVO
            val fontSizeScaleState = mutableStateOf(fontSizeScale.scale)
            Log.d(TAG, "📖 Escala de fuente cargada: ${fontSizeScale.label} (${fontSizeScale.scale}x)")

            // ✅ SETCONTENT CON COMPOSITIONLOCALPROVIDER REACTIVO
            setContent {
                CompositionLocalProvider(value = LocalFontSizeScale provides fontSizeScaleState) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        color = Color.Black
                    ) {
                        // ✅ CORRECCIÓN: SIN PARÁMETRO fontSizeScaleState
                        MainLayout(
                            exportLauncher = exportLauncher,
                            importLauncher = importLauncher,
                            backupExportLauncher = backupExportLauncher,
                            backupImportLauncher = backupImportLauncher
                        )
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "❌ CRASH EN onCreate: ${e.message}", e)
            Toast.makeText(
                this,
                "Error fatal: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isAndroidAutomotive()) {
            return when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    Log.d(TAG, "🔊 AAOS - Botón VOLUMEN UP presionado → Siguiente canción")
                    ExoPlayerManager.getInstance(this).seekToNextMediaItem()
                    Toast.makeText(this, "⏭️ Siguiente", Toast.LENGTH_SHORT).show()
                    true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    Log.d(TAG, "🔊 AAOS - Botón VOLUMEN DOWN presionado → Canción anterior")
                    ExoPlayerManager.getInstance(this).seekToPreviousMediaItem()
                    Toast.makeText(this, "⏮️ Anterior", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isAndroidAutomotive()) {
            return when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN -> true
                else -> super.onKeyUp(keyCode, event)
            }
        }

        return super.onKeyUp(keyCode, event)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        permissions.add(Manifest.permission.INTERNET)
        permissions.add(Manifest.permission.MODIFY_AUDIO_SETTINGS)

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // MANAGE_EXTERNAL_STORAGE is a special app-access permission, not a
            // runtime permission. USB access in MusicKontrol is granted through
            // the Storage Access Framework, so do not request this here.
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "📋 Solicitando permisos: $missingPermissions")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d(TAG, "✅ Todos los permisos ya están otorgados")
        }
    }

    private fun startMediaSessionService() {
        try {
            val intent = Intent(this, MediaSessionService::class.java)
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "🎵 MediaSessionService iniciado")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "❌ Error iniciando MediaSessionService: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ MainActivity destruida")

        try {
            if (mediaSessionServiceBound) {
                unbindService(serviceConnection)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}