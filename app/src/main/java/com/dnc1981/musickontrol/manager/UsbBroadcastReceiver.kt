package com.dnc1981.musickontrol.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log

class UsbBroadcastReceiver(
    private val onUsbAttached: () -> Unit,
    private val onUsbDetached: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Log.d("UsbReceiver", "🔌 USB CONECTADO")
                onUsbAttached()
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.d("UsbReceiver", "🔌 USB DESCONECTADO")
                onUsbDetached()
            }
        }
    }
}
