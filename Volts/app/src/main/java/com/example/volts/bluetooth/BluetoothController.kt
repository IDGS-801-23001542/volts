package com.example.volts.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothController(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var input: InputStream? = null

    private val hc05UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connectToHC05(onMessage: (String) -> Unit, onConnected: () -> Unit, onError: (String) -> Unit) {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                onError("Falta permiso Bluetooth")
                return
            }

            val device = bluetoothAdapter
                ?.bondedDevices
                ?.firstOrNull { it.name == "HC-05" }

            if (device == null) {
                onError("No encontré HC-05. Primero empareja el módulo desde ajustes Bluetooth.")
                return
            }

            socket = device.createRfcommSocketToServiceRecord(hc05UUID)
            socket?.connect()

            output = socket?.outputStream
            input = socket?.inputStream

            sendCommand("C")
            onConnected()

            Thread {
                val buffer = ByteArray(1024)

                while (true) {
                    try {
                        val bytes = input?.read(buffer) ?: break
                        val message = String(buffer, 0, bytes).trim()
                        if (message.isNotEmpty()) {
                            onMessage(message)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }.start()

        } catch (e: Exception) {
            onError(e.message ?: "Error Bluetooth")
        }
    }

    fun sendCommand(command: String) {
        try {
            output?.write(command.toByteArray())
        } catch (_: Exception) {
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}

