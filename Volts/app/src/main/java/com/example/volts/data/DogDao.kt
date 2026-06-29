package com.example.volts.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dogs")
data class DogEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Int = 0,
    val cloudId: String? = null,
    val deviceId: String,
    val name: String,
    val ageDays: Int = 0,
    val hunger: Int = 100,
    val happiness: Int = 100,
    val energy: Int = 100,
    val health: Int = 100,
    val battery: Int = 100,
    val alive: Boolean = true,
    val sleeping: Boolean = false,
    val color: String = "Blanco",
    val accessory: String = "Collar",
    val deathReason: String? = null
)
