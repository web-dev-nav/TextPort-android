package com.brainandbolt.textport.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @GET("health")
    suspend fun health(): BasicResponse

    @POST("auth/request-code")
    suspend fun requestCode(@Body request: RequestCodeRequest): RequestCodeResponse

    @POST("auth/activate")
    suspend fun activate(@Body request: ActivateRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("messages/sync")
    suspend fun syncMessages(
        @Header("Authorization") authHeader: String,
        @Body request: SyncRequest
    ): BasicResponse

    @POST("account/pause")
    suspend fun pause(@Header("Authorization") authHeader: String): BasicResponse

    @POST("account/resume")
    suspend fun resume(@Header("Authorization") authHeader: String): BasicResponse

    @GET("account/export")
    suspend fun export(@Header("Authorization") authHeader: String): ExportResponse

    @POST("account/delete")
    suspend fun deleteAccount(@Header("Authorization") authHeader: String): BasicResponse
}
