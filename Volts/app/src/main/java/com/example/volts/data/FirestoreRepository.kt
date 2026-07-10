package com.example.volts.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

object FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun dogRef(deviceId: String) =
        db.collection("dogs").document(deviceId)

    suspend fun createOrUpdateDog(deviceId: String, dog: DogEntity) {
        val data = hashMapOf(
            "deviceId" to dog.deviceId,
            "name" to dog.name,
            "ageDays" to dog.ageDays,
            "hunger" to dog.hunger,
            "happiness" to dog.happiness,
            "energy" to dog.energy,
            "health" to dog.health,
            "battery" to dog.battery,
            "alive" to dog.alive,
            "sleeping" to dog.sleeping,
            "deathReason" to dog.deathReason,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        dogRef(deviceId).set(data).await()
    }

    suspend fun registerEvent(
        deviceId: String,
        type: String,
        action: String,
        result: String,
        details: Map<String, Any?> = emptyMap(),
        commandId: String? = null
    ) {
        val data = hashMapOf(
            "type" to type,
            "action" to action,
            "result" to result,
            "details" to details,
            "commandId" to commandId,
            "createdAt" to FieldValue.serverTimestamp()
        )

        dogRef(deviceId)
            .collection("events")
            .add(data)
            .await()
    }

    suspend fun createCommand(
        deviceId: String,
        commandId: String,
        action: String,
        source: String = "ANDROID"
    ) {
        val data = hashMapOf(
            "commandId" to commandId,
            "action" to action,
            "status" to "REQUESTED",
            "source" to source,
            "requestedAt" to FieldValue.serverTimestamp()
        )

        dogRef(deviceId)
            .collection("commands")
            .document(commandId)
            .set(data)
            .await()
    }

    suspend fun updateCommandStatus(
        deviceId: String,
        commandId: String,
        status: String,
        errorCode: String? = null,
        durationMs: Long? = null
    ) {
        val data = mutableMapOf<String, Any?>(
            "commandId" to commandId,
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        when (status) {
            "SENT" -> data["sentAt"] = FieldValue.serverTimestamp()
            "RECEIVED" -> data["receivedAt"] = FieldValue.serverTimestamp()
            "STARTED" -> data["startedAt"] = FieldValue.serverTimestamp()
            "COMPLETED", "STOPPED" -> data["completedAt"] = FieldValue.serverTimestamp()
            "ERROR", "TIMEOUT", "DISCONNECTED" -> data["errorAt"] = FieldValue.serverTimestamp()
        }

        if (errorCode != null) data["errorCode"] = errorCode
        if (durationMs != null) data["durationMs"] = durationMs

        dogRef(deviceId)
            .collection("commands")
            .document(commandId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun saveBatteryTelemetryIfBucketChanged(
        deviceId: String,
        percentage: Int,
        voltage: Float
    ) {
        val bucket = batteryBucket(percentage)

        val dogSnapshot = dogRef(deviceId).get().await()
        val lastBucket = dogSnapshot.getLong("lastBatteryBucket")?.toInt()

        if (lastBucket == bucket) return

        val telemetry = hashMapOf(
            "type" to "BATTERY",
            "percentage" to percentage,
            "voltage" to voltage,
            "bucket" to bucket,
            "createdAt" to FieldValue.serverTimestamp()
        )

        dogRef(deviceId)
            .collection("telemetry")
            .add(telemetry)
            .await()

        dogRef(deviceId)
            .update("lastBatteryBucket", bucket)
            .await()
    }

    suspend fun registerConnectionEvent(
        deviceId: String,
        result: String,
        details: Map<String, Any?> = emptyMap()
    ) {
        registerEvent(
            deviceId = deviceId,
            type = "CONNECTION",
            action = "BLUETOOTH",
            result = result,
            details = details
        )
    }

    suspend fun registerError(
        deviceId: String,
        source: String,
        code: String,
        message: String
    ) {
        registerEvent(
            deviceId = deviceId,
            type = "ERROR",
            action = source,
            result = "ERROR",
            details = mapOf(
                "code" to code,
                "message" to message
            )
        )
    }

    private fun batteryBucket(percentage: Int): Int {
        return when {
            percentage >= 81 -> 100
            percentage >= 61 -> 80
            percentage >= 41 -> 60
            percentage >= 21 -> 40
            percentage >= 1 -> 20
            else -> 0
        }
    }

    fun listenRemoteCommand(
        deviceId: String,
        onCommandReceived: (command: String) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return dogRef(deviceId)
            .collection("remote")
            .document("current")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Error escuchando comando remoto")
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val command = snapshot.getString("command") ?: return@addSnapshotListener
                val status = snapshot.getString("status") ?: return@addSnapshotListener

                if (status == "PENDING") {
                    onCommandReceived(command)
                }
            }
    }

    suspend fun updateRemoteCommandStatus(
        deviceId: String,
        status: String,
        lastCommand: String? = null
    ) {
        val data = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (lastCommand != null) {
            data["lastCommand"] = lastCommand
        }

        dogRef(deviceId)
            .collection("remote")
            .document("current")
            .set(data, SetOptions.merge())
            .await()
    }
}