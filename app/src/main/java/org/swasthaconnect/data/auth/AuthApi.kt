package org.swasthaconnect.data.auth

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegistrationRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<MessageResponse>

    @GET("auth/me")
    suspend fun me(): Response<UserResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<MessageResponse>

    @GET("resources")
    suspend fun resources(@Query("page") page: Int = 1, @Query("per_page") perPage: Int = 25): Response<ResourceListResponse>

    @Streaming
    @GET("resources/{id}/view")
    suspend fun viewResource(@Path("id") id: Long): Response<ResponseBody>

    @Multipart
    @POST("admin/resources")
    suspend fun createResource(
        @Part("display_name") displayName: RequestBody,
        @Part resourceFile: MultipartBody.Part,
    ): Response<ResourceMutationResponse>

    @Multipart
    @POST("admin/resources/{id}")
    suspend fun updateResource(
        @Path("id") id: Long,
        @Part("display_name") displayName: RequestBody,
        @Part resourceFile: MultipartBody.Part?,
    ): Response<ResourceMutationResponse>

    @DELETE("admin/resources/{id}")
    suspend fun deleteResource(@Path("id") id: Long): Response<MessageResponse>

    @GET("admin/dashboard")
    suspend fun adminDashboard(): Response<AdminDashboardResponse>

    @GET("cases")
    suspend fun cases(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 5,
    ): Response<CaseListResponse>

    companion object {
        fun create(baseUrl: String, token: SecureTokenStore): AuthApi {
            val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    removeHeader("Authorization")
                    token.read()?.takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
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
data class RegistrationRequest(
    val email: String,
    val otp: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val password: String,
    @SerializedName("password_confirmation") val passwordConfirmation: String,
    val phone: String? = null,
    @SerializedName("facility_name") val facilityName: String? = null,
    val specialty: String? = null,
)
data class AuthResponse(val data: AuthData? = null, val message: String? = null)
data class AuthData(val user: User? = null, val token: String? = null)
data class UserResponse(val data: UserData? = null, val message: String? = null)
data class UserData(val user: User? = null)
data class MessageResponse(val message: String? = null)
data class ResourceListResponse(val data: List<EducationalResource> = emptyList(), val meta: ResourcePage? = null)
data class ResourcePage(
    @SerializedName("current_page") val currentPage: Int = 1,
    @SerializedName("last_page") val lastPage: Int = 1,
    val total: Int = 0,
)
data class ResourceMutationResponse(val data: EducationalResource? = null, val message: String? = null)
data class EducationalResource(
    val id: Long,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long = 0,
    @SerializedName("created_at") val createdAt: String? = null,
)
data class AdminDashboardResponse(val data: AdminDashboardData? = null)
data class AdminDashboardData(
    val users: Int = 0,
    val cases: Int = 0,
    val resources: Int = 0,
    @SerializedName("cases_by_status") val casesByStatus: List<CaseStatusCount> = emptyList(),
)
data class CaseStatusCount(val status: String, val total: Int)
data class CaseListResponse(val data: List<JsonObject> = emptyList(), val meta: CaseListMeta? = null)
data class CaseListMeta(val total: Int = 0)
data class User(
    val id: Long,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val name: String,
    val email: String,
    val role: String,
    @SerializedName("user_type") val userType: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    val roles: List<String>? = null,
    @SerializedName("role_label") val roleLabel: String? = null,
    val phone: String? = null,
    @SerializedName("facility_name") val facilityName: String? = null,
    val specialty: String? = null,
) {
    val hasAdminAccess: Boolean
        get() = isAdmin || role == "ADMIN" || roles.orEmpty().contains("ADMIN")

    val clinicalRole: String?
        get() = userType?.takeIf { it == "MEDICAL_OFFICER" || it == "DERMATOLOGIST" }
            ?: role.takeIf { it == "MEDICAL_OFFICER" || it == "DERMATOLOGIST" }

    val displayRole: String
        get() = roleLabel ?: buildList {
            if (hasAdminAccess) add("Administrator")
            when (clinicalRole) {
                "MEDICAL_OFFICER" -> add("Medical Officer")
                "DERMATOLOGIST" -> add("Dermatologist")
            }
        }.joinToString(" · ").ifBlank { "Care team" }
}
