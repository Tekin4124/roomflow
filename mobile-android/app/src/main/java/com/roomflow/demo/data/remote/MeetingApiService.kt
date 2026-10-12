package com.roomflow.demo.data.remote

import com.roomflow.demo.data.model.DeviceRegistrationRequest
import com.roomflow.demo.data.model.DeviceRegistrationResponse
import com.roomflow.demo.data.model.RoomEventsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST

interface MeetingApiService {
    @POST("devices/{deviceId}/register")
    suspend fun registerDevice(
        @Path("deviceId") deviceId: String,
        @Header("x-api-key") apiKey: String,
        @Body request: DeviceRegistrationRequest
    ): DeviceRegistrationResponse

    @GET("rooms/{roomEmail}/events")
    suspend fun getRoomEvents(
        @Path("roomEmail") roomEmail: String,
        @Header("x-api-key") apiKey: String
    ): RoomEventsResponse
}
