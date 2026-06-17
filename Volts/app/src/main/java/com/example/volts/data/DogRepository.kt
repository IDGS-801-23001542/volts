package com.example.volts.data

import com.example.volts.network.CloudDogRequest
import com.example.volts.network.RetrofitClient

class DogRepository(private val dao: DogDao) {

    suspend fun getActiveDog(): DogEntity? {
        return dao.getActiveDog()
    }

    suspend fun createDog(deviceId: String, name: String) {
        val dog = DogEntity(
            deviceId = deviceId,
            name = name
        )

        dao.insertDog(dog)

        try {
            RetrofitClient.api.createDog(
                CloudDogRequest(
                    deviceId = deviceId,
                    name = name,
                    ageDays = 0,
                    hunger = 100,
                    happiness = 100,
                    energy = 100,
                    health = 100,
                    battery = 100,
                    alive = true
                )
            )
        } catch (_: Exception) {
        }
    }

    suspend fun updateDog(dog: DogEntity) {
        dao.updateDog(dog)
    }

    suspend fun killDog(dog: DogEntity, reason: String) {
        dao.updateDog(
            dog.copy(
                alive = false,
                deathReason = reason
            )
        )
    }

    suspend fun getGraveyard(): List<DogEntity> {
        return dao.getGraveyard()
    }
}
