package com.example.volts.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothController(private val context: Context) {

    companion object {
        private const val TAG = "VOLTS_BT"
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var reader: BufferedReader? = null

    @Volatile
    private var listening = false

    private val hc05UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connectToHC05(
        onMessage: (String) -> Unit,
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onError("Falta permiso Bluetooth")
            return
        }

        disconnect()

        thread {
            try {
                Log.d(TAG, "Intentando conectar con VOLTS_ESP32")

                val adapter = bluetoothAdapter
                    ?: throw IllegalStateException(
                        "Este dispositivo no tiene Bluetooth"
                    )

                if (!adapter.isEnabled) {
                    throw IllegalStateException(
                        "Bluetooth está desactivado"
                    )
                }

                val device = adapter.bondedDevices.firstOrNull {
                    it.name == "VOLTS_ESP32"
                } ?: throw IllegalStateException(
                    "No encontré VOLTS_ESP32. Primero empareja el dispositivo desde ajustes Bluetooth."
                )

                Log.d(
                    TAG,
                    "Dispositivo encontrado: ${device.name} - ${device.address}"
                )

                adapter.cancelDiscovery()

                val newSocket =
                    device.createRfcommSocketToServiceRecord(hc05UUID)

                socket = newSocket

                Log.d(TAG, "Abriendo socket Bluetooth")

                newSocket.connect()

                Log.d(TAG, "Socket Bluetooth conectado")

                output = newSocket.outputStream

                reader = BufferedReader(
                    InputStreamReader(newSocket.inputStream)
                )

                listening = true

                onConnected()

                sendCommand("C")

                listenForMessages(
                    onMessage = onMessage,
                    onError = onError
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error de conexión Bluetooth", e)

                closeResources()

                onError(
                    e.message ?: "Error Bluetooth"
                )
            }
        }
    }

    private fun listenForMessages(
        onMessage: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            while (listening) {
                val message = reader?.readLine()
                    ?: throw IllegalStateException(
                        "El ESP32 cerró la conexión Bluetooth"
                    )

                val cleanMessage = message.trim()

                if (cleanMessage.isNotEmpty()) {
                    Log.d(
                        TAG,
                        "<<< RECIBIDO: $cleanMessage"
                    )

                    onMessage(cleanMessage)
                }
            }

        } catch (e: Exception) {
            val wasListening = listening

            Log.e(
                TAG,
                "Error leyendo mensajes Bluetooth",
                e
            )

            closeResources()

            if (wasListening) {
                onError(
                    e.message
                        ?: "Se perdió la conexión con el ESP32"
                )
            }
        }
    }

    fun sendCommand(command: String): Boolean {
        val currentOutput = output

        if (currentOutput == null) {
            Log.e(
                TAG,
                "No se pudo enviar porque output es null: $command"
            )

            return false
        }

        return try {
            val message = "$command\n"

            Log.d(
                TAG,
                ">>> ENVIANDO: $command"
            )

            currentOutput.write(
                message.toByteArray(Charsets.UTF_8)
            )

            currentOutput.flush()

            Log.d(
                TAG,
                "Comando enviado correctamente"
            )

            true

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error enviando comando: $command",
                e
            )

            closeResources()

            false
        }
    }

    fun isConnected(): Boolean {
        val connected =
            socket?.isConnected == true &&
                    listening

        Log.d(
            TAG,
            "isConnected: $connected"
        )

        return connected
    }

    fun disconnect() {
        Log.d(TAG, "Desconectando Bluetooth")

        listening = false
        closeResources()
    }

    private fun closeResources() {
        listening = false

        try {
            reader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando reader", e)
        }

        try {
            output?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando output", e)
        }

        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando socket", e)
        }

        reader = null
        output = null
        socket = null

        Log.d(TAG, "Recursos Bluetooth cerrados")
    }
}
