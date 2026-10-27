package org.skinsewa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.skinsewa.core.designsystem.ThemePreferences
import org.skinsewa.data.auth.AuthApi
import org.skinsewa.data.auth.DeviceTokenRequest
import org.skinsewa.data.auth.SecureTokenStore

class SkinSewaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushTokenPreferences by lazy { getSharedPreferences("fcm_device", MODE_PRIVATE) }
    lateinit var tokenStore: SecureTokenStore
        private set
    lateinit var authApi: AuthApi
        private set
    lateinit var themePreferences: ThemePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = SecureTokenStore(getSharedPreferences("secure_session", MODE_PRIVATE))
        authApi = AuthApi.create(BuildConfig.API_BASE_URL, tokenStore)
        themePreferences = ThemePreferences(this)
        createCaseUpdatesChannel()
    }

    fun syncFcmToken(refreshedToken: String? = null) {
        if (tokenStore.read() == null) return
        if (refreshedToken != null) {
            registerFcmToken(refreshedToken)
            return
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(::registerFcmToken)
            .addOnFailureListener { error -> Log.w(TAG, "Couldn't retrieve the SkinSewa notification token.", error) }
    }

    private fun registerFcmToken(token: String) {
        if (token.isBlank() || tokenStore.read() == null) return
        pushTokenPreferences.edit().putString(PUSH_TOKEN, token).apply()
        applicationScope.launch {
            runCatching {
                val response = authApi.registerDeviceToken(DeviceTokenRequest(token))
                if (!response.isSuccessful) Log.w(TAG, "SkinSewa notification token registration failed with HTTP ${response.code()}.")
            }.onFailure { error -> Log.w(TAG, "Couldn't register the SkinSewa notification token.", error) }
        }
    }

    suspend fun unregisterCurrentFcmToken() {
        if (tokenStore.read() == null) return
        val token = pushTokenPreferences.getString(PUSH_TOKEN, null) ?: return
        runCatching {
            val response = authApi.unregisterDeviceToken(DeviceTokenRequest(token))
            if (response.isSuccessful) pushTokenPreferences.edit().remove(PUSH_TOKEN).apply()
            else Log.w(TAG, "SkinSewa notification token removal failed with HTTP ${response.code()}.")
        }.onFailure { error -> Log.w(TAG, "Couldn't remove the SkinSewa notification token.", error) }
    }

    private fun createCaseUpdatesChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_CASE_UPDATES,
            getString(org.skinsewa.R.string.case_updates_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(org.skinsewa.R.string.case_updates_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_CASE_UPDATES = "case_updates"
        private const val TAG = "SkinSewaFCM"
        private const val PUSH_TOKEN = "registration_token"
    }
}
