package com.example.volts.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.volts.bluetooth.BluetoothController
import com.example.volts.data.DogEntity
import com.example.volts.data.DogRepository
import com.example.volts.data.VoltsDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.volts.data.FirestoreRepository
import java.util.UUID
import com.google.firebase.firestore.ListenerRegistration

class DogViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = VoltsDatabase.getDatabase(application).dogDao()
    private val repository = DogRepository(dao)
    private val bluetooth = BluetoothController(application)
    private var remoteCommandListener: ListenerRegistration? = null

    private val _dog = MutableStateFlow<DogEntity?>(null)
    val dog: StateFlow<DogEntity?> = _dog

    private val _message = MutableStateFlow("VOLTS listo")
    val message: StateFlow<String> = _message

    val deviceId: String = Settings.Secure.getString(
        application.contentResolver,
        Settings.Secure.ANDROID_ID
    )

    init {
        loadDog()
        startStatsTimer()
        startRemoteCommandListener()
    }

    private fun startRemoteCommandListener() {
        remoteCommandListener?.remove()

        remoteCommandListener = FirestoreRepository.listenRemoteCommand(
            deviceId = deviceId,
            onCommandReceived = { command ->
                handleRemoteCommand(command)
            },
            onError = { error ->
                _message.value = error
            }
        )
    }

    private fun handleRemoteCommand(command: String) {
        viewModelScope.launch {
            FirestoreRepository.updateRemoteCommandStatus(
                deviceId = deviceId,
                status = "SENDING",
                lastCommand = command
            )

            when (command) {
                "MOVE_FORWARD" -> moveForward()
                "MOVE_BACK" -> moveBack()
                "TURN_LEFT" -> moveLeft()
                "TURN_RIGHT" -> moveRight()
                "STOP" -> stop()

                "COOKIE" -> feedCookie()
                "BONE" -> feedBone()
                "CHILI" -> feedChili()

                "BALL" -> playBall()
                "STICK" -> playStick()

                "PET" -> petDog()
                "SLEEP" -> toggleSleep()

                else -> {
                    FirestoreRepository.updateRemoteCommandStatus(
                        deviceId = deviceId,
                        status = "ERROR",
                        lastCommand = command
                    )

                    _message.value = "Comando remoto inválido: $command"
                    return@launch
                }
            }

            FirestoreRepository.updateRemoteCommandStatus(
                deviceId = deviceId,
                status = "SENT",
                lastCommand = command
            )
        }
    }

    fun loadDog() {
        viewModelScope.launch {
            _dog.value = repository.getActiveDog()
        }
    }

    private fun startStatsTimer() {
        viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1000L)

                val current = _dog.value ?: continue

                if (!current.alive) continue

                if (current.sleeping) {
                    applySleepingTick()
                } else {
                    applyAwakeTick()
                }
            }
        }
    }

    private fun applyAwakeTick() {
        updateStats(
            hunger = -5,
            happiness = -5,
            energy = -5,
            health = -2
        )
    }

    private fun applySleepingTick() {
        updateStats(
            energy = 5,
            hunger = -10,
            health = -5
        )
    }

    fun createDog(name: String) {
        viewModelScope.launch {
            repository.createDog(deviceId, name)
            loadDog()
        }
    }

    fun connectBluetooth() {
        bluetooth.connectToHC05(
            onMessage = { handleArduinoMessage(it) },
            onConnected = {
                _message.value = "Conectado a VOLTS"

                viewModelScope.launch {
                    val current = _dog.value

                    if (current != null) {
                        val revived = current.copy(
                            alive = true,
                            battery = 100,
                            hunger = 100,
                            happiness = 100,
                            energy = 100,
                            health = 100,
                            deathReason = null,
                            sleeping = false
                        )

                        _dog.value = revived
                        repository.updateDog(revived)
                    }

                    FirestoreRepository.registerConnectionEvent(
                        deviceId = deviceId,
                        result = "CONNECTED"
                    )

                    sendPhysicalCommand("CONNECT", "CONNECTION", "CONNECT")
                    sendPhysicalCommand("SET_STATE_HAPPY", "STATE", "SET_STATE|HAPPY")
                }
            },
            onError = {
                _message.value = it
            }
        )
    }

    private fun handleArduinoMessage(message: String) {
        _message.value = message

        when {
            message.startsWith("BATTERY|") -> {
                _message.value = "Batería forzada: 100%"
                forceBattery100()
            }
            message.startsWith("BAT:") -> {
                _message.value = "Batería forzada: 100%"
                forceBattery100()
            }
            message.startsWith("ACK|") -> handleAckMessage(message)
            message.startsWith("ERROR|") -> handleErrorMessage(message)
            message.startsWith("STATE|") -> handleStateMessage(message)
            message.startsWith("EVENT|BUTTON|") -> handleButtonEvent(message)
            message.startsWith("READY|") -> handleReadyMessage(message)


            else -> {
                viewModelScope.launch {
                    FirestoreRepository.registerError(
                        deviceId = deviceId,
                        source = "BLUETOOTH",
                        code = "UNKNOWN_MESSAGE",
                        message = message
                    )
                }
            }
        }
    }

    private fun forceBattery100() {
        viewModelScope.launch {
            val current = _dog.value ?: return@launch

            val updated = current.copy(
                battery = 100,
                alive = true,
                hunger = 100,
                happiness = 100,
                energy = 100,
                health = 100,
                deathReason = null,
                sleeping = false
            )

            _dog.value = updated
            repository.updateDog(updated)
        }
    }

    fun moveForward() {
        sendPhysicalCommand("MOVE_FORWARD", "MOVEMENT", "MOVE_FORWARD")
    }

    fun moveBack() {
        sendPhysicalCommand("MOVE_BACK", "MOVEMENT", "MOVE_BACK")
    }

    fun moveLeft() {
        sendPhysicalCommand("TURN_LEFT", "MOVEMENT", "TURN_LEFT")
    }

    fun moveRight() {
        sendPhysicalCommand("TURN_RIGHT", "MOVEMENT", "TURN_RIGHT")
    }

    fun stop() {
        sendPhysicalCommand("STOP", "MOVEMENT", "STOP")
    }

    fun feedCookie() {
        sendPhysicalCommand(
            action = "COOKIE",
            type = "FEED",
            commandValue = "EAT",
            onVirtualAction = {
                updateStats(hunger = 15, health = 10, energy = -10)
            }
        )
    }

    fun feedBone() {
        sendPhysicalCommand(
            action = "BONE",
            type = "FEED",
            commandValue = "EAT",
            onVirtualAction = {
                updateStats(hunger = 10, health = 5, energy = -5)
            }
        )
    }

    fun feedChili() {
        sendPhysicalCommand(
            action = "CHILI",
            type = "FEED",
            commandValue = "EAT",
            onVirtualAction = {
                updateStats(hunger = 5, happiness = -30, energy = -15, health = -30)
            }
        )
    }

    fun playBall() {
        sendPhysicalCommand(
            action = "BALL",
            type = "PLAY",
            commandValue = "PLAY",
            onVirtualAction = {
                updateStats(hunger = -10, energy = -15, happiness = 15, health = 10)
            }
        )
    }

    fun playStick() {
        sendPhysicalCommand(
            action = "STICK",
            type = "PLAY",
            commandValue = "PLAY",
            onVirtualAction = {
                updateStats(hunger = -5, energy = -10, happiness = 10, health = 5)
            }
        )
    }

    fun petDog() {
        sendPhysicalCommand(
            action = "PET",
            type = "PET",
            commandValue = "PET",
            onVirtualAction = {
                updateStats(happiness = 5)
            }
        )
    }

    fun toggleSleep() {
        viewModelScope.launch {
            val current = _dog.value ?: return@launch

            val updated = current.copy(
                sleeping = !current.sleeping
            )

            _dog.value = updated
            repository.updateDog(updated)

            if (updated.sleeping) {
                _message.value = "VOLTS está dormido"
                sendPhysicalCommand("SLEEP", "STATE", "SLEEP")
            } else {
                _message.value = "VOLTS despertó"
            }

            sendStateToArduino()
            checkDeath()
        }
    }

    private fun updateStats(
        hunger: Int = 0,
        happiness: Int = 0,
        energy: Int = 0,
        health: Int = 0
    ) {
        viewModelScope.launch {
            val current = _dog.value ?: return@launch

            val updated = current.copy(
                hunger = (current.hunger + hunger).coerceIn(0, 100),
                happiness = (current.happiness + happiness).coerceIn(0, 100),
                energy = (current.energy + energy).coerceIn(0, 100),
                health = (current.health + health).coerceIn(0, 100)
            )

            _dog.value = updated
            repository.updateDog(updated)

            sendStateToArduino()
            checkDeath()
        }
    }

    private fun sendStateToArduino() {
        val current = _dog.value ?: return

        val fixedCurrent = current.copy(
            alive = true,
            battery = 100,
            hunger = current.hunger.coerceAtLeast(80),
            happiness = current.happiness.coerceAtLeast(80),
            energy = current.energy.coerceAtLeast(80),
            health = current.health.coerceAtLeast(80),
            deathReason = null
        )

        _dog.value = fixedCurrent

        viewModelScope.launch {
            repository.updateDog(fixedCurrent)
        }

        sendPhysicalCommand("SET_STATE_HAPPY", "STATE", "SET_STATE|HAPPY")
    }

    private fun checkDeath() {
        viewModelScope.launch {
            val current = _dog.value ?: return@launch

            val updated = current.copy(
                alive = true,
                battery = 100,
                deathReason = null
            )

            _dog.value = updated
            repository.updateDog(updated)
        }
    }

    private fun newCommandId(): String {
        return System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(8)
    }

    private fun sendPhysicalCommand(
        action: String,
        type: String,
        commandValue: String,
        onVirtualAction: (() -> Unit)? = null
    ) {
        if (commandValue.contains("DEAD", ignoreCase = true)) {
            _message.value = "DEAD bloqueado temporalmente"
            return
        }
        viewModelScope.launch {
            val commandId = newCommandId()

            try {
                FirestoreRepository.createCommand(
                    deviceId = deviceId,
                    commandId = commandId,
                    action = action
                )

                onVirtualAction?.invoke()

                bluetooth.sendCommand("CMD|$commandId|$commandValue")

                FirestoreRepository.updateCommandStatus(
                    deviceId = deviceId,
                    commandId = commandId,
                    status = "SENT"
                )

                _message.value = "$action enviado"

            } catch (error: Exception) {
                _message.value = "Error en $action: ${error.message}"

                FirestoreRepository.registerError(
                    deviceId = deviceId,
                    source = action,
                    code = "ANDROID_ERROR",
                    message = error.message ?: "Error desconocido"
                )
            }
        }
    }

    private fun handleBatteryMessage(message: String) {
        val parts = message.split("|")

        if (parts.size < 3) {
            viewModelScope.launch {
                FirestoreRepository.registerError(deviceId, "BATTERY", "INVALID_FORMAT", message)
            }
            return
        }

        val percent = parts[1].toIntOrNull()
        val voltage = parts[2].toFloatOrNull()

        if (percent == null || voltage == null) {
            viewModelScope.launch {
                FirestoreRepository.registerError(deviceId, "BATTERY", "INVALID_VALUE", message)
            }
            return
        }

        viewModelScope.launch {
            val current = _dog.value ?: return@launch
            val updated = current.copy(battery = 100)

            _dog.value = updated
            repository.updateDogLocalOnly(updated)

            FirestoreRepository.saveBatteryTelemetryIfBucketChanged(
                deviceId = deviceId,
                percentage = 100,
                voltage = 6.0f
            )

            //checkDeath()
        }
    }

    private fun handleOldBatteryMessage(message: String) {
        val clean = message.removePrefix("BAT:")
        val percent = clean.substringBefore(",").toIntOrNull() ?: return

        viewModelScope.launch {
            val current = _dog.value ?: return@launch
            val updated = current.copy(battery = 100)

            _dog.value = updated
            repository.updateDogLocalOnly(updated)

            FirestoreRepository.saveBatteryTelemetryIfBucketChanged(
                deviceId = deviceId,
                percentage = 100,
                voltage = 6.0f
            )

            //checkDeath()
        }
    }

    private fun handleAckMessage(message: String) {
        val parts = message.split("|")

        if (parts.size < 3) return

        val commandId = parts[1]
        val status = parts[2]

        // Ignora ACKs que no vienen de Firestore
        // Tus IDs reales tienen formato: tiempo-uuid
        if (!commandId.contains("-")) {
            _message.value = "ACK interno recibido: $status"
            return
        }

        val durationMs = if (parts.size >= 4) {
            parts[3].toLongOrNull()
        } else {
            null
        }

        viewModelScope.launch {
            FirestoreRepository.updateCommandStatus(
                deviceId = deviceId,
                commandId = commandId,
                status = status,
                durationMs = durationMs
            )

            if (status == "COMPLETED" || status == "STOPPED") {
                FirestoreRepository.registerEvent(
                    deviceId = deviceId,
                    type = "PHYSICAL_ACTION",
                    action = "COMMAND",
                    result = status,
                    details = mapOf("rawMessage" to message),
                    commandId = commandId
                )
            }

            _message.value = "Comando $status"
        }
    }

    private fun handleErrorMessage(message: String) {
        val parts = message.split("|")

        if (parts.size < 3) return

        val commandId = parts[1]
        val errorCode = parts[2]

        if (!commandId.contains("-")) {
            _message.value = "Error interno ESP32: $errorCode"
            return
        }

        viewModelScope.launch {
            FirestoreRepository.updateCommandStatus(
                deviceId = deviceId,
                commandId = commandId,
                status = "ERROR",
                errorCode = errorCode
            )

            FirestoreRepository.registerError(
                deviceId = deviceId,
                source = "ESP32",
                code = errorCode,
                message = message
            )

            _message.value = "Error ESP32: $errorCode"
        }
    }

    private fun handleStateMessage(message: String) {
        val state = message.substringAfter("STATE|")

        if (state == "DEAD") {
            _message.value = "Ignorando DEAD temporalmente"
            return
        }

        viewModelScope.launch {
            val current = _dog.value ?: return@launch

            val updated = when (state) {
                "RESTING" -> current.copy(sleeping = true)
                "AWAKE" -> current.copy(sleeping = false)
                else -> current
            }

            _dog.value = updated
            repository.updateDog(updated)
        }
    }

    private fun handleButtonEvent(message: String) {
        val action = message.substringAfter("EVENT|BUTTON|")

        viewModelScope.launch {
            FirestoreRepository.registerEvent(
                deviceId = deviceId,
                type = "BUTTON",
                action = action,
                result = "RECEIVED",
                details = mapOf("rawMessage" to message)
            )
        }
    }

    private fun handleReadyMessage(message: String) {
        viewModelScope.launch {
            FirestoreRepository.registerConnectionEvent(
                deviceId = deviceId,
                result = "READY",
                details = mapOf("message" to message)
            )
        }
    }

    override fun onCleared() {
        remoteCommandListener?.remove()
        super.onCleared()
    }
}
