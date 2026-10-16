package org.swasthaconnect.data.auth

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<MessageResponse>

    @GET("auth/me")
    suspend fun me(): Response<UserResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<MessageResponse>

    companion object {
        fun create(baseUrl: String, token: SecureTokenStore): AuthApi {
            val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    token.read()?.let { header("Authorization", "Bearer $it") }
                    header("Accept", "application/json")
                }.build()
                chain.proceed(request)
            }).build()
            return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
        }
    }
}

data class LoginRequest(val email: String, val password: String)
data class ForgotPasswordRequest(val email: String)
data class AuthResponse(val data: AuthData? = null, val message: String? = null)
data class AuthData(val user: User? = null, val token: String? = null)
data class UserResponse(val data: UserData? = null, val message: String? = null)
data class UserData(val user: User? = null)
data class MessageResponse(val message: String? = null)
data class User(
    val id: Long,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val name: String,
    val email: String,
    val role: String,
    val phone: String? = null,
    @SerializedName("facility_name") val facilityName: String? = null,
    val specialty: String? = null,
)
