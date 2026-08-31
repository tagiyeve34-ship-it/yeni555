package com.ailenezareti.panelapp.api

import com.ailenezareti.panelapp.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("login.php")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("children.php")
    suspend fun getChildren(): Response<ChildrenResponse>

    @GET("locations.php")
    suspend fun getLocations(
        @Query("child_id") childId: Int,
        @Query("range") range: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<LocationsResponse>

    @GET("calls.php")
    suspend fun getCalls(
        @Query("child_id") childId: Int,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("type") type: String = "all",
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<CallsResponse>

    @GET("alerts.php")
    suspend fun getAlerts(@Query("child_id") childId: Int): Response<AlertsResponse>

    @PUT("alerts.php")
    suspend fun markAlertRead(@Body body: MarkReadRequest): Response<SimpleStatus>

    @GET("zones.php")
    suspend fun getZones(@Query("child_id") childId: Int): Response<ZonesResponse>

    @POST("zones.php")
    suspend fun createZone(@Body body: ZoneSaveRequest): Response<SimpleStatus>

    @PUT("zones.php")
    suspend fun updateZone(@Body body: ZoneSaveRequest): Response<SimpleStatus>

    @HTTP(method = "DELETE", path = "zones.php", hasBody = true)
    suspend fun deleteZone(@Body body: ZoneDeleteRequest): Response<SimpleStatus>

    @POST("zones_check.php")
    suspend fun checkZones(@Body body: ZoneCheckRequest): Response<ZoneCheckResponse>

    @POST("register_push_token.php")
    suspend fun registerPushToken(@Body body: PushTokenRequest): Response<SimpleStatus>
}
