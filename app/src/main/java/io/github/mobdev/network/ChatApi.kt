package io.github.mobdev.network

import io.github.mobdev.data.LoginRequest
import io.github.mobdev.data.Message
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {

    @FormUrlEncoded
    @POST("addusr")
    suspend fun register(@Field("name") name: String): Response<String>

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<String>

    @POST("logout")
    suspend fun logout(): Response<Unit>

    @GET("channels")
    suspend fun channels(): Response<List<String>>

    @GET("channel/{channel}")
    suspend fun messages(
        @Path(value = "channel", encoded = false) channel: String,
        @Query("limit") limit: Int = 20,
        @Query("lastKnownId") lastKnownId: Long? = null,
        @Query("reverse") reverse: Boolean? = null
    ): Response<List<Message>>

    @POST("messages")
    suspend fun sendMessage(@Body message: Message): Response<String>
}
