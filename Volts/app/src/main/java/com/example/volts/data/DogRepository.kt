package com.example.volts.data

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

        FirestoreRepository.createOrUpdateDog(
            deviceId = deviceId,
            dog = dog
        )
    }

    suspend fun updateDog(dog: DogEntity) {
        dao.updateDog(dog)

        FirestoreRepository.createOrUpdateDog(
            deviceId = dog.deviceId,
            dog = dog
        )
    }

    suspend fun updateDogLocalOnly(dog: DogEntity) {
        dao.updateDog(dog)
    }

    suspend fun killDog(dog: DogEntity, reason: String) {
        val updated = dog.copy(
            alive = false,
            deathReason = reason
        )

        dao.updateDog(updated)

        FirestoreRepository.createOrUpdateDog(
            deviceId = updated.deviceId,
            dog = updated
        )

        FirestoreRepository.registerEvent(
            deviceId = updated.deviceId,
            type = "DEATH",
            action = "DOG_DIED",
            result = "COMPLETED",
            details = mapOf("reason" to reason)
        )
    }

    suspend fun getGraveyard(): List<DogEntity> {
        return dao.getGraveyard()
    }
}