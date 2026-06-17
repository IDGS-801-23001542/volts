package com.example.volts.network
import retrofit2.http.*

data class CloudDogRequest(
    val deviceId: String,
    val name: String,
    val ageDays: Int,
    val hunger: Int,
    val happiness: Int,
    val energy: Int,
    val health: Int,
    val battery: Int,
    val alive: Boolean
)

data class CloudDogResponse(
    val _id: String,
    val deviceId: String,
    val name: String,
    val ageDays: Int,
    val hunger: Int,
    val happiness: Int,
    val energy: Int,
    val health: Int,
    val battery: Int,
    val alive: Boolean
)

interface ApiService {

    @POST("dogs")
    suspend fun createDog(
        @Body dog: CloudDogRequest
    ): CloudDogResponse

    @GET("dogs/{deviceId}")
    suspend fun getActiveDog(
        @Path("deviceId") deviceId: String
    ): CloudDogResponse?

    @PUT("dogs/{id}")
    suspend fun updateDog(
        @Path("id") id: String,
        @Body dog: CloudDogRequest
    ): CloudDogResponse

    @POST("dogs/{id}/die")
    suspend fun killDog(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): CloudDogResponse
}
