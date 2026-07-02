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
                sendStateToArduino()
            },
            onError = {
                _message.value = it
            }
        )
    }

    private fun handleArduinoMessage(message: String) {
        _message.value = message

        if (message.startsWith("BAT:")) {
            val clean = message.removePrefix("BAT:")
            val percent = clean.substringBefore(",").toIntOrNull() ?: return

            viewModelScope.launch {
                val current = _dog.value ?: return@launch
                val updated = current.copy(battery = percent)
                _dog.value = updated
                repository.updateDog(updated)
                checkDeath()
            }
        }
    }

    fun moveForward() {
        bluetooth.sendCommand("F")

        FirestoreRepository.registrarAccion(
            accion = "CAMINAR_ADELANTE",
            resultado = "ENVIADO",
            onSuccess = {
                _message.value = "Movimiento enviado y registrado"
            },
            onError = {error ->
                _message.value = "Movimiento enviado, pero no se guardó: ${error.message}"
            }
        )
    }
    fun moveBack() = bluetooth.sendCommand("B")
    fun moveLeft() = bluetooth.sendCommand("L")
    fun moveRight() = bluetooth.sendCommand("R")
    fun stop() = bluetooth.sendCommand("S")

    fun feedCookie() {
        updateStats(
            hunger = 15,
            health = 10,
            energy = -10
        )
        bluetooth.sendCommand("E")
    }

    fun feedBone() {
        updateStats(
            hunger = 10,
            health = 5,
            energy = -5
        )
        bluetooth.sendCommand("E")
    }

    fun feedChili() {
        updateStats(
            hunger = 5,
            happiness = -30,
            energy = -15,
            health = -30
        )
        bluetooth.sendCommand("E")
    }

    fun playBall() {
        updateStats(
            hunger = -10,
            energy = -15,
            happiness = 15,
            health = 10
        )
        bluetooth.sendCommand("J")
    }

    fun playStick() {
        updateStats(
            hunger = -5,
            energy = -10,
            happiness = 10,
            health = 5
        )
        bluetooth.sendCommand("J")
    }

    fun petDog() {
        updateStats(
            happiness = 5
        )
        bluetooth.sendCommand("A")
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
}
