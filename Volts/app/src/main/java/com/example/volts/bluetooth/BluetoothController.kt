package com.example.volts.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class BluetoothController(
    private val context: Context
) {

    companion object {
        private const val TAG = "BluetoothController"

        private const val DEVICE_NAME = "VOLTS_ESP32"

        private const val HANDSHAKE_TIMEOUT_MS = 4_000L

        private val SERIAL_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val mainHandler = Handler(Looper.getMainLooper())

    private val commandCounter =
        AtomicLong(System.currentTimeMillis())

    private val writeLock = Any()
    private val connectionLock = Any()

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var output: OutputStream? = null

    @Volatile
    private var reader: BufferedReader? = null

    @Volatile
    private var connecting = false

    @Volatile
    private var handshakeCompleted = false

    @Volatile
    private var intentionalDisconnect = false

    @Volatile
    private var handshakeCommandId: String? = null

    private var onMessageCallback: ((String) -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null

    /**
     * Se conserva este nombre para no romper el DogViewModel actual.
     * Internamente se conecta a VOLTS_ESP32, no a HC-05.
     */
    fun connectToHC05(
        onMessage: (String) -> Unit,
        onConnected: () -> Unit,
        onError: (String) -> Unit,
        onDisconnected: (() -> Unit)? = null
    ) {
        connectToVolts(
            onMessage = onMessage,
            onConnected = onConnected,
            onError = onError,
            onDisconnected = onDisconnected
        )
    }

    fun connectToVolts(
        onMessage: (String) -> Unit,
        onConnected: () -> Unit,
        onError: (String) -> Unit,
        onDisconnected: (() -> Unit)? = null
    ) {
        onMessageCallback = onMessage
        onConnectedCallback = onConnected
        onErrorCallback = onError
        onDisconnectedCallback = onDisconnected

        if (isConnected()) {
            postToMain {
                onConnectedCallback?.invoke()
            }
            return
        }

        if (connecting) {
            reportError("Ya se está intentando conectar a VOLTS")
            return
        }

        if (!hasConnectPermission()) {
            reportError("Falta el permiso BLUETOOTH_CONNECT")
            return
        }

        val adapter = bluetoothAdapter

        if (adapter == null) {
            reportError("Este dispositivo no admite Bluetooth")
            return
        }

        if (!adapter.isEnabled) {
            reportError("Bluetooth está desactivado")
            return
        }

        connecting = true
        intentionalDisconnect = false
        handshakeCompleted = false

        Thread {
            try {
                closeCurrentConnection(notifyDisconnected = false)

                if (hasScanPermission()) {
                    adapter.cancelDiscovery()
                }

                val device = adapter.bondedDevices.firstOrNull { device ->
                    device.name == DEVICE_NAME
                }

                if (device == null) {
                    throw IOException(
                        "No encontré $DEVICE_NAME. " +
                                "Primero empareja el ESP32 desde los ajustes Bluetooth."
                    )
                }

                val newSocket =
                    device.createRfcommSocketToServiceRecord(SERIAL_UUID)

                synchronized(connectionLock) {
                    socket = newSocket
                }

                newSocket.connect()

                val newOutput = newSocket.outputStream

                val newReader = BufferedReader(
                    InputStreamReader(
                        newSocket.inputStream,
                        Charsets.UTF_8
                    )
                )

                synchronized(connectionLock) {
                    output = newOutput
                    reader = newReader
                }

                connecting = false

                startReaderThread(
                    activeSocket = newSocket,
                    activeReader = newReader
                )

                sendHandshake()

            } catch (securityException: SecurityException) {
                connecting = false

                closeCurrentConnection(
                    notifyDisconnected = false
                )

                reportError(
                    "No se pudo acceder a Bluetooth: " +
                            "${securityException.message}"
                )
            } catch (exception: Exception) {
                connecting = false

                closeCurrentConnection(
                    notifyDisconnected = false
                )

                reportError(
                    "Error al conectar con VOLTS: " +
                            "${exception.message ?: "error desconocido"}"
                )
            }
        }.start()
    }

    /**
     * Envía comandos antiguos o comandos completos.
     *
     * Ejemplos admitidos:
     *
     * sendCommand("F")
     * sendCommand("MOVE_FORWARD")
     * sendCommand("CMD|123|MOVE_FORWARD")
     *
     * Devuelve el ID del comando enviado o null si falló.
     */
    fun sendCommand(
        command: String,
        onSent: ((commandId: String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): String? {
        if (!isConnected()) {
            val error = "VOLTS no está conectado"

            reportSendError(
                error = error,
                localCallback = onError
            )

            return null
        }

        val protocolMessage = buildProtocolMessage(command)

        if (protocolMessage == null) {
            val error = "Comando no reconocido: $command"

            reportSendError(
                error = error,
                localCallback = onError
            )

            return null
        }

        return try {
            writeLine(protocolMessage.message)

            postToMain {
                onSent?.invoke(protocolMessage.commandId)
            }

            protocolMessage.commandId

        } catch (exception: Exception) {
            val error =
                "No se pudo enviar el comando: " +
                        "${exception.message ?: "error desconocido"}"

            reportSendError(
                error = error,
                localCallback = onError
            )

            handleConnectionLost(error)

            null
        }
    }

    fun isConnected(): Boolean {
        return handshakeCompleted &&
                socket?.isConnected == true &&
                output != null &&
                reader != null
    }

    fun isConnecting(): Boolean {
        return connecting
    }

    fun disconnect() {
        intentionalDisconnect = true

        closeCurrentConnection(
            notifyDisconnected = true
        )
    }

    private fun sendHandshake() {
        val commandId = nextCommandId()

        handshakeCommandId = commandId

        try {
            writeLine(
                "CMD|$commandId|CONNECT"
            )
        } catch (exception: Exception) {
            reportError(
                "No se pudo enviar el mensaje de conexión: " +
                        "${exception.message}"
            )

            closeCurrentConnection(
                notifyDisconnected = true
            )

            return
        }

        mainHandler.postDelayed(
            {
                if (
                    !handshakeCompleted &&
                    handshakeCommandId == commandId
                ) {
                    reportError(
                        "VOLTS no confirmó la conexión"
                    )

                    closeCurrentConnection(
                        notifyDisconnected = true
                    )
                }
            },
            HANDSHAKE_TIMEOUT_MS
        )
    }

    private fun startReaderThread(
        activeSocket: BluetoothSocket,
        activeReader: BufferedReader
    ) {
        Thread {
            try {
                while (
                    socket === activeSocket &&
                    activeSocket.isConnected
                ) {
                    val message =
                        activeReader.readLine()
                            ?: throw IOException(
                                "El ESP32 cerró la conexión"
                            )

                    if (message.isBlank()) {
                        continue
                    }

                    processIncomingMessage(
                        message = message.trim()
                    )
                }
            } catch (exception: Exception) {
                if (
                    !intentionalDisconnect &&
                    socket === activeSocket
                ) {
                    handleConnectionLost(
                        exception.message
                            ?: "Se perdió la conexión Bluetooth"
                    )
                }
            }
        }.start()
    }

    private fun processIncomingMessage(
        message: String
    ) {
        Log.d(TAG, "ESP32 → Android: $message")

        val fields = message.split("|")

        if (
            fields.size >= 3 &&
            fields[0] == "ACK" &&
            fields[1] == handshakeCommandId &&
            fields[2] == "COMPLETED"
        ) {
            handshakeCompleted = true
            handshakeCommandId = null

            postToMain {
                onConnectedCallback?.invoke()
            }

            return
        }

        if (
            fields.size >= 3 &&
            fields[0] == "ERROR" &&
            fields[1] == handshakeCommandId
        ) {
            val errorCode = fields[2]

            reportError(
                "VOLTS rechazó la conexión: $errorCode"
            )

            closeCurrentConnection(
                notifyDisconnected = true
            )

            return
        }

        postToMain {
            onMessageCallback?.invoke(message)
        }
    }

    private fun buildProtocolMessage(
        command: String
    ): ProtocolMessage? {
        val cleanCommand = command.trim()

        if (cleanCommand.isEmpty()) {
            return null
        }

        if (
            cleanCommand.startsWith(
                prefix = "CMD|",
                ignoreCase = true
            )
        ) {
            val fields = cleanCommand.split("|")

            if (fields.size < 3) {
                return null
            }

            val commandId = fields[1]

            if (commandId.isBlank()) {
                return null
            }

            return ProtocolMessage(
                commandId = commandId,
                message = cleanCommand
            )
        }

        val action = when (
            cleanCommand.uppercase()
        ) {
            "C",
            "CONNECT" -> "CONNECT"

            "F",
            "MOVE_FORWARD" -> "MOVE_FORWARD"

            "B",
            "MOVE_BACK" -> "MOVE_BACK"

            "L",
            "TURN_LEFT" -> "TURN_LEFT"

            "R",
            "TURN_RIGHT" -> "TURN_RIGHT"

            "S",
            "STOP" -> "STOP"

            "E",
            "EAT" -> "EAT"

            "A",
            "PET" -> "PET"

            "J",
            "PLAY" -> "PLAY"

            "H",
            "HAPPY" -> "HAPPY"

            "Z",
            "SLEEP" -> "SLEEP"

            "W",
            "WAKE" -> "WAKE"

            "P",
            "PAIRING" -> "PAIRING"

            "Q",
            "GET_BATTERY" -> "GET_BATTERY"

            "V",
            "SET_STATE_HAPPY" -> "SET_STATE|HAPPY"

            "Y",
            "SET_STATE_WARNING" -> "SET_STATE|WARNING"

            "X",
            "SET_STATE_CRITICAL" -> "SET_STATE|CRITICAL"

            "M",
            "SET_STATE_DEAD" -> "SET_STATE|DEAD"

            "REVIVE" -> "REVIVE"

            else -> {
                if (
                    cleanCommand.contains("|") &&
                    !cleanCommand.startsWith("CMD|")
                ) {
                    cleanCommand.uppercase()
                } else {
                    return null
                }
            }
        }

        val commandId = nextCommandId()

        return ProtocolMessage(
            commandId = commandId,
            message = "CMD|$commandId|$action"
        )
    }

    private fun writeLine(
        message: String
    ) {
        val currentOutput =
            output ?: throw IOException(
                "No existe un canal de salida Bluetooth"
            )

        synchronized(writeLock) {
            val bytes =
                "$message\n".toByteArray(Charsets.UTF_8)

            currentOutput.write(bytes)
            currentOutput.flush()
        }

        Log.d(TAG, "Android → ESP32: $message")
    }

    private fun handleConnectionLost(
        reason: String
    ) {
        if (intentionalDisconnect) {
            return
        }

        handshakeCompleted = false
        connecting = false

        reportError(
            "Conexión Bluetooth perdida: $reason"
        )

        closeCurrentConnection(
            notifyDisconnected = true
        )
    }

    private fun closeCurrentConnection(
        notifyDisconnected: Boolean
    ) {
        val previousSocket: BluetoothSocket?
        val previousReader: BufferedReader?

        synchronized(connectionLock) {
            previousSocket = socket
            previousReader = reader

            socket = null
            output = null
            reader = null

            handshakeCompleted = false
            handshakeCommandId = null
            connecting = false
        }

        try {
            previousReader?.close()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Error al cerrar el lector Bluetooth",
                exception
            )
        }

        try {
            previousSocket?.close()
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Error al cerrar el socket Bluetooth",
                exception
            )
        }

        if (notifyDisconnected) {
            postToMain {
                onDisconnectedCallback?.invoke()
            }
        }
    }

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun nextCommandId(): String {
        return commandCounter.incrementAndGet().toString()
    }

    private fun reportSendError(
        error: String,
        localCallback: ((String) -> Unit)?
    ) {
        Log.e(TAG, error)

        postToMain {
            if (localCallback != null) {
                localCallback(error)
            } else {
                onErrorCallback?.invoke(error)
            }
        }
    }

    private fun reportError(
        error: String
    ) {
        Log.e(TAG, error)

        postToMain {
            onErrorCallback?.invoke(error)
        }
    }

    private fun postToMain(
        action: () -> Unit
    ) {
        mainHandler.post(action)
    }

    private data class ProtocolMessage(
        val commandId: String,
        val message: String
    )
}