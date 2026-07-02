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

class DogViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = VoltsDatabase.getDatabase(application).dogDao()
    private val repository = DogRepository(dao)
    private val bluetooth = BluetoothController(application)

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
                    FirestoreRepository.registerConnectionEvent(
                        deviceId = deviceId,
                        result = "CONNECTED"
                    )
                }

                sendPhysicalCommand(
                    action = "CONNECT",
                    type = "CONNECTION",
                    commandValue = "CONNECT"
                )

                sendStateToArduino()
            },
            onError = {
                _message.value = it
            }
        )
    }

    private fun handleArduinoMessage(message: String) {
        _message.value = message

        when {
            message.startsWith("BATTERY|") -> handleBatteryMessage(message)
            message.startsWith("ACK|") -> handleAckMessage(message)
            message.startsWith("ERROR|") -> handleErrorMessage(message)
            message.startsWith("STATE|") -> handleStateMessage(message)
            message.startsWith("EVENT|BUTTON|") -> handleButtonEvent(message)
            message.startsWith("READY|") -> handleReadyMessage(message)

            message.startsWith("BAT:") -> handleOldBatteryMessage(message)

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
                bluetooth.sendCommand("Z")
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

        val average = listOf(
            current.hunger,
            current.happiness,
            current.energy,
            current.health,
            current.battery
        ).average()

        when {
            !current.alive -> bluetooth.sendCommand("M")
            average >= 70 -> bluetooth.sendCommand("V")
            average >= 35 -> bluetooth.sendCommand("Y")
            else -> bluetooth.sendCommand("X")
        }
    }

    private fun checkDeath() {
        viewModelScope.launch {
            val current = _dog.value ?: return@launch

            val reason = when {
                current.hunger <= 0 -> "Murió de hambre"
                current.happiness <= 0 -> "Murió de tristeza"
                current.energy <= 0 -> "Murió por agotamiento"
                current.health <= 0 -> "Murió por salud baja"
                current.battery <= 0 -> "Murió porque se descargó"
                else -> null
            }

            if (reason != null) {
                repository.killDog(current, reason)
                bluetooth.sendCommand("M")
                _message.value = reason
                _dog.value = null
            }
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
            val updated = current.copy(battery = percent.coerceIn(0, 100))

            _dog.value = updated
            repository.updateDogLocalOnly(updated)

            FirestoreRepository.saveBatteryTelemetryIfBucketChanged(
                deviceId = deviceId,
                percentage = percent.coerceIn(0, 100),
                voltage = voltage
            )

            checkDeath()
        }
    }

    private fun handleOldBatteryMessage(message: String) {
        val clean = message.removePrefix("BAT:")
        val percent = clean.substringBefore(",").toIntOrNull() ?: return

        viewModelScope.launch {
            val current = _dog.value ?: return@launch
            val updated = current.copy(battery = percent.coerceIn(0, 100))

            _dog.value = updated
            repository.updateDogLocalOnly(updated)

            FirestoreRepository.saveBatteryTelemetryIfBucketChanged(
                deviceId = deviceId,
                percentage = percent.coerceIn(0, 100),
                voltage = 0f
            )

            checkDeath()
        }
    }

    private fun handleAckMessage(message: String) {
        val parts = message.split("|")

        if (parts.size < 3) return

        val commandId = parts[1]
        val status = parts[2]

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

        viewModelScope.launch {
            val current = _dog.value ?: return@launch

            val updated = when (state) {
                "RESTING" -> current.copy(sleeping = true)
                "AWAKE" -> current.copy(sleeping = false)
                else -> current
            }

            _dog.value = updated
            repository.updateDog(updated)

            FirestoreRepository.registerEvent(
                deviceId = deviceId,
                type = "STATE",
                action = state,
                result = "RECEIVED"
            )
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
}
