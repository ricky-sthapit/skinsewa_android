package org.swasthaconnect.data.auth

import com.google.gson.annotations.SerializedName
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
import retrofit2.http.PartMap
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

    @GET("admin/invitations")
    suspend fun invitations(
        @Query("q") query: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 25,
    ): Response<InvitationListResponse>

    @POST("admin/invitations")
    suspend fun sendInvitation(@Body request: InvitationRequest): Response<InvitationMutationResponse>

    @POST("admin/invitations/{id}/resend")
    suspend fun resendInvitation(@Path("id") id: Long): Response<InvitationMutationResponse>

    @GET("cases")
    suspend fun cases(
        @Query("q") query: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 5,
    ): Response<CaseListResponse>

    @GET("cases/{id}")
    suspend fun caseDetails(@Path("id") id: Long): Response<CaseDetailResponse>

    @GET("case-dermatologists")
    suspend fun caseDermatologists(): Response<CaseDermatologistsResponse>

    @GET("places/provinces")
    suspend fun provinces(): Response<PlacesResponse>

    @GET("places/{id}/children")
    suspend fun placeChildren(@Path("id") id: Long): Response<PlacesResponse>

    @GET("patients/lookup")
    suspend fun lookupPatient(@Query("patient_code") patientCode: String): Response<PatientLookupResponse>

    @Multipart
    @POST("cases")
    suspend fun createCase(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part photos: List<MultipartBody.Part>,
    ): Response<CaseMutationResponse>

    @GET("cases/{id}/messages")
    suspend fun caseMessages(@Path("id") id: Long, @Query("after_id") afterId: Long = 0): Response<CaseMessagesResponse>

    @Multipart
    @POST("cases/{id}/messages")
    suspend fun sendCaseMessage(
        @Path("id") id: Long,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part photos: List<MultipartBody.Part>,
    ): Response<CaseMessageMutationResponse>

    @POST("cases/{id}/treatments")
    suspend fun prescribeCase(@Path("id") id: Long, @Body request: TreatmentRequest): Response<CaseMutationResponse>

    @Streaming
    @GET("cases/{caseId}/photos/{photoId}")
    suspend fun casePhoto(@Path("caseId") caseId: Long, @Path("photoId") photoId: Long): Response<ResponseBody>

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
data class AuthData(
    val user: User? = null,
    val token: String? = null,
    @SerializedName("expires_in") val expiresInSeconds: Long = 60 * 60L,
)
data class UserResponse(val data: UserData? = null, val message: String? = null)
data class UserData(val user: User? = null)
data class MessageResponse(val message: String? = null)
data class InvitationRequest(
    val email: String,
    @SerializedName("user_type") val userType: String,
    @SerializedName("facility_name") val facilityName: String? = null,
)
data class InvitationListResponse(val data: List<Invitation> = emptyList(), val meta: ResourcePage? = null)
data class InvitationMutationResponse(val data: Invitation? = null, val message: String? = null)
data class Invitation(
    val id: Long,
    val email: String,
    @SerializedName("user_type") val userType: String,
    val status: String,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)
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
data class CaseListResponse(val data: List<CaseSummary> = emptyList(), val meta: CaseListMeta? = null)
data class CaseListMeta(
    val total: Int = 0,
    @SerializedName("current_page") val currentPage: Int = 1,
    @SerializedName("last_page") val lastPage: Int = 1,
    @SerializedName("available_statuses") val availableStatuses: List<String> = emptyList(),
)
data class CaseDetailResponse(val data: CaseDetail? = null, val message: String? = null)
data class CaseMutationResponse(val data: CaseSummary? = null, val message: String? = null)
data class CaseMessagesResponse(val data: List<CaseMessage> = emptyList(), val meta: CaseListMeta? = null)
data class CaseMessageMutationResponse(val data: CaseMessage? = null, val message: String? = null)
data class CaseSummary(
    val id: Long,
    @SerializedName("case_number") val caseNumber: Long? = null,
    @SerializedName("patient_id") val patientId: Long? = null,
    @SerializedName("follow_up_of") val followUpOf: Long? = null,
    val status: String? = null,
    val priority: String? = null,
    val symptoms: String? = null,
    val finding: String? = null,
    val history: String? = null,
    val phc: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("closed_at") val closedAt: String? = null,
    val patient: CasePatient? = null,
    @SerializedName("assigned_doctor") val assignedDoctor: CaseUser? = null,
    val creator: CaseUser? = null,
    @SerializedName("photo_count") val photoCount: Int? = null,
    val treatments: List<CaseTreatment> = emptyList(),
    val photos: List<CasePhoto> = emptyList(),
)
data class CasePatient(
    val id: Long,
    @SerializedName("patient_code") val patientCode: String? = null,
    val name: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val phc: String? = null,
    val province: String? = null,
    val district: String? = null,
    val municipality: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val weight: String? = null,
)
data class CaseUser(
    val id: Long,
    val name: String? = null,
    val role: String? = null,
    @SerializedName("role_label") val roleLabel: String? = null,
    @SerializedName("facility_name") val facilityName: String? = null,
    val specialty: String? = null,
)
data class CasePhoto(
    val id: Long,
    val filename: String? = null,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("size_bytes") val sizeBytes: Long? = null,
    @SerializedName("sort_order") val sortOrder: Int? = null,
    val source: String? = null,
    val url: String? = null,
)
data class CaseTreatment(
    val id: Long,
    val diagnosis: String? = null,
    val treatment: String? = null,
    val prevention: String? = null,
    val referral: String? = null,
    @SerializedName("follow_up_at") val followUpAt: String? = null,
    val doctor: CaseUser? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)
data class CaseMessage(
    val id: Long,
    val detail: String? = null,
    val author: CaseUser? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val photos: List<CasePhoto> = emptyList(),
)
data class CasePermissions(
    @SerializedName("can_chat") val canChat: Boolean = false,
    @SerializedName("can_prescribe") val canPrescribe: Boolean = false,
)
data class CaseDetail(
    @SerializedName("case") val caseSummary: CaseSummary,
    val patient: CasePatient? = null,
    @SerializedName("health_worker") val healthWorker: CaseUser? = null,
    val dermatologist: CaseUser? = null,
    val photos: List<CasePhoto> = emptyList(),
    val messages: List<CaseMessage> = emptyList(),
    val treatments: List<CaseTreatment> = emptyList(),
    @SerializedName("previous_cases") val previousCases: List<CaseSummary> = emptyList(),
    val permissions: CasePermissions? = null,
)
data class CaseDermatologistsResponse(val data: List<CaseUser> = emptyList())
data class PlacesResponse(val data: List<PlaceOption> = emptyList())
data class PlaceOption(
    val id: Long,
    val name: String,
    @SerializedName("name_ne") val nameNepali: String? = null,
    val type: String? = null,
    val parent: Long? = null,
)
data class PatientLookupResponse(val data: PatientLookupData? = null, val message: String? = null)
data class PatientLookupData(val patient: CasePatient? = null, @SerializedName("previous_cases") val previousCases: List<CaseSummary> = emptyList())
data class TreatmentRequest(val diagnosis: String, val treatment: String, val prevention: String? = null, val referral: String? = null)
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
