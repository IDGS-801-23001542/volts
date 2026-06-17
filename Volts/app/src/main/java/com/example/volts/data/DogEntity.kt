package com.example.volts.data
import androidx.room.*

@Dao
interface DogDao {

    @Query("SELECT * FROM dogs WHERE alive = 1 LIMIT 1")
    suspend fun getActiveDog(): DogEntity?

    @Query("SELECT * FROM dogs WHERE alive = 0")
    suspend fun getGraveyard(): List<DogEntity>

    @Insert
    suspend fun insertDog(dog: DogEntity)

    @Update
    suspend fun updateDog(dog: DogEntity)

    @Query("SELECT COUNT(*) FROM dogs WHERE name = :name")
    suspend fun isNameUsed(name: String): Int
}